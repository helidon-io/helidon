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

package io.helidon.config.etcd;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.etcd.EtcdConfigSourceBuilder.EtcdEndpoint;
import io.helidon.config.etcd.internal.client.EtcdClient;
import io.helidon.config.etcd.internal.client.EtcdClientException;
import io.helidon.config.spi.ChangeEventType;
import io.helidon.config.spi.ChangeWatcher;

/**
 * Etcd watch strategy is based on etcd watch operation.
 */
public class EtcdWatcher implements ChangeWatcher<EtcdEndpoint> {

    private static final Logger LOGGER = Logger.getLogger(EtcdWatcher.class.getName());

    private final AtomicBoolean started = new AtomicBoolean();

    private EtcdEndpoint endpoint;
    private EtcdClient etcdClient;

    /**
     * Creates polling strategy from etcd params.
     */
    EtcdWatcher() {
    }

    /**
     * Creates a change watcher for sources based on etcd that provide
     * {@link io.helidon.config.etcd.EtcdConfigSourceBuilder.EtcdEndpoint} as a target.
     *
     * @return configured polling strategy
     */
    public static EtcdWatcher create() {
        return new EtcdWatcher();
    }

    /**
     * Create a new instance from meta configuration.
     *
     * @param metaConfig currently ignored
     * @return a new etcd watcher
     */
    public static EtcdWatcher create(Config metaConfig) {
        return new EtcdWatcher();
    }

    @Override
    public void start(EtcdEndpoint endpoint, Consumer<ChangeEvent<EtcdEndpoint>> listener) {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("EtcdWatcher cannot be reused for multiple sources");
        }
        this.endpoint = endpoint;
        this.etcdClient = endpoint.api()
                .clientFactory()
                .createClient(endpoint.uri());
        try {
            Flow.Publisher<Long> watchPublisher = etcdClient().watch(endpoint.key());
            watchPublisher.subscribe(new EtcdWatchSubscriber(listener, endpoint));
        } catch (EtcdClientException ex) {
            LOGGER.log(Level.WARNING, String.format("Subscription on watching on '%s' key has failed. "
                                                            + "Watching by '%s' polling strategy will not start.",
                                                    EtcdWatcher.this.endpoint.key(),
                                                    EtcdWatcher.this), ex);
        }
    }

    @Override
    public void stop() {
        if (!started.get()) {
            return;
        }

        try {
            this.etcdClient.close();
        } catch (EtcdClientException e) {
            LOGGER.log(Level.FINE, "Faield to close etcd client", e);
        }
    }

    @Override
    public Class<EtcdEndpoint> type() {
        return EtcdEndpoint.class;
    }

    EtcdClient etcdClient() {
        return etcdClient;
    }

    EtcdEndpoint etcdEndpoint() {
        return endpoint;
    }

    /**
     * {@link Flow.Subscriber} on {@link EtcdClient#watch(String)}.
     */
    private static class EtcdWatchSubscriber implements Flow.Subscriber<Long> {

        private Flow.Subscription subscription;
        private final Consumer<ChangeEvent<EtcdEndpoint>> listener;
        private EtcdEndpoint endpoint;

        EtcdWatchSubscriber(Consumer<ChangeEvent<EtcdEndpoint>> listener,
                                   EtcdEndpoint endpoint) {
            this.listener = listener;
            this.endpoint = endpoint;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;

            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(Long item) {
            listener.accept(ChangeEvent.create(endpoint, ChangeEventType.CHANGED));
        }

        @Override
        public void onError(Throwable throwable) {
            LOGGER.log(Level.WARNING,
                            String.format(
                                    "Watching on '%s' key has failed. Watching will not continue. ",
                                    endpoint.key()),
                            throwable);
        }

        @Override
        public void onComplete() {
        }
    }
}
