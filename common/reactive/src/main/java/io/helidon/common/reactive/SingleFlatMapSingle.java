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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Map the upstream value into a {@link Single} and emit its signal.
 * @param <T> the upstream value type
 * @param <R> the result value type
 */
final class SingleFlatMapSingle<T, R> extends CompletionSingle<R> {

    private final Single<T> source;

    private final Function<? super T, ? extends Single<? extends R>> mapper;

    SingleFlatMapSingle(Single<T> source, Function<? super T, ? extends Single<? extends R>> mapper) {
        this.source = source;
        this.mapper = mapper;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super R> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");
        source.subscribe(new FlatMapSubscriber<>(subscriber, mapper));
    }

    static final class FlatMapSubscriber<T, R>
            implements Flow.Subscriber<T>, Flow.Subscription {

        private final Flow.Subscriber<? super R> downstream;

        private final Function<? super T, ? extends Single<? extends R>> mapper;

        private final FlatMapNextSubscriber<R> nextSubscriber;

        private Flow.Subscription upstream;

        FlatMapSubscriber(Flow.Subscriber<? super R> downstream, Function<? super T, ? extends Single<? extends R>> mapper) {
            this.downstream = downstream;
            this.mapper = mapper;
            this.nextSubscriber = new FlatMapNextSubscriber<>(downstream);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            Objects.requireNonNull(subscription, "subscription is null");
            if (upstream != null) {
                subscription.cancel();
                throw new IllegalStateException("Subscription already set!");
            }
            upstream = subscription;
            downstream.onSubscribe(this);
        }

        @Override
        public void onNext(T item) {
            Single<? extends R> nextSource;

            try {
                nextSource = Objects.requireNonNull(mapper.apply(item),
                        "The mapper returned a null Single");
            } catch (Throwable ex) {
                cancel();
                downstream.onError(ex);
                return;
            }

            upstream = this;
            nextSource.subscribe(nextSubscriber);
        }

        @Override
        public void onError(Throwable throwable) {
            if (upstream != this) {
                upstream = this;
                downstream.onError(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (upstream != this) {
                upstream = this;
                downstream.onComplete();
            }
        }

        @Override
        public void request(long n) {
            Flow.Subscription s = upstream;
            if (s != this) {
                s.request(n);
            }
        }

        @Override
        public void cancel() {
            Flow.Subscription s = upstream;
            if (s != this) {
                upstream = this;
                s.cancel();
            }
            nextSubscriber.cancel();
        }

        static final class FlatMapNextSubscriber<R>
                extends AtomicReference<Flow.Subscription>
                implements Flow.Subscriber<R>, Flow.Subscription {

            private final Flow.Subscriber<? super R> downstream;

            FlatMapNextSubscriber(Flow.Subscriber<? super R> downstream) {
                this.downstream = downstream;
            }


            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                Objects.requireNonNull(subscription, "subscription is null");
                if (compareAndSet(null, subscription)) {
                    subscription.request(1L);
                } else if (get() != null) {
                    subscription.cancel();
                    if (get() != this) {
                        throw new IllegalStateException("Subscription already set");
                    }
                }
            }

            @Override
            public void onNext(R item) {
                downstream.onNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
                downstream.onError(throwable);
            }

            @Override
            public void onComplete() {
                downstream.onComplete();
            }

            @Override
            public void request(long n) {
                // deliberately no-op
            }

            @Override
            public void cancel() {
                Flow.Subscription s = getAndSet(this);
                if (s != null && s != this) {
                    s.cancel();
                }
            }
        }
    }
}
