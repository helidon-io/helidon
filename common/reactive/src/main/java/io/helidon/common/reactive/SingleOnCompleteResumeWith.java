/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * If the upstream completes, generate a new Single and emit its signals.
 *
 * @param <T> the element type of the source and fallback
 */
final class SingleOnCompleteResumeWith<T> extends CompletionSingle<T> {

    private final Single<T> source;

    private final Function<Optional<T>, ? extends Single<? extends T>> fallbackFunction;

    SingleOnCompleteResumeWith(Single<T> source, Function<Optional<T>, ? extends Single<? extends T>> fallbackFunction) {
        this.source = source;
        this.fallbackFunction = fallbackFunction;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");
        source.subscribe(new OnCompleteResumeWithSubscriber<>(subscriber, fallbackFunction));
    }

    static final class OnCompleteResumeWithSubscriber<T> extends DeferredScalarSubscription<T>
            implements Flow.Subscriber<T> {

        private final Function<Optional<T>, ? extends Single<? extends T>> fallbackFunction;

        private final FallbackSubscriber<T> fallbackSubscriber;

        private Flow.Subscription upstream;

        private T item;

        OnCompleteResumeWithSubscriber(Flow.Subscriber<? super T> downstream,
                                       Function<Optional<T>, ? extends Single<? extends T>> fallbackSupplier) {
            super(downstream);
            this.fallbackFunction = fallbackSupplier;
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
            this.item = item;
            upstream = SubscriptionHelper.CANCELED;
            complete(item);
        }

        @Override
        public void onError(Throwable throwable) {
            upstream = SubscriptionHelper.CANCELED;
            error(throwable);
        }

        @Override
        public void onComplete() {
            Single<? extends T> fallback;
            try {
                fallback = Objects.requireNonNull(
                        fallbackFunction.apply(Optional.ofNullable(this.item)
                        ), "The fallback function returned a null Single");
            } catch (Throwable ex) {
                error(ex);
                return;
            }

            fallback.subscribe(fallbackSubscriber);
        }

        @Override
        public void cancel() {
            super.cancel();
            upstream.cancel();
            SubscriptionHelper.cancel(fallbackSubscriber);
        }

        static final class FallbackSubscriber<T> extends AtomicReference<Flow.Subscription>
                implements Flow.Subscriber<T> {

            private final OnCompleteResumeWithSubscriber<T> parent;

            FallbackSubscriber(OnCompleteResumeWithSubscriber<T> parent) {
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
