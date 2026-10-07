/*
 * Copyright (c) 2017, 2026 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigFilterFactory;
import io.helidon.config.spi.ConfigFilterProvider;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;

/**
 * Config provider represents initialization context used to create new instance of Config again and again.
 */
class ProviderImpl implements Config.Context {

    private static final System.Logger LOGGER = System.getLogger(ConfigFactory.class.getName());
    private static final AtomicBoolean FILTER_RELOAD_WARNING = new AtomicBoolean();

    private final List<Consumer<ConfigDiff>> listeners = new LinkedList<>();

    private final ConfigMapperManager configMapperManager;
    private final ConfigSourcesRuntime configSource;
    private final OverrideSourceRuntime overrideSource;
    private final List<Function<Config, ConfigFilter>> filterProviders;
    private final List<ConfigFilterProvider> runtimeFilterProviders;
    private final List<ConfigFilterFactory> filterFactories = new ArrayList<>();
    private final boolean hasFixedFilters;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicBoolean reloadPending = new AtomicBoolean();
    private final AtomicBoolean reloadRunning = new AtomicBoolean();
    private final boolean cachingEnabled;

    private final Executor changesExecutor;
    private final boolean keyResolving;
    private final boolean keyResolvingFailOnMissing;

    private ConfigDiff lastConfigsDiff;
    private Config lastConfig;
    private AbstractConfigImpl lastConfigImpl;
    private boolean listening;
    private volatile boolean ready;
    private volatile boolean stopped;
    private boolean factoriesCreated;

    @SuppressWarnings("ParameterNumber")
    ProviderImpl(ConfigMapperManager configMapperManager,
                 ConfigSourcesRuntime configSource,
                 OverrideSourceRuntime overrideSource,
                 List<Function<Config, ConfigFilter>> filterProviders,
                 List<ConfigFilterProvider> runtimeFilterProviders,
                 boolean hasFixedFilters,
                 boolean cachingEnabled,
                 Executor changesExecutor,
                 boolean keyResolving,
                 boolean keyResolvingFailOnMissing) {
        this.configMapperManager = configMapperManager;
        this.configSource = configSource;
        this.overrideSource = overrideSource;
        this.filterProviders = List.copyOf(filterProviders);
        this.runtimeFilterProviders = List.copyOf(runtimeFilterProviders);
        this.hasFixedFilters = hasFixedFilters;
        this.cachingEnabled = cachingEnabled;
        this.changesExecutor = changesExecutor;

        this.lastConfigsDiff = null;
        this.lastConfig = Config.empty();
        this.lastConfigImpl = null;

        this.keyResolving = keyResolving;
        this.keyResolvingFailOnMissing = keyResolvingFailOnMissing;
    }

    public AbstractConfigImpl newConfig() {
        try {
            lock.lock();
            try {
                lastConfigImpl = build(configSource.load());
                if (!listening) {
                    // only start listening for changes once the first config is built
                    configSource.changeListener(objectNode -> rebuild(objectNode, false));
                    configSource.startChanges();
                    overrideSource.changeListener(() -> rebuild(configSource.latest(), false));
                    overrideSource.startChanges();
                    boolean changesSupported = configSource.changesSupported() || overrideSource.changesSupported();
                    for (ConfigFilterFactory factory : filterFactories) {
                        changesSupported |= factory.startChangeSupport(this::requestReload);
                    }
                    if (changesSupported) {
                        warnFixedFilters();
                    }
                    listening = true;
                }
                ready = true;
                scheduleReload();
                return lastConfigImpl;
            } catch (RuntimeException | Error e) {
                stopped = true;
                throw e;
            } finally {
                lock.unlock();
            }
        } catch (RuntimeException | Error e) {
            try {
                stopResources();
            } catch (RuntimeException | Error cleanup) {
                e.addSuppressed(cleanup);
            }
            throw e;
        }
    }

