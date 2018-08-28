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

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.reactive.Flow;
import io.helidon.config.internal.ConfigKeyImpl;

/**
 * Abstract common implementation of {@link Config} extended by appropriate Config node types:
 * {@link ConfigListImpl}, {@link ConfigMissingImpl}, {@link ConfigObjectImpl}, {@link ConfigValueImpl}.
 */
abstract class AbstractConfigImpl implements Config {

    public static final Logger LOGGER = Logger.getLogger(AbstractConfigImpl.class.getName());

    private final ConfigKeyImpl prefix;
    private final ConfigKeyImpl key;
    private final ConfigKeyImpl realKey;
    private final ConfigFactory factory;
    private final Type type;
    private final Flow.Publisher<Config> changesPublisher;
    private final Context context;
    private volatile Flow.Subscriber<ConfigDiff> subscriber;
    private final ReentrantReadWriteLock subscriberLock = new ReentrantReadWriteLock();

    /**
     * Initializes Config implementation.
     *
     * @param type    a type of config node.
     * @param prefix  prefix key for the new config node.
     * @param key     a key to this config.
     * @param factory a config factory.
     */
    AbstractConfigImpl(Type type,
                       ConfigKeyImpl prefix,
                       ConfigKeyImpl key,
                       ConfigFactory factory) {
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

    @Override
    public Context context() {
        return context;
    }

    @Override
    public final Instant timestamp() {
        return factory.getTimestamp();
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
    public final Config get(Config.Key subKey) {
        Objects.requireNonNull(subKey, "Key argument is null.");

        if (subKey.isRoot()) {
            return this;
        } else {
            return factory.getConfig(prefix, this.key.child(subKey));
        }
    }

    @Override
    public final Config detach() {
        if (key.isRoot()) {
            return this;
        } else {
            return factory.getConfig(realKey(), ConfigKeyImpl.of());
        }
    }

    private void subscribe() {
        try {
            subscriberLock.readLock().lock();
            if (subscriber == null) {
                subscriberLock.readLock().unlock();
                subscriberLock.writeLock().lock();
                try {
                    if (subscriber == null) {
                        waitForSubscription(1, TimeUnit.SECONDS);
                    }
                    subscriberLock.readLock().lock();
                } finally {
                    subscriberLock.writeLock().unlock();
                }
            }
        } finally {
            subscriberLock.readLock().unlock();
        }
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
        factory.getProvider().changes().subscribe(subscriber);
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

    ConfigFactory getFactory() {
        return factory;
    }

    @Override
    public Flow.Publisher<Config> changes() {
        return changesPublisher;
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
                delegate.onNext(AbstractConfigImpl.this.contextConfig(event.getConfig()));
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
            return AbstractConfigImpl.this.factory.getContext().timestamp();
        }

        @Override
        public Config last() {
            //the 'last config' behaviour is based on switched-on changes support
            subscribe();

            return AbstractConfigImpl.this.contextConfig(AbstractConfigImpl.this.factory.getContext().last());
        }

        @Override
        public Config reload() {
            return AbstractConfigImpl.this.contextConfig(AbstractConfigImpl.this.factory.getContext().reload());
        }

    }

}
