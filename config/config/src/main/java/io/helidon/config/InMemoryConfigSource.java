/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.util.Objects;
import java.util.Optional;

import io.helidon.config.spi.ConfigContent.NodeContent;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.NodeConfigSource;
import io.helidon.config.spi.ParsableSource;

/**
 * In-memory implementation of config source.
 */
class InMemoryConfigSource {
    private InMemoryConfigSource() {
    }

    static NodeConfigSource create(String uri, NodeContent content) {
        Objects.requireNonNull(uri, "uri cannot be null");
        Objects.requireNonNull(content, "content cannot be null");

        return new NodeInMemory(uri, content);
    }

    static ConfigSource create(String uri, ConfigParser.Content content) {
        Objects.requireNonNull(uri, "uri cannot be null");
        Objects.requireNonNull(content, "content cannot be null");

        return new ParsableInMemory(uri, content);
    }

    private static class InMemory implements ConfigSource {
        private final String uid;

        protected InMemory(String uid) {
            this.uid = uid;
        }

        @Override
        public String description() {
            return ConfigSource.super.description()
                    + "[" + uid + "]";
        }
    }

    private static final class NodeInMemory extends InMemory implements NodeConfigSource {
        private final NodeContent content;

        private NodeInMemory(String uid,  NodeContent nodeContent) {
            super(uid);
            this.content = nodeContent;
        }

        @Override
        public Optional<NodeContent> load() throws ConfigException {
            return Optional.of(content);
        }
    }

    private static final class ParsableInMemory extends InMemory implements ParsableSource {
        private final ConfigParser.Content content;

        protected ParsableInMemory(String uid, ConfigParser.Content content) {
            super(uid);
            this.content = content;
        }

        @Override
        public Optional<ConfigParser.Content> load() throws ConfigException {
            return Optional.of(content);
        }

        @Override
        public Optional<ConfigParser> parser() {
            return Optional.empty();
        }

        @Override
        public Optional<String> mediaType() {
            return Optional.empty();
        }
    }
}


