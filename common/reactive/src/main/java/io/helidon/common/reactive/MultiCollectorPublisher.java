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
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * Collects up upstream items with the help of a the callbacks of
 * a {@link java.util.stream.Collector}.
 * <p>
 *     Unlike {@link MultiCollectPublisher}, the initial accumulator
 *     value is retrieved when the upstream signals onSubscribe so
 *     that the operator behaves as Microprofile Reactive Streams
 *     expects it to.
 * </p>
 * @param <T> the element type of the upstream
 * @param <A> the collection type
 * @param <R> the result type
 */
final class MultiCollectorPublisher<T, A, R> extends CompletionSingle<R> {

    private final Multi<T> source;

    private final Collector<T, A, R> collector;

    MultiCollectorPublisher(Multi<T> source, Collector<T, A, R> collector) {
        this.source = source;
        this.collector = collector;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super R> subscriber) {
        Supplier<A> collectionSupplier;
        BiConsumer<A, T> accumulator;
        Function<A, R> finisher;
        try {
            collectionSupplier = Objects.requireNonNull(collector.supplier(),
                    "The collector.supplier returned a null value");
            accumulator = Objects.requireNonNull(collector.accumulator(),
                    "The collector.accumulator returned a null value");
            finisher = Objects.requireNonNull(collector.finisher(),
                    "The collector.finisher returned a null value");
        } catch (Throwable ex) {
            subscriber.onSubscribe(EmptySubscription.INSTANCE);
            subscriber.onError(ex);
            return;
        }

        source.subscribe(new CollectSubscriber<>(subscriber, collectionSupplier, accumulator, finisher));
    }

    static final class CollectSubscriber<T, A, R> extends DeferredScalarSubscription<R> implements Flow.Subscriber<T> {

        private A collection;

        private final Supplier<A> collectionSupplier;

        private final BiConsumer<A, T> accumulator;

        private final Function<A, R> finisher;

        private Flow.Subscription upstream;

        CollectSubscriber(Flow.Subscriber<? super R> downstream,
                          Supplier<A> collectionSupplier, BiConsumer<A, T> accumulator,
                          Function<A, R> finisher) {
            super(downstream);
            this.collectionSupplier = collectionSupplier;
            this.accumulator = accumulator;
            this.finisher = finisher;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.validate(upstream, subscription);
            this.upstream = subscription;
            try {
                collection = collectionSupplier.get();
            } catch (Throwable ex) {
                subscription.cancel();
                downstream().onSubscribe(EmptySubscription.INSTANCE);
                downstream().onError(ex);
                return;
            }
            downstream().onSubscribe(this);
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T item) {
            Flow.Subscription s = upstream;
            A c = collection;
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
                A c = collection;
                collection = null;
                upstream = SubscriptionHelper.CANCELED;
                if (c != null) {
                    R result;
                    try {
                        result = Objects.requireNonNull(finisher.apply(c),
                                "The finisher returned a null value");
                    } catch (Throwable ex) {
                        downstream().onError(ex);
                        return;
                    }

                    complete(result);
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
