/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.microprofile.graphql.server;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import javax.enterprise.inject.spi.CDI;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.PropertyDataFetcher;
import graphql.schema.PropertyDataFetcherHelper;

import graphql.GraphQLException;

import static io.helidon.microprofile.graphql.server.FormattingHelper.getCorrectDateFormatter;
import static io.helidon.microprofile.graphql.server.FormattingHelper.getCorrectNumberFormat;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.ID;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.ensureRuntimeException;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.isDateTimeScalar;

/**
 * Utilities for working with {@link DataFetcher}s.
 */
public class DataFetcherUtils {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(DataFetcherUtils.class.getName());

    /**
     * Private constructor for utilities class.
     */
    private DataFetcherUtils() {
    }

    /**
     * Create a new {@link DataFetcher} for a {@link Class} and {@link Method} to be executed.
     *
     * @param clazz  {@link Class} to call
     * @param method {@link Method} to call
     * @param source defines the source for a @Source annotation - may be null
     * @param args   optional {@link SchemaArgument}s
     * @param <V>    value type
     * @return a new {@link DataFetcher}
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <V> DataFetcher<V> newMethodDataFetcher(Class<?> clazz, Method method, String source, SchemaArgument... args) {
        Object instance = CDI.current().select(clazz).get();

        return environment -> {
            ArrayList<Object> listArgumentValues = new ArrayList<>();
            // only one @Source annotation should be present and it should be the first argument
            if (source != null) {
                Class<?> sourceClazz;
                try {
                    sourceClazz = Class.forName(source);
                    listArgumentValues.add(sourceClazz.cast(environment.getSource()));
                } catch (ClassNotFoundException e) {
                    // this should not happen
                }
            }

            Context context = environment.getContext();

            if (args.length > 0) {
                for (SchemaArgument argument : args) {
                    Object key = environment.getArgument(argument.getArgumentName());
                    if (key instanceof Map) {
                        // this means the type is an input type so convert it to the correct class instance
                        listArgumentValues.add(JsonUtils.convertFromJson(JsonUtils.convertMapToJson((Map) key),
                                                                         argument.getOriginalType()));
                    } else {
                        // standard type or enum
                        Class<?> originalType = argument.getOriginalType();
                        if (originalType.isEnum()) {
                            Class<? extends Enum> enumClass = (Class<? extends Enum>) originalType;
                            listArgumentValues.add(Enum.valueOf(enumClass, key.toString()));
                        } else if (argument.getArgumentType().equals(ID)) {
                            // convert back to original data type
                            listArgumentValues.add(getOriginalValue(originalType, (String) key));
                        } else {
                            // check the format and convert it from a string to the original format
                            String[] format = argument.getFormat();
                            if (format != null && format.length == 2) {
                                if (isDateTimeScalar(argument.getArgumentType())) {
                                    DateTimeFormatter dateFormatter = getCorrectDateFormatter(originalType.getName(),
                                                                                              format[1], format[0]);
                                    listArgumentValues.add(dateFormatter.parse(key.toString()));
                                } else {
                                    NumberFormat numberFormat = getCorrectNumberFormat(originalType.getName(),
                                                                                       format[1], format[0]);
                                    if (numberFormat != null) {
                                        // convert to the original type
                                        Number parsedValue = numberFormat.parse(key.toString());
                                        Constructor<?> constructor = argument.getOriginalType()
                                                .getDeclaredConstructor(String.class);
                                        listArgumentValues.add(constructor.newInstance(parsedValue.toString()));
                                    } else {
                                       listArgumentValues.add(key);
                                    }
                                }
                            } else {
                                listArgumentValues.add(key);
                            }
                        }
                    }
                }
            }

            try {
                return (V) method.invoke(instance, listArgumentValues.toArray());
            } catch (InvocationTargetException e) {
                throw new GraphQLException(e.getTargetException());
            }
        };
    }

    /**
     * An implementation of a {@link PropertyDataFetcher} which returns a formatted number.
     */
    public static class NumberFormattingDataFetcher
            extends PropertyDataFetcher
            implements NumberFormattingProvider {

        /**
         * {@link NumberFormat} to format with.
         */
        private NumberFormat numberFormat;

        /**
         * Indicates if this is a scalar.
         */
        private boolean isScalar;

        /**
         * Construct a new NumberFormattingDataFetcher.
         * @param propertyName property to extract
         * @param type         GraphQL type of the property
         * @param valueFormat  formatting value
         * @param locale       formatting locale
         */
        public NumberFormattingDataFetcher(String propertyName, String type, String valueFormat, String locale) {
            super(propertyName);
            numberFormat = getCorrectNumberFormat(type, locale, valueFormat);
            if (numberFormat == null) {
                ensureRuntimeException(LOGGER, "Unable to get number format for property '"
                        + propertyName + "' and type '" + type + "'");
            }
            isScalar = SchemaGeneratorHelper.getScalar(type) != null;
        }

        @Override
        public Object get(DataFetchingEnvironment environment) {
            Object originalResult = super.get(environment);
            if (isScalar) {
                return new FormattedNumberImpl(numberFormat, originalResult != null ? numberFormat.format(originalResult) : null);
            }
            return originalResult != null ? numberFormat.format(originalResult) : null;
        }

        @Override
        public NumberFormat getNumberFormat() {
            return numberFormat;
        }
    }

