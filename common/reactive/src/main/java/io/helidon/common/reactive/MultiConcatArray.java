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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Relay items in order from subsequent Flow.Publishers as a single Multi source.
 */
final class MultiConcatArray<T> implements Multi<T> {

    private final Flow.Publisher<T>[] sources;

    MultiConcatArray(Flow.Publisher<T>[] sources) {
        this.sources = sources;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        ConcatArraySubscriber<T> parent = new ConcatArraySubscriber<>(subscriber, sources);
        subscriber.onSubscribe(parent);
        parent.nextSource();
    }

    static final class ConcatArraySubscriber<T> extends SubscriptionArbiter
    implements Flow.Subscriber<T> {

        private final Flow.Subscriber<? super T> downstream;

        private final Flow.Publisher<T>[] sources;

        private final AtomicInteger wip;

        private int index;

        private long produced;

        ConcatArraySubscriber(Flow.Subscriber<? super T> downstream, Flow.Publisher<T>[] sources) {
            this.downstream = downstream;
            this.sources = sources;
            this.wip = new AtomicInteger();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            super.setSubscription(subscription);
        }

        @Override
        public void onNext(T item) {
            produced++;
            downstream.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            downstream.onError(throwable);
        }

        @Override
        public void onComplete() {
            long produced = this.produced;
            if (produced != 0L) {
                this.produced = 0L;
                super.produced(produced);
            }
            nextSource();
        }

        public void nextSource() {
            if (wip.getAndIncrement() == 0) {
                do {
                    if (index == sources.length) {
                        downstream.onComplete();
                    } else {
                        sources[index++].subscribe(this);
                    }
                } while (wip.decrementAndGet() != 0);
            }
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                downstream.onError(new IllegalArgumentException("Rule §3.9 violated: non-positive requests are forbidden"));
            } else {
                super.request(n);
            }
        }
    }
}
