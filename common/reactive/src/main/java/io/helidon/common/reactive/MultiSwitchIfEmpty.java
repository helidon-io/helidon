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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Switch to an alternate sequence if the main sequence is empty.
 * @param <T> the element type of the sequences
 */
final class MultiSwitchIfEmpty<T> implements Multi<T> {

    private final Multi<T> source;

    private final Flow.Publisher<T> fallback;

    MultiSwitchIfEmpty(Multi<T> source, Flow.Publisher<T> fallback) {
        this.source = source;
        this.fallback = fallback;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        source.subscribe(new SwitchIfEmptyMainSubscriber<>(subscriber, fallback));
    }

    static final class SwitchIfEmptyMainSubscriber<T>
            extends AtomicLong
            implements Flow.Subscriber<T>, Flow.Subscription {

        private final Flow.Subscriber<? super T> downstream;

        private final Flow.Publisher<T> fallback;

        private final FallbackSubscriber<T> fallbackSubscriber;

        private Flow.Subscription upstream;

        private boolean nonEmpty;

        SwitchIfEmptyMainSubscriber(Flow.Subscriber<? super T> downstream, Flow.Publisher<T> fallback) {
            this.downstream = downstream;
            this.fallback = fallback;
            this.fallbackSubscriber = new FallbackSubscriber<>(downstream, this);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.validate(upstream, subscription);
            upstream = subscription;
            downstream.onSubscribe(this);
        }

        @Override
        public void onNext(T item) {
            nonEmpty = true;
            downstream.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            downstream.onError(throwable);
        }

        @Override
        public void onComplete() {
            if (nonEmpty) {
                downstream.onComplete();
            } else {
                fallback.subscribe(fallbackSubscriber);
            }
        }

        @Override
        public void request(long n) {
            upstream.request(n);
            if (!nonEmpty) {
                SubscriptionHelper.deferredRequest(fallbackSubscriber, this, n);
            }
        }

        @Override
        public void cancel() {
            upstream.cancel();
            SubscriptionHelper.cancel(fallbackSubscriber);
        }

        static final class FallbackSubscriber<T>
        extends AtomicReference<Flow.Subscription>
        implements Flow.Subscriber<T> {

            private final Flow.Subscriber<? super T> downstream;

            private final AtomicLong requested;

            FallbackSubscriber(Flow.Subscriber<? super T> downstream, AtomicLong requested) {
                this.downstream = downstream;
                this.requested = requested;
            }

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                SubscriptionHelper.deferredSetOnce(this, requested, subscription);
            }

            @Override
            public void onNext(T item) {
                downstream.onNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
                downstream.onError(throwable);
            }

            @Override
            public void onComplete() {
                downstream.onComplete();
            }
        }
    }
}
