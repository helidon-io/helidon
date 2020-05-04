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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
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

import io.helidon.common.GenericType;
import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.common.Prioritized;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.common.serviceloader.Priorities;
import io.helidon.config.ConfigMapperManager.MapperProviders;
import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigMapper;
import io.helidon.config.spi.ConfigMapperProvider;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.MergingStrategy;
import io.helidon.config.spi.OverrideSource;

/**
 * {@link Config} Builder implementation.
 */
class BuilderImpl implements Config.Builder {
    static {
        HelidonFeatures.register(HelidonFlavor.SE, "Config");
    }

    /*
     * Config sources
     */
    // sources to be sorted by priority
    private final List<HelidonSourceWithPriority> prioritizedSources = new ArrayList<>();
    // sources "pre-sorted" - all user defined sources without priority will be ordered
    // as added, as well as config sources from meta configuration
    private final List<ConfigSource> sources = new LinkedList<>();
    // to use when more than one source is configured
    private MergingStrategy mergingStrategy = MergingStrategy.fallback();
    private boolean hasSystemPropertiesSource;
    private boolean hasEnvVarSource;
    /*
     * Config mapper providers
     */
    private final List<PrioritizedMapperProvider> prioritizedMappers = new ArrayList<>();
    private final MapperProviders mapperProviders;
    private boolean mapperServicesEnabled;
    /*
     * Config parsers
     */
    private final List<ConfigParser> parsers;
    private boolean parserServicesEnabled;
    /*
     * Config filters
     */
    private final List<Function<Config, ConfigFilter>> filterProviders;
    private boolean filterServicesEnabled;

    /*
     * change support
     */
    private Executor changesExecutor;
    /*
     * Other configuration.
     */
    private OverrideSource overrideSource;

    /*
     * Other switches
     */
    private boolean cachingEnabled;
    private boolean keyResolving;
    private boolean valueResolving;
    private boolean systemPropertiesSourceEnabled;
    private boolean environmentVariablesSourceEnabled;
    private boolean envVarAliasGeneratorEnabled;

    BuilderImpl() {
        overrideSource = OverrideSources.empty();
        mapperProviders = MapperProviders.create();
        mapperServicesEnabled = true;
        parsers = new ArrayList<>();
        parserServicesEnabled = true;
        filterProviders = new ArrayList<>();
        filterServicesEnabled = true;
        cachingEnabled = true;
        keyResolving = true;
        valueResolving = true;
        systemPropertiesSourceEnabled = true;
        environmentVariablesSourceEnabled = true;
        envVarAliasGeneratorEnabled = false;
    }

    @Override
    public Config.Builder sources(List<Supplier<? extends ConfigSource>> sourceSuppliers) {
        // replace current config sources with the ones provided
        sources.clear();
        prioritizedSources.clear();

        sourceSuppliers.stream()
                .map(Supplier::get)
                .forEach(this::addSource);

        return this;
    }

    @Override
    public Config.Builder addSource(ConfigSource source) {
        sources.add(source);
        if (source instanceof ConfigSources.EnvironmentVariablesConfigSource) {
            envVarAliasGeneratorEnabled = true;
            hasEnvVarSource = true;
        } else if (source instanceof ConfigSources.SystemPropertiesConfigSource) {
            hasSystemPropertiesSource = true;
        }
        return this;
    }

    @Override
    public Config.Builder overrides(Supplier<? extends OverrideSource> overridingSource) {
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

        if (String.class.equals(type)) {
            return this;
        }
        addMapper(type, config -> mapper.apply(config.asString().get()));

        return this;
    }

    @Override
    public <T> Config.Builder addMapper(Class<T> type, Function<Config, T> mapper) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(mapper);

        addMapper(() -> Map.of(type, mapper));

