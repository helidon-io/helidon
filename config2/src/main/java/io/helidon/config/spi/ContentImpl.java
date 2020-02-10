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

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.ConfigNode;

abstract class ContentImpl implements Content {
    private static final Logger LOGGER = Logger.getLogger(ContentImpl.class.getName());

    private final boolean exists;
    private final Object stamp;
    private final Object target;

    ContentImpl(Builder<?> builder) {
        this.exists = builder.exists();
        this.stamp = builder.stamp();
        this.target = builder.target();
    }

    @Override
    public Optional<Object> stamp() {
        return Optional.ofNullable(stamp);
    }

    @Override
    public Optional<Object> target() {
        return Optional.ofNullable(target);
    }

    @Override
    public boolean exists() {
        return exists;
    }

    static class ParsableContentImpl extends ContentImpl implements ParsableContent {
        private final String mediaType;
        private final ConfigParser parser;
        private final InputStream data;

        ParsableContentImpl(ParsableContentBuilder builder) {
            super(builder);
            this.mediaType = builder.mediaType();
            this.parser = builder.parser();
            this.data = builder.data();
        }

        @Override
        public void close() {
            if (exists()) {
                try {
                    data.close();
                } catch (IOException e) {
                    LOGGER.log(Level.FINE, "Failed to close input stream", e);
                }
            }
        }

        @Override
        public Optional<String> mediaType() {
            return Optional.ofNullable(mediaType);
        }

        @Override
        public Optional<ConfigParser> parser() {
            return Optional.ofNullable(parser);
        }

        @Override
        public InputStream data() {
            return data;
        }
    }

    static class NodeContentImpl extends ContentImpl implements NodeContent {
        private final ConfigNode.ObjectNode data;

        NodeContentImpl(NodeContentBuilder builder) {
            super(builder);
            this.data = builder.rootNode();
        }

        @Override
        public ConfigNode.ObjectNode data() {
            return data;
        }
    }
}
