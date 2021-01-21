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

package io.helidon.config.mp;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigHelper;
import io.helidon.config.spi.ConfigContent;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.LazyConfigSource;
import io.helidon.config.spi.NodeConfigSource;
import io.helidon.config.spi.ParsableSource;

import org.eclipse.microprofile.config.spi.ConfigSource;

final class MpHelidonSource {
    private MpHelidonSource() {
    }

    static ConfigSource create(io.helidon.config.spi.ConfigSource source) {
        source.init(it -> {
            throw new UnsupportedOperationException(
                    "Source runtimes are not available in MicroProfile Config implementation");
        });

        if (!source.exists() && !source.optional()) {
            throw new ConfigException("Config source " + source + " is mandatory, yet it does not exist.");
        }

        if (source instanceof NodeConfigSource) {
            Optional<ConfigContent.NodeContent> load = ((NodeConfigSource) source).load();
            // load the data, create a map from it
            return MpConfigSources.create(source.description(),
                                          load.map(ConfigContent.NodeContent::data)
                                                  .map(ConfigHelper::flattenNodes)
                                                  .orElseGet(Map::of));
        }

        if (source instanceof ParsableSource) {
            return HelidonParsableSource.create((ParsableSource) source);
        }

        if (source instanceof LazyConfigSource) {
            return new HelidonLazySource(source, (LazyConfigSource) source);
        }

        throw new IllegalArgumentException(
                "Helidon config source must be one of: node source, parsable source, or lazy source. Provided is neither: "
                        + source.getClass().getName());
    }

    private static class HelidonParsableSource {
        public static ConfigSource create(ParsableSource source) {
            Optional<ConfigParser.Content> load = source.load();
            if (load.isEmpty()) {
                return MpConfigSources.create(source.description(), Map.of());
            }
            ConfigParser.Content content = load.get();

            String mediaType = content.mediaType()
                    .or(source::mediaType)
                    .orElseThrow(() -> new ConfigException("Source " + source + " does not provide media type, cannot use it."));

            ConfigParser parser = source.parser()
                    .or(() -> findParser(mediaType))
                    .orElseThrow(() -> new ConfigException("Could not locate config parser for media type: \""
                                                                   + mediaType + "\""));

            // create a map from parsed node
            return MpConfigSources.create(source.description(),
                                          ConfigHelper.flattenNodes(parser.parse(content)));

        }

        private static Optional<ConfigParser> findParser(String mediaType) {
            return HelidonServiceLoader.create(ServiceLoader.load(ConfigParser.class))
                    .asList()
                    .stream()
                    .filter(it -> it.supportedMediaTypes().contains(mediaType))
                    .findFirst();
        }
    }

    private static class HelidonLazySource implements ConfigSource {
        private final Map<String, String> loadedProperties = new ConcurrentHashMap<>();
        private final LazyConfigSource lazy;
        private final io.helidon.config.spi.ConfigSource source;

        private HelidonLazySource(io.helidon.config.spi.ConfigSource source, LazyConfigSource lazy) {
            this.lazy = lazy;
            this.source = source;
        }

        @Override
        public Map<String, String> getProperties() {
            return Collections.unmodifiableMap(loadedProperties);
        }

        @Override
        public Set<String> getPropertyNames() {
            return loadedProperties.keySet();
        }

        @Override
        public String getValue(String propertyName) {
            String value = lazy.node(propertyName)
                    .flatMap(ConfigNode::value)
                    .orElse(null);

            if (null == value) {
                loadedProperties.remove(propertyName);
            } else {
                loadedProperties.put(propertyName, value);
            }

            return value;
        }

        @Override
        public String getName() {
            return source.description();
        }
    }
}
