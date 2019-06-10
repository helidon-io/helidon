/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.dbclient.common;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Db statement that does not support execution, only parameters.
 */
interface StatementParameters {
    /**
     * Configure parameters from a {@link java.util.List} by order.
     * The statement must use indexed parameters and configure them by order in the provided array.
     *
     * @param parameters ordered parameters to set on this statement, never null
     * @return updated db statement
     */
    StatementParameters params(List<?> parameters);

    /**
     * Configure parameters from an array by order.
     * The statement must use indexed parameters and configure them by order in the provided array.
     *
     * @param parameters ordered parameters to set on this statement
     * @param <T>        type of the array
     * @return updated db statement
     */
    default <T> StatementParameters params(T... parameters) {
        return params(Arrays.asList(parameters));
    }

    /**
     * Configure named parameters.
     * The statement must use named parameters and configure them from the provided map.
     *
     * @param parameters named parameters to set on this statement
     * @return updated db statement
     */
    StatementParameters params(Map<String, ?> parameters);

    /**
     * Configure parameters using {@link Object} instance with registered mapper.
     * The statement must use named parameters and configure them from the map provided by mapper.
     *
     * @param parameters {@link Object} instance containing parameters
     * @param <T>        type of the parameters
     * @return updated db statement
     */
    <T> StatementParameters namedParam(T parameters);

    /**
     * Configure parameters using {@link Object} instance with registered mapper.
     * The statement must use indexed parameters and configure them by order in the array provided by mapper.
     *
     * @param parameters {@link Object} instance containing parameters
     * @param <T>        type of the parameters
     * @return updated db statement
     */
    <T> StatementParameters indexedParam(T parameters);

    /**
     * Configure parameters using {@link Object} instance with registered mapper.
     *
     * @param parameters {@link Object} instance containing parameters
     * @param mapper     method to create map of statement named parameters mapped to values to be set
     * @param <T>        type of the parameters
     * @return updated db statement
     */
    default <T> StatementParameters namedParam(T parameters, Function<T, Map<String, ?>> mapper) {
        return params(mapper.apply(parameters));
    }

    /**
     * Configure parameters using {@link Object} instance with registered mapper.
     *
     * @param parameters {@link Object} instance containing parameters
     * @param mapper     method to create map of statement named parameters mapped to values to be set
     * @param <T>        type of the parameters
     * @return updated db statement
     */
    default <T> StatementParameters indexedParam(T parameters, Function<T, List<?>> mapper) {
        return params(mapper.apply(parameters));
    }

    /**
     * Add next parameter to the list of ordered parameters (e.g. the ones that use {@code ?} in SQL).
     *
     * @param parameter next parameter to set on this statement
     * @return updated db statement
     */
    StatementParameters addParam(Object parameter);

    /**
     * Add next parameter to the map of named parameters (e.g. the ones that use {@code :name} in Helidon
     * JDBC SQL integration).
     *
     * @param name      name of parameter
     * @param parameter value of parameter
     * @return updated db statement
     */
    StatementParameters addParam(String name, Object parameter);

    Map<String, Object> namedParams();

    List<Object> indexedParams();
}
