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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;

import io.helidon.config.spi.ConfigContent;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.NodeConfigSource;

/**
 * Provides access to built-in {@link ConfigSource} implementations.
 *
 * @see ConfigSource
 */
public final class ConfigSources {

    static final String DEFAULT_MAP_NAME = "map";
    static final String DEFAULT_PROPERTIES_NAME = "properties";

    private ConfigSources() {
        throw new AssertionError("Instantiation not allowed.");
    }

    /**
     * Provides an empty config source.
     * @return empty config source
     */
    public static ConfigSource empty() {
        return EmptyConfigSourceHolder.EMPTY;
    }

    /**
     * Returns a {@link ConfigSource} that contains the same configuration model
     * as the provided {@link Config
     * config}.
     *
     * @param config the original {@code Config}
     * @return {@code ConfigSource} for the same {@code Config} as the original
     */
    public static ConfigSource create(Config config) {
        return ConfigSources.create(config.asMap().get()).get();
    }

    /**
     * Returns a {@link ConfigSource} that wraps the specified {@code objectNode}.
     *
     * @param objectNode hierarchical configuration representation that will be
     *                   returned by the created config source
     * @return new instance of {@link ConfigSource}
     * @see ConfigNode.ObjectNode
     * @see ConfigNode.ListNode
     * @see ConfigNode.ValueNode
     * @see ConfigNode.ObjectNode.Builder
     * @see ConfigNode.ListNode.Builder
     */
    public static NodeConfigSource create(ConfigNode.ObjectNode objectNode) {
        return InMemoryConfigSource.create("ObjectNode", ConfigContent.NodeContent.builder()
                .node(objectNode)
                .build());
    }

    /**
     * Provides a {@link ConfigSource} from the provided {@link Readable readable content} and
     * with the specified {@code mediaType}.
     * <p>
     * {@link Instant#now()} is the {@link io.helidon.config.spi.ConfigContent#stamp() content timestamp}.
     *
     * @param data  a {@code InputStream} providing the configuration content
     * @param mediaType a configuration media type
     * @return a config source
     */
    public static ConfigSource create(InputStream data, String mediaType) {
        return InMemoryConfigSource.create("Readable", ConfigParser.Content.builder()
                .data(data)
                .mediaType(mediaType)
                .build());
    }

