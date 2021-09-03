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

final class MultiFirstPublisher<T> extends CompletionSingle<T> {

    private final Multi<T> source;

    MultiFirstPublisher(Multi<T> source) {
        this.source = source;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        source.subscribe(new FirstSubscriber<>(subscriber));
    }

    static final class FirstSubscriber<T> implements Flow.Subscriber<T> {

        private final Flow.Subscriber<? super T> downstream;

        private Flow.Subscription upstream;

        FirstSubscriber(Flow.Subscriber<? super T> downstream) {
            this.downstream = downstream;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.validate(upstream, subscription);
            upstream = subscription;
            downstream.onSubscribe(subscription);
        }

        @Override
        public void onNext(T item) {
            Flow.Subscription s = upstream;
            if (s != SubscriptionHelper.CANCELED) {
                s.cancel();
                downstream.onNext(item);
                onComplete();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (upstream != SubscriptionHelper.CANCELED) {
                upstream = SubscriptionHelper.CANCELED;
                downstream.onError(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (upstream != SubscriptionHelper.CANCELED) {
                upstream = SubscriptionHelper.CANCELED;
                downstream.onComplete();
            }
        }
    }
}
