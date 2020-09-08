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

import java.util.concurrent.Callable;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fall back to an alternate Single if the main doesn't signal an item
 * or completes within a specified time window.
 * @param <T> the element type
 */
final class SingleTimeout<T> extends CompletionSingle<T> {

    private final Single<T> source;

    private final long timeout;

    private final TimeUnit unit;

    private final ScheduledExecutorService executor;

    private final Single<T> fallback;

    SingleTimeout(Single<T> source, long timeout, TimeUnit unit,
                  ScheduledExecutorService executor, Single<T> fallback) {
        this.source = source;
        this.timeout = timeout;
        this.unit = unit;
        this.executor = executor;
        this.fallback = fallback;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        TimeoutSubscriber<T> parent = new TimeoutSubscriber<>(subscriber, fallback);
        subscriber.onSubscribe(parent);

        parent.setFuture(executor.schedule(parent, timeout, unit));

        source.subscribe(parent);
    }

    final class TimeoutSubscriber<T> extends DeferredScalarSubscription<T>
    implements Flow.Subscriber<T>, Callable<Void> {

        private final Single<T> fallback;

        private final AtomicBoolean once;

        private final AtomicReference<Future<?>> future;

        private final AtomicReference<Flow.Subscription> upstream;

        private final FallbackSubscriber<T> fallbackSubscriber;

        TimeoutSubscriber(Flow.Subscriber<? super T> downstream, Single<T> fallback) {
            super(downstream);
            this.fallback = fallback;
            this.once = new AtomicBoolean();
            this.future = new AtomicReference<>();
            this.upstream = new AtomicReference<>();
            this.fallbackSubscriber = new FallbackSubscriber<>(this);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (SubscriptionHelper.setOnce(upstream, subscription)) {
                subscription.request(Long.MAX_VALUE);
            }
        }

        @Override
        public void onNext(T item) {
            if (once.compareAndSet(false, true)) {
                TerminatedFuture.cancel(future);
                complete(item);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (once.compareAndSet(false, true)) {
                TerminatedFuture.cancel(future);
                error(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (once.compareAndSet(false, true)) {
                TerminatedFuture.cancel(future);
                complete();
            }
        }

        @Override
        public void cancel() {
            super.cancel();
            TerminatedFuture.cancel(future);
            SubscriptionHelper.cancel(upstream);
            SubscriptionHelper.cancel(fallbackSubscriber);
        }

        @Override
        public Void call() {
            if (once.compareAndSet(false, true)) {
                future.lazySet(TerminatedFuture.FINISHED);
                SubscriptionHelper.cancel(upstream);
                source.cancel();
                if (fallback == null) {
                    error(new TimeoutException());
                } else {
                    fallback.subscribe(fallbackSubscriber);
                }
            }
            return null;
        }

        public void setFuture(Future<?> f) {
            TerminatedFuture.setFuture(future, f);
        }

        final class FallbackSubscriber<T>
        extends AtomicReference<Flow.Subscription>
        implements Flow.Subscriber<T> {

            private final TimeoutSubscriber<T> parent;

            FallbackSubscriber(TimeoutSubscriber<T> parent) {
                this.parent = parent;
            }

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                if (SubscriptionHelper.setOnce(this, subscription)) {
                    subscription.request(Long.MAX_VALUE);
                }
            }

            @Override
            public void onNext(T item) {
                parent.complete(item);
            }

            @Override
            public void onError(Throwable throwable) {
                parent.error(throwable);
            }

            @Override
            public void onComplete() {
                parent.complete();
            }
        }
    }
}
