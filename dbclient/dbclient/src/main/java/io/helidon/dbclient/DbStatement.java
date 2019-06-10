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
package io.helidon.dbclient;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Database statement that can process parameters.
 * Method {@link #execute()} processes the statement and returns appropriate response.
 * <p>
 * All methods are non-blocking. The {@link #execute()} method returns either a {@link java.util.concurrent.CompletionStage}
 * or another object that provides similar API for eventual processing of the response.
 * <p>
 * Once parameters are set using one of the {@code params} methods, all other methods throw an
 * {@link IllegalStateException}.
 * <p>
 * Once a parameter is added using {@link #addParam(Object)} or {@link #addParam(String, Object)}, all other
 * {@code params} methods throw an {@link IllegalStateException}.
 * <p>
 * Once {@link #execute()} is called, all methods would throw an {@link IllegalStateException}.
 *
 * @param <R> Type of the result of this statement (e.g. a {@link java.util.concurrent.CompletionStage})
 * @param <D> Type of the descendant of this class
 */
public interface DbStatement<D extends DbStatement<D, R>, R> {
    /**
     * Configure parameters from a {@link java.util.List} by order.
     * The statement must use indexed parameters and configure them by order in the provided array.
     *
     * @param parameters ordered parameters to set on this statement, never null
     * @return updated db statement
     */
    D params(List<?> parameters);

    /**
     * Configure parameters from an array by order.
     * The statement must use indexed parameters and configure them by order in the provided array.
     *
     * @param parameters ordered parameters to set on this statement
     * @param <T>        type of the array
     * @return updated db statement
     */
    default <T> D params(T... parameters) {
        return params(Arrays.asList(parameters));
    }

    /**
     * Configure named parameters.
     * The statement must use named parameters and configure them from the provided map.
     *
     * @param parameters named parameters to set on this statement
     * @return updated db statement
     */
    D params(Map<String, ?> parameters);

    /**
     * Configure parameters using {@link Object} instance with registered mapper.
     * The statement must use named parameters and configure them from the map provided by mapper.
     *
     * @param parameters {@link Object} instance containing parameters
     * @param <T>        type of the parameters
     * @return updated db statement
     */
    <T> D namedParam(T parameters);

    /**
     * Configure parameters using {@link Object} instance with registered mapper.
     * The statement must use indexed parameters and configure them by order in the array provided by mapper.
     *
     * @param parameters {@link Object} instance containing parameters
     * @param <T>        type of the parameters
     * @return updated db statement
     */
    <T> D indexedParam(T parameters);

    /**
     * Add next parameter to the list of ordered parameters (e.g. the ones that use {@code ?} in SQL).
     *
     * @param parameter next parameter to set on this statement
     * @return updated db statement
     */
    D addParam(Object parameter);

    /**
     * Add next parameter to the map of named parameters (e.g. the ones that use {@code :name} in Helidon
     * JDBC SQL integration).
     *
     * @param name      name of parameter
     * @param parameter value of parameter
     * @return updated db statement
     */
    D addParam(String name, Object parameter);

    /**
     * Execute this statement using the parameters configured with {@code params} and {@code addParams} methods.
     *
     * @return The future with result of this statement.
     */
    R execute();
}
