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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.Priority;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.spi.ChangeWatcher;
import io.helidon.config.spi.ChangeWatcherProvider;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.ConfigSourceProvider;
import io.helidon.config.spi.OverrideSource;
import io.helidon.config.spi.OverrideSourceProvider;
import io.helidon.config.spi.PollingStrategy;
import io.helidon.config.spi.PollingStrategyProvider;
import io.helidon.config.spi.RetryPolicy;
import io.helidon.config.spi.RetryPolicyProvider;

/**
 * Access to Java service loaders for config sources, retry policies and polling strategies.
 */
final class MetaProviders {
    private static final List<ConfigSourceProvider> CONFIG_SOURCE_PROVIDERS;
    private static final List<RetryPolicyProvider> RETRY_POLICY_PROVIDERS;
    private static final List<PollingStrategyProvider> POLLING_STRATEGY_PROVIDERS;
    private static final List<ChangeWatcherProvider> CHANGE_WATCHER_PROVIDERS;
    private static final List<OverrideSourceProvider> OVERRIDE_SOURCE_PROVIDERS;

    private static final Set<String> SUPPORTED_CONFIG_SOURCES = new HashSet<>();
    private static final Set<String> SUPPORTED_RETRY_POLICIES = new HashSet<>();
    private static final Set<String> SUPPORTED_POLLING_STRATEGIES = new HashSet<>();
    private static final Set<String> SUPPORTED_CHANGE_WATCHERS = new HashSet<>();
    private static final Set<String> SUPPORTED_OVERRIDE_SOURCES = new HashSet<>();

    static {
        CONFIG_SOURCE_PROVIDERS = HelidonServiceLoader
                .builder(ServiceLoader.load(ConfigSourceProvider.class))
                .addService(new BuiltInConfigSourcesProvider())
                .build()
                .asList();

        CONFIG_SOURCE_PROVIDERS.stream()
                .map(ConfigSourceProvider::supported)
                .forEach(SUPPORTED_CONFIG_SOURCES::addAll);

        RETRY_POLICY_PROVIDERS = HelidonServiceLoader
                .builder(ServiceLoader.load(RetryPolicyProvider.class))
                .addService(new BuiltInRetryPolicyProvider())
                .build()
                .asList();

        RETRY_POLICY_PROVIDERS.stream()
                .map(RetryPolicyProvider::supported)
                .forEach(SUPPORTED_RETRY_POLICIES::addAll);

        POLLING_STRATEGY_PROVIDERS = HelidonServiceLoader
                .builder(ServiceLoader.load(PollingStrategyProvider.class))
                .addService(new BuiltInPollingStrategyProvider())
                .build()
                .asList();

        POLLING_STRATEGY_PROVIDERS.stream()
                .map(PollingStrategyProvider::supported)
                .forEach(SUPPORTED_POLLING_STRATEGIES::addAll);

        CHANGE_WATCHER_PROVIDERS = HelidonServiceLoader
                .builder(ServiceLoader.load(ChangeWatcherProvider.class))
                .addService(new BuiltInChangeWatchers())
                .build()
                .asList();

        CHANGE_WATCHER_PROVIDERS.stream()
                .map(ChangeWatcherProvider::supported)
                .forEach(SUPPORTED_CHANGE_WATCHERS::addAll);


        OVERRIDE_SOURCE_PROVIDERS = HelidonServiceLoader
                .builder(ServiceLoader.load(OverrideSourceProvider.class))
                .addService(new BuiltinOverrideSourceProvider())
                .build()
                .asList();

        OVERRIDE_SOURCE_PROVIDERS.stream()
                .map(OverrideSourceProvider::supported)
                .forEach(SUPPORTED_OVERRIDE_SOURCES::addAll);
    }

    private MetaProviders() {
    }

    static ConfigSource configSource(String type, Config config) {
        return CONFIG_SOURCE_PROVIDERS.stream()
                .filter(provider -> provider.supports(type))
                .findFirst()
                .map(provider -> provider.create(type, config))
                .orElseThrow(() -> new IllegalArgumentException("Config source of type " + type + " is not supported."
                                                                        + " Supported types: " + SUPPORTED_CONFIG_SOURCES));
    }

    static List<ConfigSource> configSources(String type, Config sourceProperties) {
        return CONFIG_SOURCE_PROVIDERS.stream()
                .filter(provider -> provider.supports(type))
                .findFirst()
                .map(provider -> provider.createMulti(type, sourceProperties))
                .orElseThrow(() -> new IllegalArgumentException("Config source of type " + type + " is not supported."
                                                                        + " Supported types: " + SUPPORTED_CONFIG_SOURCES));
    }

    static OverrideSource overrideSource(String type, Config config) {
        return OVERRIDE_SOURCE_PROVIDERS.stream()
                .filter(provider -> provider.supports(type))
                .findFirst()
                .map(provider -> provider.create(type, config))
                .orElseThrow(() -> new IllegalArgumentException("Config source of type " + type + " is not supported."
                                                                        + " Supported types: " + SUPPORTED_OVERRIDE_SOURCES));
    }

    static PollingStrategy pollingStrategy(String type, Config config) {
        return POLLING_STRATEGY_PROVIDERS.stream()
                .filter(provider -> provider.supports(type))
                .findFirst()
                .map(provider -> provider.create(type, config))
                .orElseThrow(() -> new IllegalArgumentException("Polling strategy of type " + type + " is not supported."
                                                                        + " Supported types: " + SUPPORTED_POLLING_STRATEGIES));
    }

