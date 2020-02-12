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
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

abstract class ContentImpl implements ConfigContent {
    private static final Logger LOGGER = Logger.getLogger(ContentImpl.class.getName());

    private final Object stamp;

    ContentImpl(Builder<?> builder) {
        this.stamp = builder.stamp();
    }

    @Override
    public Optional<Object> stamp() {
        return Optional.ofNullable(stamp);
    }

    static class ParsableContentImpl extends ContentImpl implements ConfigParser.Content {
        private final String mediaType;
        private final InputStream data;
        private final Charset charset;

        ParsableContentImpl(ConfigParser.Content.Builder builder) {
            super(builder);
            this.mediaType = builder.mediaType();
            this.data = builder.data();
            this.charset = builder.charset();
        }

        @Override
        public void close() {
            try {
                data.close();
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Failed to close input stream", e);
            }
        }

        @Override
        public Optional<String> mediaType() {
            return Optional.ofNullable(mediaType);
        }

        @Override
        public InputStream data() {
            return data;
        }

        @Override
        public Charset charset() {
            return charset;
        }
    }

    static class NodeContentImpl extends ContentImpl implements NodeContent {
        private final ConfigNode.ObjectNode data;

        NodeContentImpl(NodeContent.Builder builder) {
            super(builder);
            this.data = builder.node();
        }

        @Override
        public ConfigNode.ObjectNode data() {
            return data;
        }
    }

    static class OverrideContentImpl extends ContentImpl implements OverrideContent {
        private final OverrideSource.OverrideData data;

        OverrideContentImpl(OverrideContent.Builder builder) {
            super(builder);
            this.data = builder.data();
        }

        @Override
        public OverrideSource.OverrideData data() {
            return data;
        }
    }
}
