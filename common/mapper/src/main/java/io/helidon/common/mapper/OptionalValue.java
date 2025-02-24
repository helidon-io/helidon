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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.GenericType;

/**
 * A typed value with support for mapping (conversion) to other types.
 *
 * @param <T> type of the value
 */
public interface OptionalValue<T> extends Value<T> {
    /**
     * Create an empty value.
     * Empty value is not backed by data and all of its methods consider it is empty.
     *
     * @param mapperManager mapper manager to use for mapping types
     * @param name          name of the value
     * @param type          type of the value, to correctly handle mapping exceptions
     * @param <T>           type of the value
     * @param qualifiers    qualifiers of the mapper
     * @return an empty value
     */
    static <T> OptionalValue<T> create(Mappers mapperManager, String name, Class<T> type, String... qualifiers) {
        Objects.requireNonNull(name, "Name of the Value must not be null");
        return create(mapperManager, name, GenericType.create(type), qualifiers);
    }

    /**
     * Create an empty value.
     * Empty value is not backed by data and all of its methods consider it is empty.
     *
     * @param mapperManager mapper manager to use for mapping types
     * @param name          name of the value
     * @param type          type of the value, to correctly handle mapping exceptions
     * @param qualifiers    qualifiers of the mapper
     * @param <T>           type of the value
     * @return an empty value
     */
    static <T> OptionalValue<T> create(Mappers mapperManager, String name, GenericType<T> type, String... qualifiers) {
        Objects.requireNonNull(name, "Name of the Value must not be null");
        return new ValueEmpty<>(mapperManager, type, name, qualifiers);
    }

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
    static <T> OptionalValue<T> create(Mappers mapperManager, String name, T value, String... qualifiers) {
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
    static <T> OptionalValue<T> create(Mappers mapperManager,
                                       String name,
                                       T value,
                                       GenericType<T> type,
                                       String... qualifiers) {
        Objects.requireNonNull(name, "Name of the Value must not be null");
        Objects.requireNonNull(value, "Value content for Value " + name + " must not be null, use empty(String) instead");
        return new ValueBacked<>(mapperManager, name, value, type, qualifiers);
    }

    @Override
    <N> OptionalValue<N> as(Class<N> type);

    @Override
    <N> OptionalValue<N> as(GenericType<N> type);

    @Override
    <N> OptionalValue<N> as(Function<? super T, ? extends N> mapper);

    // it is a pity that Optional is not an interface :(

    /**
     * Typed value.
     *
     * @return direct value converted to the expected type
     * @throws java.util.NoSuchElementException or appropriate module specific exception if the value is not present
     */
    @Override
    T get();

    /**
     * If the underlying {@code Optional} does not have a value, set it to the
     * {@code Optional} produced by the supplying function.
     *
     * @param supplier the supplying function that produces an {@code Optional}
     * @return returns current value using {@link #asOptional()} if present,
     *         otherwise value produced by the supplying function.
     * @throws NullPointerException if the supplying function is {@code null} or
     *                              produces a {@code null} result
     */
    default Optional<T> or(Supplier<? extends Optional<T>> supplier) {
        return asOptional().or(supplier);
    }

    /**
     * Return {@code true} if there is a value present, otherwise {@code false}.
     * <p>
     * Copied from {@link Optional}. You can get real optional from {@link #asOptional()}.
     *
     * @return {@code true} if there is a value present, otherwise {@code false}
     * @see java.util.Optional#isPresent()
     */
    default boolean isPresent() {
        return asOptional().isPresent();
    }

    /**
     * Return {@code false} if there is a value present, otherwise {@code true}.
     * <p>
     * Copied from {@link Optional}. You can get real optional from {@link #asOptional()}.
     *
     * @return {@code false} if there is a value present, otherwise {@code true}
     * @see java.util.Optional#isEmpty() ()
     */
    default boolean isEmpty() {
        return asOptional().isEmpty();
    }

    /**
     * If a value is present, performs the given action with the value,
     * otherwise performs the given empty-based action.
     *
     * @param action      the action to be performed, if a value is present
     * @param emptyAction the empty-based action to be performed, if no value is
     *                    present
     * @throws NullPointerException if a value is present and the given action
     *                              is {@code null}, or no value is present and the given empty-based
     *                              action is {@code null}.
     */
    default void ifPresentOrElse(Consumer<T> action, Runnable emptyAction) {
        asOptional().ifPresentOrElse(action, emptyAction);
    }

    /**
     * If a value is present, invoke the specified consumer with the value,
     * otherwise do nothing.
     * <p>
     * Copied from {@link Optional}. You can get real optional from {@link #asOptional()}.
     *
     * @param consumer block to be executed if a value is present
     * @throws NullPointerException if value is present and {@code consumer} is
     *                              null
     * @see Optional#ifPresent(Consumer)
     */
    default void ifPresent(Consumer<? super T> consumer) {
        asOptional().ifPresent(consumer);
    }

    /**
     * If a value is present, apply the provided mapping function to it,
     * and if the result is non-null, return an {@code Optional} describing the
     * result.  Otherwise return an empty {@code Optional}.
     *
     * @param <U>    The type of the result of the mapping function
     * @param mapper a mapping function to apply to the value, if present
     * @return an {@code Optional} describing the result of applying a mapping
     *         function to the value of this {@code Optional}, if a value is present,
     *         otherwise an empty {@code Optional}
     * @throws NullPointerException if the mapping function is null
     *
     *                              <p>
     *                              Copied from {@link Optional}. You can get real optional from {@link #asOptional()}.
     * @see Optional#map(Function)
     */
    default <U> Optional<U> map(Function<? super T, ? extends U> mapper) {
        return asOptional().map(mapper);
    }

    /**
     * Return the value if present, otherwise return {@code other}.
     * <p>
     * Copied from {@link Optional}. You can get real optional from {@link #asOptional()}.
     *
     * @param other the value to be returned if there is no value present, may
     *              be null
     * @return the value, if present, otherwise {@code other}
     * @see Optional#orElse(Object)
     */
    default T orElse(T other) {
        return asOptional().orElse(other);
    }

    /**
     * Return the value if present, otherwise invoke {@code other} and return
     * the result of that invocation.
     * <p>
     * Copied from {@link Optional}. You can get real optional from {@link #asOptional()}.
     *
     * @param other a {@code Supplier} whose result is returned if no value
     *              is present
     * @return the value if present otherwise the result of {@code other.get()}
     * @throws NullPointerException if value is not present and {@code other} is
     *                              null
     * @see Optional#orElseGet(Supplier)
     */
    default T orElseGet(Supplier<? extends T> other) {
        return asOptional().orElseGet(other);
    }

    /**
     * Return the contained value, if present, otherwise throw an exception
     * to be created by the provided supplier.
     *
     * <p>
     * Copied from {@link Optional}. You can get real optional from {@link #asOptional()}.
     *
     * @param <X>               Type of the exception to be thrown
     * @param exceptionSupplier The supplier which will return the exception to
     *                          be thrown
     * @return the present value
     * @throws X                    if there is no value present
     * @throws NullPointerException if no value is present and
     *                              {@code exceptionSupplier} is null
     * @see Optional#orElseThrow(Supplier)
     */
    default <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        return asOptional().orElseThrow(exceptionSupplier);
    }

    /**
     * If a value is present, returns the value, otherwise throws
     * {@code NoSuchElementException}.
     *
     * @return the non-{@code null} value described by this value
     * @throws NoSuchElementException if no value is present
     */
    default T orElseThrow() {
        return orElseThrow(() -> new NoSuchElementException("No value present for " + name()));
    }

    // shortcut methods for commonly used types
    @Override
    default OptionalValue<Boolean> asBoolean() {
        return as(Boolean.class);
    }

    @Override
    default OptionalValue<String> asString() {
        return as(String.class);
    }

    @Override
    default OptionalValue<Integer> asInt() {
        return as(Integer.class);
    }

    @Override
    default OptionalValue<Long> asLong() {
        return as(Long.class);
    }

    @Override
    default OptionalValue<Double> asDouble() {
        return as(Double.class);
    }
}
