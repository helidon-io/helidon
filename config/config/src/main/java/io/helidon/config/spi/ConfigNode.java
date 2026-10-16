/*
 * Copyright (c) 2017, 2026 Oracle and/or its affiliates.
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

package io.helidon.config.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.helidon.config.ListNodeBuilderImpl;
import io.helidon.config.ObjectNodeBuilderImpl;
import io.helidon.config.ObjectNodeImpl;
import io.helidon.config.ValueNodeImpl;

/**
 * A configuration node and its source description.
 */
public interface ConfigNode {
    /**
     * Get the type of this node.
     *
     * @return NodeType this node represents
     */
    NodeType nodeType();

    /**
     * Get the direct value of this config node. Any node type can have a direct value.
     *
     * @return a value if present, {@code empty} otherwise
     */
    Optional<String> value();

    /**
     * Human-readable description of the source from which this node was loaded.
     * Returns {@code "N/A"} when the source is unknown or when a container combines different sources.
     * For an object or list with a direct value, the description identifies the source of that value;
     * its children retain their own source descriptions.
     *
     * @return source description, never {@code null}
     * @since 28.0.0
     */
    default String description() {
        return "N/A";
    }

    /**
     * Base types of config nodes.
     */
    enum NodeType {
        /**
         * An object (complex structure), optionally may have a value.
         */
        OBJECT,
        /**
         * A list of values, optionally may have a value.
         */
        LIST,
        /**
         * Only has value.
         */
        VALUE
    }

    /**
     * Single string-based configuration value.
     * <p>
     * NOTE: Do not implement this interface yourself but rather use {@link #create(String)}.
     */
    interface ValueNode extends ConfigNode {
        @Override
        default NodeType nodeType() {
            return NodeType.VALUE;
        }

        /**
         * Get the value of this value node.
         * @return string with the node value
         */
        String get();

        /**
         * Create new instance of the {@link ValueNode} from specified String {@code value}.
         *
         * @param value string value
         * @return new instance of the {@link ValueNode}
         */
        static ValueNode create(String value) {
            return ValueNodeImpl.create(Objects.requireNonNull(value));
        }
    }

    /**
     * ConfigNode-based list of configuration values.
     * <p>
     * List contains instance of {@link ValueNode}, {@link ListNode} as well as {@link ObjectNode}.
     * <p>
     * NOTE: Do not implement this interface yourself but rather use {@link #builder()}.
     */
    interface ListNode extends ConfigNode, List<ConfigNode> {
        @Override
        default NodeType nodeType() {
            return NodeType.LIST;
        }

        /**
         * Creates new instance of {@link Builder}.
         *
         * @return new instance of {@link Builder}.
         */
        static Builder builder() {
            return new ListNodeBuilderImpl();
        }

        /**
         * Creates a builder using the provided resolver for object member names.
         *
         * @param tokenResolver function resolving member names
         * @return new builder
         * @since 28.0.0
         */
        static Builder builder(Function<String, String> tokenResolver) {
            return new ListNodeBuilderImpl(Objects.requireNonNull(tokenResolver));
        }

        /**
         * Builder to build {@link ListNode} instance.
         */
        interface Builder extends io.helidon.common.Builder<Builder, ListNode> {
            /**
             * Adds a configuration node to the list.
             *
             * @param node node to add
             * @return modified builder
             * @since 28.0.0
             */
            default Builder addNode(ConfigNode node) {
                Objects.requireNonNull(node);
                return switch (node.nodeType()) {
                    case VALUE -> addValue((ValueNode) node);
                    case OBJECT -> addObject((ObjectNode) node);
                    case LIST -> addList((ListNode) node);
                };
            }

            /**
             * Adds String value to the list.
             *
             * @param value string value
             * @return modified builder
             */
            default Builder addValue(String value) {
                return addValue(ValueNode.create(value));
            }

            /**
             * Adds String value to the list.
             *
             * @param value string value
             * @return modified builder
             */
            Builder addValue(ValueNode value);

            /**
             * Adds Object node to the list.
             *
             * @param object object node
             * @return modified builder
             */
            Builder addObject(ObjectNode object);

            /**
             * Adds List node to the list.
             *
             * @param list list node
             * @return modified builder
             */
            Builder addList(ListNode list);

            /**
             * Sets the node value associated with the current node.
             *
             * @param value value to be assigned
             * @return modified builder
             */
            Builder value(String value);
        }
    }

