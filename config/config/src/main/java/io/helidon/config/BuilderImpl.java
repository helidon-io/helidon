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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Priority;

import io.helidon.common.GenericType;
import io.helidon.common.Prioritized;
import io.helidon.common.serviceloader.HelidonServiceLoader;
import io.helidon.common.serviceloader.Priorities;
import io.helidon.config.ConfigMapperManager.MapperProviders;
import io.helidon.config.internal.ConfigThreadFactory;
import io.helidon.config.internal.ConfigUtils;
import io.helidon.config.spi.AbstractMpSource;
import io.helidon.config.spi.AbstractSource;
import io.helidon.config.spi.ConfigContext;
import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigMapper;
import io.helidon.config.spi.ConfigMapperProvider;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigSource;
import io.helidon.config.spi.OverrideSource;

import org.eclipse.microprofile.config.spi.ConfigSourceProvider;
import org.eclipse.microprofile.config.spi.Converter;

/**
 * {@link Config} Builder implementation.
 */
class BuilderImpl implements Config.Builder {
    static final Executor DEFAULT_CHANGES_EXECUTOR = Executors.newCachedThreadPool(new ConfigThreadFactory("config"));

    /*
     * Config sources
     */
    // sources to be sorted by priority
    private final List<PrioritizedConfigSource> prioritizedSources = new ArrayList<>();
    // sources "pre-sorted" - all user defined sources without priority will be ordered
    // as added
    private final List<ConfigSource> sources = new LinkedList<>();
    private boolean configSourceServicesEnabled;
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
     * Changes (TODO to be removed)
     */
    private Executor changesExecutor;
    private int changesMaxBuffer;
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
        changesExecutor = DEFAULT_CHANGES_EXECUTOR;
        changesMaxBuffer = Flow.defaultBufferSize();
        keyResolving = true;
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
    public AbstractConfigImpl build() {
        if (configSourceServicesEnabled) {
            // add MP config sources from service loader (if not already done)
            mpAddDiscoveredSources();
        }
        if (mpMapperServicesEnabled) {
            // add MP discovered converters from service loader (if not already done)
            mpAddDiscoveredConverters();
        }

        return buildProvider().newConfig();
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
                .ifPresent(list -> list.forEach(it -> sourceList.add(MetaConfig.configSource(it))));

        sourceList.forEach(this::addSource);
        sourceList.clear();

        Config overrideConfig = metaConfig.get("override-source");
        if (overrideConfig.exists()) {
            overrides(() -> MetaConfig.overrideSource(overrideConfig));
        }

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
        prioritizedSources.add(new HelidonSourceWrapper(ConfigSources.systemProperties(), 100));
        prioritizedSources.add(new HelidonSourceWrapper(ConfigSources.environmentVariables(), 100));
        prioritizedSources.add(new HelidonSourceWrapper(ConfigSources.classpath("application.yaml").optional().build(), 100));
        ConfigSources.classpathAll("META-INF/microprofile-config.properties")
                .stream()
                .map(AbstractSource.Builder::build)
                .map(source -> new HelidonSourceWrapper(source, 100))
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
            prioritizedSources.add(new MpSourceWrapper(source));
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
            PrioritizedConfigSource pcs;

            if (source instanceof AbstractMpSource) {
                pcs = new HelidonSourceWrapper((AbstractMpSource<?>) source);
            } else {
                pcs = new MpSourceWrapper(source);
            }
            this.prioritizedSources.add(pcs);
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

    private ProviderImpl buildProvider() {

        //context
        ConfigContext context = new ConfigContextImpl(buildParsers(parserServicesEnabled, parsers));

        //source
        ConfigSourceConfiguration targetConfigSource = targetConfigSource(context);

        //mappers
        Priorities.sort(prioritizedMappers);
        // as the mapperProviders.add adds the last as first, we need to reverse order
        Collections.reverse(prioritizedMappers);
        prioritizedMappers.forEach(mapperProviders::add);
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

    private ConfigSourceConfiguration targetConfigSource(ConfigContext context) {
        List<ConfigSource> targetSources = new LinkedList<>();

        if (systemPropertiesSourceEnabled
                && !hasSourceType(ConfigSources.SystemPropertiesConfigSource.class)) {
            targetSources.add(ConfigSources.systemProperties());
        }

        if (hasSourceType(ConfigSources.EnvironmentVariablesConfigSource.class)) {
            envVarAliasGeneratorEnabled = true;
        } else if (environmentVariablesSourceEnabled) {
            targetSources.add(ConfigSources.environmentVariables());
            envVarAliasGeneratorEnabled = true;
        }

        if (sources.isEmpty()) {
            // if there are no sources configured, use meta-configuration
            targetSources.addAll(MetaConfig.configSources(mediaType -> context.findParser(mediaType).isPresent()));
        } else {
            targetSources.addAll(sources);
        }

        // initialize all target sources
        targetSources.forEach(it -> it.init(context));

        if (!prioritizedSources.isEmpty()) {
            // initialize all prioritized sources (before we sort them - otherwise we cannot get priority)
            prioritizedSources.forEach(it -> it.init(context));
            Priorities.sort(prioritizedSources);
            targetSources.addAll(prioritizedSources);
        }

        if (targetSources.size() == 1) {
            // the only source does not require a composite wrapper
            return new ConfigSourceConfiguration(targetSources.get(0), targetSources);
        }

        return new ConfigSourceConfiguration(ConfigSources.create(targetSources.toArray(new ConfigSource[0])).build(),
                                             targetSources);
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
                                ConfigSourceConfiguration targetConfigSource,
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
            this.converterMap.put(theClass, config -> {
                return config.asString().as(converter::convert).get();
            });
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

    private interface PrioritizedConfigSource extends Prioritized,
                                                      ConfigSource,
                                                      org.eclipse.microprofile.config.spi.ConfigSource {

    }

    private static final class MpSourceWrapper implements PrioritizedConfigSource {
        private final org.eclipse.microprofile.config.spi.ConfigSource delegate;

        private MpSourceWrapper(org.eclipse.microprofile.config.spi.ConfigSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public int priority() {
            String value = delegate.getValue(CONFIG_ORDINAL);
            if (null != value) {
                return 101 - Integer.parseInt(value);
            }

            // priority from Prioritized and annotation (MP has it reversed)
            return 101 - Priorities.find(delegate, 100);
        }

        @Override
        public Optional<ConfigNode.ObjectNode> load() throws ConfigException {
            return Optional.of(ConfigUtils.mapToObjectNode(getProperties(), false));
        }

        @Override
        public Map<String, String> getProperties() {
            return delegate.getProperties();
        }

        @Override
        public String getValue(String propertyName) {
            return delegate.getValue(propertyName);
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public String description() {
            return delegate.toString();
        }

        @Override
        public String toString() {
            return description();
        }
    }

    static final class HelidonSourceWrapper implements PrioritizedConfigSource {

        private final AbstractMpSource<?> delegate;
        private Integer explicitPriority;

        private HelidonSourceWrapper(AbstractMpSource<?> delegate) {
            this.delegate = delegate;
        }

        private HelidonSourceWrapper(AbstractMpSource<?> delegate, int explicitPriority) {
            this.delegate = delegate;
            this.explicitPriority = explicitPriority;
        }

        AbstractMpSource<?> unwrap() {
            return delegate;
        }

        @Override
        public int priority() {
            // ordinal from data
            String value = delegate.getValue(CONFIG_ORDINAL);
            if (null != value) {
                return 101 - Integer.parseInt(value);
            }

            if (null != explicitPriority) {
                return explicitPriority;
            }

            // priority from Prioritized and annotation
            return Priorities.find(delegate, 100);
        }

        @Override
        public Map<String, String> getProperties() {
            return delegate.getProperties();
        }

        @Override
        public String getValue(String propertyName) {
            return delegate.getValue(propertyName);
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Set<String> getPropertyNames() {
            return delegate.getPropertyNames();
        }

        @Override
        public String description() {
            return delegate.description();
        }

        @Override
        public Optional<ConfigNode.ObjectNode> load() throws ConfigException {
            return delegate.load();
        }

        @Override
        public ConfigSource get() {
            return delegate.get();
        }

        @Override
        public void init(ConfigContext context) {
            delegate.init(context);
        }

        @Override
        public Flow.Publisher<Optional<ConfigNode.ObjectNode>> changes() {
            return delegate.changes();
        }

        @Override
        public void close() throws Exception {
            delegate.close();
        }

        @Override
        public String toString() {
            return description();
        }
    }

    static final class ConfigSourceConfiguration {
        private static final ConfigSourceConfiguration EMPTY =
                new ConfigSourceConfiguration(ConfigSources.empty(), List.of(ConfigSources.empty()));
        private final ConfigSource compositeSource;
        private final List<ConfigSource> allSources;

        private ConfigSourceConfiguration(ConfigSource compositeSource, List<ConfigSource> allSources) {
            this.compositeSource = compositeSource;
            this.allSources = allSources;
        }

        static ConfigSourceConfiguration empty() {
            return EMPTY;
        }

        ConfigSource compositeSource() {
            return compositeSource;
        }

        List<ConfigSource> allSources() {
            return allSources;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ConfigSourceConfiguration that = (ConfigSourceConfiguration) o;
            return compositeSource.equals(that.compositeSource)
                    && allSources.equals(that.allSources);
        }

        @Override
        public int hashCode() {
            return Objects.hash(compositeSource, allSources);
        }
    }
}
