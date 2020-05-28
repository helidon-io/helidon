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
import java.util.function.Function;

/**
 * If the upstream fails, generate a fallback success item via a function.
 * @param <T> the element type of the sequence
 */
final class SingleOnErrorResume<T> extends CompletionSingle<T> {

    private final Single<T> source;

    private final Function<? super Throwable, ? extends T> fallbackFunction;

    SingleOnErrorResume(Single<T> source,
                        Function<? super Throwable, ? extends T> fallbackFunction) {
        this.source = source;
        this.fallbackFunction = fallbackFunction;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");
        source.subscribe(new OnErrorResumeSubscriber<>(subscriber, fallbackFunction));
    }

    static final class OnErrorResumeSubscriber<T> extends DeferredScalarSubscription<T>
            implements Flow.Subscriber<T> {

        private final Function<? super Throwable, ? extends T> fallbackFunction;

        private Flow.Subscription upstream;

        OnErrorResumeSubscriber(Flow.Subscriber<? super T> downstream,
                                Function<? super Throwable, ? extends T> fallbackFunction) {
            super(downstream);
            this.fallbackFunction = fallbackFunction;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.validate(this.upstream, subscription);
            this.upstream = subscription;
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
            T fallback;
            try {
                fallback = Objects.requireNonNull(fallbackFunction.apply(throwable),
                        "The fallback function returned a null item");
            } catch (Throwable ex) {
                if (ex != throwable) {
                    ex.addSuppressed(throwable);
                }
                error(ex);
                return;
            }
            complete(fallback);
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
        }
    }
}
