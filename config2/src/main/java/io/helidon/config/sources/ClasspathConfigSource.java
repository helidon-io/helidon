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

package io.helidon.config.sources;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.ConfigException;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.ConfigSourceBase;
import io.helidon.config.spi.ConfigSourceBuilderBase;
import io.helidon.config.spi.Content;

public class ClasspathConfigSource extends ConfigSourceBase implements ConfigSource.ParsableSource {
    private final URL resource;

    private ClasspathConfigSource(Builder builder) {
        super(builder);
        this.resource = builder.url;
    }

    public static ClasspathConfigSource create(String resource) {
        return builder().resource(resource).build();
    }

    public static Collection<? super ClasspathConfigSource> createAll(String resource) {
        String cleaned = resource.startsWith("/") ? resource.substring(1) : resource;

        try {
            Enumeration<URL> resources = Thread.currentThread()
                    .getContextClassLoader()
                    .getResources(cleaned);

            if (resources.hasMoreElements()) {
                List<? super ClasspathConfigSource> sources = new LinkedList<>();
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    sources.add(builder().url(url).build());
                }
                return sources;
            } else {
                // there is none - let the default source handle it, to manage optional vs. mandatory
                // with configuration and not an empty list
                return List.of(create(resource));
            }
        } catch (IOException e) {
            throw new ConfigException("Could not access config resource " + resource, e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean exists() {
        return null != resource;
    }

    @Override
    public Content.ParsableContent load() throws ConfigException {
        InputStream inputStream;
        try {
            inputStream = resource.openStream();
        } catch (IOException e) {
            throw new ConfigException("Failed to read configuration from classpath, resource: " + resource, e);
        }

        Content.ParsableContentBuilder builder = Content.parsableBuilder()
                .data(inputStream);

        MediaTypes.detectType(resource).ifPresent(builder::mediaType);

        return builder.build();
    }

    @Override
    public Optional<String> mediaType() {
        return super.mediaType();
    }

    @Override
    public Optional<ConfigParser> parser() {
        return super.parser();
    }

    public static class Builder extends ConfigSourceBuilderBase<Builder, Void>
            implements ParsableSource.Builder<Builder> {
        private URL url;

        @Override
        public Builder parser(ConfigParser parser) {
            return super.parser(parser);
        }

        @Override
        public Builder mediaType(String mediaType) {
            return super.mediaType(mediaType);
        }

        @Override
        public ClasspathConfigSource build() {
            return new ClasspathConfigSource(this);
        }

        public Builder resource(String resource) {
            String cleaned = resource.startsWith("/") ? resource.substring(1) : resource;

            // the URL may not exist, and that is fine - maybe we are an optional config source
            url = Thread.currentThread()
                    .getContextClassLoader()
                    .getResource(cleaned);

            return this;
        }

        private Builder url(URL url) {
            this.url = url;
            return this;
        }
    }
}
