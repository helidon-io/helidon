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

package io.helidon.config.spi;

import java.util.Optional;

/**
 * Config content as provided by a config source that can read all its data at once (an "eager" config source).
 * This interface provides necessary support for changes of the underlying source and for parsable content.
 * <p>
 * Content can either provide a {@link io.helidon.config.spi.ConfigNode.ObjectNode} or an array of bytes to be parsed by a
 * {@link io.helidon.config.spi.ConfigParser}.
 * <p>
 * The data stamp can be any object (to be decided by the {@link io.helidon.config.spi.ConfigSource}).
 */
public interface ConfigContent {
    /**
     * Close the content, as it is no longer needed.
     */
    default void close() {
    }

    /**
     * A modification stamp of the content.
     * <p>
     * @return a stamp of the content
     */
    default Optional<Object> stamp() {
        return Optional.empty();
    }

    /**
     * Sometimes the content may disappear between the time we checked it and the time we load it.
     * This defines whether this content exists or not. If not, other methods on this instance will not be called.
     *
     * @return {@code true} if this source exists and has data, {@code false} if the content is not there
     */
    boolean exists();

    /**
     * Config content that provides an {@link io.helidon.config.spi.ConfigNode.ObjectNode} directly, with no need
     * for parsing.
     */
    interface NodeContent extends ConfigContent {
        /**
         * Data of this config source.
         * @return the data of the underlying source as an object node
         */
        ConfigNode.ObjectNode data();
    }

    /**
     * A fluent API builder for {@link io.helidon.config.spi.ConfigContent.NodeContent}.
     *
     * @return a new builder instance
     */
    static NodeContentBuilder nodeBuilder() {
        return new NodeContentBuilder();
    }

    /**
     * Fluent API builder for {@link ConfigContent}, common ancestor for parsable content builder and node content builder.
     */
    class Builder<T extends Builder<T>> {
        private boolean exists = true;
        private Object stamp;

        @SuppressWarnings("unchecked")
        private final T me = (T) this;

        Builder() {
        }

        /**
         * Whether the content exists or not.
         *
         * @param exists default is true
         * @return updated builder instance
         */
        public T exists(boolean exists) {
            this.exists = exists;
            return me;
        }

        /**
         * Content stamp.
         *
         * @param stamp stamp of the content
         */
        public T stamp(Object stamp) {
            this.stamp = stamp;

            return me;
        }

        boolean exists() {
            return exists;
        }

        Object stamp() {
            return stamp;
        }
    }

    /**
     * Fluent API builder for {@link io.helidon.config.spi.ConfigContent.NodeContent}.
     */
    class NodeContentBuilder extends Builder<NodeContentBuilder> implements io.helidon.common.Builder<NodeContent> {
        // node based config source data
        private ConfigNode.ObjectNode rootNode;

        /**
         * Node with the configuration of this content.
         *
         * @param rootNode the root node that links the configuration tree of this source
         * @return updated builder instance
         */
        public NodeContentBuilder node(ConfigNode.ObjectNode rootNode) {
            this.rootNode = rootNode;

            return this;
        }

        ConfigNode.ObjectNode rootNode() {
            return rootNode;
        }

        @Override
        public NodeContent build() {
            return new ContentImpl.NodeContentImpl(this);
        }
    }
}
