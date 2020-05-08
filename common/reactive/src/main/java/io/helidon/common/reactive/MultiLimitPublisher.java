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
package io.helidon.common.reactive;

import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * Limit the number of items passing through, canceling the upstream and completing
 * the downstream.
 * @param <T> the element type of the sequence
 */
final class MultiLimitPublisher<T> implements Multi<T> {

    private final Multi<T> source;

    private final long limit;

    MultiLimitPublisher(Multi<T> source, long limit) {
        if (limit < 0L) {
            throw new IllegalArgumentException("limit is negative");
        }
        this.source = source;
        this.limit = limit;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");
        source.subscribe(new LimitSubscriber<>(subscriber, limit));
    }

    static final class LimitSubscriber<T> implements Flow.Subscriber<T>, Flow.Subscription {

        private final Flow.Subscriber<? super T> downstream;

        private long remaining;

        private Flow.Subscription upstream;

        LimitSubscriber(Flow.Subscriber<? super T> downstream, long remaining) {
            this.downstream = downstream;
            this.remaining = remaining;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.validate(upstream, subscription);
            if (remaining == 0L) {
                subscription.cancel();
                upstream = SubscriptionHelper.CANCELED;
                downstream.onSubscribe(EmptySubscription.INSTANCE);
                downstream.onComplete();
            } else {
                upstream = subscription;
                downstream.onSubscribe(this);
            }
        }

        @Override
        public void onNext(T item) {
            Flow.Subscription s = upstream;
            if (s != SubscriptionHelper.CANCELED) {
                long r = remaining - 1;
                remaining = r;
                downstream.onNext(item);
                if (r == 0L) {
                    s.cancel();
                    upstream = SubscriptionHelper.CANCELED;
                    downstream.onComplete();
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            Flow.Subscription s = upstream;
            if (s != SubscriptionHelper.CANCELED) {
                upstream = SubscriptionHelper.CANCELED;
                downstream.onError(throwable);
            }
        }

        @Override
        public void onComplete() {
            Flow.Subscription s = upstream;
            if (s != SubscriptionHelper.CANCELED) {
                upstream = SubscriptionHelper.CANCELED;
                downstream.onComplete();
            }
        }

        @Override
        public void request(long n) {
            upstream.request(n);
        }

        @Override
        public void cancel() {
            upstream.cancel();
            upstream = SubscriptionHelper.CANCELED;
        }
    }
}
