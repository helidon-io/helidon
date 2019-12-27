/*
 * Copyright (c)  2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.reactive;

import java.util.concurrent.Flow;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.helidon.common.reactive.Multi;
import io.helidon.microprofile.reactive.hybrid.HybridPublisher;
import io.helidon.microprofile.reactive.hybrid.HybridSubscriber;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Conversion methods between {@link io.helidon.common.reactive Helidon reactive streams} and the {@link org.reactivestreams Reactive Streams}.
 * Wraps publishers/processors/subscribers to Hybrid {@link io.helidon.microprofile.reactive.hybrid} variants
 */
public interface MultiRS {

    /**
     * Create {@link io.helidon.common.reactive Helidon reactive streams} {@link java.util.concurrent.Flow.Subscriber}
     * from {@link org.reactivestreams Reactive Streams} {@link org.reactivestreams.Subscriber}.
     *
     * @param subscriber {@link org.reactivestreams Reactive Streams} {@link org.reactivestreams.Subscriber}
     * @param <T>        type of streamed item
     * @return {@link io.helidon.common.reactive Helidon reactive streams} {@link java.util.concurrent.Flow.Subscriber}
     */
    static <T> Flow.Subscriber<T> from(Subscriber<T> subscriber) {
        return HybridSubscriber.from(subscriber);
    }

    /**
     * Create {@link org.reactivestreams Reactive Streams} {@link org.reactivestreams.Subscriber}
     * from {@link io.helidon.common.reactive Helidon reactive streams} subscriber.
     *
     * @param subscriber Helidon {@link io.helidon.common.reactive.Multi} stream {@link java.util.concurrent.Flow.Subscriber}
     * @param <T>        type of streamed item
     * @return {@link org.reactivestreams Reactive Streams} {@link org.reactivestreams.Subscriber}
     */
    static <T> Subscriber<T> from(Flow.Subscriber<T> subscriber) {
        return HybridSubscriber.from(subscriber);
    }

    /**
     * Create {@link io.helidon.common.reactive Helidon reactive streams} {@link java.util.concurrent.Flow.Publisher}
     * from {@link org.reactivestreams Reactive Streams} {@link org.reactivestreams.Publisher}.
     *
     * @param publisher {@link org.reactivestreams Reactive Streams} {@link org.reactivestreams.Publisher}
     * @param <T>       type of streamed item
     * @return Multi stream {@link java.util.concurrent.Flow.Publisher}
     */
    static <T> Flow.Publisher<T> from(Publisher<T> publisher) {
        return HybridPublisher.from(publisher);
    }

    /**
     * Create {@link org.reactivestreams Reactive Streams} {@link org.reactivestreams.Publisher}
     * from {@link io.helidon.common.reactive.Multi} stream {@link java.util.concurrent.Flow.Publisher}.
     *
     * @param publisher {@link io.helidon.common.reactive.Multi} stream {@link java.util.concurrent.Flow.Publisher}
     * @param <T>       type of streamed item
     * @return {@link org.reactivestreams Reactive Streams} {@link org.reactivestreams.Publisher}
     */
    static <T> Publisher<T> from(Flow.Publisher<T> publisher) {
        return HybridPublisher.from(publisher);
    }

    /**
     * Create Helidon {@link io.helidon.common.reactive.Multi} stream
     * from {@link org.reactivestreams Reactive Streams} {@link org.reactivestreams.Publisher}.
     *
     * @param publisher {@link org.reactivestreams Reactive Streams} {@link org.reactivestreams.Publisher}
     * @param <T>       type of streamed item
     * @return {@link io.helidon.common.reactive.Multi} stream
     */
    static <T> Multi<T> toMulti(Publisher<T> publisher) {
        return Multi.from(HybridPublisher.from(publisher));
    }

    /**
     * Create {@link org.reactivestreams Reactive Streams} {@link org.reactivestreams.Publisher}
     * from Java {@link java.util.stream.Stream}.
     *
     * @param stream Java {@link java.util.stream.Stream}
     * @param <U>    type of streamed item
     * @return {@link org.reactivestreams Reactive Streams} {@link org.reactivestreams.Publisher}
     */
    static <U> Publisher<U> just(Stream<U> stream) {
        return MultiRS.from(Multi.just(stream.collect(Collectors.toList())));
    }

    /**
     * Create {@link org.reactivestreams Reactive Streams} {@link org.reactivestreams.Publisher}
     * from items vararg.
     *
     * @param items items varargs to be streamed
     * @param <U>   type of streamed items
     * @return {@link org.reactivestreams Reactive Streams} {@link org.reactivestreams.Publisher}
     */
    static <U> Publisher<U> just(U... items) {
        return MultiRS.from(Multi.just(items));
    }
}
