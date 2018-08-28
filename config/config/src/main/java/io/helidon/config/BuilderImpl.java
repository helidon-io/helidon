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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.reactive.Flow;
import io.helidon.config.internal.ConfigThreadFactory;
import io.helidon.config.internal.ConfigUtils;
import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigMapperProvider;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.OverrideSource;

import static io.helidon.config.ConfigSources.classpath;

/**
 * {@link Config} Builder implementation.
 */
class BuilderImpl implements Config.Builder {

    static final Executor DEFAULT_CHANGES_EXECUTOR = Executors.newCachedThreadPool(new ConfigThreadFactory("config"));

    private List<ConfigSource> sources;
    private final Map<Class<?>, ConfigMapper<?>> mappers;
    private boolean mapperServicesEnabled;
    private List<ConfigParser> parsers;
    private boolean parserServicesEnabled;
    private List<Function<Config, ConfigFilter>> filterProviders;
    private boolean filterServicesEnabled;
    private boolean cachingEnabled;
    private Executor changesExecutor;
    private int changesMaxBuffer;
    private boolean keyResolving;
    private boolean systemPropertiesSourceEnabled;
    private boolean environmentVariablesSourceEnabled;
    private OverrideSource overrideSource;

    BuilderImpl() {
        sources = null;
        overrideSource = OverrideSources.empty();
        mappers = new HashMap<>();
        mapperServicesEnabled = true;
        parsers = new ArrayList<>();
        parserServicesEnabled = true;
        filterProviders = new ArrayList<>();
        filterServicesEnabled = true;
        cachingEnabled = true;
        changesExecutor = DEFAULT_CHANGES_EXECUTOR;
        changesMaxBuffer = Flow.defaultBufferSize();
        keyResolving = true;
        systemPropertiesSourceEnabled = true;
        environmentVariablesSourceEnabled = true;
    }

    @Override
    public Config.Builder sources(List<Supplier<ConfigSource>> sourceSuppliers) {
        sources = new ArrayList<>(sourceSuppliers.size());
        sourceSuppliers.stream().map(Supplier::get).forEach(sources::add);
        return this;
    }

    @Override
    public Config.Builder overrides(Supplier<OverrideSource> overridingSource) {
        this.overrideSource = overridingSource.get();
        return this;
    }

    @Override
    public Config.Builder disableMapperServices() {
        this.mapperServicesEnabled = false;
        return this;
    }

    @Override
    public <T> Config.Builder addMapper(Class<T> type, ConfigMapper<T> mapper) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(mapper);

