/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.internal.ConfigKeyImpl;

import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Abstract common implementation of {@link Config} extended by appropriate Config node types:
 * {@link ConfigListImpl}, {@link ConfigMissingImpl}, {@link ConfigObjectImpl}, {@link ConfigLeafImpl}.
 */
abstract class AbstractConfigImpl implements Config, org.eclipse.microprofile.config.Config {

    public static final Logger LOGGER = Logger.getLogger(AbstractConfigImpl.class.getName());

    private final ConfigKeyImpl prefix;
    private final ConfigKeyImpl key;
    private final ConfigKeyImpl realKey;
    private final ConfigFactory factory;
    private final Type type;
    private final Flow.Publisher<Config> changesPublisher;
    private final Context context;
    private final ConfigMapperManager mapperManager;
    private volatile Flow.Subscriber<ConfigDiff> subscriber;
    private final ReentrantReadWriteLock subscriberLock = new ReentrantReadWriteLock();
    private final AtomicReference<Config> latestConfig = new AtomicReference<>();
    private boolean useSystemProperties;
    private boolean useEnvironmentVariables;

    /**
     * Initializes Config implementation.
     *  @param type    a type of config node.
     * @param prefix  prefix key for the new config node.
     * @param key     a key to this config.
     * @param factory a config factory.
     * @param mapperManager mapper manager
     */
    AbstractConfigImpl(Type type,
                       ConfigKeyImpl prefix,
                       ConfigKeyImpl key,
                       ConfigFactory factory,
                       ConfigMapperManager mapperManager) {
        this.mapperManager = mapperManager;
        Objects.requireNonNull(prefix, "prefix argument is null.");
        Objects.requireNonNull(key, "key argument is null.");
        Objects.requireNonNull(factory, "factory argument is null.");

        this.prefix = prefix;
        this.key = key;
        this.realKey = prefix.child(key);
        this.factory = factory;
        this.type = type;

        changesPublisher = new FilteringConfigChangeEventPublisher(factory.changes());
        context = new NodeContextImpl();
    }

    ConfigMapperManager mapperManager() {
        return mapperManager;
    }

    /**
     * Returns a {@code String} value as {@link Optional} of configuration node if the node a leaf or "hybrid" node.
     * Returns a {@link Optional#empty() empty} if the node is {@link Type#MISSING} type or if the node does not contain a direct
     * value.
     * This is "raw" accessor method for String value of this config node. To have nicer variety of value accessors,
     * see {@link #asString()} and in general {@link #as(Class)}.
     *
     * @return value as type instance as {@link Optional}, {@link Optional#empty() empty} in case the node does not have a value
     *
     * use {@link #asString()} instead
     */
    Optional<String> value() {
        return Optional.empty();
    }

    @Override
    public Context context() {
        return context;
    }

    @Override
    public final Instant timestamp() {
        return factory.timestamp();
    }

    @Override
    public final ConfigKeyImpl key() {
        return key;
    }

    protected final ConfigKeyImpl realKey() {
        return realKey;
    }

    @Override
    public final Type type() {
        return type;
    }

    @Override
    public <T> T convert(Class<T> type, String value) throws ConfigMappingException {
        return mapperManager.map(value, type, key().toString());
    }

    @Override
    public final Config get(Config.Key subKey) {
        Objects.requireNonNull(subKey, "Key argument is null.");

        if (subKey.isRoot()) {
            return this;
        } else {
            return factory.config(prefix, this.key.child(subKey));
        }
    }

    @Override
    public final Config detach() {
        if (key.isRoot()) {
            return this;
        } else {
            return factory.config(realKey(), ConfigKeyImpl.of());
        }
    }

    @Override
    public ConfigValue<List<Config>> asNodeList() throws ConfigMappingException {
        return asList(Config.class);
    }

