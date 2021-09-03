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

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;

/**
 * Map each upstream item into an Iterable and stream their values.
 * @param <T> the upstream item type
 * @param <R> the output item type
 */
final class MultiFlatMapIterable<T, R> implements Multi<R> {

    private final Multi<T> source;

    private final Function<? super T, ? extends Iterable<? extends R>> mapper;

    private final int prefetch;

    MultiFlatMapIterable(Multi<T> source,
                         Function<? super T, ? extends Iterable<? extends R>> mapper,
                         int prefetch) {
        this.source = source;
        this.mapper = mapper;
        this.prefetch = prefetch;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super R> subscriber) {
        source.subscribe(new FlatMapIterableSubscriber<>(subscriber, mapper, prefetch));
    }

    static final class FlatMapIterableSubscriber<T, R>
            extends AtomicInteger
            implements Flow.Subscriber<T>, Flow.Subscription {

        private final Flow.Subscriber<? super R> downstream;

        private final Function<? super T, ? extends Iterable<? extends R>> mapper;

        private final int prefetch;

        private final AtomicLong requested;

        private final AtomicReferenceArray<T> queue;

        private final AtomicLong producerIndex;

        private final AtomicLong consumerIndex;

        private Flow.Subscription upstream;

        private long emitted;

        private volatile boolean upstreamDone;
        private Throwable error;

        private volatile boolean canceled;

        private Iterator<? extends R> currentIterator;

        private int upstreamConsumed;

        FlatMapIterableSubscriber(Flow.Subscriber<? super R> downstream,
                                  Function<? super T, ? extends Iterable<? extends R>> mapper,
                                  int prefetch) {
            this.downstream = downstream;
            this.mapper = mapper;
            this.prefetch = prefetch;
            this.requested = new AtomicLong();
            this.queue = new AtomicReferenceArray<>(roundToPowerOfTwo(prefetch));
            this.producerIndex = new AtomicLong();
            this.consumerIndex = new AtomicLong();
        }

        static int roundToPowerOfTwo(final int value) {
            return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.validate(upstream, subscription);
            upstream = subscription;
            downstream.onSubscribe(this);
            subscription.request(prefetch);
        }

        @Override
        public void onNext(T item) {
            offer(item);
            drain();
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
            upstreamDone = true;
            upstream = SubscriptionHelper.CANCELED;
            drain();
        }

        @Override
        public void onComplete() {
            upstreamDone = true;
            upstream = SubscriptionHelper.CANCELED;
            drain();
        }

        @Override
        public void request(long n) {
            if (n <= 0L) {
                onError(new IllegalArgumentException("Rule §3.9 violated: non-positive requests are forbidden!"));
            } else {
                SubscriptionHelper.addRequest(requested, n);
                drain();
            }
        }

        @Override
        public void cancel() {
            canceled = true;
            upstream.cancel();
            drain();
        }

        void drain() {
            if (getAndIncrement() != 0) {
                return;
            }

            Iterator<? extends R> iterator = currentIterator;
            Flow.Subscriber<? super R> downstream = this.downstream;
            long e = emitted;
            int limit = prefetch - (prefetch >> 2);

            int missed = 1;
            outer:
            for (;;) {

                if (canceled) {
                    iterator = null;
                    currentIterator = null;
                    clear();
                } else {
                    if (upstreamDone) {
                        Throwable ex = error;
                        if (ex != null) {
                            canceled = true;
                            downstream.onError(ex);
                            continue;
                        }
                    }
                    if (iterator == null) {
                        boolean d = upstreamDone;
                        T item = poll();
                        boolean empty = item == null;

                        if (d && empty) {
                            canceled = true;
                            downstream.onComplete();
                            return;
                        }

                        if (!empty) {

                            int c = upstreamConsumed + 1;
                            if (c == limit) {
                                upstreamConsumed = 0;
                                upstream.request(limit);
                            } else {
                                upstreamConsumed = c;
                            }

                            boolean hasNext;
                            try {
                                iterator = Objects.requireNonNull(
                                        mapper.apply(item).iterator(),
                                        "The Iterable returned a null iterator"
                                );

                                hasNext = iterator.hasNext();
                            } catch (Throwable ex) {
                                canceled = true;
                                upstream.cancel();
                                downstream.onError(ex);
                                continue;
                            }

                            if (!hasNext) {
                                iterator = null;
                                continue;
                            }
                            currentIterator = iterator;
                        }
                    }

                    if (iterator != null) {
                        long r = requested.get();

                        while (e != r) {

                            if (canceled) {
                                continue outer;
                            }

                            R result;

                            try {
                                result = Objects.requireNonNull(iterator.next(),
                                        "The iterator returned a null item");
                            } catch (Throwable ex) {
                                canceled = true;
                                upstream.cancel();
                                downstream.onError(ex);
                                continue outer;
                            }

                            if (canceled) {
                                continue outer;
                            }

                            downstream.onNext(result);
                            e++;

                            if (canceled) {
                                continue outer;
                            }

                            boolean hasNext;
                            try {
                                hasNext = iterator.hasNext();
                            } catch (Throwable ex) {
                                canceled = true;
                                upstream.cancel();
                                downstream.onError(ex);
                                continue outer;
                            }

                            if (canceled) {
                                continue outer;
                            }

                            if (!hasNext) {
                                iterator = null;
                                currentIterator = null;
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
            }
        }

        void offer(T item) {
            AtomicReferenceArray<T> queue = this.queue;
            AtomicLong producerIndex = this.producerIndex;

            long pi = producerIndex.get();
            int mask = queue.length() - 1;
            int offset = (int) pi & mask;

            queue.lazySet(offset, item);
            producerIndex.lazySet(pi + 1);
        }

        T poll() {
            AtomicReferenceArray<T> queue = this.queue;
            AtomicLong consumerIndex = this.consumerIndex;

            long ci = consumerIndex.get();
            int mask = queue.length() - 1;
            int offset = (int) ci & mask;

            T item = queue.get(offset);
            if (item == null) {
                return null;
            }
            queue.lazySet(offset, null);
            consumerIndex.lazySet(ci + 1);
            return item;
        }

        boolean isEmpty() {
            AtomicLong producerIndex = this.producerIndex;
            AtomicLong consumerIndex = this.consumerIndex;

            return producerIndex.get() == consumerIndex.get();
        }

        void clear() {
            for (;;) {
                if (poll() == null) {
                    break;
                }
            }
        }
    }
}
