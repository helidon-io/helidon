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

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.enterprise.inject.spi.CDI;

import graphql.GraphQLException;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.PropertyDataFetcher;

import static io.helidon.microprofile.graphql.server.FormattingHelper.formatDate;
import static io.helidon.microprofile.graphql.server.FormattingHelper.formatNumber;
import static io.helidon.microprofile.graphql.server.FormattingHelper.getCorrectDateFormatter;
import static io.helidon.microprofile.graphql.server.FormattingHelper.getCorrectNumberFormat;
import static io.helidon.microprofile.graphql.server.SchemaGenerator.isFormatEmpty;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.ID;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.ensureRuntimeException;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.isDateTimeClass;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.isPrimitiveArray;

/**
 * Utilities for working with {@link DataFetcher}s.
 */
public class DataFetcherUtils {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(DataFetcherUtils.class.getName());

    /**
     * Empty format.
     */
    private static final String[] EMPTY_FORMAT = new String[] {null, null };

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
     * @param schema {@link Schema} that created this {@link DataFetcher}
     * @param <V>    value type
     * @return a new {@link DataFetcher}
     */
    @SuppressWarnings("unchecked")
    public static <V> DataFetcher<V> newMethodDataFetcher(Schema schema, Class<?> clazz, Method method,
                                                          String source, SchemaArgument... args) {
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

            if (args.length > 0) {
                for (SchemaArgument argument : args) {
                    listArgumentValues.add(generateArgumentValue(schema, argument.getArgumentType(),
                                                                 argument.getOriginalType(),
                                                                 argument.getOriginalArrayType(),
                                                                 environment.getArgument(argument.getArgumentName()),
                                                                 argument.getFormat()));
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
     * Generate an argument value with the given information. This may be called recursively.
     *
     * @param schema    {@link Schema} to introspect if needed
     * @param argumentType the type of the argument
     * @param originalType if this is non null this means the array was a Collection and this is the type in the collection
     * @param originalArrayType the original type of the argument as a class
     * @param rawValue  raw value of the argument
     * @param format argument format
     * @return the argument value
     * @throws Exception
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    protected static Object generateArgumentValue(Schema schema, String argumentType, Class<?> originalType,
                                                  Class<?> originalArrayType,
                                                  Object rawValue, String[] format)
        throws Exception{
        if (rawValue instanceof Map) {
            // this means the type is an input type so convert it to the correct class instance
            SchemaInputType inputType = schema.getInputTypeByName(argumentType);

            // loop through the map and convert each entry
            Map<String, Object> map = (Map) rawValue;
            Map<String, Object> mapConverted = new HashMap<>();

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                // retrieve the Field Definition
                String fdName = entry.getKey();
                Object value  = entry.getValue();
                SchemaFieldDefinition fd = inputType.getFieldDefinitionByName(fdName);

                // check to see if the Field Definition return type is an input type
                SchemaInputType inputFdInputType = schema.getInputTypeByName(fd.getReturnType());
                if (inputFdInputType != null && value instanceof Map) {
                    mapConverted.put(fdName, generateArgumentValue(schema, inputFdInputType.getName(),
                                                                   Class.forName(inputFdInputType.getValueClassName()),
                                                                   null,
                                                                   value, EMPTY_FORMAT));
                } else {
                    if (fd.isJsonbFormat()) {
                        // don't deserialize using formatting as JsonB will do this for us
                        mapConverted.put(fdName, value);
                    } else {
                        mapConverted.put(fdName, parseArgumentValue(fd.getOriginalType(), fd.getReturnType(),
                                                                    value, fd.getFormat()));
                    }
                }
            }

            return JsonUtils.convertFromJson(JsonUtils.convertMapToJson(mapConverted), originalType);

        } else if (rawValue instanceof Collection) {
            SchemaInputType inputType = schema.getInputTypeByName(argumentType);

            Object colResults = null;
            boolean isArray = originalType.isArray();
            try {
                if (originalType.equals(List.class) || isArray) {
                    colResults = new ArrayList<>();
                } else if (originalType.equals(Set.class)
                           || originalType.equals(Collection.class)) {
                    colResults = new TreeSet<>();
                } else {
                    Constructor<?> constructor = originalType.getDeclaredConstructor();
                    colResults = constructor.newInstance();
                }
            } catch (Exception e) {
                ensureRuntimeException(LOGGER, "Unable to construct a List of type " + originalType
                        + " using Collection constructor", e);
            }

            if (inputType != null) {
                // handle complex types
                for (Object value : (Collection) rawValue) {
                    ((Collection) colResults).add(generateArgumentValue(schema, inputType.getName(),
                                                         originalArrayType, null,
                                                         value, EMPTY_FORMAT));
                }

                if (isArray) {
                    return ((List) colResults).toArray((Object[]) Array.newInstance(originalType.getComponentType(), 0));
                } else {
                    return colResults;
                }
            } else {
                // standard type or scalar so ensure we preserve the order and
                // convert any values with formats
                for (Object value : (Collection) rawValue) {
                    if (value instanceof Collection) {
                        ((Collection) colResults).add(generateArgumentValue(schema, argumentType,
                                                                            originalType, originalArrayType, value, format));
                    } else {
                       ((Collection) colResults).add(parseArgumentValue(originalArrayType, argumentType, value, format));
                    }
                }

                if (isArray) {
                    if (isPrimitiveArray(originalType)) {
                        // array of primitives
                        return generatePrimitiveArray((List) colResults, originalType, argumentType, format);
                    } else {
                        // array of Objects
                        return ((List) colResults).toArray((Object[]) Array.newInstance(originalType.getComponentType(), 0));
                    }
                }
                if (originalType.equals(List.class)) {
                    return (List) colResults;
                }
                if (originalType.equals(Collection.class)) {
                    return (Collection) colResults;
                }
                return colResults;
            }
        } else {
            return parseArgumentValue(originalType, argumentType, rawValue, format);
        }
    }

    /**
     * Return an array of primitives of the correct type from the given {@link List} of primitives.
     * @param results       results to process
     * @param originalType  the original type
     * @param argumentType  argument type
     * @param format format
     * @return an array of primitives of the correct type
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected static Object generatePrimitiveArray(List results, Class<?> originalType, String argumentType, String[] format) {
        Class<?> componentType = originalType.getComponentType();
        try {
            int i = 0;
            if (componentType.equals(byte.class)) {
                byte[] result = new byte[results.size()];
                for (Object value : results) {
                    result[i++] = Byte.parseByte(value.toString());  // this will come in as an Integer
                }
                return result;
            } else if (componentType.equals(char.class)) {
                char[] result = new char[results.size()];
                for (Object value : results) {
                    result[i++] = value.toString().charAt(0);
                }
                return result;
            } else if (componentType.equals(boolean.class)) {
                boolean[] result = new boolean[results.size()];
                for (Object value : results) {
                    result[i++] = Boolean.parseBoolean(value.toString());
                }
                return result;
            } else if (componentType.equals(short.class)) {
                short[] result = new short[results.size()];
                for (Object value : results) {
                    result[i++] = (short) parseArgumentValue(componentType, argumentType, value, format);
                }
                return result;
            } else if (componentType.equals(float.class)) {
                float[] result = new float[results.size()];
                for (Object value : results) {
                    result[i++] = (float) parseArgumentValue(componentType, argumentType, value, format);
                }
                return result;
            } else if (componentType.equals(int.class)) {
                int[] result = new int[results.size()];
                for (Object value : results) {
                    result[i++] = (int) parseArgumentValue(componentType, argumentType, value, format);
                }
                return result;
            } else if (componentType.equals(long.class)) {
                long[] result = new long[results.size()];
                for (Object value : results) {
                    result[i++] = (long) parseArgumentValue(componentType, argumentType, value, format);
                }
                return result;
            } else if (componentType.equals(double.class)) {
               double[] result = new double[results.size()];
                for (Object value : results) {
                    result[i++] = (double) parseArgumentValue(componentType, argumentType, value, format);
                }
                return result;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    /**
     * Parse the given {@link SchemaArgument} and key and return the correct value to match the method argument.
     *
     * @param originalType original type
     * @param argumentType argument type
     * @param rawValue the raw value
     * @param format format
     * @return the parsed value or the original value if no change
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected static Object parseArgumentValue(Class<?> originalType, String argumentType, Object rawValue, String[] format)
            throws Exception {

        if (originalType == null) {
            // is array type
            originalType = rawValue.getClass();
        }
        if (originalType.isEnum()) {
            Class<? extends Enum> enumClass = (Class<? extends Enum>) originalType;
            return Enum.valueOf(enumClass, rawValue.toString());
        } else if (argumentType.equals(ID) && rawValue != null) {
            // convert back to original data type
            return getOriginalIDValue(originalType, rawValue.toString());
        } else {
            // check the format and convert it from a string to the original format
            if (!isFormatEmpty(format)) {
                if (rawValue == null) {
                    return null;
                } else {
                    if (isDateTimeClass(originalType)) {
                        DateTimeFormatter dateFormatter = getCorrectDateFormatter(originalType.getName(),
                                                                                  format[1], format[0]);
                        LOGGER.info("Parsing [" + rawValue + "] with " + dateFormatter + " " + format[1] + ":" + format[0]);
                        return getOriginalDateTimeValue(originalType, dateFormatter.parse(rawValue.toString()));
                    } else {
                        NumberFormat numberFormat = getCorrectNumberFormat(originalType.getName(),
                                                                           format[1], format[0]);
                        if (numberFormat != null) {
                             return getOriginalValue(originalType, numberFormat.parse(rawValue.toString()));
                        } else {
                            return rawValue;
                        }
                    }
                }
            } else {
                // process value in case we have to convert between say a Double/Float as they are interchangeable
                return getOriginalValue(originalType, rawValue);
            }
        }
    }

    /**
     * An implementation of a {@link PropertyDataFetcher} which returns a formatted number.
     */
    public static class NumberFormattingDataFetcher
            extends PropertyDataFetcher {

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
    }

    /**
     * An implementation of a {@link PropertyDataFetcher} which returns a formatted date.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static class DateFormattingDataFetcher
            extends PropertyDataFetcher {

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
        Number numberKey = null;
        if (key instanceof Number) {
            // is a number that has be un-formatted
            numberKey = (Number) key;
        } else if (key instanceof String && originalType.isAssignableFrom(Number.class)) {
            // Is a number that has had no format
            try {
                Constructor<?> constructor = originalType.getDeclaredConstructor(String.class);
                numberKey = (Number) constructor.newInstance(key);
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException
                     | InvocationTargetException eIgnore) {
                // cannot find a constructor with String arg
            }
        }

        if (numberKey != null) {
            return  originalType.equals(Float.class) ? Float.valueOf(numberKey.floatValue())
                  : originalType.equals(float.class) ? numberKey.floatValue()
                  : originalType.equals(Integer.class) ? Integer.valueOf(numberKey.intValue())
                  : originalType.equals(int.class) ? numberKey.intValue()
                  : originalType.equals(Long.class) ? Long.valueOf(numberKey.longValue())
                  : originalType.equals(long.class) ? numberKey.longValue()
                  : originalType.equals(Double.class) ? Double.valueOf(numberKey.doubleValue())
                  : originalType.equals(double.class) ? numberKey.doubleValue()
                  : originalType.equals(Byte.class) ? Byte.valueOf(numberKey.byteValue())
                  : originalType.equals(byte.class) ? numberKey.byteValue()
                  : originalType.equals(Short.class) ? Short.valueOf(numberKey.shortValue())
                  : originalType.equals(short.class) ? numberKey.shortValue()
                  : originalType.equals(BigDecimal.class) ? BigDecimal.valueOf(numberKey.doubleValue())
                  : originalType.equals(BigInteger.class) ? BigInteger.valueOf(numberKey.longValue())
                  : key;
        }
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
