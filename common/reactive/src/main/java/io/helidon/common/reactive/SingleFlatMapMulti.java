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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Maps the upstream item into a Publisher and relays its items to the downstream.
 * @param <T> the upstream element type
 * @param <R> the downstream element type
 */
final class SingleFlatMapMulti<T, R> implements Multi<R> {

    private final Single<T> source;

    private final Function<? super T, ? extends Flow.Publisher<? extends R>> mapper;

    SingleFlatMapMulti(Single<T> source,
                       Function<? super T, ? extends Flow.Publisher<? extends R>> mapper) {
        this.source = source;
        this.mapper = mapper;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super R> subscriber) {
        source.subscribe(new FlatMapSubscriber<>(subscriber, mapper));
    }

    static final class FlatMapSubscriber<T, R>
            extends AtomicLong
            implements Flow.Subscriber<T>, Flow.Subscription {

        private final Flow.Subscriber<? super R> downstream;

        private final Function<? super T, ? extends Flow.Publisher<? extends R>> mapper;

        private final InnerSubscriber<R> inner;

        private Flow.Subscription upstream;

        FlatMapSubscriber(Flow.Subscriber<? super R> downstream,
                          Function<? super T, ? extends Flow.Publisher<? extends R>> mapper) {
            this.downstream = downstream;
            this.mapper = mapper;
            this.inner = new InnerSubscriber<R>(downstream, this);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.validate(upstream, subscription);
            upstream = subscription;
            downstream.onSubscribe(this);
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T item) {
            Flow.Subscription s = upstream;
            if (s != SubscriptionHelper.CANCELED) {
                Flow.Publisher<? extends R> p;

                try {
                    p = Objects.requireNonNull(mapper.apply(item),
                            "The mapper returned a null Publisher");
                } catch (Throwable ex) {
                    onError(ex);
                    return;
                }

                upstream = SubscriptionHelper.CANCELED;
                p.subscribe(inner);
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
            SubscriptionHelper.deferredRequest(inner, this, n);
        }

        @Override
        public void cancel() {
            upstream.cancel();
            upstream = SubscriptionHelper.CANCELED;
            SubscriptionHelper.cancel(inner);
        }

        // Workaround for SpotBugs, Flow components should never be serialized
        private void writeObject(ObjectOutputStream stream)
                throws IOException {
            stream.defaultWriteObject();
        }

        // Workaround for SpotBugs, Flow components should never be serialized
        private void readObject(ObjectInputStream stream)
                throws IOException, ClassNotFoundException {
            stream.defaultReadObject();
        }

        static final class InnerSubscriber<R>
                extends AtomicReference<Flow.Subscription>
                implements Flow.Subscriber<R> {

            private final Flow.Subscriber<? super R> downstream;

            private final AtomicLong requested;

            InnerSubscriber(Flow.Subscriber<? super R> downstream, AtomicLong requested) {
                this.downstream = downstream;
                this.requested = requested;
            }


            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                SubscriptionHelper.deferredSetOnce(this, requested, subscription);
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

            // Workaround for SpotBugs, Flow components should never be serialized
            private void writeObject(ObjectOutputStream stream)
                    throws IOException {
                stream.defaultWriteObject();
            }

            // Workaround for SpotBugs, Flow components should never be serialized
            private void readObject(ObjectInputStream stream)
                    throws IOException, ClassNotFoundException {
                stream.defaultReadObject();
            }
        }
    }
}
