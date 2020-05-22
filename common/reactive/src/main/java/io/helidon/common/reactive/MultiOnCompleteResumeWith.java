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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * If the completes, switch to a generated Flow.Publisher and relay its signals then on.
 *
 * @param <T> the element type of the flows
 */
final class MultiOnCompleteResumeWith<T> implements Multi<T> {

    private final Multi<T> source;

    private final Flow.Publisher<? extends T> fallbackPublisher;

    MultiOnCompleteResumeWith(Multi<T> source, Flow.Publisher<? extends T> fallbackPublisher) {
        this.source = source;
        this.fallbackPublisher = fallbackPublisher;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        source.subscribe(new OnCompleteResumeWithSubscriber<>(subscriber, fallbackPublisher));
    }

    static final class OnCompleteResumeWithSubscriber<T> implements Flow.Subscriber<T>, Flow.Subscription {

        private final Flow.Subscriber<? super T> downstream;

        private final Flow.Publisher<? extends T> fallbackPublisher;

        private Flow.Subscription upstream;

        private long received;

        private final AtomicLong requested;

        private final FallbackSubscriber<T> fallbackSubscriber;

        OnCompleteResumeWithSubscriber(Flow.Subscriber<? super T> downstream,
                                       Flow.Publisher<? extends T> fallbackPublisher) {
            this.downstream = downstream;
            this.fallbackPublisher = fallbackPublisher;
            this.requested = new AtomicLong();
            fallbackSubscriber = new FallbackSubscriber<>(downstream, requested);
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.validate(upstream, subscription);
            upstream = subscription;
            downstream.onSubscribe(this);
        }

        @Override
        public void onNext(T item) {
            received++;
            downstream.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            upstream = SubscriptionHelper.CANCELED;
            downstream.onError(throwable);
        }

        @Override
        public void onComplete() {
            upstream = SubscriptionHelper.CANCELED;
            long p = received;
            if (p != 0L) {
                SubscriptionHelper.produced(requested, p);
            }

            Flow.Publisher<? extends T> publisher;

            try {
                publisher = Objects.requireNonNull(fallbackPublisher,
                        "The fallback function returned a null Flow.Publisher");
            } catch (Throwable ex) {
                downstream.onError(ex);
                return;
            }

            publisher.subscribe(fallbackSubscriber);
        }

        @Override
        public void request(long n) {
            if (n <= 0L) {
                downstream.onError(new IllegalArgumentException("Rule §3.9 violated: non-positive requests are forbidden"));
            } else {
                SubscriptionHelper.deferredRequest(fallbackSubscriber, requested, n);
                upstream.request(n);
            }
        }

        @Override
        public void cancel() {
            upstream.cancel();
            SubscriptionHelper.cancel(fallbackSubscriber);
        }

        static final class FallbackSubscriber<T> extends AtomicReference<Flow.Subscription>
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
