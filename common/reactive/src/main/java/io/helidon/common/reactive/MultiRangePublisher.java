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

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;

final class MultiRangePublisher implements Multi<Integer> {

    private final int start;

    private final int end;

    MultiRangePublisher(int start, int end) {
        this.start = start;
        this.end = end;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super Integer> subscriber) {
        subscriber.onSubscribe(new RangeSubscription(subscriber, start, end));
    }

    static final class RangeSubscription extends AtomicLong implements Flow.Subscription {

        private final Flow.Subscriber<? super Integer> downstream;

        private int index;

        private final int end;

        private volatile int canceled;

        private static final int CANCELED = 1;
        private static final int BAD_REQUEST = 2;

        RangeSubscription(Flow.Subscriber<? super Integer> downstream, int index, int end) {
            this.downstream = downstream;
            this.index = index;
            this.end = end;
        }

        @Override
        public void request(long n) {
            if (n <= 0L) {
                canceled = BAD_REQUEST;
                n = 1L;
            }

            if (SubscriptionHelper.addRequest(this, n) != 0L) {
                return;
            }

            long e = 0L;
            int i = index;
            int end = this.end;
            Flow.Subscriber<? super Integer> downstream = this.downstream;

            for (;;) {

                while (i != end && e != n) {

                    int c = canceled;
                    if (c != 0) {
                        if (c == BAD_REQUEST) {
                            downstream.onError(new IllegalArgumentException(
                                    "Rule §3.9 violated: non-positive requests are forbidden."));
                        }
                        return;
                    }

                    downstream.onNext(i);

                    e++;
                    i++;
                }

                if (i == end) {
                    if (canceled == 0) {
                        downstream.onComplete();
                    }
                    return;
                }

                n = get();
                if (n == e) {
                    index = i;
                    n = SubscriptionHelper.produced(this, n);
                    if (n == 0L) {
                        break;
                    }
                    e = 0L;
                }
            }
        }

        @Override
        public void cancel() {
            canceled = CANCELED;
        }
    }
}
