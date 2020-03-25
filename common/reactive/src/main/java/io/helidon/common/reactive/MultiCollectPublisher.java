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
 */
package io.helidon.common.reactive;

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Collects up upstream items with the help of a collection supplier
 * and a collection+item combiner.
 * @param <T> the element type of the upstream
 * @param <U> the collection type
 */
final class MultiCollectPublisher<T, U> implements Single<U> {

    private final Multi<T> source;

    private final Supplier<? extends U> collectionSupplier;

    private final BiConsumer<U, T> accumulator;

    MultiCollectPublisher(Multi<T> source, Supplier<? extends U> collectionSupplier, BiConsumer<U, T> combiner) {
        this.source = source;
        this.collectionSupplier = collectionSupplier;
        this.accumulator = combiner;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super U> subscriber) {
        U collection;
        try {
            collection = Objects.requireNonNull(collectionSupplier.get(),
                    "The collectionSupplier returned a null value");
        } catch (Throwable ex) {
            subscriber.onSubscribe(EmptySubscription.INSTANCE);
            subscriber.onError(ex);
            return;
        }

        source.subscribe(new CollectSubscriber<>(subscriber, collection, accumulator));
    }

    static final class CollectSubscriber<T, U> extends DeferredScalarSubscription<U> implements Flow.Subscriber<T> {

        private U collection;

        private final BiConsumer<U, T> accumulator;

        private Flow.Subscription upstream;

        CollectSubscriber(Flow.Subscriber<? super U> downstream, U collection, BiConsumer<U, T> accumulator) {
            super(downstream);
            this.collection = collection;
            this.accumulator = accumulator;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.validate(upstream, subscription);
            this.upstream = subscription;
            downstream().onSubscribe(this);
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T item) {
            Flow.Subscription s = upstream;
            U c = collection;
            if (s != SubscriptionHelper.CANCELED && c != null) {
                try {
                    accumulator.accept(c, item);
                } catch (Throwable ex) {
                    super.cancel();
                    s.cancel();
                    onError(ex);
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            Flow.Subscription s = upstream;
            if (s != SubscriptionHelper.CANCELED) {
                collection = null;
                upstream = SubscriptionHelper.CANCELED;
                downstream().onError(throwable);
            }
        }

        @Override
        public void onComplete() {
            Flow.Subscription s = upstream;
            if (s != SubscriptionHelper.CANCELED) {
                U c = collection;
                collection = null;
                upstream = SubscriptionHelper.CANCELED;
                if (c != null) {
                    complete(c);
                }
            }
        }

        @Override
        public void cancel() {
            collection = null;
            super.cancel();
            upstream.cancel();
            upstream = SubscriptionHelper.CANCELED;
        }
    }
}
