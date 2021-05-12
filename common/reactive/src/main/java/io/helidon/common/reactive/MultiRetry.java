/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/**
 * Retry a failing source based on some condition or timing.
 * @param <T> the element type of the source
 * @param <U> the signal type of the publisher indicating when to retry
 */
final class MultiRetry<T, U> implements Multi<T> {

    private final Multi<T> source;

    private final BiFunction<? super Throwable, ? super Long, ? extends Flow.Publisher<U>> whenFunction;

    /** Indicate an immediate re-subscription. */
    private static final Multi<Object> NOW = Multi.singleton(1);

    MultiRetry(Multi<T> source,
               BiFunction<? super Throwable, ? super Long, ? extends Flow.Publisher<U>> whenFunction) {
        this.source = source;
        this.whenFunction = whenFunction;
    }

    MultiRetry(Multi<T> source, long count) {
        this(source, withCount(count));
    }

    MultiRetry(Multi<T> source, BiPredicate<? super Throwable, ? super Long> predicate) {
        this(source, withPredicate(predicate));
    }

    @SuppressWarnings("unchecked")
    static <U> BiFunction<? super Throwable, ? super Long, ? extends Flow.Publisher<U>> withCount(long count) {
        return (e, n) -> n < count ? (Flow.Publisher<U>) NOW : Single.error(e);
    }

    @SuppressWarnings("unchecked")
    static <U> BiFunction<? super Throwable, ? super Long, ? extends Flow.Publisher<U>> withPredicate(
            BiPredicate<? super Throwable, ? super Long> predicate) {
        return (e, n) -> predicate.test(e, n) ? (Flow.Publisher<U>) NOW : Single.error(e);
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        RetrySubscriber<T, U> parent = new RetrySubscriber<>(subscriber, whenFunction, source);
        subscriber.onSubscribe(parent);
        parent.retry();
    }

    static final class RetrySubscriber<T, U> extends SubscriptionArbiter
    implements Flow.Subscriber<T> {

        private final Flow.Subscriber<? super T> downstream;

        private final AtomicInteger wip;

        private final BiFunction<? super Throwable, ? super Long, ? extends Flow.Publisher<U>> whenFunction;

        private final AtomicReference<Flow.Subscription> whenSubscription;

        private final Multi<T> source;

        private long count;

        private long produced;

        private boolean hasSubscriber;

        RetrySubscriber(Flow.Subscriber<? super T> downstream,
                        BiFunction<? super Throwable, ? super Long, ? extends Flow.Publisher<U>> whenFunction,
                        Multi<T> source) {
            this.downstream = downstream;
            this.whenFunction = whenFunction;
            this.source = source;
            this.whenSubscription = new AtomicReference<>();
            this.wip = new AtomicInteger();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (!hasSubscriber) {
                hasSubscriber = true;
                setSubscription(subscription);
            } else {
                subscription.cancel();
            }
        }

        @Override
        public void onNext(T item) {
            produced++;
            downstream.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            long p = produced;
            if (p != 0L) {
                produced = 0L;
                produced(p);
            }

            hasSubscriber = false;
            Flow.Publisher<U> when;
            try {
                when = Objects.requireNonNull(whenFunction.apply(throwable, count++),
                        "The whenFunction returned a null Flow.Publisher");
            } catch (Throwable ex) {
                if (ex != throwable) {
                    ex.addSuppressed(throwable);
                }
                error(ex);
                return;
            }

            if (when == Single.empty() || when == Multi.empty()) {
                complete();
            } else if (when == NOW) {
                retry();
            } else if (when instanceof SingleError) {
                error(((SingleError<?>) when).getError());
            } else if (when instanceof MultiError) {
                error(((MultiError<?>) when).getError());
            } else {
                when.subscribe(new WhenSubscriber(this));
            }
        }

        @Override
        public void onComplete() {
            downstream.onComplete();
        }

        @Override
        public void cancel() {
            super.cancel();
            SubscriptionHelper.cancel(whenSubscription);
        }

        @Override
        public void request(long n) {
            if (n <= 0L) {
                downstream.onError(new IllegalArgumentException(
                        "Rule §3.9 violated: non-positive requests are forbidden"));
            } else {
                super.request(n);
            }
        }

        void setWhenSubscription(Flow.Subscription s) {
            SubscriptionHelper.replace(whenSubscription, s);
        }

        void retry() {
            if (wip.getAndIncrement() != 0) {
                return;
            }
            do {
                if (!isCanceled()) {
                    source.subscribe(this);
                } else {
                    break;
                }
            } while (wip.decrementAndGet() != 0);
        }

        void complete() {
            downstream.onComplete();
        }

        void error(Throwable ex) {
            downstream.onError(ex);
        }

        static final class WhenSubscriber
                implements Flow.Subscriber<Object> {

            private final RetrySubscriber<?, ?> parent;

            private Flow.Subscription upstream;

            WhenSubscriber(RetrySubscriber<?, ?> parent) {
                this.parent = parent;
            }

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                SubscriptionHelper.validate(upstream, subscription);
                upstream = subscription;
                parent.setWhenSubscription(subscription);
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(Object item) {
                upstream.cancel();
                upstream = SubscriptionHelper.CANCELED;
                parent.setWhenSubscription(null);
                parent.retry();
            }

            @Override
            public void onError(Throwable throwable) {
                Flow.Subscription s = upstream;
                if (s != SubscriptionHelper.CANCELED) {
                    upstream = SubscriptionHelper.CANCELED;
                    parent.error(throwable);
                }
            }

            @Override
            public void onComplete() {
                Flow.Subscription s = upstream;
                if (s != SubscriptionHelper.CANCELED) {
                    upstream = SubscriptionHelper.CANCELED;
                    parent.complete();
                }
            }
        }
    }
}
