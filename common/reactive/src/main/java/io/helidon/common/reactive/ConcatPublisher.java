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
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Concat streams to one.
 *
 * @param <T> item type
 */
public final class ConcatPublisher<T> implements Flow.Publisher<T>, Multi<T> {
    private final Flow.Publisher<T> firstPublisher;
    private final Flow.Publisher<T> secondPublisher;

    private ConcatPublisher(Flow.Publisher<T> firstPublisher, Flow.Publisher<T> secondPublisher) {
        this.firstPublisher = firstPublisher;
        this.secondPublisher = secondPublisher;
    }

    /**
     * Create new {@link ConcatPublisher}.
     *
     * @param firstPublisher  first stream
     * @param secondPublisher second stream
     * @param <T>             item type
     * @return {@link ConcatPublisher}
     */
    public static <T> ConcatPublisher<T> create(Flow.Publisher<T> firstPublisher, Flow.Publisher<T> secondPublisher) {
        return new ConcatPublisher<>(firstPublisher, secondPublisher);
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        firstPublisher.subscribe(new ConcatSubscriber<>(subscriber, secondPublisher));
    }

    static final class ConcatSubscriber<T> implements Flow.Subscriber<T>, Flow.Subscription {

        final Flow.Subscriber<? super T> downstream;

        final AtomicLong requested;

        final AtomicReference<Flow.Subscription> secondUpstream;

        Flow.Publisher<T> secondPublisher;

        long received;

        Flow.Subscription firstUpstream;

        ConcatSubscriber(Flow.Subscriber<? super T> downstream, Flow.Publisher<T> secondPublisher) {
            this.downstream = downstream;
            this.secondPublisher = secondPublisher;
            this.secondUpstream = new AtomicReference<>();
            this.requested = new AtomicLong();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (secondPublisher == null) {
                SubscriptionHelper.deferredSetOnce(secondUpstream, requested, subscription);
            } else {
                this.firstUpstream = subscription;
                downstream.onSubscribe(this);
            }
        }

        @Override
        public void onNext(T item) {
            received++;
            downstream.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            secondPublisher = null;
            firstUpstream = null;
            secondUpstream.lazySet(null);
            downstream.onError(throwable);
        }

        @Override
        public void onComplete() {
            if (secondPublisher == null) {
                secondUpstream.lazySet(null);
                downstream.onComplete();
            } else {
                Flow.Publisher<T> second = secondPublisher;
                secondPublisher = null;
                firstUpstream = null;

                SubscriptionHelper.produced(requested, received);

                second.subscribe(this);
            }
        }

        @Override
        public void request(long n) {
            if (n <= 0L) {
                onError(new IllegalArgumentException("Rule §3.9 violated: non-positive request calls are forbidden"));
                return;
            }
            SubscriptionHelper.deferredRequest(secondUpstream, requested, n);
            Flow.Subscription upstream = firstUpstream;
            if (upstream != null) {
                upstream.request(n);
            }
        }

        @Override
        public void cancel() {
            Flow.Subscription upstream = firstUpstream;
            firstUpstream = null;
            if (upstream != null) {
                upstream.cancel();
            }

            SubscriptionHelper.cancel(secondUpstream);
        }
    }
}
