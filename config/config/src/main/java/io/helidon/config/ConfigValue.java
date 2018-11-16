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
import java.util.function.Supplier;

/**
 * A typed value (may be empty).
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
     * Returns a {@code String} value as {@link Optional} of configuration node if the node is {@link Config.Type#VALUE}.
     * Returns a {@link Optional#empty() empty} for nodes without a value.
     *
     * @return value as type instance as {@link Optional}, {@link Optional#empty() empty} in case the node does not have
     * a direct value
     * @see #getValue()
     */
    Optional<T> value();

    default T getValue() throws MissingValueException {
        return value()
                .orElseThrow(() -> MissingValueException.forKey(key()));
    }

    default T getValue(T defaultValue) {
        return value().orElse(defaultValue);
    }

    default Supplier<T> asSupplier() {
        return this::getValue;
    }

    default Supplier<T> asSupplier(T defaultValue) {
        return () -> getValue(defaultValue);
    }

    /**
     * Returns a {@link Supplier} of an {@link Optional Optional&lt;T&gt;} of the configuration node.
     *
     * Supplier returns a {@link Optional#empty() empty} if the node does not have a direct value.
     *
     * @return a supplier of the value as an {@link Optional} typed instance, {@link Optional#empty() empty} in case the node
     * does not have a direct value
     * @see #value()
     * @see #asSupplier()
     */
    default Supplier<Optional<T>> asOptionalSupplier() {
        return this::value;
    }

    default void ifPresent(Consumer<? super T> consumer) {
        value().ifPresent(consumer);
    }
}
