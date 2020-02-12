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

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
    private final Function<String, List<String>> aliasGenerator;
    private final ConcurrentMap<PrefixedKey, Reference<AbstractConfigImpl>> configCache;
    private final Flow.Publisher<ConfigDiff> changesPublisher;
    private final Instant timestamp;
    private final List<ConfigSource> configSources;
    private final List<org.eclipse.microprofile.config.spi.ConfigSource> mpConfigSources;

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
                  Function<String, List<String>> aliasGenerator,
                  List<ConfigSource> configSources) {

        Objects.requireNonNull(mapperManager, "mapperManager argument is null.");
        Objects.requireNonNull(node, "node argument is null.");
        Objects.requireNonNull(filter, "filter argument is null.");
        Objects.requireNonNull(provider, "provider argument is null.");

        this.mapperManager = mapperManager;
        this.fullKeyToNodeMap = createFullKeyToNodeMap(node);
        this.filter = filter;
        this.provider = provider;
        this.aliasGenerator = aliasGenerator;
        this.configSources = configSources;

        configCache = new ConcurrentHashMap<>();
        changesPublisher = new FilteringConfigChangeEventPublisher(provider.changes());
        timestamp = Instant.now();

        this.mpConfigSources = configSources.stream()
                .map(ConfigFactory::toMpSource)
                .collect(Collectors.toList());
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
        switch (node.nodeType()) {
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

    public Instant timestamp() {
        return timestamp;
    }

    /**
     * Get existing or create new root {@link Config} instance.
     *
     * @return root {@link Config}
     */
    public AbstractConfigImpl config() {
        return config(ConfigKeyImpl.of(), ConfigKeyImpl.of());
    }

    /**
     * Get existing or create new {@link Config} instance for specified {@code key}.
     *
     * @param prefix key prefix (to support detached roots)
     * @param key    config key
     * @return {@code key} specific instance of {@link Config}
     */
    public AbstractConfigImpl config(ConfigKeyImpl prefix, ConfigKeyImpl key) {
        PrefixedKey prefixedKey = new PrefixedKey(prefix, key);
        Reference<AbstractConfigImpl> reference = configCache.compute(prefixedKey, (k, v) -> {
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
        ConfigNode node = fullKeyToNodeMap.get(prefix.child(key));
        if (node == null && aliasGenerator != null) {
            final String fullKey = key.toString();
            for (final String keyAlias : aliasGenerator.apply(fullKey)) {
                node = fullKeyToNodeMap.get(prefix.child(keyAlias));
                if (node != null) {
                    break;
                }
            }
        }
        return node;
    }

    public Flow.Publisher<ConfigDiff> changes() {
        return changesPublisher;
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

    List<ConfigSource> configSources() {
        return configSources;
    }

    List<org.eclipse.microprofile.config.spi.ConfigSource> mpConfigSources() {
        return mpConfigSources;
    }

    private static org.eclipse.microprofile.config.spi.ConfigSource toMpSource(ConfigSource helidonCs) {
        if (helidonCs instanceof org.eclipse.microprofile.config.spi.ConfigSource) {
            return (org.eclipse.microprofile.config.spi.ConfigSource) helidonCs;
        } else {
            // this is a non-Helidon ConfigSource
            return new MpConfigSource(helidonCs);
        }

    }

    private static final class MpConfigSource implements org.eclipse.microprofile.config.spi.ConfigSource {
        private final AtomicReference<Map<String, String>> currentValues = new AtomicReference<>();
        private final Object lock = new Object();
        private final ConfigSource delegate;

        private MpConfigSource(ConfigSource helidonCs) {
            this.delegate = helidonCs;
            delegate.changes()
                    .subscribe(new Flow.Subscriber<Optional<ObjectNode>>() {
                        @Override
                        public void onSubscribe(Flow.Subscription subscription) {
                            subscription.request(Long.MAX_VALUE);
                        }

                        @Override
                        public void onNext(Optional<ObjectNode> item) {
                            synchronized (lock) {
                                currentValues.set(loadMap(item));
                            }
                        }

                        @Override
                        public void onError(Throwable throwable) {
                        }

                        @Override
                        public void onComplete() {
                        }
                    });
        }

        @Override
        public Map<String, String> getProperties() {
            ensureValue();
            return currentValues.get();
        }

        @Override
        public String getValue(String propertyName) {
            ensureValue();
            return currentValues.get().get(propertyName);
        }

        @Override
        public String getName() {
            return delegate.description();
        }

        private void ensureValue() {
            synchronized (lock) {
                if (null == currentValues.get()) {
                    currentValues.set(loadMap(delegate.load()));
                }
            }
        }

        private static Map<String, String> loadMap(Optional<ObjectNode> item) {
            if (item.isPresent()) {
                ConfigNode.ObjectNode node = item.get();
                Map<String, String> values = new TreeMap<>();
                processNode(values, "", node);
                return values;
            } else {
                return Map.of();
            }
        }

        private static void processNode(Map<String, String> values, String keyPrefix, ConfigNode.ObjectNode node) {
            node.forEach((key, configNode) -> {
                switch (configNode.nodeType()) {
                case OBJECT:
                    processNode(values, key(keyPrefix, key), (ConfigNode.ObjectNode) configNode);
                    break;
                case LIST:
                    processNode(values, key(keyPrefix, key), ((ConfigNode.ListNode) configNode));
                    break;
                case VALUE:
                    values.put(key(keyPrefix, key), configNode.get());
                    break;
                default:
                    throw new IllegalStateException("Config node of type: " + configNode.nodeType() + " not supported");
                }

            });
        }

        private static void processNode(Map<String, String> values, String keyPrefix, ConfigNode.ListNode node) {
            List<String> directValue = new LinkedList<>();
            Map<String, String> thisListValues = new HashMap<>();
            boolean hasDirectValue = true;

            for (int i = 0; i < node.size(); i++) {
                ConfigNode configNode = node.get(i);
                String nextKey = key(keyPrefix, String.valueOf(i));
                switch (configNode.nodeType()) {
                case OBJECT:
                    processNode(thisListValues, nextKey, (ConfigNode.ObjectNode) configNode);
                    hasDirectValue = false;
                    break;
                case LIST:
                    processNode(thisListValues, nextKey, (ConfigNode.ListNode) configNode);
                    hasDirectValue = false;
                    break;
                case VALUE:
                    String value = configNode.get();
                    directValue.add(value);
                    thisListValues.put(nextKey, value);
                    break;
                default:
                    throw new IllegalStateException("Config node of type: " + configNode.nodeType() + " not supported");
                }
            }

            if (hasDirectValue) {
                values.put(keyPrefix, String.join(",", directValue));
            } else {
                values.putAll(thisListValues);
            }
        }

        private static String key(String keyPrefix, String key) {
            if (keyPrefix.isEmpty()) {
                return key;
            }
            return keyPrefix + "." + key;
        }
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
            if (ConfigFactory.this.config() == event.config()) { //ignore events about current Config instance
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
