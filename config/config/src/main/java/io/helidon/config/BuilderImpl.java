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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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

import javax.annotation.Priority;

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

import org.eclipse.microprofile.config.spi.ConfigSourceProvider;
import org.eclipse.microprofile.config.spi.Converter;

import static org.eclipse.microprofile.config.spi.ConfigSource.CONFIG_ORDINAL;

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
    private final List<PrioritizedMpSource> prioritizedMpSources = new ArrayList<>();
    // sources "pre-sorted" - all user defined sources without priority will be ordered
    // as added, as well as config sources from meta configuration
    private final List<ConfigSource> sources = new LinkedList<>();
    private boolean configSourceServicesEnabled;
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
    private boolean mpMapperServicesEnabled;
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
    private ClassLoader classLoader;
    /*
     * Other switches
     */
    private boolean cachingEnabled;
    private boolean keyResolving;
    private boolean valueResolving;
    private boolean systemPropertiesSourceEnabled;
    private boolean environmentVariablesSourceEnabled;
    private boolean envVarAliasGeneratorEnabled;
    private boolean mpDiscoveredSourcesAdded;
    private boolean mpDiscoveredConvertersAdded;

    BuilderImpl() {
        configSourceServicesEnabled = true;
        overrideSource = OverrideSources.empty();
        mapperProviders = MapperProviders.create();
        mapperServicesEnabled = true;
        mpMapperServicesEnabled = true;
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
    public Config.Builder disableSourceServices() {
        this.configSourceServicesEnabled = false;
        return this;
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
    public Config.Builder overrides(Supplier<OverrideSource> overridingSource) {
        this.overrideSource = overridingSource.get();
        return this;
    }

    @Override
    public Config.Builder disableMapperServices() {
        this.mapperServicesEnabled = false;
        return this;
    }

    void disableMpMapperServices() {
        this.mpMapperServicesEnabled = false;
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
        if (configSourceServicesEnabled) {
            // add MP config sources from service loader (if not already done)
            mpAddDiscoveredSources();
        }
        if (mpMapperServicesEnabled) {
            // add MP discovered converters from service loader (if not already done)
            mpAddDiscoveredConverters();
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
        if (mapperServicesEnabled) {
            loadMapperServices(prioritizedMappers);
        }
        Priorities.sort(prioritizedMappers);
        // as the mapperProviders.add adds the last as first, we need to reverse order
        Collections.reverse(prioritizedMappers);
        prioritizedMappers.forEach(mapperProviders::add);
        ConfigMapperManager configMapperManager = buildMappers(mapperProviders);


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
                              overrideSource,
                              filterProviders,
                              cachingEnabled,
                              changesExecutor,
                              keyResolving,
                              aliasGenerator)
                .newConfig();
    }

    @Override
    public Config.Builder config(Config metaConfig) {
        metaConfig.get("caching.enabled").asBoolean().ifPresent(this::cachingEnabled);
        metaConfig.get("key-resolving.enabled").asBoolean().ifPresent(this::keyResolvingEnabled);
        metaConfig.get("value-resolving.enabled").asBoolean().ifPresent(this::valueResolvingEnabled);
        metaConfig.get("parsers.enabled").asBoolean().ifPresent(this::parserServicesEnabled);
        metaConfig.get("mappers.enabled").asBoolean().ifPresent(this::mapperServicesEnabled);
        metaConfig.get("config-source-services.enabled").asBoolean().ifPresent(this::configSourceServicesEnabled);

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

    private void configSourceServicesEnabled(boolean enabled) {
        this.configSourceServicesEnabled = enabled;
    }

    void mpWithConverters(Converter<?>... converters) {
        for (Converter<?> converter : converters) {
            addMpConverter(converter);
        }
    }

    <T> void mpWithConverter(Class<T> type, int ordinal, Converter<T> converter) {
        // priority 1 is highest, 100 is default
        // MP ordinal 1 is lowest, 100 is default

        // 100 - priority 1
        // 101 - priority 0
        int priority = 101 - ordinal;
        prioritizedMappers.add(new MpConverterWrapper(type, converter, priority));
    }

    @SuppressWarnings("unchecked")
    private <T> void addMpConverter(Converter<T> converter) {
        Class<T> type = (Class<T>) getTypeOfMpConverter(converter.getClass());
        if (type == null) {
            throw new IllegalStateException("Converter " + converter.getClass() + " must be a ParameterizedType");
        }

        mpWithConverter(type,
                        Priorities.find(converter.getClass(), 100),
                        converter);
    }

    void mpAddDefaultSources() {
        hasEnvVarSource = true;
        hasSystemPropertiesSource = true;

        prioritizedSources.add(new HelidonSourceWithPriority(ConfigSources.systemProperties()
                                                                     .pollingStrategy(PollingStrategies
                                                                                              .regular(Duration.ofSeconds(2))
                                                                                              .build())
                                                                     .build()
                , 100));
        prioritizedSources.add(new HelidonSourceWithPriority(ConfigSources.environmentVariables(), 100));
        prioritizedSources.add(new HelidonSourceWithPriority(ConfigSources.classpath("application.yaml")
                                                                     .optional(true)
                                                                     .build(), 100));

        ConfigSources.classpathAll("META-INF/microprofile-config.properties")
                .stream()
                .map(io.helidon.common.Builder::build)
                .map(source -> new HelidonSourceWithPriority(source, 100))
                .forEach(prioritizedSources::add);
    }

    void mpAddDiscoveredSources() {
        if (mpDiscoveredSourcesAdded) {
            return;
        }
        this.mpDiscoveredSourcesAdded = true;
        this.configSourceServicesEnabled = true;
        final ClassLoader usedCl = ((null == classLoader) ? Thread.currentThread().getContextClassLoader() : classLoader);

        List<org.eclipse.microprofile.config.spi.ConfigSource> mpSources = new LinkedList<>();

        // service loader MP sources
        HelidonServiceLoader
                .create(ServiceLoader.load(org.eclipse.microprofile.config.spi.ConfigSource.class, usedCl))
                .forEach(mpSources::add);

        // config source providers
        HelidonServiceLoader.create(ServiceLoader.load(ConfigSourceProvider.class, usedCl))
                .forEach(csp -> csp.getConfigSources(usedCl)
                        .forEach(mpSources::add));

        for (org.eclipse.microprofile.config.spi.ConfigSource source : mpSources) {
            prioritizedMpSources.add(new PrioritizedMpSource(source));
        }
    }

    void mpAddDiscoveredConverters() {
        if (mpDiscoveredConvertersAdded) {
            return;
        }
        this.mpDiscoveredConvertersAdded = true;
        this.mpMapperServicesEnabled = true;

        final ClassLoader usedCl = ((null == classLoader) ? Thread.currentThread().getContextClassLoader() : classLoader);

        HelidonServiceLoader.create(ServiceLoader.load(Converter.class, usedCl))
                .forEach(this::addMpConverter);
    }

    void mpForClassLoader(ClassLoader loader) {
        this.classLoader = loader;
    }

    void mpWithSources(org.eclipse.microprofile.config.spi.ConfigSource... sources) {
        for (org.eclipse.microprofile.config.spi.ConfigSource source : sources) {
            if (source instanceof AbstractConfigSource) {
                prioritizedSources.add(new HelidonSourceWithPriority((ConfigSource) source, null));
            } else {
                prioritizedMpSources.add(new PrioritizedMpSource(source));
            }
        }
    }

    private Type getTypeOfMpConverter(Class<?> clazz) {
        if (clazz.equals(Object.class)) {
            return null;
        }

        Type[] genericInterfaces = clazz.getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            if (genericInterface instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericInterface;
                if (pt.getRawType().equals(Converter.class)) {
                    Type[] typeArguments = pt.getActualTypeArguments();
                    if (typeArguments.length != 1) {
                        throw new IllegalStateException("Converter " + clazz + " must be a ParameterizedType.");
                    }
                    return typeArguments[0];
                }
            }
        }

        return getTypeOfMpConverter(clazz.getSuperclass());
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

    private void valueResolvingEnabled(Boolean aBoolean) {
        // TODO this is a noop as is disableValueResolving
    }

    private void keyResolvingEnabled(Boolean aBoolean) {
        this.keyResolving = aBoolean;
    }

    private ConfigSourcesRuntime buildConfigSources(ConfigContextImpl context) {
        List<ConfigSourceRuntimeBase> targetSources = new LinkedList<>();

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

        boolean nothingConfigured = sources.isEmpty() && prioritizedSources.isEmpty() && prioritizedMpSources.isEmpty();

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

    private List<ConfigSourceRuntimeBase> mergePrioritized(ConfigContextImpl context) {
        List<PrioritizedConfigSource> allPrioritized = new ArrayList<>(this.prioritizedMpSources);
        prioritizedSources.stream()
                .map(it -> new PrioritizedHelidonSource(it, context))
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
                                OverrideSource overrideSource,
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

    static ConfigMapperManager buildMappers(MapperProviders userDefinedProviders) {

        MapperProviders providers = MapperProviders.create();

        List<ConfigMapperProvider> prioritizedProviders = new LinkedList<>();

        // we must add default mappers using a known priority (49), so they can be overridden by services
        // and yet we can still define a service that is only used after these (such as config beans)
        prioritizedProviders.add(new InternalPriorityMapperProvider(ConfigMappers.essentialMappers()));
        prioritizedProviders.add(new InternalPriorityMapperProvider(ConfigMappers.builtInMappers()));

        prioritizedProviders = ConfigUtils
                .asPrioritizedStream(prioritizedProviders, ConfigMapperProvider.PRIORITY)
                .collect(Collectors.toList());

        // add built in converters and converters from service loader
        prioritizedProviders.forEach(providers::add);

        // user defined converters always have priority over anything else
        providers.addAll(userDefinedProviders);

        return new ConfigMapperManager(providers);
    }

    private void loadMapperServices(List<PrioritizedMapperProvider> providers) {
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
                .map(filter -> (Function<Config, ConfigFilter>) (Config t) -> filter)
                .forEach(this::addFilter);
    }

    /**
     * {@link ConfigContext} implementation.
     */
    static class ConfigContextImpl implements ConfigContext {
        private final Map<ConfigSource, ConfigSourceRuntimeBase> runtimes = new IdentityHashMap<>();
        private final Map<org.eclipse.microprofile.config.spi.ConfigSource, ConfigSourceRuntimeBase> mpRuntimes
                = new IdentityHashMap<>();

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

        private ConfigSourceRuntimeBase sourceRuntimeBase(ConfigSource source) {
            return runtimes.computeIfAbsent(source, it -> new ConfigSourceRuntimeImpl(this, source));
        }

        Optional<ConfigParser> findParser(String mediaType) {
            Objects.requireNonNull(mediaType, "Unknown media type of resource.");

            return configParsers.stream()
                    .filter(parser -> parser.supportedMediaTypes().contains(mediaType))
                    .findFirst();
        }

        ConfigSourceRuntimeBase sourceRuntime(org.eclipse.microprofile.config.spi.ConfigSource source) {
            return mpRuntimes.computeIfAbsent(source, it -> new ConfigSourceMpRuntimeImpl(source));
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
                .disableSourceServices()
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

    private interface PrioritizedMapperProvider extends Prioritized,
                                                        ConfigMapperProvider {
    }

    private static final class MpConverterWrapper implements PrioritizedMapperProvider {
        private final Map<Class<?>, Function<Config, ?>> converterMap = new HashMap<>();
        private final Converter<?> converter;
        private final int priority;

        private MpConverterWrapper(Class<?> theClass,
                                   Converter<?> converter,
                                   int priority) {
            this.converter = converter;
            this.priority = priority;
            this.converterMap.put(theClass, config -> config.asString().as(converter::convert).get());
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public Map<Class<?>, Function<Config, ?>> mappers() {
            return converterMap;
        }

        @Override
        public String toString() {
            return converter.toString();
        }
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
    }

    private interface PrioritizedConfigSource extends Prioritized {
        ConfigSourceRuntimeBase runtime(ConfigContextImpl context);
    }

    private static final class PrioritizedMpSource implements PrioritizedConfigSource {
        private final org.eclipse.microprofile.config.spi.ConfigSource delegate;

        private PrioritizedMpSource(org.eclipse.microprofile.config.spi.ConfigSource delegate) {
            this.delegate = delegate;

        }

        @Override
        public ConfigSourceRuntimeBase runtime(ConfigContextImpl context) {
            return context.sourceRuntime(delegate);
        }

        @Override
        public int priority() {
            // MP config is using "ordinals" - the higher the number, the more important it is
            // We are using "priorities" - the lower the number, the more important it is
            String value = delegate.getValue(CONFIG_ORDINAL);

            int priority;

            if (null != value) {
                priority = Integer.parseInt(value);
            } else {
                priority = Priorities.find(delegate, 100);
            }

            // priority from Prioritized and annotation (MP has it reversed)
            // it is a tough call how to merge priorities and ordinals
            // now we use a "101" as a constant, so components with ordinal 100 will have
            // priority of 1
            return 101 - priority;
        }
    }

    private static final class PrioritizedHelidonSource implements PrioritizedConfigSource {
        private final HelidonSourceWithPriority source;
        private final ConfigContext context;

        private PrioritizedHelidonSource(HelidonSourceWithPriority source, ConfigContext context) {
            this.source = source;
            this.context = context;
        }

        @Override
        public ConfigSourceRuntimeBase runtime(ConfigContextImpl context) {
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
                    .node(CONFIG_ORDINAL)
                    .flatMap(node -> node.value()
                            .map(Integer::parseInt))
                    .orElseGet(() -> {
                        // the config source does not have an ordinal configured, I need to get it from other places
                        return Priorities.find(configSource, 100);
                    });
        }
    }

}
