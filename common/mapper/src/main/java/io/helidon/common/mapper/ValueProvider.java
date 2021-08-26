/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.common.mapper;

import java.util.List;

import io.helidon.common.GenericType;

/**
 * A provider of values that can be mapped to other types.
 */
public interface ValueProvider {
    /**
     * Generic type for {@link java.lang.String}.
     */
    GenericType<String> STRING_TYPE = GenericType.create(String.class);

    /**
     * A value provider with no value.
     *
     * @param name name of the value
     * @return empty value provider
     */
    static ValueProvider empty(String name) {
        return new EmptyValueProvider(name);
    }

    /**
     * A value provider with value and default {@link io.helidon.common.mapper.MapperManager}.
     *
     * @param name name of the value
     * @param value value content
     * @return value provider
     */
    static ValueProvider create(String name, String value) {
        return create(MapperManager.create(), name, value);
    }

    /**
     * A value provider with value and explicit mapper manager.
     *
     * @param mapperManager mapper manager to use to obtain mappers for types
     * @param name name of the value
     * @param value value content
     * @return value provider
     */
    static ValueProvider create(MapperManager mapperManager, String name, String value) {
        return new BackedValueProvider(mapperManager, name, value);
    }

    /**
     * Name of the value, to potentially troubleshoot the source of it.
     *
     * @return name of this value, such as "QueryParam("param-name")"
     */
    String name();

    /**
     * Typed value as a {@link io.helidon.common.mapper.Value}.
     *
     * @param type type class
     * @param <T>  type
     * @return typed value
     *
     * @see io.helidon.common.mapper.Value#map(java.util.function.Function)
     * @see io.helidon.common.mapper.Value#get()
     * @see io.helidon.common.mapper.Value#orElse(Object)
     */
    <T> Value<T> as(Class<T> type);

    /**
     * Typed value as a {@link Value} for a generic type.
     * If appropriate mapper exists, returns a properly typed generic instance.
     * <p>
     * Example:
     * <pre>
     * {@code
     * Value<Map<String, Integer>> myMapValue = config.as(new GenericType<Map<String, Integer>>(){});
     * myMapValue.ifPresent(map -> {
     *      Integer port = map.get("service.port");
     *  }
     * }
     * </pre>
     *
     * @param genericType a (usually anonymous) instance of generic type to prevent type erasure
     * @param <T>         type of the returned value
     * @return properly typed value
     */
    <T> Value<T> as(GenericType<T> genericType);

    // shortcut methods

    /**
     * Boolean typed value.
     *
     * @return typed value
     */
    default Value<Boolean> asBoolean() {
        return as(Boolean.class);
    }

    /**
     * String typed value.
     *
     * @return typed value
     */
    default Value<String> asString() {
        return as(String.class);
    }

    /**
     * Integer typed value.
     *
     * @return typed value
     */
    default Value<Integer> asInt() {
        return as(Integer.class);
    }

    /**
     * Long typed value.
     *
     * @return typed value
     */
    default Value<Long> asLong() {
        return as(Long.class);
    }

    /**
     * Double typed value.
     *
     * @return typed value
     */
    default Value<Double> asDouble() {
        return as(Double.class);
    }

    /**
     * Returns list of specified type.
     *
     * @param elementType class of each element in the list
     * @param <T>  type of list elements
     * @return a typed list with values
     * @throws io.helidon.common.mapper.MapperException in case of problem to map to target type
     */
    <T> Value<List<T>> asList(Class<T> elementType);
}
