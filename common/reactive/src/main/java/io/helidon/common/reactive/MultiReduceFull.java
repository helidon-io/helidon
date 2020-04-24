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
import java.util.function.Supplier;

/**
 * Combine items via an accumulator and function into a single value.
 * @param <T> the source value type
 * @param <R> the accumulator and result type
 */
final class MultiReduceFull<T, R> extends CompletionSingle<R> {

    private final Multi<T> source;

    private final Supplier<? extends R> supplier;

    private final BiFunction<R, T, R> reducer;

    MultiReduceFull(Multi<T> source, Supplier<? extends R> supplier, BiFunction<R, T, R> reducer) {
        this.source = source;
        this.supplier = supplier;
        this.reducer = reducer;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super R> subscriber) {
        R initial;
        try {
            initial = Objects.requireNonNull(supplier.get(),
                    "The supplier returned a null item");
        } catch (Throwable ex) {
            subscriber.onSubscribe(EmptySubscription.INSTANCE);
            subscriber.onError(ex);
            return;
        }
        source.subscribe(new ReduceFullSubscriber<>(subscriber, initial, reducer));
    }

    static final class ReduceFullSubscriber<T, R> extends DeferredScalarSubscription<R>
    implements Flow.Subscriber<T> {

        private final BiFunction<R, T, R> reducer;

        private R accumulator;

        private Flow.Subscription upstream;

        ReduceFullSubscriber(Flow.Subscriber<? super R> downstream, R initial, BiFunction<R, T, R> reducer) {
            super(downstream);
            this.reducer = reducer;
            this.accumulator = initial;
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
                try {
                    accumulator = Objects.requireNonNull(reducer.apply(accumulator, item),
                            "The reducer returned a null item");
                } catch (Throwable ex) {
                    s.cancel();
                    onError(ex);
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (upstream != SubscriptionHelper.CANCELED) {
                upstream = SubscriptionHelper.CANCELED;
                accumulator = null;
                error(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (upstream != SubscriptionHelper.CANCELED) {
                upstream = SubscriptionHelper.CANCELED;
                R accumulator = this.accumulator;
                this.accumulator = null;
                complete(accumulator);
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
