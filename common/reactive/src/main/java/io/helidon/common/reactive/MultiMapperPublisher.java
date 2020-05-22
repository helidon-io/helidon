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

import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.function.Function;

import io.helidon.common.mapper.Mapper;

/**
 * Maps the upstream items via a {@link Mapper} function.
 * @param <T> the upstream value type
 * @param <R> the result value type
 */
final class MultiMapperPublisher<T, R> implements Multi<R> {

    private final Flow.Publisher<T> source;

    private final Function<? super T, ? extends R> mapper;

    MultiMapperPublisher(Flow.Publisher<T> source, Function<? super T, ? extends R> mapper) {
        this.source = source;
        this.mapper = mapper;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super R> subscriber) {
        source.subscribe(new MapperSubscriber<>(subscriber, mapper));
    }

    static final class MapperSubscriber<T, R> implements Flow.Subscriber<T>, Flow.Subscription {

        private final Flow.Subscriber<? super R> downstream;

        private final Function<? super T, ? extends R> mapper;

        private Flow.Subscription upstream;

        MapperSubscriber(Flow.Subscriber<? super R> downstream, Function<? super T, ? extends R> mapper) {
            this.downstream = downstream;
            this.mapper = mapper;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            SubscriptionHelper.validate(this.upstream, subscription);
            this.upstream = subscription;
            downstream.onSubscribe(this);
        }

        @Override
        public void onNext(T item) {
            // in case the upstream doesn't stop immediately after a failed mapping
            Flow.Subscription s = upstream;
            if (s != SubscriptionHelper.CANCELED) {
                R result;

                try {
                    result = Objects.requireNonNull(mapper.apply(item), "The mapper returned a null value.");
                } catch (Throwable ex) {
                    s.cancel();
                    onError(ex);
                    return;
                }

                downstream.onNext(result);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            // if mapper.map fails above, the upstream may still emit an onError without request
            if (upstream != SubscriptionHelper.CANCELED) {
                upstream = SubscriptionHelper.CANCELED;
                downstream.onError(throwable);
            }
        }

        @Override
        public void onComplete() {
            // if mapper.map fails above, the upstream may still emit an onComplete without request
            if (upstream != SubscriptionHelper.CANCELED) {
                upstream = SubscriptionHelper.CANCELED;
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