    void subscribe() {
        try {
            subscriberLock.readLock().lock();
            if (subscriber == null) {
                subscriberLock.readLock().unlock();
                subscriberLock.writeLock().lock();
                try {
                    try {
                        if (subscriber == null) {
                            waitForSubscription(1, TimeUnit.SECONDS);
                        }
                    } finally {
                        subscriberLock.readLock().lock();
                    }
                } finally {
                    subscriberLock.writeLock().unlock();
                }
            }
        } finally {
            subscriberLock.readLock().unlock();
        }
    }

    /*
     * MicroProfile Config methods
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T getValue(String propertyName, Class<T> propertyType) {
        Config config = latestConfig.get();
        try {
            return mpFindValue(config, propertyName, propertyType);
        } catch (MissingValueException e) {
            throw new NoSuchElementException(e.getMessage());
        } catch (ConfigMappingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public <T> Optional<T> getOptionalValue(String propertyName, Class<T> propertyType) {
        try {
            return Optional.of(getValue(propertyName, propertyType));
        } catch (NoSuchElementException e) {
            return Optional.empty();
        } catch (ConfigMappingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Iterable<String> getPropertyNames() {
        Set<String> keys = new HashSet<>(latestConfig.get()
                                                 .asMap()
                                                 .orElseGet(Collections::emptyMap)
                                                 .keySet());

        if (useSystemProperties) {
            keys.addAll(System.getProperties().stringPropertyNames());
        }

        return keys;
    }

    @Override
    public Iterable<ConfigSource> getConfigSources() {
        Config config = latestConfig.get();
        if (null == config) {
            // maybe we are in progress of initializing this config (e.g. filter processing)
            config = this;
        }

        if (config instanceof AbstractConfigImpl) {
            return ((AbstractConfigImpl) config).mpConfigSources();
        }
        return Collections.emptyList();
    }

    private Iterable<ConfigSource> mpConfigSources() {
        return new LinkedList<>(factory.mpConfigSources());
    }

    private <T> T mpFindValue(Config config, String propertyName, Class<T> propertyType) {
        // this is a workaround TCK tests that expect system properties to be mutable
        //  Helidon config does the same, yet with a slight delay (polling reasons)
        //  we need to check if system properties are enabled and first - if so, do this

        String property = null;
        if (useSystemProperties) {
            property = System.getProperty(propertyName);
        }

        if (null == property) {
            ConfigValue<T> value = config
                    .get(propertyName)
                    .as(propertyType);

            if (value.isPresent()) {
                return value.get();
            }

            // try to find in env vars
            if (useEnvironmentVariables) {
                T envVar = mpFindEnvVar(config, propertyName, propertyType);
                if (null != envVar) {
                    return envVar;
                }
            }

            return value.get();
        } else {
            return config.get(propertyName).convert(propertyType, property);
        }
    }

    private <T> T mpFindEnvVar(Config config, String propertyName, Class<T> propertyType) {
        String result = System.getenv(propertyName);

        // now let's resolve all variants required by the specification
        if (null == result) {
            for (String alias : EnvironmentVariableAliases.aliasesOf(propertyName)) {
                result = System.getenv(alias);
                if (null != result) {
                    break;
                }
            }
        }

        if (null != result) {
            return config.convert(propertyType, result);
        }

        return null;
    }

    /**
     * We should wait for a subscription, otherwise, we might miss some changes.
     */
    private void waitForSubscription(long timeout, TimeUnit unit) {
        CountDownLatch subscribeLatch = new CountDownLatch(1);
        subscriber = new Flow.Subscriber<ConfigDiff>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
                subscribeLatch.countDown();
            }

            @Override
            public void onNext(ConfigDiff item) {
            }

            @Override
            public void onError(Throwable throwable) {
                LOGGER.log(Level.CONFIG, "Error while subscribing a supplier to the changes.", throwable);
            }

