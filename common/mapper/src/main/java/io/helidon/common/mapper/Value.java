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

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A typed value with support for mapping (conversion) to other types.
 *
 * @param <T> type of the value
 */
public interface Value<T> {
    /**
     * Create an empty value.
     * Empty value is not backed and all of its methods consider it is empty.
     *
     * @param name name of the value
     * @param <T> type of the value
     * @return an empty value
     */
    static <T> Value<T> empty(String name) {
        Objects.requireNonNull(name, "Name of the Value must not be null");
        return new EmptyValue<>(name);
    }

    /**
     * Create a value backed by data.
     *
     * @param name name of the value
     * @param value value, must not be null
     * @param <T> type of the value
     * @return a value backed by data
     */
    static <T> Value<T> create(String name, T value) {
        Objects.requireNonNull(name, "Name of the Value must not be null");
        Objects.requireNonNull(value, "Value content for Value " + name + " must not be null, use empty(String) instead");
        return new BackedValue<>(name, value);
    }

    /**
     * Name of this value, to potentially troubleshoot the source of it.
     *
     * @return name of this value, such as "QueryParam("param-name")"
     */
    String name();

    /**
     * Typed value as {@link java.util.Optional}.
     * Returns a {@link java.util.Optional#empty() empty} if this value does not have a backing value present.
     * As this class implements all methods of {@link java.util.Optional}, this is only a utility method if an actual
     * {@link java.util.Optional} instance is needed ({@code Optional} itself is {code final}).
     *
     * @return value as {@link java.util.Optional}, {@link java.util.Optional#empty() empty} in case the value does not have
     * a direct value
     * @throws io.helidon.common.mapper.MapperException in case the value cannot be converted to the expected type
     * @see #get()
     */
    Optional<T> asOptional() throws MapperException;

    /**
     * Typed value.
     * Throws {@link java.util.NoSuchElementException} if the direct value is missing.
     *
     * @return direct value converted to the expected type
     * @throws java.util.NoSuchElementException  in case there is no value
     * @throws io.helidon.common.mapper.MapperException in case the value cannot be converted to the expected type
     */
    default T get() throws MapperException, NoSuchElementException {
        return asOptional()
                .orElseThrow(() -> new NoSuchElementException(name() + " does not have a value."));
    }

    /**
     * Convert this {@code Value} to a different type using a mapper function.
     *
     * @param mapper mapper to map the type of this {@code Value} to a type of the returned {@code Value}
     * @param <N>    type of the returned {@code Value}
     * @return a new value with the new type
     */
    <N> Value<N> as(Function<T, N> mapper);

    // it is a pity that Optional is not an interface :(

    /**
     * If the underlying {@code Optional} does not have a value, set it to the
     * {@code Optional} produced by the supplying function.
     *
     * @param supplier the supplying function that produces an {@code Optional}
     * @return returns current value using {@link #asOptional()} if present,
     *   otherwise value produced by the supplying function.
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
     * If a value is present, and the value matches the given predicate,
     * return an {@code Optional} describing the value, otherwise return an
     * empty {@code Optional}.
     * <p>
     * Copied from {@link Optional}. You can get real optional from {@link #asOptional()}.
     *
     * @param predicate a predicate to apply to the value, if present
     * @return an {@code Optional} describing the value of this {@code Optional}
     * if a value is present and the value matches the given predicate,
     * otherwise an empty {@code Optional}
     * @throws NullPointerException if the predicate is null
     * @see Optional#filter(java.util.function.Predicate)
     */
    default Optional<T> filter(Predicate<? super T> predicate) {
        return asOptional().filter(predicate);
    }

    /**
     * If a value is present, apply the provided mapping function to it,
     * and if the result is non-null, return an {@code Optional} describing the
     * result.  Otherwise return an empty {@code Optional}.
     *
     * @param <U>    The type of the result of the mapping function
     * @param mapper a mapping function to apply to the value, if present
     * @return an {@code Optional} describing the result of applying a mapping
     * function to the value of this {@code Optional}, if a value is present,
     * otherwise an empty {@code Optional}
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
     * If a value is present, apply the provided {@code Optional}-bearing
     * mapping function to it, return that result, otherwise return an empty
     * {@code Optional}.  This method is similar to {@link #map(Function)},
     * but the provided mapper is one whose result is already an {@code Optional},
     * and if invoked, {@code flatMap} does not wrap it with an additional
     * {@code Optional}.
     *
     * <p>
     * Copied from {@link Optional}. You can get real optional from {@link #asOptional()}.
     *
     * @param <U>    The type parameter to the {@code Optional} returned by
     * @param mapper a mapping function to apply to the value, if present
     *               the mapping function
     * @return the result of applying an {@code Optional}-bearing mapping
     * function to the value of this {@code Optional}, if a value is present,
     * otherwise an empty {@code Optional}
     * @throws NullPointerException if the mapping function is null or returns
     *                              a null result
     * @see Optional#flatMap(Function)
     */
    default <U> Optional<U> flatMap(Function<? super T, Optional<U>> mapper) {
        return asOptional().flatMap(mapper);
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
     * If a value is present, returns a sequential {@link java.util.stream.Stream} containing
     * only that value, otherwise returns an empty {@code Stream}.
     *
     * @return the optional value as a {@code Stream}
     */
    default Stream<T> stream() {
        return asOptional().stream();
    }
}
