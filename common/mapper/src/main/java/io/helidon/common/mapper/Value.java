/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.helidon.common.GenericType;

/**
 * A typed value with support for mapping (conversion) to other types.
 * For values not backed by an data, see {@link io.helidon.common.mapper.OptionalValue}.
 *
 * @param <T> type of the value
 */
public interface Value<T> {
    /**
     * Create a value backed by data. The type of the value is "guessed" from the instance provided.
     *
     * @param mapperManager mapper manager to use for mapping types
     * @param name          name of the value
     * @param value         value, must not be null
     * @param qualifiers    qualifiers of the mapper
     * @param <T>           type of the value
     * @return a value backed by data
     */
    static <T> Value<T> create(MapperManager mapperManager, String name, T value, String... qualifiers) {
        Objects.requireNonNull(name, "Name of the Value must not be null");
        Objects.requireNonNull(value, "Value content for Value " + name + " must not be null, use empty(String) instead");
        return new ValueBacked<>(mapperManager, name, value, qualifiers);
    }

    /**
     * Create a value backed by data.
     *
     * @param mapperManager mapper manager to use for mapping types
     * @param name          name of the value
     * @param value         value, must not be null
     * @param type          a more precise type that could be guessed form an instance
     * @param qualifiers    qualifiers of the mapper
     * @param <T>           type of the value
     * @return a value backed by data
     */
    static <T> Value<T> create(MapperManager mapperManager, String name, T value, GenericType<T> type, String... qualifiers) {
        Objects.requireNonNull(name, "Name of the Value must not be null");
        Objects.requireNonNull(value, "Value content for Value " + name + " must not be null, use empty(String) instead");
        return new ValueBacked<>(mapperManager, name, value, type, qualifiers);
    }

    /**
     * Name of this value, to potentially troubleshoot the source of it.
     *
     * @return name of this value, such as "QueryParam("param-name")"
     */
    String name();

    /**
     * Typed value.
     *
     * @return direct value
     */
    T get();

    /**
     * Convert this value to a different type using a mapper.
     *
     * @param type type to convert to
     * @return converted value
     * @param <N> type we expect
     * @throws io.helidon.common.mapper.MapperException in case the value cannot be converted
     * @throws java.util.NoSuchElementException in case the value does not exist
     */
    default <N> N get(Class<N> type) throws MapperException, NoSuchElementException {
        return as(type).get();
    }

    /**
     * Convert this value to a different type using a mapper.
     *
     * @param type type to convert to
     * @return converted value
     * @param <N> type we expect
     */
    default <N> N get(GenericType<N> type) throws MapperException, NoSuchElementException {
        return as(type).get();
    }

    /**
     * Convert this value to a different type using a mapper.
     *
     * @param type type to convert to
     * @return converted value
     * @param <N> type we expect
     * @throws io.helidon.common.mapper.MapperException in case the value cannot be converted
     */
    <N> Value<N> as(Class<N> type) throws MapperException;

    /**
     * Convert this value to a different type using a mapper.
     *
     * @param type type to convert to
     * @return converted value
     * @param <N> type we expect
     */
    <N> Value<N> as(GenericType<N> type) throws MapperException;

    /**
     * Convert this {@code Value} to a different type using a mapper function.
     *
     * @param mapper mapper to map the type of this {@code Value} to a type of the returned {@code Value}
     * @param <N>    type of the returned {@code Value}
     * @return a new value with the new type
     */
    <N> Value<N> as(Function<? super T, ? extends N> mapper);

    /**
     * Typed value as {@link java.util.Optional}.
     * Returns a {@link java.util.Optional#empty() empty} if this value does not have a backing value present.
     * As this class implements all methods of {@link java.util.Optional}, this is only a utility method if an actual
     * {@link java.util.Optional} instance is needed ({@code Optional} itself is {code final}).
     *
     * @return value as {@link java.util.Optional}, {@link java.util.Optional#empty() empty} in case the value does not have
     *         a direct value
     * @throws io.helidon.common.mapper.MapperException in case the value cannot be converted to the expected type
     * @see #get()
     */
    Optional<T> asOptional() throws MapperException;

    // it is a pity that Optional is not an interface :(

    /**
     * If a value is present, and the value matches the given predicate,
     * return an {@code Optional} describing the value, otherwise return an
     * empty {@code Optional}.
     *
     * @param predicate a predicate to apply to the value, if present
     * @return an {@code Optional} describing the value of this {@code Optional}
     *         if a value is present and the value matches the given predicate,
     *         otherwise an empty {@code Optional}
     * @throws NullPointerException if the predicate is null
     * @see java.util.Optional#filter(java.util.function.Predicate)
     */
    default Optional<T> filter(Predicate<? super T> predicate) {
        return asOptional().filter(predicate);
    }

    /**
     * Apply the provided {@code Optional}-bearing
     * mapping function to this value, return that result.
     *
     * @param <U>    The type parameter to the {@code Optional} returned by
     * @param mapper a mapping function to apply to the value, if present
     *               the mapping function
     * @return the result of applying an {@code Optional}-bearing mapping
     *         function to this value
     * @throws NullPointerException if the mapping function is null or returns
     *                              a null result
     * @see java.util.Optional#flatMap(java.util.function.Function)
     */
    default <U> Optional<U> flatMap(Function<? super T, Optional<? extends U>> mapper) {
        return asOptional().flatMap(mapper);
    }

    /**
     * If a value is present, returns a sequential {@link java.util.stream.Stream} containing
     * only that value, otherwise returns an empty {@code Stream}.
     *
     * @return the optional value as a {@code Stream}
     */
    default Stream<T> stream() {
        return asOptional().stream();
    }

    // shortcut methods for commonly used types
    /**
     * Boolean typed value.
     *
     * @return typed value
     */
    Value<Boolean> asBoolean();

    /**
     * Boolean typed value.
     *
     * @return boolean value
     */
    default boolean getBoolean() {
        return get(Boolean.class);
    }

    /**
     * String typed value.
     *
     * @return typed value
     */
    Value<String> asString();

    /**
     * String typed value.
     *
     * @return string value
     */
    default String getString() {
        return get(String.class);
    }

    /**
     * Integer typed value.
     *
     * @return typed value
     */
    Value<Integer> asInt();

    /**
     * Integer typed value.
     *
     * @return integer value
     */
    default int getInt() {
        return get(Integer.class);
    }

    /**
     * Long typed value.
     *
     * @return typed value
     */
    Value<Long> asLong();

    /**
     * Long typed value.
     *
     * @return long value
     */
    default long getLong() {
        return get(Long.class);
    }

    /**
     * Double typed value.
     *
     * @return typed value
     */
    Value<Double> asDouble();

    /**
     * Double typed value.
     *
     * @return double value
     */
    default double getDouble() {
        return get(Double.class);
    }
}
