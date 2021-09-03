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

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Signal an item if the source is empty.
 * @param <T> the element type
 */
final class MultiDefaultIfEmpty<T> implements Multi<T> {

    private final Multi<T> source;

    private final T defaultItem;

    MultiDefaultIfEmpty(Multi<T> source, T defaultItem) {
        this.source = source;
        this.defaultItem = defaultItem;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        source.subscribe(new DefaultIfEmptySubscriber<>(subscriber, defaultItem));
    }

    static final class DefaultIfEmptySubscriber<T>
    implements Flow.Subscriber<T>, Flow.Subscription {

        private final Flow.Subscriber<? super T> downstream;

        private final T defaultItem;

        private final AtomicLong requested;

        private final AtomicReference<Flow.Subscription> fallback;

        private Flow.Subscription upstream;

        private boolean nonEmpty;

        DefaultIfEmptySubscriber(Flow.Subscriber<? super T> downstream, T defaultItem) {
            this.downstream = downstream;
            this.defaultItem = defaultItem;
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
                        new SingleSubscription<>(defaultItem, downstream));
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