    /**
     * Provides a {@link ConfigSource} from the provided {@code String} content and
     * with the specified {@code mediaType}.
     * <p>
     * {@link Instant#now()} is the {@link io.helidon.config.spi.ConfigContent#stamp() content timestamp}.
     *
     * @param content a configuration content
     * @param mediaType a configuration media type
     * @return a config source
     */
    public static ConfigSource create(String content, String mediaType) {
        return InMemoryConfigSource.create("String", ConfigParser.Content.builder()
                .data(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))
                .mediaType(mediaType)
                .build());
    }

    /**
     * Provides a {@link MapConfigSource.Builder} for creating a {@code ConfigSource}
     * for a {@code Map}.
     *
     * @param map a map
     * @return new Builder instance
     * @see #create(Properties)
     */
    public static MapConfigSource.Builder create(Map<String, String> map) {
        return create(map, DEFAULT_MAP_NAME);
    }

    /**
     * Provides a {@link MapConfigSource.Builder} for creating a {@code ConfigSource}
     * for a {@code Map}.
     *
     * @param map a map
     * @param name the name.
     * @return new Builder instance
     * @see #create(Properties)
     */
    public static MapConfigSource.Builder create(Map<String, String> map, String name) {
        return MapConfigSource.builder()
                .map(map)
                .name(name);
    }

    /**
     * Provides a {@link MapConfigSource.Builder} for creating a {@code ConfigSource} for a
     * {@code Map} from a {@code Properties} object.
     *
     * @param properties properties
     * @return new Builder instance
     * @see #create(Map)
     */
    public static MapConfigSource.Builder create(Properties properties) {
        return create(properties, DEFAULT_PROPERTIES_NAME);
    }

    /**
     * Provides a {@link MapConfigSource.Builder} for creating a {@code ConfigSource} for a
     * {@code Map} from a {@code Properties} object.
     *
     * @param properties properties
     * @param name the name.
     * @return new Builder instance
     * @see #create(Map)
     */
    public static MapConfigSource.Builder create(Properties properties, String name) {
        return MapConfigSource.builder()
                .properties(properties)
                .name(name);
    }

    /**
     * Provides a {@link ConfigSource} from a {@link Supplier sourceSupplier}, adding the
     * specified {@code prefix} to the keys in the source.
     *
     * @param key            key prefix to be added to all keys
     * @param sourceSupplier a config source supplier
     * @return new @{code ConfigSource} for the newly-prefixed content
     */
    public static ConfigSource prefixed(String key, Supplier<? extends ConfigSource> sourceSupplier) {
        return PrefixedConfigSource.create(key, sourceSupplier.get());
    }

    /**
     * Provides a {@code ConfigSource} for creating a {@code Config} derived
     * from system properties.
     *
     * @return {@code ConfigSource} for config derived from system properties
     */
    public static SystemPropertiesConfigSource.Builder systemProperties() {
        return new SystemPropertiesConfigSource.Builder()
                .properties(System.getProperties());
    }

    /**
     * Provides a @{code ConfigSource} for creating a {@code Config} from
     * environment variables.
     *
     * @return {@code ConfigSource} for config derived from environment variables
     */
    public static MapConfigSource environmentVariables() {
        return new EnvironmentVariablesConfigSource();
    }

    /**
     * Provides a {@code Builder} for creating a {@code ConfigSource}
     * from the specified resource located on the classpath of the current
     * thread's context classloader.
     * <p>
     * The name of a resource is a '{@code /}'-separated full path name that
     * identifies the resource. If the resource name has a leading slash then it
     * is dropped before lookup.
     *
     * @param resource a name of the resource
     * @return builder for a {@code ConfigSource} for the classpath-based resource
     */
    public static ClasspathConfigSource.Builder classpath(String resource) {
        return ClasspathConfigSource.builder().resource(resource);
    }

    /**
     * Create builders for each instance of the resource on the current classpath.
     * @param resource resource to look for
     * @return a list of classpath config source builders
     */
    public static List<UrlConfigSource.Builder> classpathAll(String resource) {

        List<UrlConfigSource.Builder> result = new LinkedList<>();
        try {
            Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(resource);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                result.add(url(url));
            }
        } catch (IOException e) {
            throw new ConfigException("Failed to read " + resource + " from classpath", e);
        }

        return result;
    }

    /**
     * Provides a {@code Builder} for creating a {@code ConfigSource} from the specified
     * file path.
     *
     * @param path a file path
     * @return builder for the file-based {@code ConfigSource}
     */
    public static FileConfigSource.Builder file(String path) {
        return FileConfigSource.builder().path(Paths.get(path));
    }

    /**
     * Provides a {@code Builder} for creating a {@code ConfigSource} from the specified
     * file path.
     *
     * @param path a file path
     * @return builder for the file-based {@code ConfigSource}
     */
    public static FileConfigSource.Builder file(Path path) {
        return FileConfigSource.builder().path(path);
    }

    /**
     * Provides a {@code Builder} for creating a {@code ConfigSource} from the specified
     * directory path.
     *
     * @param path a directory path
     * @return new Builder instance
     */
    public static DirectoryConfigSource.Builder directory(String path) {
        return DirectoryConfigSource.builder().path(Paths.get(path));
    }

    /**
     * Provides a {@code Builder} for creating a {@code ConfigSource} from the specified
     * URL.
     *
     * @param url a URL with configuration
     * @return new Builder instance
     * @see #url(URL)
     */
    public static UrlConfigSource.Builder url(URL url) {
        return UrlConfigSource.builder().url(url);
    }

    /**
     * Holder of singleton instance of {@link ConfigSource}.
     *
     * @see ConfigSources#empty()
     */
    static final class EmptyConfigSourceHolder {

        private EmptyConfigSourceHolder() {
            throw new AssertionError("Instantiation not allowed.");
        }

        /**
         * EMPTY singleton instance.
         */
        static final ConfigSource EMPTY = new NodeConfigSource() {
            @Override
            public String description() {
                return "Empty";
            }

            @Override
            public Optional<ConfigContent.NodeContent> load() throws ConfigException {
                return Optional.empty();
            }

            @Override
            public String toString() {
                return "EmptyConfigSource";
            }

            @Override
            public boolean optional() {
                return true;
            }
        };
    }

    /**
     * Environment variables config source.
     */
    static final class EnvironmentVariablesConfigSource extends MapConfigSource {
        /**
         * Constructor.
         */
        EnvironmentVariablesConfigSource() {
            super(MapConfigSource.builder().map(EnvironmentVariables.expand()).name(""));
        }
    }

    /**
     * System properties config source.
     */
    public static final class SystemPropertiesConfigSource extends MapConfigSource {
        private SystemPropertiesConfigSource(Builder builder) {
            super(builder);
        }

        /**
         * A fluent API builder for {@link io.helidon.config.ConfigSources.SystemPropertiesConfigSource}.
         */
        public static final class Builder extends MapBuilder<Builder> {
            private Builder() {
            }

            @Override
            public MapConfigSource build() {
                super.name("");
                return new SystemPropertiesConfigSource(this);
            }

            @Override
            public Builder name(String sourceName) {
                return this;
            }
        }
    }
}
