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

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Emits the elements of a non-empty array to the downstream on demand.
 * @param <T> the element type of the array
 */
final class MultiFromArrayPublisher<T> implements Multi<T> {

    private final T[] items;

    MultiFromArrayPublisher(T[] items) {
        this.items = items;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        subscriber.onSubscribe(new ArraySubscription<>(subscriber, items));
    }

    static final class ArraySubscription<T> extends AtomicLong implements Flow.Subscription {

        private final Flow.Subscriber<? super T> downstream;

        private final T[] array;

        private int index;

        private volatile int canceled;

        static final int CANCEL = 1;
        static final int BAD_REQUEST = 2;

        ArraySubscription(Flow.Subscriber<? super T> downstream, T[] array) {
            this.downstream = downstream;
            this.array = array;
        }

        @Override
        public void request(long n) {
            if (n <= 0L) {
                canceled = BAD_REQUEST;
                n = 1;
            }
            if (SubscriptionHelper.addRequest(this, n) != 0L) {
                return;
            }

            long emitted = 0L;
            int i = index;
            T[] array = this.array;
            int length = array.length;
            outer:
            for (;;) {
                int c = canceled;
                if (c != 0) {
                    if (c == BAD_REQUEST) {
                        downstream.onError(new IllegalArgumentException(
                                "Rule §3.9 violated: non-positive requests are forbidden"));
                    }
                    return;
                } else {

                    for (; i != length && emitted != n; i++, emitted++) {
                        T item = array[i];
                        if (item == null) {
                            c = CANCEL;
                            downstream.onError(new NullPointerException(
                                    "Array element at index " + i + " is null"));
                            return;
                        }
                        downstream.onNext(item);
                        if (canceled != 0) {
                            continue outer;
                        }
                    }

                    if (i == length) {
                        downstream.onComplete();
                        return;
                    }

                    n = get();
                    if (n == emitted) {
                        index = i;
                        n = SubscriptionHelper.produced(this, n);
                        if (n == 0L) {
                            break;
                        }
                        emitted = 0L;
                    }
                }
            }
        }

        @Override
        public void cancel() {
            canceled = CANCEL;
        }
    }
}
