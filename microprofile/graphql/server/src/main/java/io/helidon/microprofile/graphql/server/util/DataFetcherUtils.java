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

package io.helidon.microprofile.graphql.server.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import graphql.schema.DataFetcher;
import io.helidon.microprofile.graphql.server.model.SchemaArgument;

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
     * @param args   optional {@link SchemaArgument}s
     * @param <V>    value type
     * @return a new {@link DataFetcher}
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <V> DataFetcher<V> newMethodDataFetcher(Class<?> clazz, Method method, SchemaArgument... args) {
        return environment -> {
            Constructor constructor = clazz.getConstructor();
            if (constructor == null) {
                throw new IllegalArgumentException("Class " + clazz.getName()
                                                           + " must have a no-args constructor");
            }

            ArrayList<Object> listArgumentValues = new ArrayList<>();
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
                        } else {
                            listArgumentValues.add(key);
                        }
                    }
                }
            }

            return (V) method.invoke(constructor.newInstance(), listArgumentValues.toArray());
        };
    }
}
