/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
import java.util.function.Function;
import java.util.function.Supplier;

import io.helidon.common.mapper.Value;

/**
 * A typed value of a {@link Config} node.
 * <p>
 * You can use accessor methods on {@link Config} to obtain this value, such as {@link Config#as(Class)}.
 * A typed value that has all the methods of {@link Optional} - including the ones added in JDK9 and newer.
 * In addition it has methods to access config values as {@link #supplier()}.
 *
 * @param <T> type of the value
 * @see Config#as(Class)
 * @see Config#as(Function)
 * @see Config#as(io.helidon.common.GenericType)
 */
public interface ConfigValue<T> extends Value<T> {
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
    @Override
    default String name() {
        return key().name();
    }

    /**
     * Typed value of the represented {@link Config} node.
     * Throws {@link MissingValueException} if the node is {@link Config.Type#MISSING} type.
     *
     * @return direct value of this node converted to the expected type
     * @throws MissingValueException  in case the node is {@link Config.Type#MISSING}.
     * @throws ConfigMappingException in case the value cannot be converted to the expected type
     */
    @Override
    default T get() throws MissingValueException, ConfigMappingException {
        return asOptional()
                .orElseThrow(() -> MissingValueException.create(key()));
    }

    /**
     * Convert this {@code ConfigValue} to a different type using a mapper function.
     *
     * @param mapper mapper to map the type of this {@code ConfigValue} to a type of the returned {@code ConfigValue}
     * @param <N>    type of the returned {@code ConfigValue}
     * @return a new value with the new type
     */
    @Override
    <N> ConfigValue<N> as(Function<T, N> mapper);

    /**
     * Returns a supplier of a typed value. The value provided from the supplier is the latest value available.
     * E.g. in case there is a file config source that is being watched and a value is changed, this supplier
     * would return the latest value, whereas {@link #get()} would return the original value.
     * <p>
     * Note that {@link Supplier#get()} can throw a {@link ConfigMappingException} or {@link MissingValueException} as the
     * {@link #get()} method.
     *
     * @return a supplier of a typed value
     */
    Supplier<T> supplier();

    /**
     * Returns a supplier of a typed value with a default. The value provided from the supplier is the latest value available.
     * E.g. in case there is a file config source that is being watched and a value is changed, this supplier
     * would return the latest value, whereas {@link #orElse(Object)} would return the original value.
     * <p>
     * Note that {@link Supplier#get()} can throw a {@link ConfigMappingException} as the
     * {@link #orElse(Object)} method.
     *
     * @param defaultValue a value to be returned if the supplied value represents a {@link Config} node that has no direct
     *                     value
     * @return a supplier of a typed value
     */
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
}
