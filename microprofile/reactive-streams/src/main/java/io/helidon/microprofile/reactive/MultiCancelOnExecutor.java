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

package io.helidon.microprofile.reactive;

import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

import io.helidon.common.reactive.Multi;

/**
 * Routes the downstream cancel() call through the given executor.
 * @param <T> the element type of the sequence
 */
final class MultiCancelOnExecutor<T> implements Multi<T> {

    private final Multi<T> source;

    private final Executor executor;

    MultiCancelOnExecutor(Multi<T> source, Executor executor) {
        this.source = source;
        this.executor = executor;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        source.subscribe(new CancelOnExecutorSubscriber<>(subscriber, executor));
    }

    static final class CancelOnExecutorSubscriber<T>
    implements Flow.Subscriber<T>, Flow.Subscription, Runnable {

        private final Flow.Subscriber<? super T> downstream;

        private final Executor executor;

        private volatile boolean canceled;

        private Flow.Subscription upstream;

        CancelOnExecutorSubscriber(Flow.Subscriber<? super T> downstream, Executor executor) {
            this.downstream = downstream;
            this.executor = executor;
        }

        @Override
        public void run() {
            upstream.cancel();
            upstream = SubscriptionHelper.CANCELED;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.validate(upstream, subscription);
            upstream = subscription;
            downstream.onSubscribe(this);
        }

        @Override
        public void onNext(T item) {
            if (!canceled) {
                downstream.onNext(item);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (!canceled) {
                downstream.onError(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (!canceled) {
                downstream.onComplete();
            }
        }

        @Override
        public void request(long n) {
            upstream.request(n);
        }

        @Override
        public void cancel() {
            if (!canceled) {
                canceled = true;
                executor.execute(this);
            }
        }
    }
}
