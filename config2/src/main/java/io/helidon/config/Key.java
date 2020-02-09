/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.util.List;

/**
 * Object represents fully-qualified key of config node.
 * <p>
 * Fully-qualified key is list of key tokens separated by {@code .} (dot character).
 * Depending on context the key token is evaluated one by one:
 * <ul>
 * <li>in {@link Type#OBJECT} node the token represents a <strong>name of object member</strong>;</li>
 * <li>in {@link Type#LIST} node the token represents an zero-based <strong>index of list element</strong>,
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
public interface Key extends Comparable<Key> {
    /**
     * Returns instance of Key that represents key of parent config node.
     * <p>
     * If the key represents root config node it throws an exception.
     *
     * @return key that represents key of parent config node.
     * @see #isRoot()
     * @throws java.lang.IllegalStateException in case you attempt to call this method on a root node
     */
    Key parent();

    /**
     * Returns {@code true} in case the key represents root config node,
     * otherwise it returns {@code false}.
     *
     * @return {@code true} in case the key represents root node, otherwise {@code false}.
     * @see #parent()
     */
    boolean isRoot();

    /**
     * Returns the name of Config node.
     * <p>
     * The name of a node is the last token in fully-qualified key.
     * Depending on context the name is evaluated one by one:
     * <ul>
     * <li>in {@link Type#OBJECT} node the name represents a <strong>name of object member</strong>;</li>
     * <li>in {@link Type#LIST} node the name represents an zero-based <strong>index of list element</strong>,
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

    Key child(Key key);

    List<String> elements();

    /**
     * Creates new instance of Key for specified {@code key} literal.
     * <p>
     * Empty literal means root node.
     * Character dot ('.') has special meaning - it separates fully-qualified key by key tokens (node names).
     *
     * @param key formatted fully-qualified key.
     * @return Key instance representing specified fully-qualified key.
     */
    static Key create(String key) {
        return ConfigKeyImpl.of(key);
    }

    static Key create(List<String> elements) {
        return ConfigKeyImpl.of(elements);
    }

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
        return escapedName.replaceAll("~1", ".")
                .replaceAll("~0", "~");
    }
}
