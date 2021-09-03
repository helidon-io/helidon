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

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.function.Predicate;

final class MultiTakeWhilePublisher<T> implements Multi<T> {

    private final Multi<T> source;

    private final Predicate<? super T> predicate;

    MultiTakeWhilePublisher(Multi<T> source, Predicate<? super T> predicate) {
        this.source = source;
        this.predicate = predicate;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        source.subscribe(new TakeWhileSubscriber<>(subscriber, predicate));
    }

    static final class TakeWhileSubscriber<T> implements Flow.Subscriber<T> {

        private final Flow.Subscriber<? super T> downstream;

        private final Predicate<? super T> predicate;

        private Flow.Subscription upstream;


        TakeWhileSubscriber(Flow.Subscriber<? super T> downstream, Predicate<? super T> predicate) {
            this.downstream = downstream;
            this.predicate = predicate;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            Objects.requireNonNull(subscription, "subscription is null");
            if (upstream != null) {
                subscription.cancel();
                throw new IllegalStateException("Subscription already set!");
            }
            upstream = subscription;
            downstream.onSubscribe(subscription);
        }

        @Override
        public void onNext(T item) {
            Flow.Subscription s = upstream;
            if (s != null) {
                boolean pass;
                try {
                    pass = predicate.test(item);
                } catch (Throwable ex) {
                    s.cancel();
                    onError(ex);
                    return;
                }

                if (pass) {
                    downstream.onNext(item);
                } else {
                    s.cancel();
                    onComplete();
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (upstream != null) {
                upstream = null;
                downstream.onError(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (upstream != null) {
                upstream = null;
                downstream.onComplete();
            }
        }
    }
}
