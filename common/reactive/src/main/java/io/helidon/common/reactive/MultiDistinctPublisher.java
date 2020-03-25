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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.function.Function;

final class MultiDistinctPublisher<T, K> implements Multi<T> {

    private final Multi<T> source;

    private final Function<T, K> keySelector;

    MultiDistinctPublisher(Multi<T> source, Function<T, K> keySelector) {
        this.source = source;
        this.keySelector = keySelector;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        source.subscribe(new DistinctSubscriber<>(subscriber, keySelector));
    }

    static final class DistinctSubscriber<T, K> implements Flow.Subscriber<T>, Flow.Subscription {

        private final Flow.Subscriber<? super T> downstream;

        private final Function<T, K> keySelector;

        private Set<K> memory;

        private Flow.Subscription upstream;

        DistinctSubscriber(Flow.Subscriber<? super T> downstream, Function<T, K> keySelector) {
            this.downstream = downstream;
            this.keySelector = keySelector;
            this.memory = new HashSet<>();
        }


        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.validate(upstream, subscription);
            this.upstream = subscription;
            downstream.onSubscribe(this);
        }

        @Override
        public void onNext(T item) {
            Flow.Subscription s = upstream;
            Set<K> m = memory;
            if (s != SubscriptionHelper.CANCELED && m != null) {
                boolean pass;
                try {
                    pass = memory.add(keySelector.apply(item));
                } catch (Throwable ex) {
                    s.cancel();
                    onError(ex);
                    return;
                }

                if (pass) {
                    downstream.onNext(item);
                } else {
                    s.request(1L);
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (upstream != SubscriptionHelper.CANCELED) {
                upstream = SubscriptionHelper.CANCELED;
                memory = null;
                downstream.onError(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (upstream != SubscriptionHelper.CANCELED) {
                upstream = SubscriptionHelper.CANCELED;
                memory = null;
                downstream.onComplete();
            }
        }

        @Override
        public void request(long n) {
            upstream.request(n);
        }

        @Override
        public void cancel() {
            memory = null;
            upstream.cancel();
            upstream = SubscriptionHelper.CANCELED;
        }
    }
}
