/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

package io.helidon.common.config;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A typed value of a {@link Config} node.
 * <p>
 * You can use accessor methods on {@link Config} to obtain this value, such as {@link Config#as(Class)}.
 * A typed value that has all the methods of {@link java.util.Optional} - including the ones added in JDK9 and newer.
 *
 * @param <T> type of the value
 */
public interface ConfigValue<T> {

    /**
     * Returns the fully-qualified key of the originating {@code Config} node.
     * <p>
     * The fully-qualified key is a sequence of tokens derived from the name of
     * each node along the path from the config root to the current node. Tokens
     * are separated by {@code .} (the dot character). See {@link #name()} for
     * more information on the format of each token.
     *
     * @return current config node key
     * @see #name()
     */
    Config.Key key();

    /**
     * Returns the last token of the fully-qualified key for the originating {@code Config}
     * node.
     * <p>
     * The name of a node is the last token in its fully-qualified key.
     * <p>
     * The exact format of the name depends on the {@code Type} of the
     * containing node:
     * <ul>
     * <li>from a Type#OBJECT node the token for a child is the
     * <strong>name of the object member</strong>;</li>
     * <li>from a Type#LIST node the token for a child is a zero-based
     * <strong>index of the element</strong>, an unsigned base-10 integer value
     * with no leading zeros.</li>
     * </ul>
     * <p>
     * The ABNF syntax of config key is:
     * <pre>{@code
     * config-key = *1( key-token *( "." key-token ) )
     *  key-token = *( unescaped / escaped )
     *  unescaped = %x00-2D / %x2F-7D / %x7F-10FFFF
     *            ; %x2E ('.') and %x7E ('~') are excluded from 'unescaped'
     *    escaped = "~" ( "0" / "1" )
     *            ; representing '~' and '.', respectively
     * }</pre>
     *
     * @return current config node key
     * @see #key()
     * @see io.helidon.common.config.Config.Key#name()
     */
    default String name() {
        return key().name();
    }

    /**
     * Returns a typed value as {@link java.util.Optional}.
     * Returns a {@link java.util.Optional#empty() empty} for nodes without a value.
     * As this class implements all methods of {@link java.util.Optional}, this is only a utility method if an actual
     * {@link java.util.Optional}
     * instance is needed.
     *
     * @return value as type instance as {@link java.util.Optional}, {@link java.util.Optional#empty() empty} in case the node
     * does not have
     * a direct value
     * @throws io.helidon.common.config.ConfigException in case the value cannot be converted to the expected type
     * @see #get()
     */
    Optional<T> asOptional() throws ConfigException;

    /**
     * Typed value of the represented {@link Config} node.
     * Throws {@link io.helidon.common.config.ConfigException} if the node is Type#MISSING type.
     *
     * @return direct value of this node converted to the expected type
     * @throws io.helidon.common.config.ConfigException in case the value cannot be converted to the expected type
     */
    T get() throws ConfigException;

    /**
     * Convert this {@code ConfigValue} to a different type using a mapper function.
     *
     * @param mapper mapper to map the type of this {@code ConfigValue} to a type of the returned {@code ConfigValue}
     * @param <N>    type of the returned {@code ConfigValue}
     * @return a new value with the new type
     */
    <N> ConfigValue<N> as(Function<T, N> mapper);

    /**
     * Return {@code true} if there is a value present, otherwise {@code false}.
     * <p>
     * Copied from {@link java.util.Optional}. You can get real optional from {@link #asOptional()}.
     *
     * @return {@code true} if there is a value present, otherwise {@code false}
     * @see java.util.Optional#isPresent()
     */
    default boolean isPresent() {
        return asOptional().isPresent();
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
        Optional<T> optional = asOptional();
        if (optional.isPresent()) {
            action.accept(optional.get());
        } else {
            emptyAction.run();
        }
    }

    /**
     * If a value is present, invoke the specified consumer with the value,
     * otherwise do nothing.
     * <p>
     * Copied from {@link java.util.Optional}. You can get real optional from {@link #asOptional()}.
     *
     * @param consumer block to be executed if a value is present
     * @throws NullPointerException if value is present and {@code consumer} is
     *                              null
     * @see java.util.Optional#ifPresent(java.util.function.Consumer)
     */
    default void ifPresent(Consumer<? super T> consumer) {
        asOptional().ifPresent(consumer);
    }

    /**
     * If a value is present, and the value matches the given predicate,
     * return an {@code Optional} describing the value, otherwise return an
     * empty {@code Optional}.
     * <p>
     * Copied from {@link java.util.Optional}. You can get real optional from {@link #asOptional()}.
     *
     * @param predicate a predicate to apply to the value, if present
     * @return an {@code Optional} describing the value of this {@code Optional}
     * if a value is present and the value matches the given predicate,
     * otherwise an empty {@code Optional}
     * @throws NullPointerException if the predicate is null
     * @see java.util.Optional#filter(java.util.function.Predicate)
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
     *                              Copied from {@link java.util.Optional}. You can get real optional from {@link #asOptional()}.
     * @see java.util.Optional#map(java.util.function.Function)
     */
    default <U> Optional<U> map(Function<? super T, ? extends U> mapper) {
        return asOptional().map(mapper);
    }

    /**
     * If a value is present, apply the provided {@code Optional}-bearing
     * mapping function to it, return that result, otherwise return an empty
     * {@code Optional}.  This method is similar to {@link #map(java.util.function.Function)},
     * but the provided mapper is one whose result is already an {@code Optional},
     * and if invoked, {@code flatMap} does not wrap it with an additional
     * {@code Optional}.
     *
     * <p>
     * Copied from {@link java.util.Optional}. You can get real optional from {@link #asOptional()}.
     *
     * @param <U>    The type parameter to the {@code Optional} returned by
     * @param mapper a mapping function to apply to the value, if present
     *               the mapping function
     * @return the result of applying an {@code Optional}-bearing mapping
     * function to the value of this {@code Optional}, if a value is present,
     * otherwise an empty {@code Optional}
     * @throws NullPointerException if the mapping function is null or returns
     *                              a null result
     * @see java.util.Optional#flatMap(java.util.function.Function)
     */
    default <U> Optional<U> flatMap(Function<? super T, Optional<U>> mapper) {
        return asOptional().flatMap(mapper);
    }

    /**
     * Return the value if present, otherwise return {@code other}.
     * <p>
     * Copied from {@link java.util.Optional}. You can get real optional from {@link #asOptional()}.
     *
     * @param other the value to be returned if there is no value present, may
     *              be null
     * @return the value, if present, otherwise {@code other}
     * @see java.util.Optional#orElse(Object)
     */
    default T orElse(T other) {
        return asOptional().orElse(other);
    }

    /**
     * Return the value if present, otherwise invoke {@code other} and return
     * the result of that invocation.
     * <p>
     * Copied from {@link java.util.Optional}. You can get real optional from {@link #asOptional()}.
     *
     * @param other a {@code Supplier} whose result is returned if no value
     *              is present
     * @return the value if present otherwise the result of {@code other.get()}
     * @throws NullPointerException if value is not present and {@code other} is
     *                              null
     * @see java.util.Optional#orElseGet(java.util.function.Supplier)
     */
    default T orElseGet(Supplier<? extends T> other) {
        return asOptional().orElseGet(other);
    }

    /**
     * Return the contained value, if present, otherwise throw an exception
     * to be created by the provided supplier.
     *
     * <p>
     * Copied from {@link java.util.Optional}. You can get real optional from {@link #asOptional()}.
     *
     * @param <X>               Type of the exception to be thrown
     * @param exceptionSupplier The supplier which will return the exception to
     *                          be thrown
     * @return the present value
     * @throws X                    if there is no value present
     * @throws NullPointerException if no value is present and
     *                              {@code exceptionSupplier} is null
     * @see java.util.Optional#orElseThrow(java.util.function.Supplier)
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
