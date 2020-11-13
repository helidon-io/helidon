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

import java.util.concurrent.Callable;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Falls back to an alternate sequence if the main sequence doesn't produce
 * the next item in the given time window.
 * @param <T> the element type
 */
final class MultiTimeout<T> implements Multi<T> {

    private final Multi<T> source;

    private final long timeout;

    private final TimeUnit unit;

    private final ScheduledExecutorService executor;

    private final Flow.Publisher<T> fallback;

    MultiTimeout(Multi<T> source, long timeout, TimeUnit unit,
                 ScheduledExecutorService executor, Flow.Publisher<T> fallback) {
        this.source = source;
        this.timeout = timeout;
        this.unit = unit;
        this.executor = executor;
        this.fallback = fallback;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        TimeoutSubscriber<T> parent = new TimeoutSubscriber<>(subscriber, timeout, unit, executor, fallback);
        subscriber.onSubscribe(parent);
        parent.schedule(0L);
        source.subscribe(parent);
    }

    static final class TimeoutSubscriber<T> extends AtomicLong
    implements Flow.Subscriber<T>, Flow.Subscription {

        private final Flow.Subscriber<? super T> downstream;

        private final long timeout;

        private final TimeUnit unit;

        private final ScheduledExecutorService executor;

        private final Flow.Publisher<T> fallback;

        private final AtomicLong requested;

        private final AtomicReference<Future<?>> future;

        private final FallbackSubscriber<T> fallbackSubscriber;

        private long emitted;

        private final AtomicReference<Flow.Subscription> upstream;

        private final AtomicLong requestedInitial;

        TimeoutSubscriber(Flow.Subscriber<? super T> downstream, long timeout, TimeUnit unit,
                          ScheduledExecutorService executor, Flow.Publisher<T> fallback) {
            this.downstream = downstream;
            this.timeout = timeout;
            this.unit = unit;
            this.executor = executor;
            this.fallback = fallback;
            this.requested = new AtomicLong();
            this.future = new AtomicReference<>();
            this.fallbackSubscriber = new FallbackSubscriber<T>(downstream, requested);
            this.upstream = new AtomicReference<>();
            this.requestedInitial = new AtomicLong();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.deferredSetOnce(upstream, requestedInitial, subscription);
        }

        void schedule(long index) {
            TerminatedFuture.setFuture(future, executor.schedule(
                    new TimeoutTask(this, index), timeout, unit));
        }

        @Override
        public void onNext(T item) {
            long index = get();
            if (index != Long.MAX_VALUE && compareAndSet(index, index + 1)) {
                Future<?> task = future.getAndSet(null);
                if (task != null) {
                    task.cancel(true);
                }

                emitted++;
                downstream.onNext(item);

                schedule(index + 1);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (getAndSet(Long.MAX_VALUE) != Long.MAX_VALUE) {
                TerminatedFuture.cancel(future);
                downstream.onError(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (getAndSet(Long.MAX_VALUE) != Long.MAX_VALUE) {
                TerminatedFuture.cancel(future);
                downstream.onComplete();
            }
        }

        @Override
        public void request(long n) {
            if (n <= 0L) {
                onError(new IllegalArgumentException("Rule §3.9 violated: non-positive requests are forbidden"));
                return;
            }
            SubscriptionHelper.deferredRequest(upstream, requestedInitial, n);
            SubscriptionHelper.deferredRequest(fallbackSubscriber, requested, n);
        }

        @Override
        public void cancel() {
            SubscriptionHelper.cancel(upstream);
            TerminatedFuture.cancel(future);
            SubscriptionHelper.cancel(fallbackSubscriber);
        }

        void timeout(long index) {
            if (compareAndSet(index, Long.MAX_VALUE)) {
                future.lazySet(TerminatedFuture.FINISHED);
                SubscriptionHelper.cancel(upstream);

                if (fallback == null) {
                    downstream.onError(new TimeoutException());
                } else {
                    long p = emitted;
                    if (p != 0L) {
                        SubscriptionHelper.produced(requested, p);
                    }
                    fallback.subscribe(fallbackSubscriber);
                }
            }
        }

        static final class TimeoutTask implements Callable<Void> {

            private final TimeoutSubscriber<?> parent;

            private final long index;

            TimeoutTask(TimeoutSubscriber<?> parent, long index) {
                this.parent = parent;
                this.index = index;
            }

            @Override
            public Void call() throws Exception {
                parent.timeout(index);
                return null;
            }
        }

        static final class FallbackSubscriber<T>
        extends AtomicReference<Flow.Subscription>
        implements Flow.Subscriber<T> {

            private final Flow.Subscriber<? super T> downstream;

            private final AtomicLong requested;

            FallbackSubscriber(Flow.Subscriber<? super T> downstream, AtomicLong requested) {
                this.downstream = downstream;
                this.requested = requested;
            }

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                SubscriptionHelper.deferredSetOnce(this, requested, subscription);
            }

            @Override
            public void onNext(T item) {
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
        }
    }
}
