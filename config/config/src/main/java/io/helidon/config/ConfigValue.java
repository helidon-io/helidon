/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.config;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A typed value that has all the methods of {@link Optional}.
 * In addition it has methods to access config values as {@link #supplier()}, to access values with defaults etc.
 */
// A top level class, as Config must implement it (and if internal, we have a cyclic dependency)
public interface ConfigValue<T> {
    /**
     * Returns the fully-qualified key of the {@code Config} node.
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
     * Returns the last token of the fully-qualified key for the {@code Config}
     * node.
     * <p>
     * The name of a node is the last token in its fully-qualified key.
     * <p>
     * The exact format of the name depends on the {@code Type} of the
     * containing node:
     * <ul>
     * <li>from a {@link Config.Type#OBJECT} node the token for a child is the
     * <strong>name of the object member</strong>;</li>
     * <li>from a {@link Config.Type#LIST} node the token for a child is a zero-based
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
     * @see Config.Key#name()
     */
    default String name() {
        return key().name();
    }

    /**
     * Returns a typed value as {@link Optional}.
     * Returns a {@link Optional#empty() empty} for nodes without a value.
     * As this class implements all methods of {@link Optional}, this is only a utility method if an actual {@link Optional}
     * instance is needed.
     *
     * @return value as type instance as {@link Optional}, {@link Optional#empty() empty} in case the node does not have
     * a direct value
     * @see #get()
     */
    Optional<T> asOptional();

    default T get() throws MissingValueException {
        return asOptional()
                .orElseThrow(() -> MissingValueException.forKey(key()));
    }

    default T get(T defaultValue) {
        return asOptional().orElse(defaultValue);
    }

    <N> ConfigValue<N> as(Function<T, N> mapper);

    Supplier<T> supplier();

    Supplier<T> supplier(T defaultValue);

    /**
     * Returns a {@link Supplier} of an {@link Optional Optional&lt;T&gt;} of the configuration node.
     *
     * Supplier returns a {@link Optional#empty() empty} if the node does not have a direct value.
     *
     * @return a supplier of the value as an {@link Optional} typed instance, {@link Optional#empty() empty} in case the node
     * does not have a direct value
     * @see #asOptional()
     * @see #supplier()
     */
    Supplier<Optional<T>> optionalSupplier();

    // it is a pity that Optional is not an interface :(
    default boolean isPresent() {
        return asOptional().isPresent();
    }

    default void ifPresent(Consumer<? super T> consumer) {
        asOptional().ifPresent(consumer);
    }

    default Optional<T> filter(Predicate<? super T> predicate) {
        return asOptional().filter(predicate);
    }

    default <U> Optional<U> map(Function<? super T, ? extends U> mapper) {
        return asOptional().map(mapper);
    }

    default <U> Optional<U> flatMap(Function<? super T, Optional<U>> mapper) {
        return asOptional().flatMap(mapper);
    }

    default T orElse(T other) {
        return asOptional().orElse(other);
    }

    default T orElseGet(Supplier<? extends T> other) {
        return asOptional().orElseGet(other);
    }

    default <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        return asOptional().orElseThrow(exceptionSupplier);
    }
}
