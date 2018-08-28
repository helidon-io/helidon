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

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.helidon.common.reactive.Flow;
import io.helidon.config.internal.ConfigKeyImpl;
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
    private final ConcurrentMap<PrefixedKey, Reference<Config>> configCache;
    private final Flow.Publisher<ConfigDiff> changesPublisher;
    private final Instant timestamp;

    /**
     * Create new instance of the factory operating on specified {@link ConfigSource}.
     *
     * @param mapperManager manager to be used to map string value to appropriate type
     * @param node          root configuration node provided by the configuration source to be used to build
     *                      {@link Config} instances on.
     * @param filter        config filter used to filter each single value
     * @param provider      shared config provider
     */
    ConfigFactory(ConfigMapperManager mapperManager,
                  ConfigNode.ObjectNode node,
                  ConfigFilter filter,
                  ProviderImpl provider) {
        Objects.requireNonNull(mapperManager, "mapperManager argument is null.");
        Objects.requireNonNull(node, "node argument is null.");
        Objects.requireNonNull(filter, "filter argument is null.");
        Objects.requireNonNull(provider, "provider argument is null.");

        this.mapperManager = mapperManager;
        this.fullKeyToNodeMap = createFullKeyToNodeMap(node);
        this.filter = filter;
        this.provider = provider;

        configCache = new ConcurrentHashMap<>();
        changesPublisher = new FilteringConfigChangeEventPublisher(provider.changes());
        timestamp = Instant.now();
    }

    private static Map<ConfigKeyImpl, ConfigNode> createFullKeyToNodeMap(ObjectNode objectNode) {
        Map<ConfigKeyImpl, ConfigNode> result;

        Stream<Map.Entry<ConfigKeyImpl, ConfigNode>> flattenNodes = objectNode.entrySet()
                .stream()
                .map(node -> flattenNodes(ConfigKeyImpl.of(node.getKey()), node.getValue()))
                .reduce(Stream.empty(), Stream::concat);
        result = flattenNodes.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        result.put(ConfigKeyImpl.of(), objectNode);

        return result;
    }

    static Stream<Map.Entry<ConfigKeyImpl, ConfigNode>> flattenNodes(ConfigKeyImpl key, ConfigNode node) {
        switch (node.getNodeType()) {
        case OBJECT:
            return ((ObjectNode) node).entrySet().stream()
                    .map(e -> flattenNodes(key.child(e.getKey()), e.getValue()))
                    .reduce(Stream.of(new AbstractMap.SimpleEntry<>(key, node)), Stream::concat);
        case LIST:
            return IntStream.range(0, ((ListNode) node).size())
                    .boxed()
                    .map(i -> flattenNodes(key.child(Integer.toString(i)), ((ListNode) node).get(i)))
                    .reduce(Stream.of(new AbstractMap.SimpleEntry<>(key, node)), Stream::concat);
        case VALUE:
            return Stream.of(new AbstractMap.SimpleEntry<>(key, node));
        default:
            throw new IllegalArgumentException("Invalid node type.");
        }
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Get existing or create new root {@link Config} instance.
     *
     * @return root {@link Config}
     */
    public Config getConfig() {
        return getConfig(ConfigKeyImpl.of(), ConfigKeyImpl.of());
    }

    /**
     * Get existing or create new {@link Config} instance for specified {@code key}.
     *
     * @param prefix key prefix (to support detached roots)
     * @param key    config key
     * @return {@code key} specific instance of {@link Config}
     */
    public Config getConfig(ConfigKeyImpl prefix, ConfigKeyImpl key) {
        PrefixedKey prefixedKey = new PrefixedKey(prefix, key);
        Reference<Config> reference = configCache.compute(prefixedKey, (k, v) -> {
            if (v == null || v.get() == null) {
                return new SoftReference<>(createConfig(prefix, key));
            } else {
                return v;
            }
        });
        return reference.get();
    }

    /**
     * Create new instance of {@link Config}.
     *
     * @param key config key
     * @return new instance of {@link Config} for specified {@code key}
     */
    private Config createConfig(ConfigKeyImpl prefix, ConfigKeyImpl key) {
        ConfigNode value = fullKeyToNodeMap.get(prefix.child(key));

        if (null == value) {
            return new ConfigMissingImpl(prefix, key, this);
        }

        switch (value.getNodeType()) {
        case OBJECT:
            return new ConfigObjectImpl(prefix, key, (ObjectNode) value, filter, this, mapperManager);
        case LIST:
            return new ConfigListImpl(prefix, key, (ListNode) value, filter, this, mapperManager);
        case VALUE:
            return new ConfigValueImpl(prefix, key, (ValueNode) value, filter, this, mapperManager);
        default:
            return new ConfigMissingImpl(prefix, key, this);
        }
    }

    public Flow.Publisher<ConfigDiff> changes() {
        return changesPublisher;
    }

    /**
     * Returns whole configuration context.
     *
     * @return whole configuration context.
     */
    Config.Context getContext() {
        return provider;
    }

    ProviderImpl getProvider() {
        return provider;
    }

    /**
     * {@link Flow.Publisher} implementation that filters original {@link ProviderImpl#changes()} events to be wrapped by
     * {@link FilteringConfigChangeEventSubscriber} to ignore events about current Config instance.
     */
    private class FilteringConfigChangeEventPublisher implements Flow.Publisher<ConfigDiff> {

        private Flow.Publisher<ConfigDiff> delegate;

        private FilteringConfigChangeEventPublisher(Flow.Publisher<ConfigDiff> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ConfigDiff> subscriber) {
            delegate.subscribe(new FilteringConfigChangeEventSubscriber(subscriber));
        }

    }

    /**
     * {@link Flow.Subscriber} wrapper implementation that filters original {@link ProviderImpl#changes()} events
     * and ignore events about current Config instance.
     *
     * @see FilteringConfigChangeEventPublisher
     */
    private class FilteringConfigChangeEventSubscriber implements Flow.Subscriber<ConfigDiff> {

        private final Flow.Subscriber<? super ConfigDiff> delegate;
        private Flow.Subscription subscription;

        private FilteringConfigChangeEventSubscriber(Flow.Subscriber<? super ConfigDiff> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;

            delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(ConfigDiff event) {
            if (ConfigFactory.this.getConfig() == event.getConfig()) { //ignore events about current Config instance
                //missed event must be requested once more
                subscription.request(1);
            } else {
                delegate.onNext(event);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            delegate.onError(throwable);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }
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
