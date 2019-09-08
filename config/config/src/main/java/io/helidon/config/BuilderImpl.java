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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Priority;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.GenericType;
import io.helidon.common.reactive.Flow;
import io.helidon.config.ConfigMapperManager.MapperProviders;
import io.helidon.config.internal.ConfigThreadFactory;
import io.helidon.config.internal.ConfigUtils;
import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigMapper;
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
    private static final List<String> DEFAULT_FILE_EXTENSIONS = Arrays.asList("yaml", "conf", "json", "properties");

    private List<ConfigSource> sources;
    private final MapperProviders mapperProviders;
    private boolean mapperServicesEnabled;
    private final List<ConfigParser> parsers;
    private boolean parserServicesEnabled;
    private final List<Function<Config, ConfigFilter>> filterProviders;
    private boolean filterServicesEnabled;
    private boolean cachingEnabled;
    private Executor changesExecutor;
    private int changesMaxBuffer;
    private boolean keyResolving;
    private boolean systemPropertiesSourceEnabled;
    private boolean environmentVariablesSourceEnabled;
    private OverrideSource overrideSource;
    private boolean envVarAliasGeneratorEnabled;

    BuilderImpl() {
        sources = null;
        overrideSource = OverrideSources.empty();
        mapperProviders = MapperProviders.create();
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
        envVarAliasGeneratorEnabled = false;
    }

    @Override
    public Config.Builder sources(List<Supplier<ConfigSource>> sourceSuppliers) {
        sources = new ArrayList<>(sourceSuppliers.size());
        sourceSuppliers.stream().map(Supplier::get).forEach(source -> {
            sources.add(source);
            if (source instanceof ConfigSources.EnvironmentVariablesConfigSource) {
                envVarAliasGeneratorEnabled = true;
            }
        });
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
    public <T> Config.Builder addStringMapper(Class<T> type, Function<String, T> mapper) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(mapper);

        addMapper(type, config -> mapper.apply(config.asString().get()));

        return this;
    }

    @Override
    public <T> Config.Builder addMapper(Class<T> type, Function<Config, T> mapper) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(mapper);

        addMapper(() -> CollectionsHelper.mapOf(type, mapper));

        return this;
    }

    @Override
    public <T> Config.Builder addMapper(GenericType<T> type, Function<Config, T> mappingFunction) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(mappingFunction);

        addMapper(new ConfigMapperProvider() {
            @Override
            public Map<Class<?>, Function<Config, ?>> mappers() {
                return CollectionsHelper.mapOf();
            }

            @Override
            public Map<GenericType<?>, BiFunction<Config, ConfigMapper, ?>> genericTypeMappers() {
                return CollectionsHelper.mapOf(type, (config, aMapper) -> mappingFunction.apply(config));
            }
        });

        return this;
    }

    @Override
    public Config.Builder addMapper(ConfigMapperProvider mapperProvider) {
        Objects.requireNonNull(mapperProvider);
        mapperProviders.add(mapperProvider);
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

    @Override
    public Config.Builder mappersFrom(Config config) {
        if (config instanceof AbstractConfigImpl) {
            ConfigMapperManager mapperManager = ((AbstractConfigImpl) config).mapperManager();
            addMapper(new ConfigMapperProvider() {
                @Override
                public Map<Class<?>, Function<Config, ?>> mappers() {
                    return CollectionsHelper.mapOf();
                }

                @Override
                public <T> Optional<BiFunction<Config, ConfigMapper, T>> mapper(GenericType<T> type) {
                    Optional<? extends BiFunction<Config, ConfigMapper, T>> mapper = mapperManager.mapper(type);
                    return Optional.ofNullable(mapper.orElse(null));
                }
            });
        } else {
            throw new ConfigException("Unexpected configuration implementation used to copy mappers: "
                                              + config.getClass().getName());
        }

        return this;
    }

    private ProviderImpl buildProvider() {

        //context
        ConfigContext context = new ConfigContextImpl(buildParsers(parserServicesEnabled, parsers));

        //source
        ConfigSource targetConfigSource = targetConfigSource(context);

        //mappers
        ConfigMapperManager configMapperManager = buildMappers(mapperServicesEnabled, mapperProviders);

        if (filterServicesEnabled) {
            addAutoLoadedFilters();
        }

        Function<String, List<String>> aliasGenerator = envVarAliasGeneratorEnabled
                                                        ? EnvironmentVariableAliases::aliasesOf
                                                        : null;

        //config provider
        return createProvider(configMapperManager,
                              targetConfigSource,
                              overrideSource,
                              filterProviders,
                              cachingEnabled,
                              changesExecutor,
                              changesMaxBuffer,
                              keyResolving,
                              aliasGenerator);
    }

    private ConfigSource targetConfigSource(ConfigContext context) {
        List<ConfigSource> targetSources = new LinkedList<>();

        if (hasSourceType(ConfigSources.EnvironmentVariablesConfigSource.class)) {
            envVarAliasGeneratorEnabled = true;
        } else if (environmentVariablesSourceEnabled) {
            targetSources.add(ConfigSources.environmentVariables());
            envVarAliasGeneratorEnabled = true;
        }

        if (systemPropertiesSourceEnabled
            && !hasSourceType(ConfigSources.SystemPropertiesConfigSource.class)) {
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
            targetConfigSource = ConfigSources.create(targetSources.toArray(new ConfigSource[0])).build();
        }
        targetConfigSource.init(context);
        return targetConfigSource;
    }

    private boolean hasSourceType(Class<?> sourceType) {
        if (sources != null) {
            for (ConfigSource source : sources) {
                if (sourceType.isAssignableFrom(source.getClass())) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("ParameterNumber")
    ProviderImpl createProvider(ConfigMapperManager configMapperManager,
                                ConfigSource targetConfigSource,
                                OverrideSource overrideSource,
                                List<Function<Config, ConfigFilter>> filterProviders,
                                boolean cachingEnabled,
                                Executor changesExecutor,
                                int changesMaxBuffer,
                                boolean keyResolving,
                                Function<String, List<String>> aliasGenerator) {
        return new ProviderImpl(configMapperManager,
                                targetConfigSource,
                                overrideSource,
                                filterProviders,
                                cachingEnabled,
                                changesExecutor,
                                changesMaxBuffer,
                                keyResolving,
                                aliasGenerator);
    }

    //
    // utils
    //

    private static ConfigSource defaultConfigSource() {
        final List<ConfigSource> sources = new ArrayList<>();
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        final List<ConfigSource> meta = defaultConfigSources(classLoader, "meta-config");
        if (!meta.isEmpty()) {
            sources.add(ConfigSources.load(toDefaultConfigSource(meta)).build());
        }
        sources.addAll(defaultConfigSources(classLoader, "application"));
        return ConfigSources.create(toDefaultConfigSource(sources)).build();
    }

    private static List<ConfigSource> defaultConfigSources(final ClassLoader classLoader, final String baseResourceName) {
        final List<ConfigSource> sources = new ArrayList<>();
        for (final String extension : DEFAULT_FILE_EXTENSIONS) {
            final String resource = baseResourceName + "." + extension;
            if (classLoader.getResource(resource) != null) {
                sources.add(classpath(resource).optional().build());
            }
        }
        return sources;
    }

    private static ConfigSource toDefaultConfigSource(final List<ConfigSource> sources) {
        if (sources.isEmpty()) {
            return ConfigSources.empty();
        } else if (sources.size() == 1) {
            return sources.get(0);
        } else {
            return new UseFirstAvailableConfigSource(sources);
        }
    }

    static List<ConfigParser> buildParsers(boolean servicesEnabled, List<ConfigParser> userDefinedParsers) {
        List<ConfigParser> parsers = new LinkedList<>(userDefinedParsers);
        if (servicesEnabled) {
            parsers.addAll(loadParserServices());
        }
        return parsers;
    }

    static ConfigMapperManager buildMappers(boolean servicesEnabled,
                                            MapperProviders userDefinedProviders) {

        MapperProviders providers = MapperProviders.create();

        List<ConfigMapperProvider> prioritizedProviders = new LinkedList<>();

        // we must add default mappers using a known priority (49), so they can be overridden by services
        // and yet we can still define a service that is only used after these (such as config beans)
        prioritizedProviders.add(new InternalPriorityMapperProvider(ConfigMappers.essentialMappers()));
        prioritizedProviders.add(new InternalPriorityMapperProvider(ConfigMappers.builtInMappers()));

        if (servicesEnabled) {
            loadMapperServices(prioritizedProviders);
        }

        prioritizedProviders = ConfigUtils
                .asPrioritizedStream(prioritizedProviders, ConfigMapperProvider.PRIORITY)
                .collect(Collectors.toList());

        // add built in converters and converters from service loader
        prioritizedProviders.forEach(providers::add);

        // user defined converters always have priority over anything else
        providers.addAll(userDefinedProviders);

        return new ConfigMapperManager(providers);
    }

    private static void loadMapperServices(List<ConfigMapperProvider> providers) {
        ServiceLoader.load(ConfigMapperProvider.class)
                .forEach(providers::add);
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
                .map(filter -> (Function<Config, ConfigFilter>) (Config t) -> filter)
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
                    .filter(parser -> parser.supportedMediaTypes().contains(mediaType))
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
                .disableParserServices()
                .disableFilterServices()
                .build();

    }

    /**
     * Internal mapper with low priority to enable overrides.
     */
    @Priority(200)
    static class InternalPriorityMapperProvider implements ConfigMapperProvider {
        private final Map<Class<?>, Function<Config, ?>> converterMap;

        InternalPriorityMapperProvider(Map<Class<?>, Function<Config, ?>> converterMap) {
            this.converterMap = converterMap;
        }

        @Override
        public Map<Class<?>, Function<Config, ?>> mappers() {
            return converterMap;
        }
    }
}
