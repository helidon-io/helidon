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

public class ClasspathConfigSource implements ConfigSource.EagerSource {
    private final Optional<String> configuredMediaType;
    private final Optional<ConfigParser> configuredParser;
    private final URL resource;

    private ClasspathConfigSource(URL resource) {
        this.configuredMediaType = Optional.empty();
        this.configuredParser = Optional.empty();
        this.resource = resource;
    }

    public static ClasspathConfigSource create(String resource) {
        String cleaned = resource.startsWith("/") ? resource.substring(1) : resource;

        URL resourceUrl = Thread.currentThread()
                .getContextClassLoader()
                .getResource(cleaned);

        return new ClasspathConfigSource(resourceUrl);
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
                    sources.add(new ClasspathConfigSource(url));
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

    @Override
    public boolean exists() {
        return null != resource;
    }

    @Override
    public ConfigParser.Content load() throws ConfigException {
        InputStream inputStream = null;
        try {
            inputStream = resource.openStream();
        } catch (IOException e) {
            throw new ConfigException("Failed to read configuration from classpath, resource: " + resource, e);
        }

        ConfigParser.ParsableContentBuilder parsable = ConfigParser.ParsableContentBuilder
                .create(inputStream, "classpath " + resource);

        configuredMediaType.or(() -> MediaTypes.detectType(resource)).ifPresent(parsable::mediaType);
        configuredParser.ifPresent(parsable::parser);

        return ConfigParser.Content.builder()
                .parsable(parsable)
                .build();
    }
}