    /**
     * Configuration node representing a hierarchical structure parsed by a
     * suitable {@link ConfigParser} if necessary.
     * <p>
     * The map contains this object's immediate children, keyed by their names. A child may be a
     * {@link ValueNode}, {@link ListNode}, or another {@link ObjectNode}. Builder methods accepting
     * dotted paths create nested objects, so adding {@code "server.port"} creates a {@code "server"}
     * object containing a {@code "port"} child. A direct value may coexist with children.
     * <p>
     * NOTE: Do not implement this interface yourself but rather use
     * {@link #builder()}.
     */
    interface ObjectNode extends ConfigNode, Map<String, ConfigNode> {
        @Override
        default NodeType nodeType() {
            return NodeType.OBJECT;
        }

        /**
         * Returns empty object node.
         *
         * @return empty object node.
         */
        static ObjectNode empty() {
            return SpiHelper.EmptyObjectNodeHolder.EMPTY;
        }

        /**
         * Returns an object node containing a single simple value.
         *
         * @param key key of the value
         * @param value value
         * @return a new object node
         */
        static ObjectNode simple(String key, String value) {
            return ObjectNode.builder()
                    .addValue(key, value)
                    .build();
        }

        /**
         * Creates new instance of {@link Builder}.
         *
         * @return new instance of {@link Builder}.
         */
        static Builder builder() {
            return ObjectNodeBuilderImpl.create();
        }

        /**
         * Creates a builder using the provided resolver for object member names.
         *
         * @param tokenResolver function resolving member names
         * @return new builder
         * @since 28.0.0
         */
        static Builder builder(Function<String, String> tokenResolver) {
            return new ObjectNodeBuilderImpl(Objects.requireNonNull(tokenResolver));
        }

        /**
         * Copies an object node, preserving its direct value and source descriptions.
         *
         * @param node original node
         * @return copied node
         * @since 28.0.0
         */
        static ObjectNode wrap(ObjectNode node) {
            return wrap(node, Function.identity());
        }

        /**
         * Copies an object node using a resolver for object member names, preserving its direct value
         * and source descriptions.
         *
         * @param node original node
         * @param tokenResolver function resolving member names
         * @return copied node
         * @since 28.0.0
         */
        static ObjectNode wrap(ObjectNode node, Function<String, String> tokenResolver) {
            return ObjectNodeImpl.wrap(Objects.requireNonNull(node), Objects.requireNonNull(tokenResolver));
        }

        /**
         * Builder to build {@link ObjectNode} instance.
         */
        interface Builder {

            /**
             * Adds the provided immediate members to this builder. Keys are member names, not dotted paths.
             * This operation copies members only; use {@link ObjectNode#wrap(ObjectNode)} to also retain an
             * object's direct value and source description.
             *
             * @param members members to add
             * @return modified builder
             * @since 28.0.0
             */
            default Builder from(Map<String, ConfigNode> members) {
                Objects.requireNonNull(members).forEach(this::addNode);
                return this;
            }

            /**
             * Sets String value associated with specified {@code key}.
             *
             * @param key   member key
             * @param value string value
             * @return modified builder
             */
            default Builder addValue(String key, String value) {
                return addValue(key, ValueNode.create(value));
            }

            /**
             * Sets String value associated with specified {@code key}.
             *
             * @param key   member key
             * @param value string value
             * @return modified builder
             */
            Builder addValue(String key, ValueNode value);

            /**
             * Sets Object node associated with specified {@code key}.
             *
             * @param key    member key
             * @param object object node
             * @return modified builder
             */
            Builder addObject(String key, ObjectNode object);

            /**
             * Sets List node associated with specified {@code key}.
             *
             * @param key  member key
             * @param list list node
             * @return modified builder
             */
            Builder addList(String key, ListNode list);

            /**
             * Sets the node value associated with the current node.
             *
             * @param value value to be assigned
             * @return modified builder
             */
            Builder value(String value);

            /**
             * Build new instance of {@link ObjectNode}.
             *
             * @return new instance of {@link ObjectNode}.
             */
            ObjectNode build();

            /**
             * Add a config node.
             * The method will determine the type of the node and add it to builder.
             *
             * @param key key of the node
             * @param node node to be added
             * @return modified builder
             */
            Builder addNode(String key, ConfigNode node);
        }
    }

}
