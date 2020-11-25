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
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * Publisher from a {@link Stream}, implemented as trampoline stack-less recursion.
 *
 * @param <T> item type
 */
final class MultiFromStream<T> implements Multi<T> {

    private final Stream<T> stream;

    MultiFromStream(Stream<T> stream) {
        Objects.requireNonNull(stream, "stream is null");
        this.stream = stream;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");

        Iterator<T> iterator;
        boolean hasFirst;
        try {
            iterator = stream.iterator();
            hasFirst = iterator.hasNext();
        } catch (Throwable ex) {
            subscriber.onSubscribe(EmptySubscription.INSTANCE);
            try {
                stream.close();
            } catch (Throwable exc) {
                if (exc != ex) {
                    ex.addSuppressed(exc);
                }
            }
            subscriber.onError(ex);
            return;
        }

        if (!hasFirst) {
            subscriber.onSubscribe(EmptySubscription.INSTANCE);
            try {
                stream.close();
            } catch (Throwable ex) {
                subscriber.onError(ex);
                return;
            }
            subscriber.onComplete();
            return;
        }

        subscriber.onSubscribe(new IteratorSubscription<>(subscriber, iterator, stream));
    }

    static final class IteratorSubscription<T> extends AtomicLong implements Flow.Subscription {

        private final Flow.Subscriber<? super T> downstream;

        private Iterator<T> iterator;

        private AutoCloseable close;

        private volatile int canceled;

        static final int NORMAL_CANCEL = 1;
        static final int BAD_REQUEST = 2;

        IteratorSubscription(Flow.Subscriber<? super T> downstream, Iterator<T> iterator, AutoCloseable close) {
            this.downstream = downstream;
            this.iterator = iterator;
            this.close = close;
        }

        @Override
        public void request(long n) {
            if (n <= 0L) {
                canceled = BAD_REQUEST;
                n = 1; // for cleanup
            }

            if (SubscriptionHelper.addRequest(this, n) != 0L) {
                return;
            }

            long emitted = 0L;
            Flow.Subscriber<? super T> downstream = this.downstream;

            for (;;) {
                while (emitted != n) {
                    int isCanceled = canceled;
                    if (isCanceled != 0) {
                        iterator = null;
                        if (isCanceled == BAD_REQUEST) {
                            downstream.onError(new IllegalArgumentException(
                                    "Rule §3.9 violated: non-positive request amount is forbidden"));
                        }
                        close();
                        return;
                    }

                    T value;

                    try {
                        value = Objects.requireNonNull(iterator.next(),
                                "The iterator returned a null value");
                    } catch (Throwable ex) {
                        iterator = null;
                        canceled = NORMAL_CANCEL;
                        downstream.onError(ex);
                        close();
                        return;
                    }

                    if (canceled != 0) {
                        continue;
                    }

                    downstream.onNext(value);

                    if (canceled != 0) {
                        continue;
                    }

                    boolean hasNext;

                    try {
                        hasNext = iterator.hasNext();
                    } catch (Throwable ex) {
                        iterator = null;
                        canceled = NORMAL_CANCEL;
                        downstream.onError(ex);
                        close();
                        return;
                    }

                    if (canceled != 0) {
                        continue;
                    }

                    if (!hasNext) {
                        iterator = null;
                        downstream.onComplete();
                        close();
                        return;
                    }

                    emitted++;
                }

                n = get();
                if (n == emitted) {
                    n = SubscriptionHelper.produced(this, emitted);
                    if (n == 0L) {
                        return;
                    }
                    emitted = 0L;
                }
            }
        }

        @Override
        public void cancel() {
            canceled = NORMAL_CANCEL;
            request(1); // for cleanup
        }

        void close() {
            AutoCloseable c = close;
            close = null;
            if (c != null) {
                try {
                    c.close();
                } catch (Throwable ex) {
                    Thread t = Thread.currentThread();
                    t.getUncaughtExceptionHandler().uncaughtException(t, ex);
                }
            }
        }
    }
}
