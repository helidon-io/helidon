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

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import io.helidon.config.spi.ConfigFilter;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigNode.ValueNode;
import io.helidon.config.spi.ConfigSource;

/**
 * The factory class creates and caches already created instances of {@link Config} for specified {@code key}.
 */
final class ConfigFactory {

    private final ConfigMapperManager mapperManager;
    private final Map<ConfigKeyImpl, ConfigNode> fullKeyToNodeMap;
    private final ConfigFilter filter;
    private final ProviderImpl provider;
    private final Function<String, List<String>> aliasGenerator;
    private final Map<PrefixedKey, AbstractConfigImpl> configCache;
    private final ReentrantReadWriteLock configCacheLock = new ReentrantReadWriteLock();
    private final Instant timestamp;

    /**
     * Create new instance of the factory operating on specified {@link ConfigSource}.
     * @param mapperManager  manager to be used to map string value to appropriate type
     * @param node           root configuration node provided by the configuration source to be used to build
     *                       {@link Config} instances on.
     * @param filter         config filter used to filter each single value
     * @param provider       shared config provider
     * @param aliasGenerator key alias generator (may be {@code null})
     */
    ConfigFactory(ConfigMapperManager mapperManager,
                  ObjectNode node,
                  ConfigFilter filter,
                  ProviderImpl provider,
                  Function<String, List<String>> aliasGenerator) {

        Objects.requireNonNull(mapperManager, "mapperManager argument is null.");
        Objects.requireNonNull(node, "node argument is null.");
        Objects.requireNonNull(filter, "filter argument is null.");
        Objects.requireNonNull(provider, "provider argument is null.");

        this.mapperManager = mapperManager;
        this.fullKeyToNodeMap = ConfigHelper.createFullKeyToNodeMap(node);
        this.filter = filter;
        this.provider = provider;
        this.aliasGenerator = aliasGenerator;

        // all access must be guarded by configCacheLock
        this.configCache = new HashMap<>();
        this.timestamp = Instant.now();
    }

    Instant timestamp() {
        return timestamp;
    }

    /**
     * Get existing or create new root {@link Config} instance.
     *
     * @return root {@link Config}
     */
    AbstractConfigImpl config() {
        return config(ConfigKeyImpl.of(), ConfigKeyImpl.of());
    }

    /**
     * Get existing or create new {@link Config} instance for specified {@code key}.
     *
     * @param prefix key prefix (to support detached roots)
     * @param key    config key
     * @return {@code key} specific instance of {@link Config}
     */
    AbstractConfigImpl config(ConfigKeyImpl prefix, ConfigKeyImpl key) {
        PrefixedKey prefixedKey = new PrefixedKey(prefix, key);

        try {
            configCacheLock.readLock().lock();
            AbstractConfigImpl config = configCache.get(prefixedKey);
            if (config != null) {
                return config;
            }
        } finally {
            configCacheLock.readLock().unlock();
        }

        // use write lock, re-check, and create if still missing (we want to have a guarantee each key is only created once)
        try {
            configCacheLock.writeLock().lock();
            AbstractConfigImpl config = configCache.get(prefixedKey);
            if (config != null) {
                return config;
            }
            // we use locks, as this may be a blocking operation
            // such as when using lazy config source that accesses remote servers
            config = createConfig(prefix, key);
            configCache.put(prefixedKey, config);
            return config;
        } finally {
            configCacheLock.writeLock().unlock();
        }
    }

    /**
     * Create new instance of {@link Config}.
     *
     * @param key config key
     * @return new instance of {@link Config} for specified {@code key}
     */
    private AbstractConfigImpl createConfig(ConfigKeyImpl prefix, ConfigKeyImpl key) {
        ConfigNode value = findNode(prefix, key);

        if (null == value) {
            return new ConfigMissingImpl(prefix, key, this, mapperManager);
        }

        switch (value.nodeType()) {
        case OBJECT:
            return new ConfigObjectImpl(prefix, key, (ObjectNode) value, filter, this, mapperManager);
        case LIST:
            return new ConfigListImpl(prefix, key, (ListNode) value, filter, this, mapperManager);
        case VALUE:
            return new ConfigLeafImpl(prefix, key, (ValueNode) value, filter, this, mapperManager);
        default:
            return new ConfigMissingImpl(prefix, key, this, mapperManager);
        }
    }

    private ConfigNode findNode(ConfigKeyImpl prefix, ConfigKeyImpl key) {
        ConfigKeyImpl realKey = prefix.child(key);
        ConfigNode node = fullKeyToNodeMap.get(realKey);
        if (node == null && aliasGenerator != null) {
            final String fullKey = key.toString();
            for (final String keyAlias : aliasGenerator.apply(fullKey)) {
                node = fullKeyToNodeMap.get(prefix.child(keyAlias));
                if (node != null) {
                    break;
                }
            }
        }
        if (node == null) {
            // check if any lazy source supports this node
            return provider.lazyValue(realKey.toString())
                    .orElse(null);
        }
        return node;
    }

    /**
     * Returns whole configuration context.
     *
     * @return whole configuration context.
     */
    Config.Context context() {
        return provider;
    }

    ProviderImpl provider() {
        return provider;
    }

    /**
     * Prefix represents detached roots.
     */
    private static final class PrefixedKey {
        private final ConfigKeyImpl prefix;
        private final ConfigKeyImpl key;

        private PrefixedKey(ConfigKeyImpl prefix, ConfigKeyImpl key) {
            this.prefix = prefix;
            this.key = key;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PrefixedKey that = (PrefixedKey) o;
            return Objects.equals(prefix, that.prefix)
                    && Objects.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(prefix, key);
        }
    }

}
