/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.reactive;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Relay upstream items until the other source signals an item or completes.
 * @param <T> the upstream and output value type
 * @param <U> the other sequence indicating when the main sequence should stop
 */
final class MultiTakeUntilPublisher<T, U> implements Multi<T> {

    private final Multi<T> source;

    private final Flow.Publisher<U> other;

    MultiTakeUntilPublisher(Multi<T> source, Flow.Publisher<U> other) {
        this.source = source;
        this.other = other;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");

        TakeUntilMainSubscriber<T> parent = new TakeUntilMainSubscriber<>(subscriber);
        subscriber.onSubscribe(parent);

        other.subscribe(parent.other());
        source.subscribe(parent);
    }

    static final class TakeUntilMainSubscriber<T>
            extends AtomicInteger
            implements Flow.Subscriber<T>, Flow.Subscription {

        private final Flow.Subscriber<? super T> downstream;

        private final AtomicLong requested;

        private final AtomicReference<Flow.Subscription> upstream;

        private final AtomicReference<Throwable> error;

        private final TakeUntilOtherSubscriber other;

        TakeUntilMainSubscriber(Flow.Subscriber<? super T> downstream) {
            this.downstream = downstream;
            this.requested = new AtomicLong();
            this.upstream = new AtomicReference<>();
            this.error = new AtomicReference<>();
            this.other = new TakeUntilOtherSubscriber(this);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.deferredSetOnce(upstream, requested, subscription);
        }

        @Override
        public void onNext(T item) {
            HalfSerializer.onNext(downstream, this, error, item);
        }

        @Override
        public void onError(Throwable throwable) {
            SubscriptionHelper.cancel(other);
            HalfSerializer.onError(downstream, this, error, throwable);
        }

        @Override
        public void onComplete() {
            SubscriptionHelper.cancel(other);
            HalfSerializer.onComplete(downstream, this, error);
        }

        @Override
        public void request(long n) {
            if (n <= 0L) {
                SubscriptionHelper.cancel(upstream);
                onError(new IllegalArgumentException("Rule §3.9 violated: non-positive requests are forbidden"));
            } else {
                SubscriptionHelper.deferredRequest(upstream, requested, n);
            }
        }

        @Override
        public void cancel() {
            SubscriptionHelper.cancel(upstream);
            SubscriptionHelper.cancel(other);
        }

        void otherComplete() {
            SubscriptionHelper.cancel(upstream);
            HalfSerializer.onComplete(downstream, this, error);
        }

        void otherError(Throwable throwable) {
            SubscriptionHelper.cancel(upstream);
            HalfSerializer.onError(downstream, this, error, throwable);
        }

        TakeUntilOtherSubscriber other() {
            return other;
        }

        static final class TakeUntilOtherSubscriber extends AtomicReference<Flow.Subscription>
        implements Flow.Subscriber<Object> {

            private final TakeUntilMainSubscriber<?> parent;

            TakeUntilOtherSubscriber(TakeUntilMainSubscriber<?> parent) {
                this.parent = parent;
            }

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                if (SubscriptionHelper.setOnce(this, subscription)) {
                    subscription.request(Long.MAX_VALUE);
                }
            }

            @Override
            public void onNext(Object item) {
                SubscriptionHelper.cancel(this);
                parent.otherComplete();
            }

            @Override
            public void onError(Throwable throwable) {
                if (get() != SubscriptionHelper.CANCELED) {
                    lazySet(SubscriptionHelper.CANCELED);
                    parent.otherError(throwable);
                }
            }

            @Override
            public void onComplete() {
                if (get() != SubscriptionHelper.CANCELED) {
                    lazySet(SubscriptionHelper.CANCELED);
                    parent.otherComplete();
                }
            }
        }
    }
}
