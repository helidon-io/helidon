/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package io.helidon.microprofile.reactive.hybrid;

import java.util.Objects;
import java.util.concurrent.Flow;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Wrapper for {@link org.reactivestreams Reactive Streams} {@link org.reactivestreams.Subscriber}
 * or {@link io.helidon.common.reactive Helidon reactive streams} {@link java.util.concurrent.Flow.Subscriber},
 * to be used interchangeably.
 *
 * @param <T> type of items
 */
public interface HybridSubscriber<T> extends Flow.Subscriber<T>, Subscriber<T> {


    /**
     * Create new {@link io.helidon.microprofile.reactive.hybrid.HybridSubscriber}
     * from {@link java.util.concurrent.Flow.Subscriber}.
     *
     * @param subscriber {@link java.util.concurrent.Flow.Subscriber} to wrap
     * @param <T>        type of items
     * @return {@link io.helidon.microprofile.reactive.hybrid.HybridSubscriber}
     * compatible with {@link org.reactivestreams Reactive Streams}
     * and {@link io.helidon.common.reactive Helidon reactive streams}
     */
    static <T> HybridSubscriber<T> from(Flow.Subscriber<T> subscriber) {
        return new HybridSubscriber<T>() {

            @Override
            public void onSubscribe(Subscription subscription) {
                subscriber.onSubscribe(HybridSubscription.from(subscription));
            }

            @Override
            public void onNext(T item) {
                subscriber.onNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
                subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        };
    }

    /**
     * Create new {@link io.helidon.microprofile.reactive.hybrid.HybridSubscriber}
     * from {@link org.reactivestreams.Subscriber}.
     *
     * @param s   {@link org.reactivestreams.Subscriber} to wrap
     * @param <T> type of items
     * @return {@link io.helidon.microprofile.reactive.hybrid.HybridSubscriber}
     * compatible with {@link org.reactivestreams Reactive Streams}
     * and {@link io.helidon.common.reactive Helidon reactive streams}
     */
    static <T> HybridSubscriber<T> from(Subscriber<T> s) {
        final SubscriberReference<T> subscriberReference = new SubscriberReference<>(Objects.requireNonNull(s));
        return new HybridSubscriber<T>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriberReference
                        .onSubscribe(HybridSubscription.from(subscription)
                                .onCancel(subscriberReference::release)
                        );
            }

            @Override
            public void onNext(T item) {
                subscriberReference.onNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
                subscriberReference.onError(throwable);
            }

            @Override
            public void onComplete() {
                subscriberReference.onComplete();
            }
        };
    }

    @Override
    default void onSubscribe(Flow.Subscription subscription) {
        onSubscribe((Subscription) HybridSubscription.from(subscription));
    }

    @Override
    default void onSubscribe(Subscription subscription) {
        onSubscribe((Flow.Subscription) HybridSubscription.from(subscription));
    }

    /**
     * Simple releasable subscriber reference.
     * https://github.com/reactive-streams/reactive-streams-jvm#3.13
     *
     * @param <T> type of the item
     */
    class SubscriberReference<T> implements Subscriber<T> {
        private Subscriber<T> subscriber;

        SubscriberReference(Subscriber<T> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            subscriber.onSubscribe(subscription);
        }

        @Override
        public void onNext(T item) {
            subscriber.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            subscriber.onError(throwable);
        }

        @Override
        public void onComplete() {
            subscriber.onComplete();
        }

        void release() {
            subscriber = new DummySubscriber<T>();
        }
    }

    /**
     * Simple releasable subscriber reference.
     * https://github.com/reactive-streams/reactive-streams-jvm#3.13
     *
     * @param <T> type of the item
     */
    class DummySubscriber<T> implements Subscriber<T> {

        @Override
        public void onSubscribe(Subscription s) {
            s.cancel();
        }

        @Override
        public void onNext(T t) {

        }

        @Override
        public void onError(Throwable t) {

        }

        @Override
        public void onComplete() {

        }
    }
}
