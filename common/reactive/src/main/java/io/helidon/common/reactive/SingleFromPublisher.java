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

import java.util.concurrent.Flow;

/**
 * Expects the source Flow.Publisher no items or only a single item, signals
 * an error otherwise.
 * @param <T> the element type of the flow
 */
final class SingleFromPublisher<T> extends CompletionSingle<T> {

    private final Flow.Publisher<T> source;

    SingleFromPublisher(Flow.Publisher<T> source) {
        this.source = source;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        source.subscribe(new SingleSubscriber<>(subscriber));
    }

    static final class SingleSubscriber<T> implements Flow.Subscriber<T> {

        private final Flow.Subscriber<? super T> downstream;

        private Flow.Subscription upstream;

        private T item;

        SingleSubscriber(Flow.Subscriber<? super T> downstream) {
            this.downstream = downstream;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.validate(this.upstream, subscription);
            this.upstream = subscription;
            downstream.onSubscribe(subscription);
        }

        @Override
        public void onNext(T item) {
            if (upstream != SubscriptionHelper.CANCELED) {
                if (this.item != null) {
                    upstream.cancel();
                    onError(new IllegalStateException("The source produced more than one item."));
                } else {
                    this.item = item;
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (upstream != SubscriptionHelper.CANCELED) {
                upstream = SubscriptionHelper.CANCELED;
                item = null;
                downstream.onError(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (upstream != SubscriptionHelper.CANCELED) {
                upstream = SubscriptionHelper.CANCELED;
                T v = item;
                if (v != null) {
                    item = null;
                    downstream.onNext(v);
                }
                downstream.onComplete();
            }
        }
    }
}
