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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Switch to another Single if the main is empty.
 * @param <T> the element type
 */
final class SingleSwitchIfEmpty<T> extends CompletionSingle<T> {

    private final Single<T> source;

    private final Single<T> fallback;

    SingleSwitchIfEmpty(Single<T> source, Single<T> fallback) {
        this.source = source;
        this.fallback = fallback;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        source.subscribe(new SwitchIfEmptySubscriber<>(subscriber, fallback));
    }

    static final class SwitchIfEmptySubscriber<T> extends DeferredScalarSubscription<T>
    implements Flow.Subscriber<T> {

        private final Single<T> fallback;

        private final FallbackSubscriber<T> fallbackSubscriber;

        private Flow.Subscription upstream;

        private boolean nonEmpty;

        SwitchIfEmptySubscriber(Flow.Subscriber<? super T> downstream, Single<T> fallback) {
            super(downstream);
            this.fallback = fallback;
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
            nonEmpty = true;
            complete(item);
        }

        @Override
        public void onError(Throwable throwable) {
            error(throwable);
        }

        @Override
        public void onComplete() {
            if (!nonEmpty) {
                fallback.subscribe(fallbackSubscriber);
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

            private final SwitchIfEmptySubscriber<T> parent;

            FallbackSubscriber(SwitchIfEmptySubscriber<T> parent) {
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
