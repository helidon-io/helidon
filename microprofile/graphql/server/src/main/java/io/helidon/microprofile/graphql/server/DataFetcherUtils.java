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
import java.math.BigInteger;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.logging.Logger;

import javax.enterprise.inject.spi.CDI;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.PropertyDataFetcher;
import graphql.GraphQLException;

import static io.helidon.microprofile.graphql.server.FormattingHelper.formatDate;
import static io.helidon.microprofile.graphql.server.FormattingHelper.formatNumber;
import static io.helidon.microprofile.graphql.server.FormattingHelper.getCorrectDateFormatter;
import static io.helidon.microprofile.graphql.server.FormattingHelper.getCorrectNumberFormat;
import static io.helidon.microprofile.graphql.server.SchemaGenerator.isFormatEmpty;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.ID;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.ensureRuntimeException;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.isDateTimeClass;

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
    @SuppressWarnings( { "unchecked", "rawtypes" })
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
                    } else if (key instanceof Collection) {
                        // handle collection type - just working on simple String type for the moment
                        // TODO: Need to handle collections of types
                        // TODO: need to handle formatting
                        // ensure we preserve the order
                        listArgumentValues.add(argument.getOriginalType().equals(List.class)
                                                       ? new ArrayList((Collection) key)
                                                       : new TreeSet((Collection) key));
                    } else {
                        // standard type or enum
                        Class<?> originalType = argument.getOriginalType();
                        if (originalType.isEnum()) {
                            Class<? extends Enum> enumClass = (Class<? extends Enum>) originalType;
                            listArgumentValues.add(Enum.valueOf(enumClass, key.toString()));
                        } else if (argument.getArgumentType().equals(ID)) {
                            // convert back to original data type
                            listArgumentValues.add(getOriginalIDValue(originalType, (String) key));
                        } else {
                            // check the format and convert it from a string to the original format
                            String[] format = argument.getFormat();
                            if (!isFormatEmpty(format)) {
                                if (key == null) {
                                    listArgumentValues.add(null);
                                } else {
                                    if (isDateTimeClass(argument.getOriginalType())) {
                                        DateTimeFormatter dateFormatter = getCorrectDateFormatter(originalType.getName(),
                                                                                                  format[1], format[0]);
                                        listArgumentValues.add(
                                                getOriginalDateTimeValue(originalType, dateFormatter.parse(key.toString())));
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
                                }
                            } else {
                                // process value in case we have to convert between say a Double/Float as they are interchangeable
                                listArgumentValues.add(getOriginalValue(originalType, key));
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
         *
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
            return formatNumber(super.get(environment), isScalar, numberFormat);
        }

        @Override
        public NumberFormat getNumberFormat() {
            return numberFormat;
        }
    }

    /**
     * An implementation of a {@link PropertyDataFetcher} which returns a formatted date.
     */
    @SuppressWarnings( { "unchecked", "rawtypes" })
    public static class DateFormattingDataFetcher
            extends PropertyDataFetcher
            implements DateFormattingProvider {

        /**
         * {@link DateTimeFormatter} to format with.
         */
        private DateTimeFormatter dateTimeFormatter;

        /**
         * Construct a new NumberFormattingDataFetcher.
         *
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
            return formatDate(super.get(environment), dateTimeFormatter);
        }

        @Override
        public DateTimeFormatter getDateTimeFormat() {
            return dateTimeFormatter;
        }
    }

    /**
     * Convert the ID type back to the original type for the method call.
     *
     * @param originalType original type
     * @param key          the key value passed in
     * @return the value as the original type
     */
    private static Object getOriginalIDValue(Class<?> originalType, String key) {
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

    /**
     * Convert the Object back to the original type for the method call.
     *
     * @param originalType original type
     * @param key          the key value passed in
     * @return the value as the original type
     */
    private static Object getOriginalValue(Class<?> originalType, Object key) {
        if (originalType.equals(Float.class) || originalType.equals(float.class)) {
            // key could be a float or double
            return key instanceof Double ? Float.valueOf(key.toString()) : (Float) key;
        } else if (originalType.equals(Long.class) || originalType.equals(long.class)) {
            // key could be BigInteger or long
            return Long.valueOf(key.toString());
        }

        return key;
    }

    /**
     * Return the original date/time value.
     *
     * @param originalType original type
     * @param value        the {@link TemporalAccessor} value
     * @return the original date/time value
     */
    private static TemporalAccessor getOriginalDateTimeValue(Class<?> originalType, TemporalAccessor value) {
        if (originalType.equals(LocalDateTime.class)) {
            return LocalDateTime.from(value);
        } else if (originalType.equals(LocalDate.class)) {
            return LocalDate.from(value);
        } else if (originalType.equals(ZonedDateTime.class)) {
            return ZonedDateTime.from(value);
        } else if (originalType.equals(OffsetDateTime.class)) {
            return OffsetDateTime.from(value);
        } else if (originalType.equals(LocalTime.class)) {
            return LocalTime.from(value);
        } else {
            return null;
        }
    }
}
