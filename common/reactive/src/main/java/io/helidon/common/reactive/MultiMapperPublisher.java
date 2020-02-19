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

import io.helidon.common.mapper.Mapper;

import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * Maps the upstream items via a {@link Mapper} function.
 * @param <T> the upstream value type
 * @param <R> the result value type
 */
final class MultiMapperPublisher<T, R> implements Multi<R> {

    private final Flow.Publisher<T> source;

    private final Mapper<T, R> mapper;

    MultiMapperPublisher(Flow.Publisher<T> source, Mapper<T, R> mapper) {
        this.source = source;
        this.mapper = mapper;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super R> subscriber) {
        source.subscribe(new MapperSubscriber<>(subscriber, mapper));
    }

    static final class MapperSubscriber<T, R> implements Flow.Subscriber<T>, Flow.Subscription {

        private final Flow.Subscriber<? super R> downstream;

        private final Mapper<T, R> mapper;

        Flow.Subscription upstream;

        MapperSubscriber(Flow.Subscriber<? super R> downstream, Mapper<T, R> mapper) {
            this.downstream = downstream;
            this.mapper = mapper;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.upstream = subscription;
            // FIXME onSubscribe(subscription) should work too, but there are bugs in other
            //  pre-existing operators preventing the TCK from passing without this
            downstream.onSubscribe(this);
        }

        @Override
        public void onNext(T item) {
            // in case the upstream doesn't stop immediately after a failed mapping
            if (upstream != null) {
                R result;

                try {
                    result = Objects.requireNonNull(mapper.map(item), "The mapper returned a null value.");
                } catch (Throwable ex) {
                    upstream.cancel();
                    onError(ex);
                    return;
                }

                downstream.onNext(result);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            // if mapper.map fails above, the upstream may still emit an onError without request
            if (upstream != null) {
                upstream = null;
                downstream.onError(throwable);
            }
        }

        @Override
        public void onComplete() {
            // if mapper.map fails above, the upstream may still emit an onComplete without request
            if (upstream != null) {
                upstream = null;
                downstream.onComplete();
            }
        }

        @Override
        public void request(long n) {
            Flow.Subscription s = upstream;
            if (s != null) {
                s.request(n);
            }
        }

        @Override
        public void cancel() {
            Flow.Subscription s = upstream;
            upstream = null;
            if (s != null) {
                s.cancel();
            }
        }
    }
}
