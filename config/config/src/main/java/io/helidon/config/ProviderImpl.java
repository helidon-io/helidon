/*
 * Copyright (c) 2017, 2024 Oracle and/or its affiliates.
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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;

/**
 * Config provider represents initialization context used to create new instance of Config again and again.
 */
class ProviderImpl implements Config.Context {

    private static final System.Logger LOGGER = System.getLogger(ConfigFactory.class.getName());

    private final List<Consumer<ConfigDiff>> listeners = new LinkedList<>();

    private final ConfigMapperManager configMapperManager;
    private final ConfigSourcesRuntime configSource;
    private final OverrideSourceRuntime overrideSource;
    private final List<Function<Config, ConfigFilter>> filterProviders;
    private final boolean cachingEnabled;

    private final Executor changesExecutor;
    private final boolean keyResolving;
    private final boolean keyResolvingFailOnMissing;
    private final Function<String, List<String>> aliasGenerator;

    private ConfigDiff lastConfigsDiff;
    private AbstractConfigImpl lastConfig;
    private boolean listening;

    @SuppressWarnings("ParameterNumber")
    ProviderImpl(ConfigMapperManager configMapperManager,
                 ConfigSourcesRuntime configSource,
                 OverrideSourceRuntime overrideSource,
                 List<Function<Config, ConfigFilter>> filterProviders,
                 boolean cachingEnabled,
                 Executor changesExecutor,
                 boolean keyResolving,
                 boolean keyResolvingFailOnMissing,
                 Function<String, List<String>> aliasGenerator) {
        this.configMapperManager = configMapperManager;
        this.configSource = configSource;
        this.overrideSource = overrideSource;
        this.filterProviders = Collections.unmodifiableList(filterProviders);
        this.cachingEnabled = cachingEnabled;
        this.changesExecutor = changesExecutor;

        this.lastConfigsDiff = null;
        this.lastConfig = (AbstractConfigImpl) Config.empty();

        this.keyResolving = keyResolving;
        this.keyResolvingFailOnMissing = keyResolvingFailOnMissing;
        this.aliasGenerator = aliasGenerator;
    }

    public synchronized AbstractConfigImpl newConfig() {
        lastConfig = build(configSource.load());

        if (!listening) {
            // only start listening for changes once the first config is built
            configSource.changeListener(objectNode -> rebuild(objectNode, false));
            configSource.startChanges();
            overrideSource.changeListener(() -> rebuild(configSource.latest(), false));
            overrideSource.startChanges();
            listening = true;
        }

        return lastConfig;
    }

    @Override
    public synchronized Config reload() {
        rebuild(configSource.latest(), true);
        return lastConfig;
    }

    @Override
    public synchronized Instant timestamp() {
        return lastConfig.timestamp();
    }

    @Override
    public synchronized Config last() {
        return lastConfig;
    }

    Optional<ConfigNode> lazyValue(String string) {
        return configSource.lazyValue(string);
    }

    void onChange(Consumer<ConfigDiff> listener) {
        this.listeners.add(listener);
    }

    private synchronized AbstractConfigImpl build(Optional<ObjectNode> rootNode) {

        // resolve tokens
        rootNode = rootNode.map(this::resolveKeys);
        // filtering
        ChainConfigFilter targetFilter = new ChainConfigFilter();
        // add override filter
        overrideSource.addFilter(targetFilter);

        // factory
        ConfigFactory factory = new ConfigFactory(configMapperManager,
                                                  rootNode.orElseGet(ObjectNode::empty),
                                                  targetFilter,
                                                  this,
                                                  aliasGenerator);
        AbstractConfigImpl config = factory.config();
        // initialize filters
        initializeFilters(config, targetFilter);
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

    private synchronized void rebuild(Optional<ObjectNode> objectNode, boolean force) {
        // 1. build new Config
        AbstractConfigImpl newConfig = build(objectNode);
        // 2. for each subscriber fire event on specific node/key - see AbstractConfigImpl.FilteringConfigChangeEventSubscriber
        // 3. fire event
        ConfigDiff configsDiff = ConfigDiff.from(lastConfig, newConfig);
        if (!configsDiff.isEmpty()) {
            lastConfig = newConfig;
            lastConfigsDiff = configsDiff;

            fireLastChangeEvent();
        } else {
            if (force) {
                lastConfig = newConfig;
            }

            LOGGER.log(Level.TRACE, "Change event is not fired, there is no change from the last load.");
        }
    }

    private void fireLastChangeEvent() {
        ConfigDiff configDiffs;

        synchronized (this) {
            configDiffs = this.lastConfigsDiff;
        }

        if (configDiffs != null) {
            LOGGER.log(Level.TRACE, String.format("Firing last event %s (again)", configDiffs));

            changesExecutor.execute(() -> {
                for (Consumer<ConfigDiff> listener : listeners) {
                    listener.accept(configDiffs);
                }
            });
        }
    }

    private void initializeFilters(Config config, ChainConfigFilter chain) {
        chain.init(config);

        filterProviders.stream()
                .map(providerFunction -> providerFunction.apply(config))
                .forEachOrdered(chain::addFilter);

        chain.filterProviders.stream()
                .map(providerFunction -> providerFunction.apply(config))
                .forEachOrdered(filter -> filter.init(config));
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