        mappers.put(type, mapper);
        return this;
    }

    @Override
    public <T> Config.Builder addMapper(Class<T> type, Function<String, T> mapper) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(mapper);

        mappers.put(type, ConfigMappers.wrap(mapper));
        return this;
    }

    @Override
    public Config.Builder addMapper(ConfigMapperProvider mapperProvider) {
        Objects.requireNonNull(mapperProvider);

        mapperProvider.getMappers().forEach(mappers::put);
        return this;
    }

    @Override
    public Config.Builder addParser(ConfigParser configParser) {
        Objects.requireNonNull(configParser);

        parsers.add(configParser);
        return this;
    }

    @Override
    public Config.Builder disableParserServices() {
        parserServicesEnabled = false;
        return this;
    }

    @Override
    public Config.Builder addFilter(ConfigFilter configFilter) {
        Objects.requireNonNull(configFilter);

        filterProviders.add((config) -> configFilter);
        return this;
    }

    @Override
    public Config.Builder addFilter(Function<Config, ConfigFilter> configFilterProvider) {
        Objects.requireNonNull(configFilterProvider);

        filterProviders.add(configFilterProvider);
        return this;
    }

    @Override
    public Config.Builder addFilter(Supplier<Function<Config, ConfigFilter>> configFilterSupplier) {
        Objects.requireNonNull(configFilterSupplier);

        filterProviders.add(configFilterSupplier.get());
        return this;
    }

    @Override
    public Config.Builder disableFilterServices() {
        filterServicesEnabled = false;
        return this;
    }

    @Override
    public Config.Builder disableCaching() {
        cachingEnabled = false;
        return this;
    }

    @Override
    public Config.Builder changesExecutor(Executor changesExecutor) {
        Objects.requireNonNull(changesExecutor);

        this.changesExecutor = changesExecutor;
        return this;
    }

    @Override
    public Config.Builder changesMaxBuffer(int changesMaxBuffer) {
        this.changesMaxBuffer = changesMaxBuffer;
        return this;
    }

    @Override
    public Config.Builder disableKeyResolving() {
        keyResolving = false;
        return this;
    }

    @Override
    public Config.Builder disableValueResolving() {
        return this;
    }

    @Override
    public Config.Builder disableEnvironmentVariablesSource() {
        environmentVariablesSourceEnabled = false;
        return this;
    }

    @Override
    public Config.Builder disableSystemPropertiesSource() {
        systemPropertiesSourceEnabled = false;
        return this;
    }

    @Override
    public Config build() {
        return buildProvider().newConfig();
    }

    private ProviderImpl buildProvider() {

        //context
        ConfigContext context = new ConfigContextImpl(buildParsers(parserServicesEnabled, parsers));

        //source
        ConfigSource targetConfigSource = targetConfigSource(context);

        //mappers
        ConfigMapperManager configMapperManager = buildMappers(mapperServicesEnabled, mappers);

        if (filterServicesEnabled) {
            addAutoLoadedFilters();
        }

        //config provider
        return createProvider(configMapperManager,
                              targetConfigSource,
                              overrideSource,
                              filterProviders,
                              cachingEnabled,
                              changesExecutor,
                              changesMaxBuffer,
                              keyResolving);
    }

    private ConfigSource targetConfigSource(ConfigContext context) {
        List<ConfigSource> targetSources = new LinkedList<>();
        if (environmentVariablesSourceEnabled) {
            targetSources.add(ConfigSources.environmentVariables());
        }
        if (systemPropertiesSourceEnabled) {
            targetSources.add(ConfigSources.systemProperties());
        }
        if (sources != null) {
            targetSources.addAll(sources);
        } else {
            targetSources.add(defaultConfigSource());
        }

        final ConfigSource targetConfigSource;
        if (targetSources.size() == 1) {
            targetConfigSource = targetSources.get(0);
        } else {
            targetConfigSource = ConfigSources.from(targetSources.toArray(new ConfigSource[0])).build();
        }
        targetConfigSource.init(context);
        return targetConfigSource;
    }

    @SuppressWarnings("ParameterNumber")
    ProviderImpl createProvider(ConfigMapperManager configMapperManager,
                                ConfigSource targetConfigSource,
                                OverrideSource overrideSource,
                                List<Function<Config, ConfigFilter>> filterProviders,
                                boolean cachingEnabled,
                                Executor changesExecutor,
                                int changesMaxBuffer,
                                boolean keyResolving) {
        return new ProviderImpl(configMapperManager,
                                targetConfigSource,
                                overrideSource,
                                filterProviders,
                                cachingEnabled,
                                changesExecutor,
                                changesMaxBuffer,
                                keyResolving);
    }

    //
    // utils
    //

    static ConfigSource defaultConfigSource() {
        return ConfigSources.from(
                new UseFirstAvailableConfigSource(
                        ConfigSources.load(
                                new UseFirstAvailableConfigSource(
                                        classpath("meta-config.yaml").optional().build(),
                                        classpath("meta-config.conf").optional().build(),
                                        classpath("meta-config.json").optional().build(),
                                        classpath("meta-config.properties").optional().build()
                                )).build(),
                        classpath("application.yaml").optional().build(),
                        classpath("application.conf").optional().build(),
                        classpath("application.json").optional().build(),
                        classpath("application.properties").optional().build()
                )).build();
    }

    static List<ConfigParser> buildParsers(boolean servicesEnabled, List<ConfigParser> userDefinedParsers) {
        List<ConfigParser> parsers = new LinkedList<>();
        parsers.addAll(userDefinedParsers);
        if (servicesEnabled) {
            parsers.addAll(loadParserServices());
        }
        return parsers;
    }

    static ConfigMapperManager buildMappers(boolean servicesEnabled,
                                            Map<Class<?>, ConfigMapper<?>> userDefinedMappers) {
        Map<Class<?>, ConfigMapper<?>> mappers = new HashMap<>();

        mappers.putAll(ConfigMappers.essentialMappers());
        mappers.putAll(ConfigMappers.builtInMappers());
        if (servicesEnabled) {
            mappers.putAll(loadMapperServices());
        }
        mappers.putAll(userDefinedMappers);

        return new ConfigMapperManager(mappers);
    }

    private static Map<Class<?>, ConfigMapper<?>> loadMapperServices() {
        Map<Class<?>, ConfigMapper<?>> loadedMappers = new HashMap<>();

        List<ConfigMapperProvider> loadedProviders = ConfigUtils
                .asPrioritizedStream(ServiceLoader.load(ConfigMapperProvider.class), ConfigMapperProvider.PRIORITY)
                .collect(Collectors.toList());
        Collections.reverse(loadedProviders);
        loadedProviders.forEach(provider -> loadedMappers.putAll(provider.getMappers()));
        return loadedMappers;
    }

    private static List<ConfigParser> loadParserServices() {
        return loadPrioritizedServices(ConfigParser.class, ConfigParser.PRIORITY);
    }

    private void addAutoLoadedFilters() {
        /*
         * The filterProviders field holds a list of Function<Config,
         * ConfigFilter> so the filters can be instantiated later in
         * ProviderImpl when we actually have a Config instance. Auto-loaded
         * filters can come from Java services that provide a ConfigFilter
         * instance. We need to convert the results from loading the services to
         * Function<Config, ConfigFilter> so we can store the functions into
         * filterProviders.
         *
         * Sorting the filters by priority has to happen later, in the provider,
         * once a Config instance is available to use in obtaining filters from
         * providers.
         */

        /*
         * Map each autoloaded ConfigFilter to a filter-providing function.
         */
        ConfigUtils.asStream(ServiceLoader.load(ConfigFilter.class).iterator())
                .map(filter -> {
                    return (Function<Config, ConfigFilter>) (Config t) -> filter;
                    })
                .forEach(this::addFilter);
    }

    private static <T> List<T> loadPrioritizedServices(Class<T> serviceClass, int priority) {
        return ConfigUtils
                .asPrioritizedStream(ServiceLoader.load(serviceClass), priority)
                .collect(Collectors.toList());
    }

    /**
     * {@link ConfigContext} implementation.
     */
    static class ConfigContextImpl implements ConfigContext {

        private final List<ConfigParser> configParsers;

        /**
         * Creates a config context.
         *
         * @param configParsers a config parsers
         */
        ConfigContextImpl(List<ConfigParser> configParsers) {
            this.configParsers = configParsers;
        }

        @Override
        public Optional<ConfigParser> findParser(String mediaType) {
            if (mediaType == null) {
                throw new NullPointerException("Unknown media type of resource.");
            }
            return configParsers.stream()
                    .filter(parser -> parser.getSupportedMediaTypes().contains(mediaType))
                    .findFirst();
        }

    }

    /**
     * Holds single instance of empty Config.
     */
    static final class EmptyConfigHolder {
        private EmptyConfigHolder() {
            throw new AssertionError("Instantiation not allowed.");
        }

        static final Config EMPTY = new BuilderImpl()
                .sources(ConfigSources.empty())
                .overrides(OverrideSources.empty())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableMapperServices()
                .disableParserServices()
                .disableFilterServices()
                .build();

    }

}
