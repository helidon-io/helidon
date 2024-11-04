/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Immutable tree-structured configuration.
 * <p>
 * See {@link ConfigValue}.
 */
public interface Config {
    /**
     * Empty instance of {@code Config}.
     *
     * @return empty instance of {@code Config}.
     */
    static Config empty() {
        return EmptyConfig.EMPTY;
    }

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
    Key key();

    /**
     * Returns the last token of the fully-qualified key for the {@code Config}
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
     * Returns the single sub-node for the specified sub-key.
     * <p>
     * The format of the key is described on {@link #key()} method.
     *
     * @param key sub-key of requested sub-node
     * @return config node for specified sub-key, never returns {@code null}.
     * @see #get(io.helidon.common.config.Config.Key)
     * @throws io.helidon.common.config.ConfigException if not defined
     */
    Config get(String key) throws ConfigException;

    /**
     * Get the root of the configuration tree.
     * In case this node is part of {@link #detach() detached} tree, this method returns the node that was detached.
     *
     * @return root of this configuration tree
     */
    Config root();

    /**
     * Returns the single sub-node for the specified sub-key.
     *
     * @param key sub-key of requested sub-node
     * @return config node for specified sub-key, never returns {@code null}.
     * @see #get(String)
     */
    default Config get(Key key) {
        return get(key.name());
    }

    /**
     * Returns a copy of the {@code Config} node with no parent.
     * <p>
     * The returned node acts as a root node for the subtree below it. Its key
     * is the empty string; {@code ""}. The original config node is unchanged,
     * and the original and the copy point to the same children.
     * <p>
     * Consider the following configuration:
     * <pre>
     * app:
     *      name: Example 1
     *      page-size: 20
     * logging:
     *      app.level = INFO
     *      level = WARNING
     * </pre>
     * The {@code Config} instances {@code name1} and {@code name2} represents same data and
     * in fact refer to the same object:
     * <pre>
     * Config name1 = config
     *                  .get("app")
     *                  .get("name");
     * Config name2 = config
     *                  .get("app")
     *                  .detach()               //DETACHED node
     *                  .get("name");
     *
     * assert name1.asString() == "Example 1";
     * assert name2.asString() == "Example 1";  //DETACHED node
     * </pre>
     * The only difference is the key each node returns:
     * <pre>
     * assert name1.key() == "app.name";
     * assert name2.key() == "name";            //DETACHED node
     * </pre>
     * <p>
     * See asMap() for example of config detaching.
     *
     * @return returns detached Config instance of same config node
     * @throws io.helidon.common.config.ConfigException if not defined
     */
    Config detach() throws ConfigException;

    /**
     * Returns {@code true} if the node exists, whether an object, a list, a
     * value node, etc.
     *
     * @return {@code true} if the node exists
     */
    boolean exists();

    /**
     * Returns {@code true} if this node exists and is a leaf node (has no
     * children).
     * <p>
     * A leaf node has no nested configuration subtree and has a single value.
     *
     * @return {@code true} if the node is existing leaf node, {@code false}
     *         otherwise.
     */
    boolean isLeaf();

    /**
     * Returns {@code true} if this node exists and is Type#Object.
     *
     * @return {@code true} if the node exists and is Type#Object, {@code false}
     *         otherwise.
     */
    boolean isObject();

    /**
     * Returns {@code true} if this node exists and is Type#List.
     *
     * @return {@code true} if the node exists and is Type#List, {@code false}
     *         otherwise.
     */
    boolean isList();

    /**
     * Returns {@code true} if this configuration node has a direct value.
     * <p>
     * This may be a value node (e.g. a leaf) or object node or a list node
     * (e.g. a branch with value). The application can invoke methods such as
     * {@link #as(Class)} on nodes that have value.
     *
     * @return {@code true} if the node has direct value, {@code false} otherwise.
     */
    boolean hasValue();

    //
    // accessors
    //

    /**
     * Typed value as a {@link ConfigValue}.
     *
     * @param type type class
     * @param <T>  type
     * @return typed value
     * @see ConfigValue#map(java.util.function.Function)
     * @see ConfigValue#get()
     * @see ConfigValue#orElse(Object)
     */
    <T> ConfigValue<T> as(Class<T> type);

    /**
     * Typed value as a {@link ConfigValue} created from factory method.
     * To convert from String, you can use
     * {@link #asString() config.asString()}{@link ConfigValue#as(java.util.function.Function) .as(Function)}.
     *
     * @param mapper method to create an instance from config
     * @param <T>    type
     * @return typed value
     */
    <T> ConfigValue<T> map(Function<Config, T> mapper);

    // shortcut methods

    /**
     * Returns list of specified type.
     *
     * @param type type class
     * @param <T>  type of list elements
     * @return a typed list with values
     * @throws io.helidon.common.config.ConfigException in case of problem to map property value.
     */
    <T> ConfigValue<List<T>> asList(Class<T> type) throws ConfigException;

    /**
     * Returns this node as a list mapping each list value using the provided mapper.
     *
     * @param mapper mapper to convert each list node into a typed value
     * @param <T>    type of list elements
     * @return a typed list with values
     * @throws io.helidon.common.config.ConfigException in case the mapper fails to map the values
     */
    <T> ConfigValue<List<T>> mapList(Function<Config, T> mapper) throws ConfigException;