    static RetryPolicy retryPolicy(String type, Config config) {
        return RETRY_POLICY_PROVIDERS.stream()
                .filter(provider -> provider.supports(type))
                .findFirst()
                .map(provider -> provider.create(type, config))
                .orElseThrow(() -> new IllegalArgumentException("Retry policy of type " + type + " is not supported."
                                                                        + " Supported types: " + SUPPORTED_RETRY_POLICIES));
    }

    public static ChangeWatcher<?> changeWatcher(String type, Config config) {
        return CHANGE_WATCHER_PROVIDERS.stream()
                .filter(provider -> provider.supports(type))
                .findFirst()
                .map(provider -> provider.create(type, config))
                .orElseThrow(() -> new IllegalArgumentException("Change watcher of type " + type + " is not supported."
                                                                + " Supported types: " + SUPPORTED_CHANGE_WATCHERS));
    }

    @Priority(Integer.MAX_VALUE)
    private static final class BuiltInChangeWatchers implements ChangeWatcherProvider {
        private static final String FILE_WATCH = "file";

        @Override
        public boolean supports(String type) {
            return FILE_WATCH.equals(type);
        }

        @Override
        public ChangeWatcher<?> create(String type, Config metaConfig) {
            return FileSystemWatcher.builder().config(metaConfig).build();
        }

        @Override
        public Set<String> supported() {
            return Set.of(FILE_WATCH);
        }
    }

    @Priority(Integer.MAX_VALUE)
    private static final class BuiltInPollingStrategyProvider implements PollingStrategyProvider {
        private static final String REGULAR_TYPE = "regular";

        @Override
        public boolean supports(String type) {
            return REGULAR_TYPE.equals(type);
        }

        @Override
        public PollingStrategy create(String type, Config metaConfig) {
            return PollingStrategies.ScheduledBuilder.create(metaConfig).build();
        }

        @Override
        public Set<String> supported() {
            return Set.of(REGULAR_TYPE);
        }
    }

    @Priority(Integer.MAX_VALUE)
    private static final class BuiltInRetryPolicyProvider implements RetryPolicyProvider {
        private static final String REPEAT_TYPE = "repeat";

        private BuiltInRetryPolicyProvider() {
        }

        @Override
        public boolean supports(String type) {
            return REPEAT_TYPE.equals(type);
        }

        @Override
        public RetryPolicy create(String type, Config metaConfig) {
            return SimpleRetryPolicy.create(metaConfig);
        }

        @Override
        public Set<String> supported() {
            return Set.of(REPEAT_TYPE);
        }
    }

    @Priority(Integer.MAX_VALUE)
    private static final class BuiltinOverrideSourceProvider implements OverrideSourceProvider {
        private static final String FILE_TYPE = "file";
        private static final String CLASSPATH_TYPE = "classpath";
        private static final String URL_TYPE = "url";

        private static final Map<String, Function<Config, OverrideSource>> BUILT_INS = new HashMap<>();

        static {
            BUILT_INS.put(CLASSPATH_TYPE, ClasspathOverrideSource::create);
            BUILT_INS.put(FILE_TYPE, FileOverrideSource::create);
            BUILT_INS.put(URL_TYPE, UrlOverrideSource::create);
        }

        @Override
        public boolean supports(String type) {
            return BUILT_INS.containsKey(type);
        }

        @Override
        public OverrideSource create(String type, Config metaConfig) {
            return BUILT_INS.get(type).apply(metaConfig);
        }

        @Override
        public Set<String> supported() {
            return BUILT_INS.keySet();
        }
    }

    @Priority(Integer.MAX_VALUE)
    private static final class BuiltInConfigSourcesProvider implements ConfigSourceProvider {
        private static final String SYSTEM_PROPERTIES_TYPE = "system-properties";
        private static final String ENVIRONMENT_VARIABLES_TYPE = "environment-variables";
        private static final String CLASSPATH_TYPE = "classpath";
        private static final String FILE_TYPE = "file";
        private static final String DIRECTORY_TYPE = "directory";
        private static final String URL_TYPE = "url";
        private static final String PREFIXED_TYPE = "prefixed";
        private static final String INLINED_TYPE = "inlined";

        private static final Map<String, Function<Config, ConfigSource>> BUILT_INS = new HashMap<>();

        static {
            BUILT_INS.put(SYSTEM_PROPERTIES_TYPE, config -> ConfigSources.systemProperties().config(config).build());
            BUILT_INS.put(ENVIRONMENT_VARIABLES_TYPE, config -> ConfigSources.environmentVariables());
            BUILT_INS.put(CLASSPATH_TYPE, ClasspathConfigSource::create);

            BUILT_INS.put(FILE_TYPE, FileConfigSource::create);
            BUILT_INS.put(DIRECTORY_TYPE, DirectoryConfigSource::create);
            BUILT_INS.put(URL_TYPE, UrlConfigSource::create);
            BUILT_INS.put(PREFIXED_TYPE, PrefixedConfigSource::create);
            BUILT_INS.put(INLINED_TYPE, InlinedConfigSource::create);
        }

        @Override
        public boolean supports(String type) {
            return BUILT_INS.containsKey(type);
        }

        @Override
        public ConfigSource create(String type, Config metaConfig) {
            return BUILT_INS.get(type).apply(metaConfig);
        }

        @Override
        public List<ConfigSource> createMulti(String type, Config metaConfig) {
            if (CLASSPATH_TYPE.equals(type)) {
                return ClasspathConfigSource.createAll(metaConfig);
            }
            throw new ConfigException("Config source of type \"" + type + "\" does not support multiple config sources"
                                              + " from a single configuration");
        }

        @Override
        public Set<String> supported() {
            return BUILT_INS.keySet();
        }
    }
}
