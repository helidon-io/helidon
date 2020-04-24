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

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * If the upstream fails, generate a fallback Single and emit its signals.
 * @param <T> the element type of the source and fallback
 */
final class SingleOnErrorResumeWith<T> extends CompletionSingle<T> {

    private final Single<T> source;

    private final Function<? super Throwable, ? extends Single<? extends T>> fallbackFunction;

    SingleOnErrorResumeWith(Single<T> source,
                            Function<? super Throwable, ? extends Single<? extends T>> fallbackFunction) {
        this.source = source;
        this.fallbackFunction = fallbackFunction;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");
        source.subscribe(new OnErrorResumeWithSubscriber<>(subscriber, fallbackFunction));
    }

    static final class OnErrorResumeWithSubscriber<T> extends DeferredScalarSubscription<T>
    implements Flow.Subscriber<T> {

        private final Function<? super Throwable, ? extends Single<? extends T>> fallbackFunction;

        private final FallbackSubscriber<T> fallbackSubscriber;

        private Flow.Subscription upstream;

        OnErrorResumeWithSubscriber(Flow.Subscriber<? super T> downstream,
                                    Function<? super Throwable, ? extends Single<? extends T>> fallbackFunction) {
            super(downstream);
            this.fallbackFunction = fallbackFunction;
            this.fallbackSubscriber = new FallbackSubscriber<>(this);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.validate(upstream, subscription);
            upstream = subscription;
            subscribeSelf();
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T item) {
            upstream = SubscriptionHelper.CANCELED;
            complete(item);
        }

        @Override
        public void onError(Throwable throwable) {
            upstream = SubscriptionHelper.CANCELED;
            Single<? extends T> fallback;

            try {
                fallback = Objects.requireNonNull(fallbackFunction.apply(throwable),
                        "The fallback function returned a null Single");
            } catch (Throwable ex) {
                if (ex != throwable) {
                    ex.addSuppressed(throwable);
                }
                error(ex);
                return;
            }

            fallback.subscribe(fallbackSubscriber);
        }

        @Override
        public void onComplete() {
            if (upstream != SubscriptionHelper.CANCELED) {
                upstream = SubscriptionHelper.CANCELED;
                complete();
            }
        }

        @Override
        public void cancel() {
            super.cancel();
            upstream.cancel();
            SubscriptionHelper.cancel(fallbackSubscriber);
        }

        static final class FallbackSubscriber<T> extends AtomicReference<Flow.Subscription>
        implements Flow.Subscriber<T> {

            private final OnErrorResumeWithSubscriber<T> parent;

            FallbackSubscriber(OnErrorResumeWithSubscriber<T> parent) {
                this.parent = parent;
            }

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                SubscriptionHelper.setOnce(this, subscription);
                subscription.request(Long.MAX_VALUE);
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
