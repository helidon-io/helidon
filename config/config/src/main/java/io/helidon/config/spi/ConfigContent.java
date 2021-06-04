/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
     * 
     * @return a stamp of the content
     */
    default Optional<Object> stamp() {
        return Optional.empty();
    }

    /**
     * A content of an {@link io.helidon.config.spi.OverrideSource}.
     */
    interface OverrideContent extends ConfigContent {
        /**
         * A fluent API builder for {@link io.helidon.config.spi.ConfigContent.OverrideContent}.
         *
         * @return a new builder instance
         */
        static Builder builder() {
            return new Builder();
        }

        /**
         * Data of this override source.
         *
         * @return the data of the underlying source as override data
         */
        OverrideSource.OverrideData data();

        /**
         * Builder.
         */
        class Builder extends ConfigContent.Builder<Builder> implements io.helidon.common.Builder<OverrideContent> {
            // override data
            private OverrideSource.OverrideData data;

            /**
             * Data of this override source.
             * @param data the data of this source
             * @return updated builder instance
             */
            public Builder data(OverrideSource.OverrideData data) {
                this.data = data;
                return this;
            }

            OverrideSource.OverrideData data() {
                return data;
            }

            @Override
            public OverrideContent build() {
                return new ContentImpl.OverrideContentImpl(this);
            }
        }
    }

    /**
     * Config content that provides an {@link io.helidon.config.spi.ConfigNode.ObjectNode} directly, with no need
     * for parsing.
     */
    interface NodeContent extends ConfigContent {
        /**
         * A fluent API builder for {@link io.helidon.config.spi.ConfigContent.NodeContent}.
         *
         * @return a new builder instance
         */
        static Builder builder() {
            return new Builder();
        }

        /**
         * Data of this config source.
         * @return the data of the underlying source as an object node
         */
        ConfigNode.ObjectNode data();

        /**
         * Fluent API builder for {@link io.helidon.config.spi.ConfigContent.NodeContent}.
         */
        class Builder extends ConfigContent.Builder<Builder> implements io.helidon.common.Builder<NodeContent> {
            // node based config source data
            private ConfigNode.ObjectNode rootNode;

            /**
             * Node with the configuration of this content.
             *
             * @param rootNode the root node that links the configuration tree of this source
             * @return updated builder instance
             */
            public Builder node(ConfigNode.ObjectNode rootNode) {
                this.rootNode = rootNode;

                return this;
            }

            ConfigNode.ObjectNode node() {
                return rootNode;
            }

            @Override
            public NodeContent build() {
                return new ContentImpl.NodeContentImpl(this);
            }
        }
    }

    /**
     * Fluent API builder for {@link ConfigContent}, common ancestor for parsable content builder and node content builder.
     *
     * @param <T> type of the implementing builder
     */
    class Builder<T extends Builder<T>> {
        private Object stamp;

        @SuppressWarnings("unchecked")
        private final T me = (T) this;

        Builder() {
        }

        /**
         * Content stamp.
         *
         * @param stamp stamp of the content
         * @return updated builder instance
         */
        public T stamp(Object stamp) {
            this.stamp = stamp;

            return me;
        }

        Object stamp() {
            return stamp;
        }
    }

}