            @Override
            public void onComplete() {
                LOGGER.log(Level.CONFIG, "The config suppliers will no longer receive any change.");
            }
        };
        factory.provider().changes().subscribe(subscriber);
        try {
            subscribeLatch.await(timeout, unit);
        } catch (InterruptedException e) {
            LOGGER.log(Level.CONFIG, "Waiting for a supplier subscription has been interrupted.", e);
            Thread.currentThread().interrupt();
        }
    }

    private Config contextConfig(Config rootConfig) {
        return rootConfig
                .get(AbstractConfigImpl.this.prefix)
                .detach()
                .get(AbstractConfigImpl.this.key);
    }

    ConfigFactory factory() {
        return factory;
    }

    @Override
    public Flow.Publisher<Config> changes() {
        return changesPublisher;
    }

    void initMp() {
        this.latestConfig.set(this);

        List<io.helidon.config.spi.ConfigSource> configSources = factory.configSources();
        if (configSources.isEmpty()) {
            // if no config sources, then no changes
            return;
        }
        if (configSources.size() == 1) {
            if (configSources.get(0) == ConfigSources.EmptyConfigSourceHolder.EMPTY) {
                // if the only config source is the empty one, then no changes
                return;
            }
        }

        io.helidon.config.spi.ConfigSource first = configSources.get(0);
        if (first instanceof BuilderImpl.HelidonSourceWrapper) {
            first = ((BuilderImpl.HelidonSourceWrapper) first).unwrap();
        }

        if (first instanceof ConfigSources.SystemPropertiesConfigSource) {
            this.useSystemProperties = true;
        }

        for (io.helidon.config.spi.ConfigSource configSource : configSources) {
            io.helidon.config.spi.ConfigSource it = configSource;
            if (it instanceof BuilderImpl.HelidonSourceWrapper) {
                it = ((BuilderImpl.HelidonSourceWrapper) it).unwrap();
            }

            if (it instanceof ConfigSources.EnvironmentVariablesConfigSource) {
                // there is an env var config source
                this.useEnvironmentVariables = true;
                break;
            }
        }

        // see #1183
        // this must be changed, as otherwise we would not get changes in MP
        //       and why did it work when the MpConfig was a separate implementation?
        //        onChange(newConfig -> {
        //            // this does not work - seems that when there is more than one subscriber, the events are not delivered
        //            latestConfig.set(newConfig);
        //        });
    }

    /**
     * {@link Flow.Publisher} implementation that filters general {@link ConfigFactory#changes()} events to be wrapped by
     * {@link FilteringConfigChangeEventSubscriber} for appropriate Config key and subscribers on the config node.
     */
    private class FilteringConfigChangeEventPublisher implements Flow.Publisher<Config> {

        private Flow.Publisher<ConfigDiff> delegate;

        private FilteringConfigChangeEventPublisher(Flow.Publisher<ConfigDiff> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super Config> subscriber) {
            delegate.subscribe(new FilteringConfigChangeEventSubscriber(subscriber));
        }

    }

    /**
     * {@link Flow.Subscriber} wrapper implementation that filters general {@link ConfigFactory#changes()} events
     * for appropriate Config key and subscribers on the config node.
     *
     * @see FilteringConfigChangeEventPublisher
     */
    private class FilteringConfigChangeEventSubscriber implements Flow.Subscriber<ConfigDiff> {

        private final Flow.Subscriber<? super Config> delegate;
        private Flow.Subscription subscription;

        private FilteringConfigChangeEventSubscriber(Flow.Subscriber<? super Config> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(ConfigDiff event) {
            //(3. fire just on case the sub-node has changed)
            if (event.changedKeys().contains(AbstractConfigImpl.this.realKey)) {
                delegate.onNext(AbstractConfigImpl.this.contextConfig(event.config()));
            } else {
                subscription.request(1);
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
     * Implementation of node specific context.
     */
    private class NodeContextImpl implements Context {

        @Override
        public Instant timestamp() {
            return AbstractConfigImpl.this.factory.context().timestamp();
        }

        @Override
        public Config last() {
            //the 'last config' behaviour is based on switched-on changes support
            subscribe();

            return AbstractConfigImpl.this.contextConfig(AbstractConfigImpl.this.factory.context().last());
        }

        @Override
        public Config reload() {
            return AbstractConfigImpl.this.contextConfig(AbstractConfigImpl.this.factory.context().reload());
        }

    }

}
