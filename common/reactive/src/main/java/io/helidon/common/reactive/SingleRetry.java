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
final class SingleRetry<T, U> extends CompletionSingle<T> {

    private final Single<T> source;

    private final BiFunction<? super Throwable, ? super Long, ? extends Flow.Publisher<U>> whenFunction;

    /** Indicate an immediate re-subscription. */
    private static final Single<Object> NOW = Single.just(1);

    SingleRetry(Single<T> source,
                BiFunction<? super Throwable, ? super Long, ? extends Flow.Publisher<U>> whenFunction) {
        this.source = source;
        this.whenFunction = whenFunction;
    }

    SingleRetry(Single<T> source, long count) {
        this(source, withCount(count));
    }

    SingleRetry(Single<T> source, BiPredicate<? super Throwable, ? super Long> predicate) {
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

    static final class RetrySubscriber<T, U> extends DeferredScalarSubscription<T>
    implements Flow.Subscriber<T> {

        private final AtomicInteger wip;

        private final BiFunction<? super Throwable, ? super Long, ? extends Flow.Publisher<U>> whenFunction;

        private final AtomicReference<Flow.Subscription> upstream;

        private final AtomicReference<Flow.Subscription> whenSubscription;

        private final Single<T> source;

        private long count;

        RetrySubscriber(Flow.Subscriber<? super T> downstream,
                        BiFunction<? super Throwable, ? super Long, ? extends Flow.Publisher<U>> whenFunction,
                        Single<T> source) {
            super(downstream);
            this.whenFunction = whenFunction;
            this.upstream = new AtomicReference<>();
            this.whenSubscription = new AtomicReference<>();
            this.wip = new AtomicInteger();
            this.source = source;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (SubscriptionHelper.setOnce(upstream, subscription)) {
                subscription.request(Long.MAX_VALUE);
            }
        }

        @Override
        public void onNext(T item) {
            complete(item);
        }

        @Override
        public void onError(Throwable throwable) {
            Flow.Subscription current = upstream.get();
            if (current != SubscriptionHelper.CANCELED
                    && upstream.compareAndSet(current, null)) {
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
        }

        @Override
        public void onComplete() {
            complete();
        }

        @Override
        public void cancel() {
            super.cancel();
            SubscriptionHelper.cancel(upstream);
            SubscriptionHelper.cancel(whenSubscription);
        }

        void setWhenSubscription(Flow.Subscription s) {
            SubscriptionHelper.replace(whenSubscription, s);
        }

        void retry() {
            if (wip.getAndIncrement() != 0) {
                return;
            }
            do {
                if (upstream.get() != SubscriptionHelper.CANCELED) {
                    source.subscribe(this);
                } else {
                    break;
                }
            } while (wip.decrementAndGet() != 0);
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