    @Override
    public Config reload() {
        lock.lock();
        try {
            warnFixedFilters();
            rebuild(configSource.latest(), true);
            return lastConfigInstance();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Instant timestamp() {
        lock.lock();
        try {
            return lastConfigInstance().timestamp();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Config last() {
        lock.lock();
        try {
            return lastConfigInstance();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void stopChangeSupport() {
        lock.lock();
        try {
            if (stopped) {
                return;
            }
            stopped = true;
            reloadPending.set(false);
        } finally {
            lock.unlock();
        }
        stopResources();
    }

    Optional<ConfigNode> lazyValue(String string) {
        return configSource.lazyValue(string);
    }

    void onChange(Consumer<ConfigDiff> listener) {
        lock.lock();
        try {
            this.listeners.add(listener);
        } finally {
            lock.unlock();
        }
    }

    private Config lastConfigInstance() {
        return lastConfigImpl == null ? lastConfig : lastConfigImpl;
    }

    private AbstractConfigImpl build(Optional<ObjectNode> rootNode) {

        // resolve tokens
        rootNode = rootNode.map(this::resolveKeys);
        ObjectNode node = rootNode.orElseGet(ObjectNode::empty);
        Config raw = runtimeFilterProviders.isEmpty()
                ? Config.empty()
                : new ConfigFactory(configMapperManager, node, (_, value) -> value, this, true).config();
        if (!factoriesCreated) {
            for (ConfigFilterProvider provider : runtimeFilterProviders) {
                filterFactories.add(Objects.requireNonNull(provider.create(raw), "Config filter factory"));
            }
            factoriesCreated = true;
        }
        // filtering
        ChainConfigFilter targetFilter = new ChainConfigFilter();
        // add override filter
        overrideSource.addFilter(targetFilter);
        List<ConfigFilter> legacyFilters = new ArrayList<>();
        targetFilter.filterProviders.stream().map(provider -> provider.apply(raw)).forEachOrdered(legacyFilters::add);
        for (ConfigFilterFactory filterFactory : filterFactories) {
            targetFilter.addFilter(Objects.requireNonNull(filterFactory.create(raw), "Config filter"));
        }

        // factory
        ConfigFactory factory = new ConfigFactory(configMapperManager,
                                                  rootNode.orElseGet(ObjectNode::empty),
                                                  targetFilter,
                                                  this);
        AbstractConfigImpl config = factory.config();
        // initialize filters
        initializeFilters(config, targetFilter, legacyFilters);
        // caching
        if (cachingEnabled) {
            targetFilter.enableCaching();
        }
        return config;
    }

    private ObjectNode resolveKeys(ObjectNode rootNode) {
        Function<String, String> resolveTokenFunction = Function.identity();
        if (keyResolving) {
            Map<String, String> flattenValueNodes = ConfigHelper.flattenNodes(rootNode);

            if (flattenValueNodes.isEmpty()) {
                return rootNode;
            }

            Map<String, String> tokenValueMap = tokenToValueMap(flattenValueNodes);
            boolean failOnMissingKeyReference = getBoolean(flattenValueNodes,
                                                           "config.key-resolving.fail-on-missing-reference",
                                                           keyResolvingFailOnMissing);

            resolveTokenFunction = (token) -> {
                if (token.startsWith("$")) {
                    String tokenRef = parseTokenReference(token);
                    String resolvedValue = tokenValueMap.get(tokenRef);
                    if (resolvedValue.isEmpty()) {
                        if (failOnMissingKeyReference) {
                            throw new ConfigException(String.format("Missing token '%s' to resolve a key reference.", tokenRef));
                        } else {
                            return token;
                        }
                    }
                    return resolvedValue;
                }
                return token;
            };
        }
        return ObjectNodeBuilderImpl.create(rootNode, resolveTokenFunction).build();
    }

    /*
     * Returns a map of required replacement tokens to their respective values from the current config tree.
     * The values may be empty strings, representing unresolved references.
     */
    private Map<String, String> tokenToValueMap(Map<String, String> flattenValueNodes) {
        return flattenValueNodes.keySet()
                .stream()
                .flatMap(this::tokensFromKey)
                .distinct()
                .collect(Collectors.toMap(Function.identity(), t -> {
                                              // t is the reference we need to resolve
                                              // we cannot use compute, as that modifies the map we are currently navigating
                                              String value = flattenValueNodes.get(Config.Key.unescapeName(t));
                                              if (value == null) {
                                                  value = "";
                                              } else {
                                                  if (value.startsWith("$")) {
                                                      throw new ConfigException(String.format(
                                                              "Key token '%s' references to a reference in value. A recursive"
                                                                      + " references is not allowed.",
                                                              t));
                                                  }
                                                  value = Config.Key.escapeName(value);
                                              }
                                              // either null (not found), or escaped value
                                              return value;
                                          }));
    }

    private Stream<String> tokensFromKey(String s) {
        String[] tokens = s.split("\\.+(?![^(${)]*})");
        return Arrays.stream(tokens).filter(t -> t.startsWith("$")).map(ProviderImpl::parseTokenReference);
    }

    private static String parseTokenReference(String token) {
        if (token.startsWith("${") && token.endsWith("}")) {
            return token.substring(2, token.length() - 1);
        } else if (token.startsWith("$")) {
            return token.substring(1);
        }
        return token;
    }

    private void rebuild(Optional<ObjectNode> objectNode, boolean force) {
        lock.lock();
        try {
            if (stopped && !force) {
                return;
            }
            AbstractConfigImpl newConfig = build(objectNode);
            ConfigDiff configsDiff = ConfigDiff.from(lastConfigInstance(), newConfig);
            if (!configsDiff.isEmpty()) {
                lastConfig = newConfig;
                lastConfigImpl = newConfig;
                lastConfigsDiff = configsDiff;
                fireLastChangeEvent();
            } else {
                if (force) {
                    lastConfig = newConfig;
                    lastConfigImpl = newConfig;
                }
                LOGGER.log(Level.TRACE, "Change event is not fired, there is no change from the last load.");
            }
        } finally {
            lock.unlock();
        }
    }

    private void fireLastChangeEvent() {
        ConfigDiff configDiffs;

        lock.lock();
        try {
            configDiffs = this.lastConfigsDiff;
        } finally {
            lock.unlock();
        }

        if (configDiffs != null) {
            LOGGER.log(Level.TRACE, String.format("Firing last event %s (again)", configDiffs));

            List<Consumer<ConfigDiff>> currentListeners = List.copyOf(listeners);
            changesExecutor.execute(() -> {
                for (Consumer<ConfigDiff> listener : currentListeners) {
                    listener.accept(configDiffs);
                }
            });
        }
    }

    @SuppressWarnings("removal")
    private void initializeFilters(Config config, ChainConfigFilter chain, List<ConfigFilter> legacyFilters) {
        chain.init(config);

        filterProviders.stream()
                .map(providerFunction -> providerFunction.apply(config))
                .forEachOrdered(filter -> {
                    chain.addFilter(filter);
                    legacyFilters.add(filter);
                });
        legacyFilters.forEach(filter -> filter.init(config));
    }

    private void warnFixedFilters() {
        if (hasFixedFilters && FILTER_RELOAD_WARNING.compareAndSet(false, true)) {
            LOGGER.log(Level.WARNING, "Reloading configuration with shared ConfigFilter instances may create inconsistent "
                    + "filter state. Use ConfigFilterProvider for configuration-dependent filters.");
        }
    }

    private void requestReload() {
        if (!stopped) {
            reloadPending.set(true);
            scheduleReload();
        }
    }

    private void scheduleReload() {
        if (ready && !stopped && reloadPending.get() && reloadRunning.compareAndSet(false, true)) {
            ForkJoinPool.commonPool().execute(this::drainReloads);
        }
    }

    private void drainReloads() {
        try {
            while (!stopped && reloadPending.getAndSet(false)) {
                try {
                    rebuild(configSource.latest(), false);
                } catch (RuntimeException e) {
                    LOGGER.log(Level.WARNING, "Cannot reload configuration after a config filter change; "
                            + "the previous configuration remains available.");
                }
            }
        } finally {
            reloadRunning.set(false);
            scheduleReload();
        }
    }

    private void stopResources() {
        Throwable failure = null;
        List<Runnable> stops = new ArrayList<>();
        stops.add(configSource::stopChanges);
        stops.add(overrideSource::stopChanges);
        filterFactories.forEach(factory -> stops.add(factory::stopChangeSupport));
        for (Runnable stop : stops) {
            try {
                stop.run();
            } catch (RuntimeException | Error e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure instanceof RuntimeException e) {
            throw e;
        }
        if (failure instanceof Error e) {
            throw e;
        }
    }

    private boolean getBoolean(Map<String, String> valueNodes, String key, boolean defaultValue) {
        String value = valueNodes.get(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * Config filter chain that can combine a collection of {@link ConfigFilter} and wrap them into one config filter.
     */
    static class ChainConfigFilter implements ConfigFilter {

        private final List<Function<Config, ConfigFilter>> filterProviders;
        private boolean cachingEnabled = false;
        private ConcurrentMap<Config.Key, String> valueCache;
        private Config config;

        /**
         * Creates config filter chain from given filters.
         */
        ChainConfigFilter() {
            this.filterProviders = new ArrayList<>();
        }

        @Override
        public void init(Config config) {
            this.config = config;
        }

        void addFilter(ConfigFilter filter) {
            if (cachingEnabled) {
                throw new IllegalStateException("Cannot add new filter to the chain when cache is already enabled.");
            }
            filterProviders.add((config) -> filter);
        }

        @Override
        public String apply(Config.Key key, String stringValue) {
            if (cachingEnabled) {
                if (!valueCache.containsKey(key)) {
                    ConfigItem configItem = ConfigItem.builder()
                            .cacheItem(cachingEnabled)
                            .item(stringValue)
                            .build();
                    configItem = proceedFilters(key, configItem);
                    String value = configItem.item();
                    if (configItem.cacheItem()) {
                        valueCache.put(key, value);
                    }
                    return value;
                }
                return valueCache.get(key);
            } else {
                ConfigItem configItem = ConfigItem.builder()
                        .cacheItem(cachingEnabled)
                        .item(stringValue)
                        .build();
                return proceedFilters(key, configItem).item();
            }
        }

        private ConfigItem proceedFilters(Config.Key key, ConfigItem configItem) {
            ConfigItem toReturn = configItem;
            for (Function<Config, ConfigFilter> configFilterProvider : filterProviders) {
                toReturn = configFilterProvider.apply(config).apply(key, toReturn);
            }
            return toReturn;
        }

        void enableCaching() {
            this.cachingEnabled = true;
            this.valueCache = new ConcurrentHashMap<>();
        }
    }
}
