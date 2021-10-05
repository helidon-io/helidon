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
 *
 */
package io.helidon.common.reactive;

import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * Executes given {@link java.lang.Runnable} when stream is finished without value(empty stream).
 *
 * @param <T> the item type
 */
final class MultiIfEmptyPublisher<T> implements Multi<T> {

    private final Multi<T> source;
    private final Runnable ifEmpty;

    MultiIfEmptyPublisher(Multi<T> source, Runnable ifEmpty) {
        this.source = source;
        this.ifEmpty = ifEmpty;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");
        source.subscribe(new IfEmptySubscriber<>(subscriber, ifEmpty));
    }

    static final class IfEmptySubscriber<T> implements Flow.Subscriber<T>, Flow.Subscription {

        private final Flow.Subscriber<? super T> downstream;
        private final Runnable ifEmpty;

        private boolean empty;

        private Flow.Subscription upstream;

        IfEmptySubscriber(Flow.Subscriber<? super T> downstream, Runnable ifEmpty) {
            this.downstream = downstream;
            this.ifEmpty = ifEmpty;
            this.empty = true;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.validate(upstream, subscription);
            upstream = subscription;
            downstream.onSubscribe(this);
        }

        @Override
        public void onNext(T item) {
            Flow.Subscription s = upstream;
            if (s != SubscriptionHelper.CANCELED) {
                empty = false;
                downstream.onNext(item);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            Flow.Subscription s = upstream;
            if (s != SubscriptionHelper.CANCELED) {
                upstream = SubscriptionHelper.CANCELED;
                downstream.onError(throwable);
            }
        }

        @Override
        public void onComplete() {
            Flow.Subscription s = upstream;
            if (s != SubscriptionHelper.CANCELED) {
                upstream = SubscriptionHelper.CANCELED;
                boolean e = empty;
                if (e) {
                    try {
                        ifEmpty.run();
                    } catch (Throwable t) {
                        downstream.onError(t);
                        return;
                    }
                }
                downstream.onComplete();
            }
        }

        @Override
        public void request(long n) {
            upstream.request(n);
        }

        @Override
        public void cancel() {
            upstream.cancel();
            upstream = SubscriptionHelper.CANCELED;
        }
    }
}
