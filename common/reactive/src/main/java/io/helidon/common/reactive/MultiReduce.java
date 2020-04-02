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

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.function.BiFunction;

/**
 * Combine subsequent items via a callback function and emit
 * the result as a Single.
 * @param <T> the element type of the source and result
 */
final class MultiReduce<T> implements Single<T> {

    private final Multi<T> source;

    private final BiFunction<T, T, T> reducer;

    MultiReduce(Multi<T> source, BiFunction<T, T, T> reducer) {
        this.source = source;
        this.reducer = reducer;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        source.subscribe(new ReducerSubscriber<>(subscriber, reducer));
    }

    static final class ReducerSubscriber<T> extends DeferredScalarSubscription<T>
    implements Flow.Subscriber<T> {

        private final BiFunction<T, T, T> reducer;

        private Flow.Subscription upstream;

        private T current;

        ReducerSubscriber(Flow.Subscriber<? super T> downstream, BiFunction<T, T, T> reducer) {
            super(downstream);
            this.reducer = reducer;
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
            Flow.Subscription s = upstream;
            if (s != SubscriptionHelper.CANCELED) {
                T current = this.current;
                if (current == null) {
                    this.current = item;
                } else {
                    try {
                        this.current = Objects.requireNonNull(reducer.apply(current, item),
                        "The reducer returned a null item");
                    } catch (Throwable ex) {
                        s.cancel();
                        onError(ex);
                    }
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (upstream != SubscriptionHelper.CANCELED) {
                upstream = SubscriptionHelper.CANCELED;
                current = null;
                error(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (upstream != SubscriptionHelper.CANCELED) {
                upstream = SubscriptionHelper.CANCELED;
                T current = this.current;
                this.current = null;
                if (current == null) {
                    complete();
                } else {
                    complete(current);
                }
            }
        }

        @Override
        public void cancel() {
            super.cancel();
            upstream.cancel();
            upstream = SubscriptionHelper.CANCELED;
        }
    }
}
