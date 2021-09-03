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

/**
 * Skips the first {@code n} items from the source and relays the rest as they are.
 * @param <T> the element type of the sequence
 */
final class MultiSkipPublisher<T> implements Multi<T> {

    private final Multi<T> source;

    private final long n;

    MultiSkipPublisher(Multi<T> source, long n) {
        this.source = source;
        this.n = Math.max(0L, n);
    }


    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");
        source.subscribe(new SkipSubscriber<>(subscriber, n));
    }

    static final class SkipSubscriber<T> implements Flow.Subscriber<T> {

        private final Flow.Subscriber<? super T> downstream;

        private long remaining;

        private Flow.Subscription upstream;

        SkipSubscriber(Flow.Subscriber<? super T> downstream, long remaining) {
            this.downstream = downstream;
            this.remaining = remaining;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            Objects.requireNonNull(subscription, "subscription is null");
            if (upstream != null) {
                subscription.cancel();
                throw new IllegalStateException("Subscription already set!");
            }
            long n = remaining;
            upstream = subscription;
            downstream.onSubscribe(subscription);
            if (n != 0L) {
                subscription.request(n);
            }
        }

        @Override
        public void onNext(T item) {
            long n = remaining;
            if (n == 0L) {
                downstream.onNext(item);
            } else {
                remaining = n - 1L;
            }
        }

        @Override
        public void onError(Throwable throwable) {
            downstream.onError(throwable);
        }

        @Override
        public void onComplete() {
            downstream.onComplete();
        }
    }
}
