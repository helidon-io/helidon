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

import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

/**
 * Re-emit the item or terminal signals on the given executor's thread.
 * @param <T> the element type
 */
final class SingleObserveOn<T> extends CompletionSingle<T> {

    private final Single<T> source;

    private final Executor executor;

    SingleObserveOn(Single<T> source, Executor executor) {
        this.source = source;
        this.executor = executor;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        source.subscribe(new ObserveOnSubscriber<>(subscriber, executor));
    }

    static final class ObserveOnSubscriber<T> implements Flow.Subscriber<T>, Flow.Subscription, Runnable {

        private final Flow.Subscriber<? super T> downstream;

        private final Executor executor;

        private Flow.Subscription upstream;

        private T item;
        private Throwable error;

        private volatile boolean canceled;

        private volatile boolean upstreamReady;
        private volatile boolean requestReady;

        ObserveOnSubscriber(Flow.Subscriber<? super T> downstream, Executor executor) {
            this.downstream = downstream;
            this.executor = executor;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.validate(upstream, subscription);
            upstream = subscription;
            downstream.onSubscribe(this);

            // Requests must be deferred because the TCK doesn't like when
            // onNext is called from another thread while onSubscribe hasn't returned yet.
            // This is essentially SubscriptionHelper.deferredSetOnce simplified for Single
            upstreamReady = true;
            if (requestReady) {
                subscription.request(Long.MAX_VALUE);
            }
        }

        @Override
        public void onNext(T item) {
            this.item = item;
            executor.execute(this);
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
            executor.execute(this);
        }

        @Override
        public void onComplete() {
            if (this.item == null && this.error == null) {
                executor.execute(this);
            }
        }

        @Override
        public void request(long n) {
            if (n <= 0L) {
                onError(new IllegalArgumentException("Rule §3.9 violated: non-positive requests are forbidden"));
            } else {
                // Requests must be deferred because the TCK doesn't like when
                // onNext is called from another thread while onSubscribe hasn't returned yet.
                // This is essentially SubscriptionHelper.deferredRequest simplified for Single
                requestReady = true;
                if (upstreamReady) {
                    upstream.request(Long.MAX_VALUE);
                }
            }
        }

        @Override
        public void cancel() {
            canceled = true;
            upstream.cancel();
        }

        @Override
        public void run() {
            if (!canceled) {
                Throwable ex = error;
                if (ex != null) {
                    downstream.onError(ex);
                } else {
                    T item = this.item;
                    if (item != null) {
                        downstream.onNext(item);
                    }
                    downstream.onComplete();
                }
            }
        }
    }
}
