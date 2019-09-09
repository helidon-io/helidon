/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.io.StringReader;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.Builder;
import io.helidon.common.CollectionsHelper;
import io.helidon.common.reactive.Flow;
import io.helidon.config.internal.ClasspathConfigSource;
import io.helidon.config.internal.ConfigUtils;
import io.helidon.config.internal.DirectoryConfigSource;
import io.helidon.config.internal.FileConfigSource;
import io.helidon.config.internal.MapConfigSource;
import io.helidon.config.internal.PrefixedConfigSource;
import io.helidon.config.internal.UrlConfigSource;
import io.helidon.config.spi.AbstractConfigSource;
import io.helidon.config.spi.AbstractParsableConfigSource;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;

import static java.util.Objects.requireNonNull;

/**
 * Provides access to built-in {@link ConfigSource} implementations.
 *
 * @see ConfigSource
 */
public final class ConfigSources {

    private static final String SOURCES_KEY = "sources";
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
     * Returns a {@link ConfigSource} that wraps the specified {@code objectNode}
     * and returns it when {@link ConfigSource#load()} is invoked.
     *
     * @param objectNode hierarchical configuration representation that will be
     *                   returned by {@link ConfigSource#load()}
     * @return new instance of {@link ConfigSource}
     * @see ConfigNode.ObjectNode
     * @see ConfigNode.ListNode
     * @see ConfigNode.ValueNode
     * @see ConfigNode.ObjectNode.Builder
     * @see ConfigNode.ListNode.Builder
     */
    public static ConfigSource create(ConfigNode.ObjectNode objectNode) {
        return new ConfigSource() {
            @Override
            public String description() {
                return "InMemoryConfig[ObjectNode]";
            }

            @Override
            public Optional<ConfigNode.ObjectNode> load() throws ConfigException {
                return Optional.of(objectNode);
            }
        };
    }

    /**
     * Provides a {@link ConfigSource} from the provided {@link Readable readable content} and
     * with the specified {@code mediaType}.
     * <p>
     * {@link Instant#now()} is the {@link ConfigParser.Content#stamp() content timestamp}.
     *
     * @param readable  a {@code Readable} providing the configuration content
     * @param mediaType a configuration media type
     * @return a config source
     */
    public static ConfigSource create(Readable readable, String mediaType) {
        return InMemoryConfigSource.builder()
                .mediaType(mediaType)
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .content("Readable", ConfigParser.Content.create(readable, mediaType, Optional.of(Instant.now())))
                .build();
    }

    /**
     * Provides a {@link ConfigSource} from the provided {@code String} content and
     * with the specified {@code mediaType}.
     * <p>
     * {@link Instant#now()} is the {@link ConfigParser.Content#stamp() content timestamp}.
     *
     * @param content a configuration content
     * @param mediaType a configuration media type
     * @return a config source
     */
    public static ConfigSource create(String content, String mediaType) {
        return InMemoryConfigSource.builder()
                .mediaType(mediaType)
                .changesExecutor(Runnable::run)
                .changesMaxBuffer(1)
                .content("String", ConfigParser.Content.create(new StringReader(content), mediaType, Optional.of(Instant.now())))
                .build();
    }

    /**
     * Provides a {@link MapBuilder} for creating a {@code ConfigSource}
     * for a {@code Map}.
     *
     * @param map a map
     * @return new Builder instance
     * @see #create(Properties)
     */
    public static MapBuilder create(Map<String, String> map) {
        return create(map, DEFAULT_MAP_NAME);
    }

    /**
     * Provides a {@link MapBuilder} for creating a {@code ConfigSource}
     * for a {@code Map}.
     *
     * @param map a map
     * @param name the name.
     * @return new Builder instance
     * @see #create(Properties)
     */
    public static MapBuilder create(Map<String, String> map, String name) {
        return new MapBuilder(map, name);
    }

    /**
     * Provides a {@link MapBuilder} for creating a {@code ConfigSource} for a
     * {@code Map} from a {@code Properties} object.
     *
     * @param properties properties
     * @return new Builder instance
     * @see #create(Map)
     */
    public static MapBuilder create(Properties properties) {
        return create(properties, DEFAULT_PROPERTIES_NAME);
    }

    /**
     * Provides a {@link MapBuilder} for creating a {@code ConfigSource} for a
     * {@code Map} from a {@code Properties} object.
     *
     * @param properties properties
     * @param name the name.
     * @return new Builder instance
     * @see #create(Map)
     */
    public static MapBuilder create(Properties properties, String name) {
        return new MapBuilder(properties, name);
    }

    /**
     * Provides a {@link ConfigSource} from a {@link Supplier sourceSupplier}, adding the
     * specified {@code prefix} to the keys in the source.
     *
     * @param key            key prefix to be added to all keys
     * @param sourceSupplier a config source supplier
     * @return new @{code ConfigSource} for the newly-prefixed content
     */
    public static ConfigSource prefixed(String key, Supplier<ConfigSource> sourceSupplier) {
        return new PrefixedConfigSource(key, sourceSupplier.get());
    }

    /**
     * Provides a {@code ConfigSource} for creating a {@code Config} derived
     * from system properties.
     *
     * @return {@code ConfigSource} for config derived from system properties
     */
    public static ConfigSource systemProperties() {
        return new SystemPropertiesConfigSource();
    }

    /**
     * Provides a @{code ConfigSource} for creating a {@code Config} from
     * environment variables.
     *
     * @return {@code ConfigSource} for config derived from environment variables
     */
    public static ConfigSource environmentVariables() {
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
    public static AbstractParsableConfigSource.Builder
            <? extends AbstractParsableConfigSource.Builder<?, Path>, Path> classpath(String resource) {
        return new ClasspathConfigSource.ClasspathBuilder(resource);
    }

    /**
     * Provides a {@code Builder} for creating a {@code ConfigSource} from the specified
     * file path.
     *
     * @param path a file path
     * @return builder for the file-based {@code ConfigSource}
     */
    public static AbstractParsableConfigSource.Builder
            <? extends AbstractParsableConfigSource.Builder<?, Path>, Path> file(String path) {
        return new FileConfigSource.FileBuilder(Paths.get(path));
    }

    /**
     * Provides a {@code Builder} for creating a {@code ConfigSource} from the specified
     * directory path.
     *
     * @param path a directory path
     * @return new Builder instance
     */
    public static AbstractConfigSource.Builder
            <? extends AbstractConfigSource.Builder<?, Path>, Path> directory(String path) {
        return new DirectoryConfigSource.DirectoryBuilder(Paths.get(path));
    }

    /**
     * Provides a {@code Builder} for creating a {@code ConfigSource} from the specified
     * URL.
     *
     * @param url a URL with configuration
     * @return new Builder instance
     * @see #url(URL)
     */
    public static AbstractParsableConfigSource.Builder
            <? extends AbstractParsableConfigSource.Builder<?, URL>, URL> url(URL url) {
        return new UrlConfigSource.UrlBuilder(url);
    }

    /**
     * Provides a {@link CompositeBuilder} for creating a composite
     * {@code ConfigSource} based on the specified {@link ConfigSource}s, used
     * in the order in which they are passed as arguments.
     * <p>
     * By default the resulting {@code ConfigSource} combines the various
     * {@code ConfigSource}s using the system-provided
     * {@link MergingStrategy#fallback() fallback merging strategy}. The
     * application can invoke {@link CompositeBuilder#mergingStrategy} to change
     * how the sources are combined.
     *
     * @param configSources original config sources to be treated as one
     * @return new composite config source builder instance initialized from config sources.
     * @see CompositeBuilder
     * @see MergingStrategy
     * @see #create(Supplier[])
     * @see #create(List)
     * @see #load(Supplier[])
     * @see #load(Config)
     * @see Config#create(Supplier[])
     * @see Config#builder(Supplier[])
     */
    @SafeVarargs
    public static CompositeBuilder create(Supplier<ConfigSource>... configSources) {
        return create(CollectionsHelper.listOf(configSources));
    }

    /**
     * Provides a {@link CompositeBuilder} for creating a composite
     * {@code ConfigSource} based on the {@link ConfigSource}s and their order
     * in the specified list.
     * <p>
     * By default the resulting {@code ConfigSource} combines the various
     * {@code ConfigSource}s using the system-provided
     * {@link MergingStrategy#fallback() fallback merging strategy}. The
     * application can invoke {@link CompositeBuilder#mergingStrategy} to change
     * how the sources are combined.
     *
     * @param configSources original config sources to be treated as one
     * @return new composite config source builder instance initialized from config sources.
     * @see CompositeBuilder
     * @see MergingStrategy
     * @see #create(Supplier[])
     * @see #create(List)
     * @see #load(Supplier[])
     * @see #load(Config)
     * @see Config#create(Supplier[])
     * @see Config#builder(Supplier[])
     */
    public static CompositeBuilder create(List<Supplier<ConfigSource>> configSources) {
        return new CompositeBuilder(configSources);
    }

    /**
     * Provides a {@link CompositeBuilder} for creating a composite
     * {@code ConfigSource} based on the {@code ConfigSource}s returned by the
     * provided meta-sources.
     * <p>
     * Each meta-source must contain the {@code sources} property which is an
     * array of config sources. See {@link ConfigSource#create(Config)} for more
     * information about the format of meta-configuration.
     * <p>
     * The returned builder is a {@code CompositeBuilder} that combines the
     * config from all config sources derived from the meta-configuration in the
     * meta-sources. By default the composite builder uses the
     * {@link MergingStrategy#fallback() fallback merging strategy}.
     *
     * @param metaSources ordered list of meta-sources from which the builder
     * will read meta-configuration indicating the config sources
     * @return new composite config source builder initialized from the
     * specified meta-sources.
     * @see CompositeBuilder
     * @see MergingStrategy
     * @see #create(Supplier[])
     * @see #create(List)
     * @see #load(Supplier[])
     * @see #load(Config)
     * @see Config#builderLoadSourcesFrom(Supplier[])
     * @see Config#loadSourcesFrom(Supplier[])
     */
    @SafeVarargs
    public static CompositeBuilder load(Supplier<ConfigSource>... metaSources) {
        return load(Config.builder(metaSources)
                            .disableEnvironmentVariablesSource()
                            .disableSystemPropertiesSource()
                            .build());
    }

    /**
     * Provides a {@link CompositeBuilder} for creating a composite
     * {@code ConfigSource} based on the {@link ConfigSource}s returned by the
     * provided meta-configuration.
     * <p>
     * The meta-configuration must contain the {@code sources} property which is
     * an array of config sources. See {@link ConfigSource#create(Config)} for
     * more information about the format of meta-configuration.
     * <p>
     * The returned builder is a {@code CompositeBuilder} that combines the
     * config from all config sources derived from the meta-configuration. By
     * default the composite builder uses the
     * {@link MergingStrategy#fallback() fallback merging strategy}.
     *
     * @param metaConfig meta-configuration from which the builder will derive
     * config sources
     * @return new composite config source builder initialized from the
     * specified meta-config
     * @see CompositeBuilder
     * @see MergingStrategy
     * @see #create(Supplier[])
     * @see #create(List)
     * @see #load(Supplier[])
     * @see #load(Config)
     * @see Config#builderLoadSourcesFrom(Supplier[])
     * @see Config#loadSourcesFrom(Supplier[])
     */
    public static CompositeBuilder load(Config metaConfig) {
        List<Supplier<ConfigSource>> sources = metaConfig.get(SOURCES_KEY)
                .asNodeList()
                .orElse(CollectionsHelper.listOf())
                .stream()
                .map(node -> node.as(ConfigSource::create))
                .map(ConfigValue::get)
                .collect(Collectors.toList());

        return ConfigSources.create(sources);
    }

    /**
     * Builder of a {@code ConfigSource} based on a {@code Map} containing
     * config entries.
     * <p>
     * The caller constructs a {@code MapBuilder} with either a {@code Map} or a
     * {@code Properties} object. The builder uses the {@code Map} entries or
     * {@code Properties} name/value pairs to create config entries:
     * <ul>
     * <li>Each {@code Map} key or {@code Properties} property name is the
     * fully-qualified dotted-name format key for the corresponding
     * {@code Config} node.
     * <li>Each {@code Map} value or {@code Properties} property value is the
     * corresponding value of the corresponding {@code Config} node.
     * </ul>
     * By default, if the provided {@code Map} or {@code Properties} object
     * contains duplicate keys then the {@link ConfigSource#load} on the
     * returned {@code ConfigSource} will fail. The caller can invoke
     * {@link MapBuilder#lax} to relax this restriction, in which case the
     * {@code load} operation will log collision warnings but continue.
     * <p>
     * For example, the following properties collide:
     * <pre>{@code
     * app.port = 8080
     * app      = app-name
     * }</pre>
     * <p>
     * The {@code MapConfigSource} returned by {@link #build} and {@link #get}
     * works with an immutable copy of original map; it does
     * <strong>not</strong> support
     * {@link ConfigSource#changes() ConfigSource mutability}.
     */
    public static final class MapBuilder implements Builder<ConfigSource> {
        private Map<String, String> map;
        private boolean strict;
        private String mapSourceName;
        private volatile ConfigSource configSource;

        private MapBuilder(final Map<String, String> map, final String name) {
            requireNonNull(name, "name cannot be null");
            requireNonNull(map, "map cannot be null");

            this.map = Collections.unmodifiableMap(map);
            this.strict = true;
            this.mapSourceName = name;
        }

        private MapBuilder(final Properties properties, final String name) {
            requireNonNull(name, "name cannot be null");
            requireNonNull(properties, "properties cannot be null");

            this.map = ConfigUtils.propertiesToMap(properties);
            this.strict = true;
            this.mapSourceName = name;
        }

        /**
         * Switches off strict mode.
         * <p>
         * In lax mode {@link ConfigSource#load()} does not fail if config keys
         * collide; it logs warnings and continues.
         *
         * @return updated builder
         */
        public MapBuilder lax() {
            strict = false;

            return this;
        }

        /**
         * Builds new instance of {@code MapConfigSource} from the {@code Map}
         * or {@code Properties} passed to a constructor.
         *
         * @return {@link MapConfigSource} based on the specified {@code Map} or {@code Properties}
         */
        @Override
        public ConfigSource build() {
            return new MapConfigSource(map, strict, mapSourceName);
        }

        @Override
        public ConfigSource get() {
            if (configSource == null) {
                configSource = build();
            }
            return configSource;
        }
    }

    /**
     * Builder of a {@code ConfigSource} that encapsulates multiple separate
     * {@code ConfigSource}s.
     * <p>
     * The caller invokes {@link #add} one or more times to assemble an ordered
     * list of {@code ConfigSource}s to be combined. The {@link #build} and
     * {@link #get} methods return a single {@code ConfigSource} that combines
     * the ordered sequence of {@code ConfigSource}s using a merging strategy
     * (by default, the
     * {@link MergingStrategy#fallback() fallback merging strategy}). The caller
     * can change the merging strategy by passing an alternative strategy to the
     * {@link #mergingStrategy} method.
     * <p>
     * The {@code CompositeBuilder} also supports change monitoring. The
     * application can control these aspects:
     * <table class="config">
     * <caption>Application Control of Change Monitoring</caption>
     * <tr>
     * <th>Change Support Behavior</th>
     * <th>Use</th>
     * <th>Method</th>
     * </tr>
     * <tr>
     * <td>reload executor</td>
     * <td>The executor used to reload the configuration upon detection of a change in the
     * underlying {@code ConfigSource}</td>
     * <td>{@link #changesExecutor(java.util.concurrent.ScheduledExecutorService)
     * }</td>
     * </tr>
     * <tr>
     * <td>debounce timeout</td>
     * <td>Minimum delay between reloads - helps reduce multiple reloads due to
     * multiple changes in a short period, collecting a group of changes into
     * one notification</td>
     * <td>{@link #changesDebounce(java.time.Duration)}</td>
     * </tr>
     * <tr>
     * <td>buffer size</td>
     * <td>Maximum number of changes allowed in the change flow</td>
     * <td>{@link #changesMaxBuffer(int) }</td>
     * </tr>
     * </table>
     *
     * @see ConfigSources#create(Supplier...)
     * @see MergingStrategy
     * @see MergingStrategy#fallback() default merging strategy
     */
    public static class CompositeBuilder implements Builder<ConfigSource> {

        private static final long DEFAULT_CHANGES_DEBOUNCE_TIMEOUT = 100;

        private final List<ConfigSource> configSources;
        private MergingStrategy mergingStrategy;
        private ScheduledExecutorService changesExecutor;
        private int changesMaxBuffer;
        private Duration debounceTimeout;
        private volatile ConfigSource configSource;

        private CompositeBuilder(List<Supplier<ConfigSource>> configSources) {
            this.configSources = initConfigSources(configSources);

            changesExecutor = CompositeConfigSource.DEFAULT_CHANGES_EXECUTOR_SERVICE;
            debounceTimeout = Duration.ofMillis(DEFAULT_CHANGES_DEBOUNCE_TIMEOUT);
            changesMaxBuffer = Flow.defaultBufferSize();
        }

        private static List<ConfigSource> initConfigSources(List<Supplier<ConfigSource>> sourceSuppliers) {
            List<ConfigSource> configSources = new LinkedList<>();
            for (Supplier<ConfigSource> configSupplier : sourceSuppliers) {
                configSources.add(configSupplier.get());
            }
            return configSources;
        }

        /**
         * Adds a {@code ConfigSource} to the ordered list of sources.
         *
         * @param source config source
         * @return updated builder
         */
        public CompositeBuilder add(Supplier<ConfigSource> source) {
            requireNonNull(source, "source cannot be null");

            configSources.add(source.get());
            return this;
        }

        /**
         * Sets the strategy to be used for merging the root nodes provided by
         * the list of {@code ConfigSource}s.
         *
         * @param mergingStrategy strategy for merging root nodes from the
         * config sources
         * @return updated builder
         * @see MergingStrategy#fallback()
         */
        public CompositeBuilder mergingStrategy(MergingStrategy mergingStrategy) {
            requireNonNull(mergingStrategy, "mergingStrategy cannot be null");

            this.mergingStrategy = mergingStrategy;
            return this;
        }

        /**
         * Specifies {@link ScheduledExecutorService} on which reloads of the
         * config source will occur.
         * <p>
         * By default, a dedicated thread pool that can schedule reload commands to
         * run after a given {@link #debounceTimeout timeout} is used.
         *
         * @param changesExecutor the executor used for scheduling config source
         * reloads
         * @return updated builder
         * @see #changesDebounce(Duration)
         */
        public CompositeBuilder changesExecutor(ScheduledExecutorService changesExecutor) {
            this.changesExecutor = changesExecutor;
            return this;
        }

        /**
         * Specifies debounce timeout for reloading the config after the
         * underlying config source(s) change.
         * <p>
         * Debouncing reduces the number of change events by collecting any
         * changes over the debounce timeout interval into a single event.
         * <p>
         * The default is {@code 100} milliseconds.
         *
         * @param debounceTimeout debounce timeout to process reloading
         * @return modified builder instance
         * @see #changesExecutor(ScheduledExecutorService)
         */
        public CompositeBuilder changesDebounce(Duration debounceTimeout) {
            this.debounceTimeout = debounceTimeout;
            return this;
        }

        /**
         * Specifies maximum capacity for each subscriber's buffer to be used to deliver
         * {@link ConfigSource#changes() config source changes}.
         * <p>
         * By default {@link Flow#defaultBufferSize()} is used.
         * <p>
         * Note: Any events not consumed by a subscriber will be lost.
         *
         * @param changesMaxBuffer the maximum capacity for each subscriber's buffer of {@link ConfigSource#changes()} events.
         * @return modified builder instance
         * @see #changesExecutor(ScheduledExecutorService)
         * @see ConfigSource#changes()
         */
        public CompositeBuilder changesMaxBuffer(int changesMaxBuffer) {
            this.changesMaxBuffer = changesMaxBuffer;
            return this;
        }

        /**
         * Builds new instance of Composite ConfigSource.
         *
         * @return new instance of Composite ConfigSource.
         */
        @Override
        public ConfigSource build() {
            final List<ConfigSource> finalConfigSources = new LinkedList<>(configSources);

            final MergingStrategy finalMergingStrategy = mergingStrategy != null
                    ? mergingStrategy
                    : new FallbackMergingStrategy();

            return createCompositeConfigSource(finalConfigSources, finalMergingStrategy, changesExecutor, debounceTimeout,
                                               changesMaxBuffer);
        }

        @Override
        public ConfigSource get() {
            if (configSource == null) {
                configSource = build();
            }
            return configSource;
        }

        CompositeConfigSource createCompositeConfigSource(List<ConfigSource> finalConfigSources,
                                                          MergingStrategy finalMergingStrategy,
                                                          ScheduledExecutorService reloadExecutorService,
                                                          Duration debounceTimeout,
                                                          int changesMaxBuffer) {
            return new CompositeConfigSource(finalConfigSources, finalMergingStrategy, reloadExecutorService, debounceTimeout,
                                             changesMaxBuffer);
        }

    }

    /**
     * An algorithm for combining multiple {@code ConfigNode.ObjectNode} root nodes
     * into a single {@code ConfigNode.ObjectNode} root node.
     *
     * @see ConfigSources#create(Supplier...)
     * @see CompositeBuilder
     * @see CompositeBuilder#mergingStrategy(MergingStrategy)
     * @see #fallback() default merging strategy
     */
    public interface MergingStrategy {
        /**
         * Merges an ordered list of {@link ConfigNode.ObjectNode}s into a
         * single instance.
         * <p>
         * Typically nodes (object, list or value) from a root earlier in the
         * list are considered to have a higher priority than nodes from a root
         * that appears later in the list, but this is not required and is
         * entirely up to each {@code MergingStrategy} implementation.
         *
         * @param rootNodes list of root nodes to combine
         * @return ObjectNode root node resulting from the merge
         */
        ConfigNode.ObjectNode merge(List<ConfigNode.ObjectNode> rootNodes);

        /**
         * Returns an implementation of {@code MergingStrategy} in which nodes
         * from a root earlier in the list have higher priority than nodes from
         * a root later in the list.
         * <p>
         * The merged behavior is as if the resulting merged {@code Config},
         * when resolving a value of a key, consults the {@code Config} roots in
         * the order they were passed to {@code merge}. As soon as it finds a
         * {@code Config} tree containing a value for the key is it immediately
         * returns that value, disregarding other later config roots.
         *
         * @return new instance of fallback merging strategy
         */
        static ConfigSources.MergingStrategy fallback() {
            return new FallbackMergingStrategy();
        }

    }

    /**
     * Holder of singleton instance of {@link ConfigSource}.
     *
     * @see ConfigSources#empty()
     */
    private static final class EmptyConfigSourceHolder {

        private EmptyConfigSourceHolder() {
            throw new AssertionError("Instantiation not allowed.");
        }

        /**
         * EMPTY singleton instance.
         */
        private static final ConfigSource EMPTY = new ConfigSource() {
            @Override
            public String description() {
                return "Empty";
            }

            @Override
            public Optional<ConfigNode.ObjectNode> load() throws ConfigException {
                return Optional.empty();
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
            super(EnvironmentVariables.expand(), false, "");
        }
    }

    /**
     * System properties config source.
     */
    static final class SystemPropertiesConfigSource extends MapConfigSource {
        /**
         * Constructor.
         */
        SystemPropertiesConfigSource() {
            super(ConfigUtils.propertiesToMap(System.getProperties()), false, "");
        }
    }
}
