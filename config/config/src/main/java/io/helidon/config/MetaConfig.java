/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.spi.ChangeWatcher;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.OverrideSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.RetryPolicy;

/**
 * Meta configuration.
 * <p>
 * Configuration allows configuring itself using meta configuration.
 * Config looks for {@code meta-config.*} files in the current directory and on the classpath, where the {@code *} is
 * one of the supported media type suffixes (such as {@code yaml} when {@code helidon-config-yaml} module is on the classpath).
 * <p>
 * Meta configuration can define which config sources to load, including possible retry policy, polling strategy and change
 * watchers.
 * <p>
 * Example of a YAML meta configuration file:
 * <pre>
 * sources:
 *   - type: "environment-variables"
 *   - type: "system-properties"
 *   - type: "file"
 *     properties:
 *       path: "conf/dev.yaml"
 *       optional: true
 *   - type: "file"
 *     properties:
 *       path: "conf/config.yaml"
 *       optional: true
 *   - type: "classpath"
 *     properties:
 *       resource: "default.yaml"
 * </pre>
 * This configuration would load the following config sources (in the order specified):
 * <ul>
 *     <li>Environment variables config source
 *     <li>System properties config source</li>
 *     <li>File config source from file {@code conf/dev.yaml} that is optional</li>
 *     <li>File config source from file {@code conf/config.yaml} that is optional</li>
 *     <li>Classpath resource config source for resource {@code default.yaml} that is mandatory</li>
 * </ul>
 */
public final class MetaConfig {
    private static final Logger LOGGER = Logger.getLogger(MetaConfig.class.getName());
    private static final Set<String> SUPPORTED_MEDIA_TYPES;
    private static final List<String> SUPPORTED_SUFFIXES;

    static {
        Set<String> supportedMediaTypes = new HashSet<>();
        List<String> supportedSuffixes = new LinkedList<>();

        HelidonServiceLoader.create(ServiceLoader.load(ConfigParser.class))
                .forEach(parser -> {
                    supportedMediaTypes.addAll(parser.supportedMediaTypes());
                    supportedSuffixes.addAll(parser.supportedSuffixes());
                });

        SUPPORTED_MEDIA_TYPES = Set.copyOf(supportedMediaTypes);
        SUPPORTED_SUFFIXES = List.copyOf(supportedSuffixes);
    }

    private MetaConfig() {
    }

    /**
     * Create configuration from meta configuration (files or classpath resources), or create a default config instance
     *  if meta configuration is not present.
     *
     * @return a config instance
     */
    public static Config config() {
        return metaConfig().map(MetaConfig::config)
                // if not found, create a default instance
                .orElseGet(MetaConfig::createDefault);
    }

    /**
     * Create configuration from provided meta configuration.
     *
     * @param metaConfig meta configuration
     * @return a new config instance built from meta configuration
     */
    public static Config config(Config metaConfig) {
        return Config.builder()
                .config(metaConfig)
                .build();
    }

    /**
     * Find meta configuration (files or classpath resources) and create a meta configuration instance from it.
     *
     * @return meta configuration if present, or empty
     */
    public static Optional<Config> metaConfig() {
        return MetaConfigFinder.findMetaConfig(SUPPORTED_MEDIA_TYPES::contains, SUPPORTED_SUFFIXES);
    }

    /**
     * Load a polling strategy based on its meta configuration.
     *
     * @param metaConfig meta configuration of a polling strategy
     * @return a polling strategy instance
     */
    public static PollingStrategy pollingStrategy(Config metaConfig) {
        return MetaProviders.pollingStrategy(metaConfig.get("type").asString().get(),
                                             metaConfig.get("properties"));
    }

    /**
     * Load a change watcher based on its meta configuration.
     *
     * @param metaConfig meta configuration of a change watcher
     * @return a change watcher instance
     */
    public static ChangeWatcher<?> changeWatcher(Config metaConfig) {
        String type = metaConfig.get("type").asString().get();
        ChangeWatcher<?> changeWatcher = MetaProviders.changeWatcher(type, metaConfig.get("properties"));

        LOGGER.fine(() -> "Loaded change watcher of type \"" + type + "\", class: " + changeWatcher.getClass().getName());

        return changeWatcher;
    }

    /**
     * Load a retry policy based on its meta configuration.
     *
     * @param metaConfig meta configuration of retry policy
     * @return retry policy instance
     */
    public static RetryPolicy retryPolicy(Config metaConfig) {
        String type = metaConfig.get("type").asString().get();
        RetryPolicy retryPolicy = MetaProviders.retryPolicy(type, metaConfig.get("properties"));

        LOGGER.fine(() -> "Loaded retry policy of type \"" + type + "\", class: " + retryPolicy.getClass().getName());

        return retryPolicy;
    }

    /**
     * Load a config source (or config sources) based on its meta configuration.
     * The metaConfig must contain a key {@code type} that defines the type of the source to be found via providers, and
     *   a key {@code properties} with configuration of the config sources
     * @param sourceMetaConfig meta configuration of a config source
     * @return config source instance
     * @see Config.Builder#config(Config)
     */
    public static List<ConfigSource> configSource(Config sourceMetaConfig) {
        String type = sourceMetaConfig.get("type").asString().get();
        boolean multiSource = sourceMetaConfig.get("multi-source").asBoolean().orElse(false);

        Config sourceProperties = sourceMetaConfig.get("properties");

        if (multiSource) {
            List<ConfigSource> sources = MetaProviders.configSources(type, sourceProperties);

            LOGGER.fine(() -> "Loaded sources of type \"" + type + "\", values: " + sources);

            return sources;
        } else {
            ConfigSource source = MetaProviders.configSource(type, sourceProperties);

            LOGGER.fine(() -> "Loaded source of type \"" + type + "\", class: " + source.getClass().getName());

            return List.of(source);
        }

    }

    // override config source
    static OverrideSource overrideSource(Config sourceMetaConfig) {
        String type = sourceMetaConfig.get("type").asString().get();
        OverrideSource source = MetaProviders.overrideSource(type,
                                                             sourceMetaConfig.get("properties"));

        LOGGER.fine(() -> "Loaded override source of type \"" + type + "\", class: " + source.getClass().getName());

        return source;
    }

    static List<ConfigSource> configSources(Config metaConfig) {
        List<ConfigSource> configSources = new LinkedList<>();

        metaConfig.get("sources")
                .asNodeList()
                .ifPresent(list -> list.forEach(it -> configSources.addAll(MetaConfig.configSource(it))));

        return configSources;
    }

    // only interested in config source
    static List<ConfigSource> configSources(Function<String, Boolean> supportedMediaType, List<String> supportedSuffixes) {
        Optional<Config> metaConfigOpt = metaConfig();

        return metaConfigOpt
                .map(MetaConfig::configSources)
                .orElseGet(() -> MetaConfigFinder.findConfigSource(supportedMediaType, supportedSuffixes)
                        .map(List::of)
                        .orElseGet(List::of));

    }

    private static Config createDefault() {
        // use defaults
        Config.Builder builder = Config.builder();
        MetaConfigFinder.findConfigSource(SUPPORTED_MEDIA_TYPES::contains, SUPPORTED_SUFFIXES).ifPresent(builder::addSource);
        return builder.build();
    }
}
