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

import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;

import io.helidon.config.ConfigException;
import io.helidon.config.ConfigNode;

/**
 * Content of an eager config source.
 * {@link ConfigSource} configuration Content to be
 * {@link ConfigParser#parse(Content) parsed} into
 * {@link io.helidon.config.ConfigNode.ObjectNode hierarchical configuration representation}.
 */
public interface Content {

    default void close() {
    }

    Optional<Object> stamp();

    Optional<Object> target();

    boolean exists();

    static ParsableContentBuilder parsableBuilder() {
        return new ParsableContentBuilder();
    }

    static NodeContentBuilder nodeBuilder() {
        return new NodeContentBuilder();
    }

    interface NodeContent extends Content {
        ConfigNode.ObjectNode data();
    }

    interface ParsableContent extends Content {
        /**
         * Media type of the content. This method is only called if
         * the source {@link io.helidon.config.spi.ConfigSource#exists()} and there is no parser configured.
         *
         * @return content media type if known, {@code empty} otherwise
         */
        Optional<String> mediaType();

        Optional<ConfigParser> parser();

        InputStream data();
    }

    /**
     * Fluent API builder for {@link Content}.
     */
    class Builder<T extends Builder<T>> {
        private boolean exists = true;

        // polling related information
        private Object stamp;
        private Object target;
        @SuppressWarnings("unchecked")
        private final T me = (T) this;

        private Builder() {
        }

        public T exists(boolean exists) {
            this.exists = exists;
            return me;
        }

        public T pollingStamp(Object stamp) {
            this.stamp = stamp;

            return me;
        }

        public T pollingTarget(Object target) {
            this.target = target;

            return me;
        }

        boolean exists() {
            return exists;
        }

        Object stamp() {
            return stamp;
        }

        Object target() {
            return target;
        }
    }

    class ParsableContentBuilder extends Builder<ParsableContentBuilder> implements io.helidon.common.Builder<ParsableContent> {
        private InputStream data;
        private ConfigParser explicitParser;
        private String mediaType;

        private ParsableContentBuilder() {
        }

        public ParsableContentBuilder data(InputStream data) {
            Objects.requireNonNull(data, "Parsable input stream must be provided");
            this.data = data;
            return this;
        }

        public ParsableContentBuilder mediaType(String mediaType) {
            Objects.requireNonNull(mediaType, "Media type must be provided, or this method should not be called");
            this.mediaType = mediaType;
            return this;
        }

        public ParsableContentBuilder parser(ConfigParser parser) {
            Objects.requireNonNull(mediaType, "Parser must be provided, or this method should not be called");
            this.explicitParser = parser;
            return this;
        }

        InputStream data() {
            return data;
        }

        ConfigParser parser() {
            return explicitParser;
        }

        String mediaType() {
            return mediaType;
        }

        @Override
        public ParsableContent build() {
            if (exists() && null == data) {
                throw new ConfigException("Parsable content exists, yet input stream was not configured.");
            }
            return new ContentImpl.ParsableContentImpl(this);
        }
    }

    class NodeContentBuilder extends Builder<NodeContentBuilder> implements io.helidon.common.Builder<NodeContent> {
        // node based config source data
        private ConfigNode.ObjectNode rootNode;

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
