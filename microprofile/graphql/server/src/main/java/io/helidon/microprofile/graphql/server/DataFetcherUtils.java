/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Method;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import javax.enterprise.inject.spi.CDI;

import graphql.schema.DataFetcher;
import graphql.schema.PropertyDataFetcherHelper;

import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.ID;
import static io.helidon.microprofile.graphql.server.FormattingHelper.getCorrectFormat;

/**
 * Utilities for working with {@link DataFetcher}s.
 */
public class DataFetcherUtils {

    /**
     * Private constructor for utilities class.
     */
    private DataFetcherUtils() {
    }

    /**
     * Create a new {@link DataFetcher} for a {@link Class} and {@link Method}.
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
                            listArgumentValues.add(key);
                        }
                    }
                }
            }

            return (V) method.invoke(instance, listArgumentValues.toArray());
        };
    }

    /**
     * Create a new {@link DataFetcher} which formats a number.
     *
     * @param propertyName property to extract
     * @param type         GraphQL type of the property
     * @param valueFormat  formatting value
     * @param locale       formatting locale
     * @param <S>          type of the source
     * @return a new {@link DataFetcher}
     */
    public static <S> DataFetcher<String> newNumberFormatPropertyDataFetcher(String propertyName, String type, String valueFormat, String locale) {
        NumberFormat numberFormat = getCorrectFormat(type, locale, valueFormat);

        return environment -> {
            S source = environment.getSource();
            if (source == null) {
                return null;
            }
            Object rawValue = PropertyDataFetcherHelper
                    .getPropertyValue(propertyName, source, environment.getFieldType(), environment);

            return rawValue != null ? numberFormat.format(rawValue) : null;
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
