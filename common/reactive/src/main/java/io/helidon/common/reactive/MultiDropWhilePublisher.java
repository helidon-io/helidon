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
import java.util.function.Predicate;

/**
 * Drop items while the predicate returns true, relay the rest as they are.
 * @param <T> the element type of the sequence
 */
final class MultiDropWhilePublisher<T> implements Multi<T> {

    private final Multi<T> source;

    private final Predicate<? super T> predicate;

    MultiDropWhilePublisher(Multi<T> source, Predicate<? super T> predicate) {
        this.source = source;
        this.predicate = predicate;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        source.subscribe(new DropWhileSubscriber<>(subscriber, predicate));
    }

    static final class DropWhileSubscriber<T> implements Flow.Subscriber<T> {

        private final Flow.Subscriber<? super T> downstream;

        private final Predicate<? super T> predicate;

        private Flow.Subscription upstream;

        private boolean passThrough;

        DropWhileSubscriber(Flow.Subscriber<? super T> downstream, Predicate<? super T> predicate) {
            this.downstream = downstream;
            this.predicate = predicate;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.validate(upstream, subscription);
            upstream = subscription;
            downstream.onSubscribe(subscription);
        }

        @Override
        public void onNext(T item) {
            if (upstream != SubscriptionHelper.CANCELED) {
                if (passThrough) {
                    downstream.onNext(item);
                } else {
                    boolean b;
                    try {
                        b = predicate.test(item);
                    } catch (Throwable ex) {
                        upstream.cancel();
                        onError(ex);
                        return;
                    }

                    if (!b) {
                        passThrough = true;
                        downstream.onNext(item);
                    } else {
                        upstream.request(1);
                    }
                }
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
