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
 *
 *
 */

package io.helidon.config;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Config node implementation.
 */
public interface ConfigNode extends Supplier<String> {
    /**
     * Get the type of this node.
     *
     * @return NodeType this node represents
     */
    NodeType nodeType();

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
         * Create new instance of the {@link ConfigNode.ValueNode} from specified String {@code value}.
         *
         * @param value string value
         * @return new instance of the {@link ConfigNode.ValueNode}
         */
        static ValueNode create(String value) {
            return ValueNodeImpl.create(value);
        }
    }

    /**
     * ConfigNode-based list of configuration values.
     * <p>
     * List contains instance of {@link ConfigNode.ValueNode}, {@link ConfigNode.ListNode} as well as {@link ConfigNode.ObjectNode}.
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
         * Builder to build {@link ConfigNode.ListNode} instance.
         */
        interface Builder extends io.helidon.common.Builder<ListNode> {
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
     * suitable {@link io.helidon.config.spi.ConfigParser} if necessary.
     * <p>
     * In the map exposed by this interface, the map keys are {@code String}s
     * containing the fully-qualified dotted names of the config keys and the
     * map values are the corresponding {@link ConfigNode.ValueNode} or {@link ConfigNode.ListNode}
     * instances. The map never contains {@link ConfigNode.ObjectNode} values because the
     * {@link ConfigNode.ObjectNode} is implemented as a flat map.
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
            return ConfigUtils.EmptyObjectNodeHolder.EMPTY;
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
            return new ObjectNodeBuilderImpl();
        }

        /**
         * Builder to build {@link ConfigNode.ObjectNode} instance.
         */
        interface Builder {

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
             * Build new instance of {@link ConfigNode.ObjectNode}.
             *
             * @return new instance of {@link ConfigNode.ObjectNode}.
             */
            ObjectNode build();

        }
    }

}