    /**
     * Returns a list of child {@code Config} nodes if the node is {@code Type#OBJECT}.
     * Returns a list of element nodes if the node is {@code Type#LIST}.
     * Throws {@code MissingValueException} if the node is {@code Type#MISSING}.
     * Otherwise, if node is {@code Type#VALUE}, it throws {@code ConfigMappingException}.
     *
     * @return a list of {@code Type#OBJECT} members or a list of {@code Type#LIST} members
     * @param <C> the common config derived type
     * @throws io.helidon.common.config.ConfigException in case the node is {@code Type#VALUE}
     */
    <C extends Config> ConfigValue<List<C>> asNodeList() throws ConfigException;

    /**
     * Transform all leaf nodes (values) into Map instance.
     *
     * @return new Map instance that contains all config leaf node values
     * @throws io.helidon.common.config.ConfigException in case the node is Type#MISSING.
     */
    ConfigValue<Map<String, String>> asMap() throws ConfigException;

    /**
     * Returns existing current config node as {@link io.helidon.common.config.ConfigValue}.
     *
     * @return current config node as {@link io.helidon.common.config.ConfigValue}
     */
    default ConfigValue<? extends Config> asNode() {
        return as(Config.class);
    }

    /**
     * String typed value.
     *
     * @return typed value
     */
    default ConfigValue<String> asString() {
        return as(String.class);
    }

    // shortcut methods

    /**
     * Boolean typed value.
     *
     * @return typed value
     */
    default ConfigValue<Boolean> asBoolean() {
        return as(Boolean.class);
    }

    /**
     * Integer typed value.
     *
     * @return typed value
     */
    default ConfigValue<Integer> asInt() {
        return as(Integer.class);
    }

    /**
     * Long typed value.
     *
     * @return typed value
     */
    default ConfigValue<Long> asLong() {
        return as(Long.class);
    }

    /**
     * Double typed value.
     *
     * @return typed value
     */
    default ConfigValue<Double> asDouble() {
        return as(Double.class);
    }

    /**
     * Object represents fully-qualified key of config node.
     * <p>
     * Fully-qualified key is list of key tokens separated by {@code .} (dot character).
     * Depending on context the key token is evaluated one by one:
     * <ul>
     * <li>in Type#OBJECT node the token represents a <strong>name of object member</strong>;</li>
     * <li>in Type#LIST node the token represents an zero-based <strong>index of list
     * element</strong>,
     * an unsigned base-10 integer value, leading zeros are not allowed.</li>
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
     * @see Config#key()
     */
    interface Key extends Comparable<Key> {
        /**
         * Escape {@code '~'} to {@code ~0} and {@code '.'} to {@code ~1} in specified name.
         *
         * @param name name to be escaped
         * @return escaped name
         */
        static String escapeName(String name) {
            if (!name.contains("~") && !name.contains(".")) {
                return name;
            }
            StringBuilder sb = new StringBuilder();
            char[] chars = name.toCharArray();
            for (char ch : chars) {
                if (ch == '~') {
                    sb.append("~0");
                } else if (ch == '.') {
                    sb.append("~1");
                } else {
                    sb.append(ch);
                }
            }
            return sb.toString();
        }

        /**
         * Unescape {@code ~0} to {@code '~'} and {@code ~1} to {@code '.'} in specified escaped name.
         *
         * @param escapedName escaped name
         * @return unescaped name
         */
        static String unescapeName(String escapedName) {
            return escapedName.replace("~1", ".")
                    .replace("~0", "~");
        }

        /**
         * Returns instance of Key that represents key of parent config node.
         * <p>
         * If the key represents root config node it throws an exception.
         *
         * @return key that represents key of parent config node.
         * @see #isRoot()
         * @throws IllegalStateException in case you attempt to call this method on a root node
         * @throws io.helidon.common.config.ConfigException if not defined
         */
        Key parent() throws ConfigException;

        /**
         * Returns {@code true} in case the key represents root config node,
         * otherwise it returns {@code false}.
         *
         * @return {@code true} in case the key represents root node, otherwise {@code false}.
         * @see #parent()
         * @throws io.helidon.common.config.ConfigException if not defined
         */
        boolean isRoot();

        /**
         * Returns the name of Config node.
         * <p>
         * The name of a node is the last token in fully-qualified key.
         * Depending on context the name is evaluated one by one:
         * <ul>
         * <li>in Type#OBJECT} node the name represents a <strong>name of object member</strong>;
         * </li>
         * <li>in Type#LIST} node the name represents an zero-based <strong>index of list
         * element</strong>,
         * an unsigned base-10 integer value, leading zeros are not allowed.</li>
         * </ul>
         *
         * @return name of config node
         * @see Config#name()
         */
        String name();

        /**
         * Returns formatted fully-qualified key.
         *
         * @return formatted fully-qualified key.
         */
        @Override
        String toString();

        /**
         * Create a child key to the current key.
         *
         * @param key child key (relative to current key)
         * @return a new resolved key
         * @throws io.helidon.common.config.ConfigException if not defined
         */
        Key child(Key key);

    }

}
