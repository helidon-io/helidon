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

package io.helidon.config.etcd;

import java.time.Instant;
import java.util.concurrent.Flow;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.reactive.SubmissionPublisher;
import io.helidon.config.ConfigException;
import io.helidon.config.ConfigHelper;
import io.helidon.config.etcd.EtcdConfigSourceBuilder.EtcdEndpoint;
import io.helidon.config.etcd.internal.client.EtcdClient;
import io.helidon.config.etcd.internal.client.EtcdClientException;
import io.helidon.config.spi.PollingStrategy;

/**
 * Etcd watch strategy is based on etcd watch operation.
 */
public class EtcdWatchPollingStrategy implements PollingStrategy {

    private static final Logger LOGGER = Logger.getLogger(EtcdWatchPollingStrategy.class.getName());

    private final EtcdEndpoint endpoint;
    private final EtcdClient etcdClient;
    private final SubmissionPublisher<PollingEvent> ticksSubmitter;
    private final Flow.Publisher<PollingEvent> ticksPublisher;

    private EtcdWatchSubscriber etcdWatchSubscriber;

    /**
     * Creates polling strategy from etcd params.
     *
     * @param endpoint etcd remote descriptor
     * @return configured polling strategy
     */
    public static EtcdWatchPollingStrategy create(EtcdEndpoint endpoint) {
        return new EtcdWatchPollingStrategy(endpoint);
    }

    /**
     * Creates polling strategy from etcd params.
     *
     * @param endpoint etcd remote descriptor
     */
    // this has to be public, as it is used by meta-configuration
    public EtcdWatchPollingStrategy(EtcdEndpoint endpoint) {
        this.endpoint = endpoint;
        etcdClient = endpoint.api()
                .clientFactory()
                .createClient(endpoint.uri());

        ticksSubmitter = new SubmissionPublisher<>(Runnable::run, //deliver events on current thread
                                                   1); //(almost) do not buffer events
        ticksPublisher = ConfigHelper.suspendablePublisher(ticksSubmitter,
                                                           this::subscribePollingStrategy,
                                                           this::cancelPollingStrategy);
    }

    EtcdClient etcdClient() {
        return etcdClient;
    }

    void subscribePollingStrategy() {
        etcdWatchSubscriber = new EtcdWatchSubscriber();
        try {
            Flow.Publisher<Long> watchPublisher = etcdClient().watch(endpoint.key());
            watchPublisher.subscribe(etcdWatchSubscriber);
        } catch (EtcdClientException ex) {
            ticksSubmitter.closeExceptionally(
                    new ConfigException(
                            String.format("Subscription on watching on '%s' key has failed. "
                                                  + "Watching by '%s' polling strategy will not start.",
                                          EtcdWatchPollingStrategy.this.endpoint.key(),
                                          EtcdWatchPollingStrategy.this),
                            ex));
        }
    }

    void cancelPollingStrategy() {
        etcdWatchSubscriber.cancelSubscription();
        etcdWatchSubscriber = null;
    }

    @Override
    public Flow.Publisher<PollingEvent> ticks() {
        return ticksPublisher;
    }

    private void fireEvent(Long item) {
        ticksSubmitter.offer(
                EtcdPollingEvent.from(item),
                (subscriber, pollingEvent) -> {
                    LOGGER.log(Level.FINER, String.format("Event %s has not been delivered to %s.", pollingEvent, subscriber));
                    return false;
                });
    }

    EtcdEndpoint etcdEndpoint() {
        return endpoint;
    }

    /**
     * An etcd polling event with the new content.
     */
    private interface EtcdPollingEvent extends PollingEvent {

        /**
         * Returns etcd revision.
         *
         * @return etcd revision
         */
        Long index();

        static EtcdPollingEvent from(Long index) {
            Instant timestamp = Instant.now();
            return new EtcdPollingEvent() {
                @Override
                public Long index() {
                    return index;
                }

                @Override
                public Instant timestamp() {
                    return timestamp;
                }

                @Override
                public String toString() {
                    return "EtcdPollingEvent @ " + timestamp + " # " + index;
                }
            };
        }

    }

    /**
     * {@link Flow.Subscriber} on {@link EtcdClient#watch(String)}.
     */
    private class EtcdWatchSubscriber implements Flow.Subscriber<Long> {

        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;

            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(Long item) {
            EtcdWatchPollingStrategy.this.fireEvent(item);
        }

        @Override
        public void onError(Throwable throwable) {
            EtcdWatchPollingStrategy.this.ticksSubmitter
                    .closeExceptionally(new ConfigException(
                            String.format(
                                    "Watching on '%s' key has failed. Watching by '%s' polling strategy will not continue. %s",
                                    EtcdWatchPollingStrategy.this.endpoint.key(),
                                    EtcdWatchPollingStrategy.this,
                                    throwable.getLocalizedMessage()),
                            throwable));
        }

        @Override
        public void onComplete() {
            LOGGER.fine(String.format("Watching on '%s' key has completed. Watching by '%s' polling strategy will not continue.",
                                      EtcdWatchPollingStrategy.this.endpoint.key(),
                                      EtcdWatchPollingStrategy.this));

            EtcdWatchPollingStrategy.this.ticksSubmitter.close();
        }

        private void cancelSubscription() {
            if (subscription != null) {
                subscription.cancel();
            }
        }
    }

}
