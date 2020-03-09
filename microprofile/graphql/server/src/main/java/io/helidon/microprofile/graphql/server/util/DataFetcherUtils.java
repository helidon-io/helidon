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

import graphql.schema.DataFetcher;

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
     * @param clazz    {@link Class}
     * @param method   {@link Method}
     * @param args     Optional argument names
     * @param <V>      value type
     * @return a new {@link DataFetcher}
     */
    @SuppressWarnings("unchecked")
    public static <V> DataFetcher<V> newMethodDataFetcher(Class<?> clazz, Method method, String... args) {
        return environment -> {
            Constructor constructor = clazz.getConstructor();
            if (constructor == null) {
                throw new IllegalArgumentException("Class " + clazz.getName()
                + " must have a no-args constructor");
            }

            ArrayList<Object> listArgumentValues = new ArrayList<>();
            if (args.length > 0) {
                Arrays.stream(args).forEach(a -> listArgumentValues.add(environment.getArgument(a)));
            }

            return (V) method.invoke(constructor.newInstance(), listArgumentValues.toArray());
        };
    }
}
