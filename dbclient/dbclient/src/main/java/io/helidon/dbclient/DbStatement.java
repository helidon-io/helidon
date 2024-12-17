/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Database statement that can process parameters.
 * <p>
 * Once parameters are set using one of the {@code params} methods, all other methods throw an
 * {@link IllegalStateException}.
 * <p>
 * Once a parameter is added using {@link #addParam(Object)} or {@link #addParam(String, Object)}, all other
 * {@code params} methods throw an {@link IllegalStateException}.
 *
 * @param <D> Type of the descendant of this class
 */
public interface DbStatement<D extends DbStatement<D>> {

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
     * @return updated db statement
     */
    default D params(Object... parameters) {
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
     * @return updated db statement
     */
    D namedParam(Object parameters);

    /**
     * Configure parameters using {@link Object} instance with registered mapper.
     * The statement must use indexed parameters and configure them by order in the array provided by mapper.
     *
     * @param parameters {@link Object} instance containing parameters
     * @return updated db statement
     */
    D indexedParam(Object parameters);

    /**
     * Configure parameters using {@link Object} instance with registered mapper.
     * The statement must use indexed parameters and configure them by order in the array provided by mapper.
     * Indexed parameters are generated using provided list of parameter names.
     *
     * @param parameters {@link Object} instance containing parameters
     * @param names parameter names from the map of statement named parameters returned by the
     *              {@link DbMapper#toNamedParameters(Object)} method
     * @return updated db statement
     */
    D indexedParam(Object parameters, String... names);

    /**
     * Add next parameter to the list of ordered parameters (e.g. the ones that use {@code ?} in SQL).
     *
     * @param parameter next parameter to set on this statement
     * @return updated db statement
     */
    D addParam(Object parameter);

    /**
     * Add next boolean parameter to the list of ordered parameters (e.g. the ones that use {@code ?} in SQL).
     *
     * @param parameter next parameter to set on this statement
     * @return updated db statement
     */
    D addParam(boolean parameter);

    /**
     * Add next {@link String} parameter to the list of ordered parameters (e.g. the ones that use {@code ?} in SQL).
     *
     * @param parameter next parameter to set on this statement
     * @return updated db statement
     */
    D addParam(String parameter);

    /**
     * Add next byte parameter to the list of ordered parameters (e.g. the ones that use {@code ?} in SQL).
     *
     * @param parameter next parameter to set on this statement
     * @return updated db statement
     */
    D addParam(byte parameter);

    /**
     * Add next short parameter to the list of ordered parameters (e.g. the ones that use {@code ?} in SQL).
     *
     * @param parameter next parameter to set on this statement
     * @return updated db statement
     */
    D addParam(short parameter);

    /**
     * Add next integer parameter to the list of ordered parameters (e.g. the ones that use {@code ?} in SQL).
     *
     * @param parameter next parameter to set on this statement
     * @return updated db statement
     */
    D addParam(int parameter);

    /**
     * Add next long parameter to the list of ordered parameters (e.g. the ones that use {@code ?} in SQL).
     *
     * @param parameter next parameter to set on this statement
     * @return updated db statement
     */
    D addParam(long parameter);

    /**
     * Add next float parameter to the list of ordered parameters (e.g. the ones that use {@code ?} in SQL).
     *
     * @param parameter next parameter to set on this statement
     * @return updated db statement
     */
    D addParam(float parameter);

    /**
     * Add next double parameter to the list of ordered parameters (e.g. the ones that use {@code ?} in SQL).
     *
     * @param parameter next parameter to set on this statement
     * @return updated db statement
     */
    D addParam(double parameter);

    /**
     * Add next {@link BigInteger} parameter to the list of ordered parameters (e.g. the ones that use {@code ?} in SQL).
     *
     * @param parameter next parameter to set on this statement
     * @return updated db statement
     */
    D addParam(BigInteger parameter);

    /**
     * Add next {@link BigDecimal} parameter to the list of ordered parameters (e.g. the ones that use {@code ?} in SQL).
     *
     * @param parameter next parameter to set on this statement
     * @return updated db statement
     */
    D addParam(BigDecimal parameter);

    /**
     * Add next byte[] parameter to the list of ordered parameters (e.g. the ones that use {@code ?} in SQL).
     *
     * @param parameter next parameter to set on this statement
     * @return updated db statement
     */
    D addParam(byte[] parameter);

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
     * Add next boolean parameter to the map of named parameters (e.g. the ones that use {@code :name} in Helidon
     * JDBC SQL integration).
     *
     * @param name      name of parameter
     * @param parameter value of parameter
     * @return updated db statement
     */
    D addParam(String name, boolean parameter);

    /**
     * Add next {@link String} parameter to the map of named parameters (e.g. the ones that use {@code :name} in Helidon
     * JDBC SQL integration).
     *
     * @param name      name of parameter
     * @param parameter value of parameter
     * @return updated db statement
     */
    D addParam(String name, String parameter);

    /**
     * Add next byte parameter to the map of named parameters (e.g. the ones that use {@code :name} in Helidon
     * JDBC SQL integration).
     *
     * @param name      name of parameter
     * @param parameter value of parameter
     * @return updated db statement
     */
    D addParam(String name, byte parameter);

    /**
     * Add next short parameter to the map of named parameters (e.g. the ones that use {@code :name} in Helidon
     * JDBC SQL integration).
     *
     * @param name      name of parameter
     * @param parameter value of parameter
     * @return updated db statement
     */
    D addParam(String name, short parameter);

    /**
     * Add next int parameter to the map of named parameters (e.g. the ones that use {@code :name} in Helidon
     * JDBC SQL integration).
     *
     * @param name      name of parameter
     * @param parameter value of parameter
     * @return updated db statement
     */
    D addParam(String name, int parameter);

    /**
     * Add next long parameter to the map of named parameters (e.g. the ones that use {@code :name} in Helidon
     * JDBC SQL integration).
     *
     * @param name      name of parameter
     * @param parameter value of parameter
     * @return updated db statement
     */
    D addParam(String name, long parameter);

    /**
     * Add next float parameter to the map of named parameters (e.g. the ones that use {@code :name} in Helidon
     * JDBC SQL integration).
     *
     * @param name      name of parameter
     * @param parameter value of parameter
     * @return updated db statement
     */
    D addParam(String name, float parameter);

    /**
     * Add next double parameter to the map of named parameters (e.g. the ones that use {@code :name} in Helidon
     * JDBC SQL integration).
     *
     * @param name      name of parameter
     * @param parameter value of parameter
     * @return updated db statement
     */
    D addParam(String name, double parameter);

    /**
     * Add next {@link BigInteger} parameter to the map of named parameters (e.g. the ones that use {@code :name} in Helidon
     * JDBC SQL integration).
     *
     * @param name      name of parameter
     * @param parameter value of parameter
     * @return updated db statement
     */
    D addParam(String name, BigInteger parameter);

    /**
     * Add next {@link BigDecimal} parameter to the map of named parameters (e.g. the ones that use {@code :name} in Helidon
     * JDBC SQL integration).
     *
     * @param name      name of parameter
     * @param parameter value of parameter
     * @return updated db statement
     */
    D addParam(String name, BigDecimal parameter);

    /**
     * Add next byte[] parameter to the map of named parameters (e.g. the ones that use {@code :name} in Helidon
     * JDBC SQL integration).
     *
     * @param name      name of parameter
     * @param parameter value of parameter
     * @return updated db statement
     */
    D addParam(String name, byte[] parameter);
}
