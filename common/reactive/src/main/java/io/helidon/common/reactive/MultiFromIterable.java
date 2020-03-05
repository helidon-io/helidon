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

import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publisher from iterable, implemented as trampoline stack-less recursion.
 *
 * @param <T> item type
 */
final class MultiFromIterable<T> implements Multi<T> {

    private final Iterable<T> iterable;

    MultiFromIterable(Iterable<T> iterable) {
        Objects.requireNonNull(iterable, "iterable is null");
        this.iterable = iterable;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");

        Iterator<T> iterator;
        boolean hasFirst;
        try {
            iterator = iterable.iterator();
            hasFirst = iterator.hasNext();
        } catch (Throwable ex) {
            subscriber.onSubscribe(EmptySubscription.INSTANCE);
            subscriber.onError(ex);
            return;
        }

        if (!hasFirst) {
            subscriber.onSubscribe(EmptySubscription.INSTANCE);
            subscriber.onComplete();
            return;
        }

        subscriber.onSubscribe(new IteratorSubscription<>(subscriber, iterator));
    }

    static final class IteratorSubscription<T> extends AtomicLong implements Flow.Subscription {

        private final Flow.Subscriber<? super T> downstream;

        private Iterator<T> iterator;

        private volatile int canceled;

        static final int NORMAL_CANCEL = 1;
        static final int BAD_REQUEST = 2;

        IteratorSubscription(Flow.Subscriber<? super T> downstream, Iterator<T> iterator) {
            this.downstream = downstream;
            this.iterator = iterator;
        }

        @Override
        public void request(long n) {
            if (n <= 0L) {
                canceled = BAD_REQUEST;
                n = 1; // for cleanup
            }

            // TODO replace this with SubscriptionHelper.addRequest
            if (addRequest(n) != 0L) {
                return;
            }

            long emitted = 0L;

            for (;;) {
                while (emitted != n) {
                    int isCanceled = canceled;
                    if (isCanceled != 0) {
                        iterator = null;
                        if (isCanceled == BAD_REQUEST) {
                            downstream.onError(new IllegalArgumentException(
                                    "Rule §3.9 violated: non-positive request amount is forbidded"));
                        }
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
                        return;
                    }

                    if (canceled != 0) {
                        continue;
                    }

                    if (!hasNext) {
                        iterator = null;
                        downstream.onComplete();
                        return;
                    }

                    emitted++;
                }

                n = get();
                if (n == emitted) {
                    // TODO replace this with SubscriptionHelper.produced
                    n = produced(emitted);
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

        private long addRequest(long n) {
            for (;;) {
                long current = get();
                if (current == Long.MAX_VALUE) {
                    return Long.MAX_VALUE;
                }
                long update = current + n;
                if (update < 0L) {
                    update = Long.MAX_VALUE;
                }
                if (compareAndSet(current, update)) {
                    return current;
                }
            }
        }

        private long produced(long n) {
            for (;;) {
                long current = get();
                if (current == Long.MAX_VALUE) {
                    return Long.MAX_VALUE;
                }
                long update = current - n;
                if (update < 0L) {
                    throw new IllegalStateException("More produced than requested!");
                }
                if (compareAndSet(current, update)) {
                    return update;
                }
            }
        }
    }
}
