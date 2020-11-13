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

import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Signal items and terminal signals of the upstream on the given executor.
 * @param <T> the element type of the sequence
 */
final class MultiObserveOn<T> implements Multi<T> {

    private final Multi<T> source;

    private final Executor executor;

    private final int bufferSize;

    private final boolean delayError;

    MultiObserveOn(Multi<T> source, Executor executor, int bufferSize, boolean delayError) {
        this.source = source;
        this.executor = executor;
        this.bufferSize = bufferSize;
        this.delayError = delayError;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        source.subscribe(new ObserveOnSubscriber<>(subscriber, executor, bufferSize, delayError));
    }

    static int roundToPowerOfTwo(final int value) {
        return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
    }

    static final class ObserveOnSubscriber<T> extends AtomicInteger
    implements Flow.Subscriber<T>, Flow.Subscription, Runnable {

        private final Flow.Subscriber<? super T> downstream;

        private final Executor executor;

        private final int bufferSize;

        private final boolean delayError;

        private final AtomicLong requested;

        private final AtomicReferenceArray<T> queue;

        private final AtomicLong producerIndex;

        private final AtomicLong consumerIndex;

        private Flow.Subscription upstream;

        private Throwable error;
        private volatile boolean done;

        private volatile boolean canceled;

        private long emitted;
        private int consumed;

        ObserveOnSubscriber(Flow.Subscriber<? super T> downstream, Executor executor,
                            int bufferSize, boolean delayError) {
            this.downstream = downstream;
            this.executor = executor;
            this.bufferSize = bufferSize;
            this.delayError = delayError;
            this.requested = new AtomicLong();
            this.queue = new AtomicReferenceArray<>(roundToPowerOfTwo(bufferSize));
            this.producerIndex = new AtomicLong();
            this.consumerIndex = new AtomicLong();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.validate(upstream, subscription);
            upstream = subscription;
            downstream.onSubscribe(this);
            subscription.request(bufferSize);
        }

        @Override
        public void onNext(T item) {
            offer(item);
            schedule();
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
            this.done = true;
            schedule();
        }

        @Override
        public void onComplete() {
            this.done = true;
            schedule();
        }

        void schedule() {
            if (getAndIncrement() == 0) {
                executor.execute(this);
            }
        }

        @Override
        public void request(long n) {
            if (n <= 0L) {
                onError(new IllegalArgumentException("Rule §3.9 violated: non-positive requests are forbidden"));
            } else {
                SubscriptionHelper.addRequest(requested, n);
                schedule();
            }
        }

        @Override
        public void cancel() {
            canceled = true;
            upstream.cancel();
            schedule();
        }

        @Override
        public void run() {

            int missed = 1;
            long r = requested.get();
            Flow.Subscriber<? super T> downstream = this.downstream;
            int consumed = this.consumed;
            long emitted = this.emitted;
            int limit = bufferSize - (bufferSize >> 2);

            for (;;) {
                if (canceled) {
                    clear();
                } else {
                    boolean d = done;
                    if (d && !delayError) {
                        Throwable ex = error;
                        if (ex != null) {
                            canceled = true;
                            downstream.onError(ex);
                            continue;
                        }
                    }

                    boolean empty;
                    if (r != emitted) {

                        T item = poll();

                        if (item != null) {

                            downstream.onNext(item);
                            emitted++;
                            if (++consumed == limit) {
                                consumed = 0;
                                upstream.request(limit);
                            }
                            continue;
                        }
                        empty = true;
                    } else {
                        empty = isEmpty();
                    }

                    if (d && empty) {
                        canceled = true;
                        Throwable ex = error;
                        if (ex != null) {
                            downstream.onError(ex);
                        } else {
                            downstream.onComplete();
                        }
                        continue;
                    }
                }

                this.emitted = emitted;
                this.consumed = consumed;
                missed = addAndGet(-missed);
                if (missed == 0) {
                    break;
                }
                r = requested.get();
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
