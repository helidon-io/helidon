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

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Maps the success value of a {@link Single} into an {@link Iterable} and
 * emits its items to the downstream.
 * @param <T> the source value type
 * @param <R> the result value type
 */
final class SingleFlatMapIterable<T, R> implements Multi<R> {

    private final Single<T> source;

    private final Function<? super T, ? extends Iterable<? extends R>> mapper;

    SingleFlatMapIterable(Single<T> source, Function<? super T, ? extends Iterable<? extends R>> mapper) {
        this.source = source;
        this.mapper = mapper;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super R> subscriber) {
        source.subscribe(new FlatMapIterableSubscriber<>(subscriber, mapper));
    }

    static final class FlatMapIterableSubscriber<T, R>
            extends AtomicInteger
            implements Flow.Subscriber<T>, Flow.Subscription {

        private final Flow.Subscriber<? super R> downstream;

        private final Function<? super T, ? extends Iterable<? extends R>> mapper;

        private final AtomicLong requested;

        private Flow.Subscription upstream;

        private long emitted;

        private volatile int canceled;

        private volatile Iterator<? extends R> iterator;

        private static final int CANCELED = 1;

        private static final int BAD_REQUEST = 2;

        FlatMapIterableSubscriber(Flow.Subscriber<? super R> downstream,
                                  Function<? super T, ? extends Iterable<? extends R>> mapper) {
            this.downstream = downstream;
            this.mapper = mapper;
            this.requested = new AtomicLong();
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
                upstream = SubscriptionHelper.CANCELED;

                Iterator<? extends R> iterator;
                try {
                    Iterable<? extends R> iterable = Objects.requireNonNull(mapper.apply(item),
                            "The mapper returned a null Iterable");

                    iterator = iterable.iterator();

                    if (!iterator.hasNext()) {
                        iterator = null;
                    }
                } catch (Throwable ex) {
                    downstream.onError(ex);
                    return;
                }

                if (iterator != null) {
                    this.iterator = iterator;
                    drain();
                } else {
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
            if (n <= 0L) {
                canceled = BAD_REQUEST;
                n = 1;
            }
            SubscriptionHelper.addRequest(requested, n);
            drain();
        }

        @Override
        public void cancel() {
            canceled = CANCELED;
            upstream.cancel();
            upstream = SubscriptionHelper.CANCELED;
            drain();
        }

        void drain() {
            if (getAndIncrement() != 0) {
                return;
            }

            int missed = 1;
            Iterator<? extends R> it = iterator;
            Flow.Subscriber<? super R> downstream = this.downstream;
            long e = emitted;

            outer:
            for (;;) {

                int c = canceled;
                if (c != 0) {
                    it = null;
                    iterator = null;
                    if (c == BAD_REQUEST) {
                        downstream.onError(new IllegalArgumentException(
                                "Rule §3.9 violated: non-positive requests are forbidden"));
                    }
                } else {
                    if (it != null) {

                        long r = requested.get();

                        while (r != e) {
                            R item;
                            try {
                                item = Objects.requireNonNull(it.next(),
                                        "The iterator returned a null item");
                            } catch (Throwable ex) {
                                canceled = CANCELED;
                                downstream.onError(ex);
                                continue outer;
                            }

                            if (canceled != 0) {
                                continue outer;
                            }

                            downstream.onNext(item);
                            e++;

                            if (canceled != 0) {
                                continue outer;
                            }

                            boolean hasNext;
                            try {
                                hasNext = it.hasNext();
                            } catch (Throwable ex) {
                                canceled = CANCELED;
                                downstream.onError(ex);
                                continue outer;
                            }

                            if (!hasNext) {
                                canceled = CANCELED;
                                downstream.onComplete();
                                continue outer;
                            }

                            if (canceled != 0) {
                                continue outer;
                            }
                        }
                    }
                }

                emitted = e;
                missed = addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
                if (it == null) {
                    it = iterator;
                }
            }
        }
    }
}
