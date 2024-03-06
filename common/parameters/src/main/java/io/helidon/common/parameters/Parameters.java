/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.common.parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.common.GenericType;
import io.helidon.common.mapper.MapperManager;
import io.helidon.common.mapper.OptionalValue;
import io.helidon.common.mapper.Value;

/**
 * Parameters abstraction (used by any component that has named parameters with possible multiple values).
 * This is a read-only access to the parameters.
 */
public interface Parameters {
    /**
     * Generic type for parameters.
     */
    GenericType<Parameters> GENERIC_TYPE = GenericType.create(Parameters.class);

    /**
     * Creates a new {@link Builder} of {@link io.helidon.common.parameters.Parameters}.
     *
     * @param component component these parameters represent (such as request headers)
     * @return builder instance
     */
    static Builder builder(String component) {
        return new Builder(component);
    }

    /**
     * Create empty (named) parameters.
     *
     * @param component component of the parameters to correctly report errors
     * @return new named parameters with no values
     */
    static Parameters empty(String component) {
        return new ParametersEmpty(component);
    }

    /**
     * Read only parameters based on a map.
     *
     * @param component component of the parameters to correctly report errors
     * @param params    underlying map
     * @return new named parameters with values based on the map
     */
    static Parameters create(String component, Map<String, List<String>> params) {
        return new ParametersMap(MapperManager.global(), component, params);
    }

    /**
     * Read only parameters based on a map with just a single value.
     *
     * @param component component of the parameters to correctly report errors
     * @param params    underlying map
     * @return new named parameters with values based on the map
     */
    static Parameters createSingleValueMap(String component, Map<String, String> params) {
        return new ParametersSingleValueMap(MapperManager.global(), component, params);
    }

    /**
     * Get all values.
     *
     * @param name name of the parameter
     * @return all values as a list
     * @throws NoSuchElementException in case the name is not present
     */
    List<String> all(String name) throws NoSuchElementException;

    /**
     * Get all values using a default value supplier if the parameter does not exist.
     *
     * @param name          name of the parameter
     * @param defaultValues default values supplier to use if parameter is not present
     * @return all values as a list
     */
    default List<String> all(String name, Supplier<List<String>> defaultValues) {
        if (contains(name)) {
            return all(name);
        }
        return defaultValues.get();
    }

    /**
     * A list of values for the named parameter.
     *
     * @param name name of the parameter
     * @return list of parameter values, mappable to other types
     * @throws NoSuchElementException in case the name is not present in these parameters
     */
    List<Value<String>> allValues(String name) throws NoSuchElementException;

    /**
     * Get all values using a default value supplier if the parameter does not exist.
     *
     * @param name          name of the parameter
     * @param defaultValues default values supplier to use if parameter is not present
     * @return all values as a list
     */
    default List<Value<String>> allValues(String name, Supplier<List<Value<String>>> defaultValues) {
        if (contains(name)) {
            return allValues(name);
        }
        return defaultValues.get();
    }

    /**
     * Get the first value.
     *
     * @param name name of the parameter
     * @return first value
     * @throws NoSuchElementException in case the name is not present
     */
    String get(String name) throws NoSuchElementException;

    /**
     * Get the first value as an optional.
     * Managing optionals has performance impact. If performance is of issue, use {@link #contains(String)}
     * and {@link #get(String)} instead.
     *
     * @param name name of the parameter
     * @return first value or empty optional
     */
    OptionalValue<String> first(String name);

    /**
     * Whether these parameters contain the provided name.
     *
     * @param name name of the parameter
     * @return {@code true} if the parameter is present, {@code false} otherwise
     */
    boolean contains(String name);

    /**
     * Whether these parameters are empty.
     *
     * @return {@code true} if there is no parameter defined
     */
    default boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Number of parameter names in these parameters.
     *
     * @return size of these parameters
     */
    int size();

    /**
     * Set of parameter names.
     *
     * @return names available in these parameters
     */
    Set<String> names();

    /**
     * Name of the component of these parameters.
     *
     * @return component name
     */
    String component();

    /**
     * Get a map representation of these parameters. Changes to the map will not be propagated to this instance.
     *
     * @return a new map
     */
    default Map<String, List<String>> toMap() {
        Map<String, List<String>> result = new HashMap<>();

        for (String name : names()) {
            result.put(name, all(name));
        }

        return result;
    }

    /**
     * Builder of a new {@link io.helidon.common.parameters.Parameters} instance.
     */
    class Builder implements io.helidon.common.Builder<Builder, Parameters> {

        private final Map<String, List<String>> params = new LinkedHashMap<>();
        private final String component;

        private MapperManager mapperManager;

        private Builder(String component) {
            this.component = component;
        }

        @Override
        public Parameters build() {
            if (mapperManager == null) {
                mapperManager = MapperManager.global();
            }
            return new ParametersMap(mapperManager, component, params);
        }

        /**
         * Configure mapper manager to use.
         *
         * @param mapperManager mapper manager
         * @return updated builder
         */
        public Builder mapperManager(MapperManager mapperManager) {
            this.mapperManager = mapperManager;
            return this;
        }

        /**
         * Add new value(s) to the parameters. If the name existed, values will be added.
         *
         * @param name   parameter name
         * @param values parameter value
         * @return updated builder
         */
        public Builder add(String name, String... values) {
            Objects.requireNonNull(name);
            params.computeIfAbsent(name, k -> new ArrayList<>()).addAll(Arrays.asList(values));
            return this;
        }

        /**
         * Set new value(s) to the parameters. If the name existed, values will be replaced.
         *
         * @param name   parameter name
         * @param values parameter value
         * @return updated builder
         */
        public Builder set(String name, String... values) {
            Objects.requireNonNull(name);
            params.put(name, new ArrayList<>(List.of(values)));
            return this;
        }
    }
}
