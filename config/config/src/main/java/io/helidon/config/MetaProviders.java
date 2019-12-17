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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;

import javax.annotation.Priority;

import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.config.internal.FileOverrideSource;
import io.helidon.config.internal.PrefixedConfigSource;
import io.helidon.config.internal.UrlOverrideSource;
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
    private static final List<OverrideSourceProvider> OVERRIDE_SOURCE_PROVIDERS;

    private static final Set<String> SUPPORTED_CONFIG_SOURCES = new HashSet<>();
    private static final Set<String> SUPPORTED_RETRY_POLICIES = new HashSet<>();
    private static final Set<String> SUPPORTED_POLLING_STRATEGIES = new HashSet<>();
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

    public static ConfigSource configSource(String type, Config config) {
        return CONFIG_SOURCE_PROVIDERS.stream()
                .filter(provider -> provider.supports(type))
                .findFirst()
                .map(provider -> provider.create(type, config))
                .orElseThrow(() -> new IllegalArgumentException("Config source of type " + type + " is not supported."
                                                                        + " Supported types: " + SUPPORTED_CONFIG_SOURCES));
    }

    public static OverrideSource overrideSource(String type, Config config) {
        return OVERRIDE_SOURCE_PROVIDERS.stream()
                .filter(provider -> provider.supports(type))
                .findFirst()
                .map(provider -> provider.create(type, config))
                .orElseThrow(() -> new IllegalArgumentException("Config source of type " + type + " is not supported."
                                                                        + " Supported types: " + SUPPORTED_OVERRIDE_SOURCES));
    }

    public static Function<Object, PollingStrategy> pollingStrategy(String type, Config config) {
        return POLLING_STRATEGY_PROVIDERS.stream()
                .filter(provider -> provider.supports(type))
                .findFirst()
                .map(provider -> provider.create(type, config))
                .orElseThrow(() -> new IllegalArgumentException("Polling strategy of type " + type + " is not supported."
                                                                        + " Supported types: " + SUPPORTED_POLLING_STRATEGIES));
    }

    public static RetryPolicy retryPolicy(String type, Config config) {
        return RETRY_POLICY_PROVIDERS.stream()
                .filter(provider -> provider.supports(type))
                .findFirst()
                .map(provider -> provider.create(type, config))
                .orElseThrow(() -> new IllegalArgumentException("Retry policy of type " + type + " is not supported."
                                                                        + " Supported types: " + SUPPORTED_RETRY_POLICIES));
    }

    @Priority(Integer.MAX_VALUE)
    private static final class BuiltInPollingStrategyProvider implements PollingStrategyProvider {
        private static final String REGULAR_TYPE = "regular";
        private static final String WATCH_TYPE = "watch";

        private static final Map<String, Function<Config, Function<Object, PollingStrategy>>> BUILT_IN =
                Map.of(
                        REGULAR_TYPE, config -> target -> PollingStrategies.ScheduledBuilder.create(config).build(),
                        WATCH_TYPE, config -> BuiltInPollingStrategyProvider::watchStrategy
                );

        private static PollingStrategy watchStrategy(Object target) {
            if (target instanceof Path) {
                Path path = (Path) target;
                return PollingStrategies.watch(path).build();
            }

            throw new ConfigException("Incorrect target type ('" + target.getClass().getName()
                                              + "') for WATCH polling strategy. Expected '" + Path.class.getName() + "'.");
        }

        @Override
        public boolean supports(String type) {
            return BUILT_IN.containsKey(type);
        }

        @Override
        public Function<Object, PollingStrategy> create(String type, Config metaConfig) {
            return BUILT_IN.get(type).apply(metaConfig);
        }

        @Override
        public Set<String> supported() {
            return BUILT_IN.keySet();
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
            // This method is actually dedicated to repeat type and does no reflection at all
            // TODO refactor to proper factory methods and a builder
            //  (e.g. RetryPolicies.repeatBuilder().config(metaConfig).build())
            return RetryPolicies.Builder.create(metaConfig).build();
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

        private static final Map<String, Function<Config, ConfigSource>> BUILT_INS = new HashMap<>();

        static {
            BUILT_INS.put(SYSTEM_PROPERTIES_TYPE, config -> ConfigSources.systemProperties());
            BUILT_INS.put(ENVIRONMENT_VARIABLES_TYPE, config -> ConfigSources.environmentVariables());
            BUILT_INS.put(CLASSPATH_TYPE, ClasspathConfigSource::create);
            BUILT_INS.put(FILE_TYPE, FileConfigSource::create);
            BUILT_INS.put(DIRECTORY_TYPE, DirectoryConfigSource::create);
            BUILT_INS.put(URL_TYPE, UrlConfigSource::create);
            BUILT_INS.put(PREFIXED_TYPE, PrefixedConfigSource::create);
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
        public Set<String> supported() {
            return BUILT_INS.keySet();
        }
    }
}
