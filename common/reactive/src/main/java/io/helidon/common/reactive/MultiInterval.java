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
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Signal an ever increasing long value periodically.
 */
final class MultiInterval implements Multi<Long> {

    private final long initialDelay;

    private final long period;

    private final TimeUnit unit;

    private final ScheduledExecutorService executor;

    MultiInterval(long initialDelay, long period, TimeUnit unit, ScheduledExecutorService executor) {
        this.initialDelay = initialDelay;
        this.period = period;
        this.unit = unit;
        this.executor = executor;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super Long> subscriber) {
        IntervalSubscription subscription = new IntervalSubscription(subscriber);
        subscriber.onSubscribe(subscription);

        subscription.setFuture(executor.scheduleAtFixedRate(subscription, initialDelay, period, unit));
    }

    static final class IntervalSubscription extends AtomicInteger implements Flow.Subscription, Runnable {

        private final Flow.Subscriber<? super Long> downstream;

        private final AtomicLong requested;

        private final AtomicReference<Future<?>> future;

        private volatile long available;

        private volatile int canceled;

        private long emitted;

        private static final int NORMAL_CANCEL = 1;
        private static final int BAD_REQUEST = 2;

        IntervalSubscription(Flow.Subscriber<? super Long> downstream) {
            this.downstream = downstream;
            this.requested = new AtomicLong();
            this.future = new AtomicReference<>();
        }

        @Override
        public void run() {
            long next = available + 1;
            available = next;
            drain();
        }

        @Override
        public void request(long n) {
            if (n <= 0L) {
                canceled = BAD_REQUEST;
                n = 1L;
            }
            SubscriptionHelper.addRequest(requested, n);
            drain();
        }

        @Override
        public void cancel() {
            canceled = NORMAL_CANCEL;
            TerminatedFuture.cancel(future);
        }

        void setFuture(Future<?> f) {
            TerminatedFuture.setFuture(future, f);
        }

        void drain() {
            if (getAndIncrement() != 0) {
                return;
            }

            do {
                int c = canceled;
                if (c != 0) {
                    if (c == BAD_REQUEST) {
                        downstream.onError(new IllegalArgumentException(
                                "Rule §3.9 violated: non-positive requests are forbidden"));
                    }
                    return;
                }

                long avail = available;
                long req = requested.get();
                long emit = emitted;

                if (emit != req && emit != avail) {
                    downstream.onNext(emit);
                    emitted = emit + 1;
                }

            } while (decrementAndGet() != 0);
        }
    }
}