        return this;
    }

    @Override
    public <T> Config.Builder addMapper(GenericType<T> type, Function<Config, T> mappingFunction) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(mappingFunction);

        addMapper(new ConfigMapperProvider() {
            @Override
            public Map<Class<?>, Function<Config, ?>> mappers() {
                return Map.of();
            }

            @Override
            public Map<GenericType<?>, BiFunction<Config, ConfigMapper, ?>> genericTypeMappers() {
                return Map.of(type, (config, aMapper) -> mappingFunction.apply(config));
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
    public Config.Builder disableKeyResolving() {
        keyResolving = false;
        return this;
    }

    @Override
    public Config.Builder disableValueResolving() {
        this.valueResolving = false;
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
    public AbstractConfigImpl build() {
        if (valueResolving) {
            addFilter(ConfigFilters.valueResolving());
        }
        if (null == changesExecutor) {
            changesExecutor = Executors.newCachedThreadPool(new ConfigThreadFactory("config-changes"));
        }

        /*
         * Now prepare the config runtime.
         * We need the following setup:
         * 1. Mappers
         * 2. Filters
         * 3. Config Sources
         */

        /*
         Mappers
         */
        ConfigMapperManager configMapperManager = buildMappers(prioritizedMappers,
                                                               mapperProviders,
                                                               mapperServicesEnabled);

        /*
         Filters
         */
        if (filterServicesEnabled) {
            addAutoLoadedFilters();
        }

        /*
         Config Sources
         */
        // collect all sources (in correct order)
        ConfigContextImpl context = new ConfigContextImpl(changesExecutor, buildParsers(parserServicesEnabled, parsers));
        ConfigSourcesRuntime configSources = buildConfigSources(context);

        Function<String, List<String>> aliasGenerator = envVarAliasGeneratorEnabled
                ? EnvironmentVariableAliases::aliasesOf
                : null;

        //config provider
        return createProvider(configMapperManager,
                              configSources,
                              new OverrideSourceRuntime(overrideSource),
                              filterProviders,
                              cachingEnabled,
                              changesExecutor,
                              keyResolving,
                              aliasGenerator)
                .newConfig();
    }

    private static void addBuiltInMapperServices(List<PrioritizedMapperProvider> prioritizedMappers) {
        // we must add default mappers using a known priority (200), so they can be overridden by services
        // and yet we can still define a service that is only used after these (such as config beans)
        prioritizedMappers
                .add(new HelidonMapperWrapper(new InternalMapperProvider(ConfigMappers.essentialMappers(),
                                                                         "essential"), 200));
        prioritizedMappers
                .add(new HelidonMapperWrapper(new InternalMapperProvider(ConfigMappers.builtInMappers(),
                                                                         "built-in"), 200));
    }

    @Override
    public Config.Builder config(Config metaConfig) {
        metaConfig.get("caching.enabled").asBoolean().ifPresent(this::cachingEnabled);
        metaConfig.get("key-resolving.enabled").asBoolean().ifPresent(this::keyResolvingEnabled);
        metaConfig.get("parsers.enabled").asBoolean().ifPresent(this::parserServicesEnabled);
        metaConfig.get("mappers.enabled").asBoolean().ifPresent(this::mapperServicesEnabled);

        disableSystemPropertiesSource();
        disableEnvironmentVariablesSource();

        List<ConfigSource> sourceList = new LinkedList<>();

        metaConfig.get("sources")
                .asNodeList()
                .ifPresent(list -> list.forEach(it -> sourceList.addAll(MetaConfig.configSource(it))));

        sourceList.forEach(this::addSource);
        sourceList.clear();

        Config overrideConfig = metaConfig.get("override-source");
        if (overrideConfig.exists()) {
            overrides(() -> MetaConfig.overrideSource(overrideConfig));
        }

        return this;
    }

    @Override
    public Config.Builder mergingStrategy(MergingStrategy strategy) {
        this.mergingStrategy = strategy;
        return this;
    }

    private void cachingEnabled(boolean enabled) {
        this.cachingEnabled = enabled;
    }

    private void mapperServicesEnabled(Boolean aBoolean) {
        this.mapperServicesEnabled = aBoolean;
    }

    private void parserServicesEnabled(Boolean aBoolean) {
        parserServicesEnabled = aBoolean;
    }

    private void keyResolvingEnabled(Boolean aBoolean) {
        this.keyResolving = aBoolean;
    }

    private ConfigSourcesRuntime buildConfigSources(ConfigContextImpl context) {
        List<ConfigSourceRuntimeImpl> targetSources = new LinkedList<>();

        if (systemPropertiesSourceEnabled && !hasSystemPropertiesSource) {
            hasSystemPropertiesSource = true;
            targetSources.add(context.sourceRuntimeBase(ConfigSources.systemProperties().build()));
        }

        if (environmentVariablesSourceEnabled && !hasEnvVarSource) {
            hasEnvVarSource = true;
            targetSources.add(context.sourceRuntimeBase(ConfigSources.environmentVariables()));
        }

        if (hasEnvVarSource) {
            envVarAliasGeneratorEnabled = true;
        }

        boolean nothingConfigured = sources.isEmpty() && prioritizedSources.isEmpty();

        if (nothingConfigured) {
            // use meta configuration to load all sources
            MetaConfig.configSources(mediaType -> context.findParser(mediaType).isPresent())
                    .stream()
                    .map(context::sourceRuntimeBase)
                    .forEach(targetSources::add);
        } else {
            // add all configured or discovered sources

            // configured sources are always first in the list (explicitly added by user)
            sources.stream()
                    .map(context::sourceRuntimeBase)
                    .forEach(targetSources::add);

            // prioritized sources are next
            targetSources.addAll(mergePrioritized(context));
        }

        // targetSources now contain runtimes correctly ordered for each config source
        return new ConfigSourcesRuntime(targetSources, mergingStrategy);
    }

    private List<ConfigSourceRuntimeImpl> mergePrioritized(ConfigContextImpl context) {
        List<PrioritizedConfigSource> allPrioritized = new ArrayList<>();
        prioritizedSources.stream()
                .map(it -> new PrioritizedConfigSource(it, context))
                .forEach(allPrioritized::add);

        Priorities.sort(allPrioritized);

        return allPrioritized
                .stream()
                .map(it -> it.runtime(context))
                .collect(Collectors.toList());
    }

    @SuppressWarnings("ParameterNumber")
    ProviderImpl createProvider(ConfigMapperManager configMapperManager,
                                ConfigSourcesRuntime targetConfigSource,
                                OverrideSourceRuntime overrideSource,
                                List<Function<Config, ConfigFilter>> filterProviders,
                                boolean cachingEnabled,
                                Executor changesExecutor,
                                boolean keyResolving,
                                Function<String, List<String>> aliasGenerator) {
        return new ProviderImpl(configMapperManager,
                                targetConfigSource,
                                overrideSource,
                                filterProviders,
                                cachingEnabled,
                                changesExecutor,
                                keyResolving,
                                aliasGenerator);
    }

    //
    // utils
    //
    static List<ConfigParser> buildParsers(boolean servicesEnabled, List<ConfigParser> userDefinedParsers) {
        List<ConfigParser> parsers = new LinkedList<>(userDefinedParsers);
        if (servicesEnabled) {
            parsers.addAll(loadParserServices());
        }
        return parsers;
    }

    // this is a unit test method
    static ConfigMapperManager buildMappers(MapperProviders userDefinedProviders) {
        return buildMappers(new ArrayList<>(), userDefinedProviders, false);
    }

    static ConfigMapperManager buildMappers(List<PrioritizedMapperProvider> prioritizedMappers,
                                            MapperProviders userDefinedProviders,
                                            boolean mapperServicesEnabled) {

        // prioritized mapper providers
        if (mapperServicesEnabled) {
            loadMapperServices(prioritizedMappers);
        }
        addBuiltInMapperServices(prioritizedMappers);
        Priorities.sort(prioritizedMappers);

        // as the mapperProviders.add adds the last as first, we need to reverse order
        Collections.reverse(prioritizedMappers);

        MapperProviders providers = MapperProviders.create();

        // these are added first, as they end up last
        prioritizedMappers.forEach(providers::add);
        // user defined converters always have priority over anything else
        providers.addAll(userDefinedProviders);

        return new ConfigMapperManager(providers);
    }

    private static void loadMapperServices(List<PrioritizedMapperProvider> providers) {
        HelidonServiceLoader.builder(ServiceLoader.load(ConfigMapperProvider.class))
                .defaultPriority(ConfigMapperProvider.PRIORITY)
                .build()
                .forEach(mapper -> providers.add(new HelidonMapperWrapper(mapper, Priorities.find(mapper, 100))));
    }

    private static List<ConfigParser> loadParserServices() {
        return HelidonServiceLoader.builder(ServiceLoader.load(ConfigParser.class))
                .defaultPriority(ConfigParser.PRIORITY)
                .build()
                .asList();
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
        HelidonServiceLoader.builder(ServiceLoader.load(ConfigFilter.class))
                .defaultPriority(ConfigFilter.PRIORITY)
                .build()
                .asList()
                .stream()
                .map(LoadedFilterProvider::new)
                .forEach(this::addFilter);
    }

    /**
     * {@link ConfigContext} implementation.
     */
    static class ConfigContextImpl implements ConfigContext {
        private final Map<ConfigSource, ConfigSourceRuntimeImpl> runtimes = new IdentityHashMap<>();

        private final Executor changesExecutor;
        private final List<ConfigParser> configParsers;

        ConfigContextImpl(Executor changesExecutor, List<ConfigParser> configParsers) {
            this.changesExecutor = changesExecutor;
            this.configParsers = configParsers;
        }

        @Override
        public ConfigSourceRuntime sourceRuntime(ConfigSource source) {
            return sourceRuntimeBase(source);
        }

        private ConfigSourceRuntimeImpl sourceRuntimeBase(ConfigSource source) {
            return runtimes.computeIfAbsent(source, it -> new ConfigSourceRuntimeImpl(this, source));
        }

        Optional<ConfigParser> findParser(String mediaType) {
            Objects.requireNonNull(mediaType, "Unknown media type of resource.");

            return configParsers.stream()
                    .filter(parser -> parser.supportedMediaTypes().contains(mediaType))
                    .findFirst();
        }

        Executor changesExecutor() {
            return changesExecutor;
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
                // the empty config source is needed, so we do not look for meta config or default
                // config sources
                .sources(ConfigSources.empty())
                .overrides(OverrideSources.empty())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableParserServices()
                .disableFilterServices()
                .build();

    }

    static class InternalMapperProvider implements ConfigMapperProvider {
        private final Map<Class<?>, Function<Config, ?>> converterMap;
        private final String name;

        InternalMapperProvider(Map<Class<?>, Function<Config, ?>> converterMap, String name) {
            this.converterMap = converterMap;
            this.name = name;
        }

        @Override
        public Map<Class<?>, Function<Config, ?>> mappers() {
            return converterMap;
        }

        @Override
        public String toString() {
            return name + " internal mappers";
        }
    }

    private interface PrioritizedMapperProvider extends Prioritized,
                                                        ConfigMapperProvider {
    }

    private static final class HelidonMapperWrapper implements PrioritizedMapperProvider {
        private final ConfigMapperProvider delegate;
        private final int priority;

        private HelidonMapperWrapper(ConfigMapperProvider delegate, int priority) {
            this.delegate = delegate;
            this.priority = priority;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public Map<Class<?>, Function<Config, ?>> mappers() {
            return delegate.mappers();
        }

        @Override
        public Map<GenericType<?>, BiFunction<Config, ConfigMapper, ?>> genericTypeMappers() {
            return delegate.genericTypeMappers();
        }

        @Override
        public <T> Optional<Function<Config, T>> mapper(Class<T> type) {
            return delegate.mapper(type);
        }

        @Override
        public <T> Optional<BiFunction<Config, ConfigMapper, T>> mapper(GenericType<T> type) {
            return delegate.mapper(type);
        }

        @Override
        public String toString() {
            return priority + ": " + delegate;
        }
    }

    private static final class PrioritizedConfigSource implements Prioritized {
        private final HelidonSourceWithPriority source;
        private final ConfigContext context;

        private PrioritizedConfigSource(HelidonSourceWithPriority source, ConfigContext context) {
            this.source = source;
            this.context = context;
        }

        private ConfigSourceRuntimeImpl runtime(ConfigContextImpl context) {
            return context.sourceRuntimeBase(source.unwrap());
        }

        @Override
        public int priority() {
            return source.priority(context);
        }
    }

    private static final class HelidonSourceWithPriority {
        private final ConfigSource configSource;
        private final Integer explicitPriority;

        private HelidonSourceWithPriority(ConfigSource configSource, Integer explicitPriority) {
            this.configSource = configSource;
            this.explicitPriority = explicitPriority;
        }

        ConfigSource unwrap() {
            return configSource;
        }

        int priority(ConfigContext context) {
            // first - explicit priority. If configured by user, return it
            if (null != explicitPriority) {
                return explicitPriority;
            }

            // ordinal from data
            return context.sourceRuntime(configSource)
                    .node("config_priority")
                    .flatMap(node -> node.value()
                            .map(Integer::parseInt))
                    .orElseGet(() -> {
                        // the config source does not have an ordinal configured, I need to get it from other places
                        return Priorities.find(configSource, 100);
                    });
        }
    }

    private static class LoadedFilterProvider implements Function<Config, ConfigFilter> {
        private final ConfigFilter filter;

        private LoadedFilterProvider(ConfigFilter filter) {
            this.filter = filter;
        }

        @Override
        public ConfigFilter apply(Config config) {
            return filter;
        }

        @Override
        public String toString() {
            return filter.toString();
        }
    }
}
