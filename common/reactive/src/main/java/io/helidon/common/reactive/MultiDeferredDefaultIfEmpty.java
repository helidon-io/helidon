/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.common.reactive;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Signal an item if the source is empty.
 * @param <T> the element type
 */
final class MultiDeferredDefaultIfEmpty<T> implements Multi<T> {

    private final Multi<T> source;

    private final Supplier<? extends T> defaultItemSupplier;

    MultiDeferredDefaultIfEmpty(Multi<T> source, Supplier<? extends T> defaultItem) {
        this.source = source;
        this.defaultItemSupplier = defaultItem;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        source.subscribe(new DefaultIfEmptySubscriber<>(subscriber, defaultItemSupplier));
    }

    static final class DefaultIfEmptySubscriber<T>
            implements Flow.Subscriber<T>, Flow.Subscription {

        private final Flow.Subscriber<? super T> downstream;

        private final Supplier<? extends T> defaultItemSupplier;

        private final AtomicLong requested;

        private final AtomicReference<Flow.Subscription> fallback;

        private Flow.Subscription upstream;

        private boolean nonEmpty;

        DefaultIfEmptySubscriber(Flow.Subscriber<? super T> downstream, Supplier<? extends T> defaultItem) {
            this.downstream = downstream;
            this.defaultItemSupplier = defaultItem;
            this.requested = new AtomicLong();
            this.fallback = new AtomicReference<>();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.validate(upstream, subscription);
            upstream = subscription;
            downstream.onSubscribe(this);
        }

        @Override
        public void onNext(T item) {
            nonEmpty = true;
            downstream.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            downstream.onError(throwable);
        }

        @Override
        public void onComplete() {
            if (nonEmpty) {
                downstream.onComplete();
            } else {
                SubscriptionHelper.deferredSetOnce(fallback, requested,
                        new SingleDeferredSubscription<>(defaultItemSupplier, downstream));
            }
        }

        @Override
        public void request(long n) {
            upstream.request(n);
            SubscriptionHelper.deferredRequest(fallback, requested, n);
        }

        @Override
        public void cancel() {
            upstream.cancel();
            SubscriptionHelper.cancel(fallback);
        }
    }
}