    /**
     * An implementation of a {@link PropertyDataFetcher} which returns a formatted date.
     */
    public static class DateFormattingDataFetcher
            extends PropertyDataFetcher implements DateFormattingProvider {

        /**
         * {@link DateTimeFormatter} to format with.
         */
        private DateTimeFormatter dateTimeFormatter;

        /**
         * Construct a new NumberFormattingDataFetcher.
         * @param propertyName property to extract
         * @param type         GraphQL type of the property
         * @param valueFormat  formatting value
         * @param locale       formatting locale
         */
        public DateFormattingDataFetcher(String propertyName, String type, String valueFormat, String locale) {
            super(propertyName);
            dateTimeFormatter = getCorrectDateFormatter(type, locale, valueFormat);
        }

        @Override
        public Object get(DataFetchingEnvironment environment) {
            Object originalResult = super.get(environment);
            return originalResult instanceof TemporalAccessor
                    ? dateTimeFormatter.format((TemporalAccessor) originalResult) : null;
            }

        @Override
        public DateTimeFormatter getDateTimeFormat() {
            return dateTimeFormatter;
        }
    }

    /**
     * Create a new {@link DataFetcher} which formats a date/time.
     *
     * @param propertyName property to extract
     * @param valueFormat  formatting value
     * @param locale       formatting locale
     * @param <S>          type of the source
     * @return a new {@link DataFetcher}
     */
    public static <S> DataFetcher<String> newDateFormatPropertyDataFetcher(String propertyName,
                                                                           String valueFormat, String locale) {
        Locale actualLocale = SchemaGeneratorHelper.DEFAULT_LOCALE.equals(locale)
                ? Locale.getDefault()
                : Locale.forLanguageTag(locale);
        DateFormat dateFormat = new SimpleDateFormat(valueFormat, actualLocale);

        return environment -> {
            S source = environment.getSource();
            if (source == null) {
                return null;
            }
            Object rawValue = PropertyDataFetcherHelper
                    .getPropertyValue(propertyName, source, environment.getFieldType(), environment);

            return rawValue != null ? dateFormat.format(rawValue) : null;
        };
    }

    /**
     * Convert the ID type back to the original type for the method call.
     *
     * @param originalType original type
     * @param key          the key value passed in
     * @return the value as the original type
     */
    private static Object getOriginalValue(Class<?> originalType, String key) {
        if (originalType.equals(Long.class) || originalType.equals(long.class)) {
            return Long.parseLong(key);
        } else if (originalType.equals(Integer.class) || originalType.equals(int.class)) {
            return Integer.parseInt(key);
        } else if (originalType.equals(java.util.UUID.class)) {
            return UUID.fromString(key);
        } else {
            return key;
        }
    }
}
