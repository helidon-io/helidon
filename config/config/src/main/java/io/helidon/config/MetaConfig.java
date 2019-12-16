/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.OverrideSource;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.RetryPolicy;

/**
 * Meta configuration.
 *
 * TODO document meta configuration
 *  - files loaded as part of meta config lookup
 *  - options to specify in meta configuration
 */
public final class MetaConfig {
    private static final Logger LOGGER = Logger.getLogger(MetaConfig.class.getName());

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
        return MetaConfigFinder.findMetaConfig(supportedMediaTypes());
    }

    /**
     * Load a polling strategy based on its meta configuration.
     *
     * @param metaConfig meta configuration of a polling strategy
     * @return a function that creates a polling strategy instance for an instance of target type
     */
    public static Function<Object, PollingStrategy> pollingStrategy(Config metaConfig) {
        return MetaProviders.pollingStrategy(metaConfig.get("type").asString().get(),
                                             metaConfig.get("properties"));
    }

    /**
     * Load a retry policy based on its meta configuration.
     *
     * @param metaConfig meta configuration of retry policy
     * @return retry policy instance
     */
    public static RetryPolicy retryPolicy(Config metaConfig) {
        String type = metaConfig.get("type").asString().get();
        RetryPolicy retryPolicy = MetaProviders.retryPolicy(type,
                                                            metaConfig.get("properties"));

        LOGGER.fine(() -> "Loaded retry policy of type \"" + type + "\", class: " + retryPolicy.getClass().getName());

        return retryPolicy;
    }

    /**
     * Load a config source based on its meta configuration.
     * The metaConfig must contain a key {@code type} that defines the type of the source to be found via providers, and
     *   a key {@code properties} with configuration of the config sources
     * @param sourceMetaConfig meta configuration of a config source
     * @return config source instance
     * @see Config.Builder#config(Config)
     */
    public static ConfigSource configSource(Config sourceMetaConfig) {
        String type = sourceMetaConfig.get("type").asString().get();
        ConfigSource source = MetaProviders.configSource(type,
                                                         sourceMetaConfig.get("properties"));

        LOGGER.fine(() -> "Loaded source of type \"" + type + "\", class: " + source.getClass().getName());

        return source;
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
                .ifPresent(list -> list.forEach(it -> configSources.add(MetaConfig.configSource(it))));

        return configSources;
    }

    // only interested in config source
    static List<ConfigSource> configSources(Function<String, Boolean> supportedMediaType) {
        Optional<Config> metaConfigOpt = metaConfig();

        return metaConfigOpt
                .map(MetaConfig::configSources)
                .orElseGet(() -> MetaConfigFinder.findConfigSource(supportedMediaType)
                        .map(List::of)
                        .orElseGet(List::of));

    }

    private static Function<String, Boolean> supportedMediaTypes() {
        Set<String> supportedMediaTypes = new HashSet<>();

        HelidonServiceLoader.create(ServiceLoader.load(ConfigParser.class))
                .forEach(parser -> supportedMediaTypes.addAll(parser.supportedMediaTypes()));

        return supportedMediaTypes::contains;
    }

    private static Config createDefault() {
        // use defaults
        Config.Builder builder = Config.builder();
        MetaConfigFinder.findConfigSource(supportedMediaTypes()).ifPresent(builder::addSource);
        return builder.build();
    }
}
