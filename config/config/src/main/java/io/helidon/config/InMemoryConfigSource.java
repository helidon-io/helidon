/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.config.spi.AbstractParsableConfigSource;
import io.helidon.config.spi.ConfigParser;

/**
 * In-memory implementation of config source.
 */
class InMemoryConfigSource extends AbstractParsableConfigSource<Object> {

    private final String uri;
    private final ConfigParser.Content<Object> content;

    InMemoryConfigSource(InMemoryConfigSource.Builder builder) {
        super(builder);

        uri = builder.getUri();
        content = builder.getContent();
    }

    @Override
    protected String uid() {
        return uri;
    }

    @Override
    protected Optional<Object> dataStamp() {
        return Optional.of(this);
    }

    @Override
    protected ConfigParser.Content<Object> content() throws ConfigException {
        return content;
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder extends AbstractParsableConfigSource.Builder<InMemoryConfigSource.Builder, Void> {

        private String uri;
        private ConfigParser.Content content;

        Builder() {
            super(Void.class);
        }

        Builder content(String uri, ConfigParser.Content content) {
            Objects.requireNonNull(uri, "uri cannot be null");
            Objects.requireNonNull(content, "content cannot be null");

            this.uri = uri;
            this.content = content;
            return this;
        }

        @Override
        public InMemoryConfigSource build() {
            Objects.requireNonNull(uri, "uri cannot be null");
            Objects.requireNonNull(content, "content cannot be null");

            return new InMemoryConfigSource(this);
        }

        private String getUri() {
            return uri;
        }

        private ConfigParser.Content getContent() {
            return content;
        }
    }
}
