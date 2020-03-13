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

package io.helidon.microprofile.reactive.hybrid;

import java.util.concurrent.Flow;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Wrapper for {@link org.reactivestreams Reactive Streams} {@link org.reactivestreams.Publisher}
 * or {@link io.helidon.common.reactive Helidon reactive streams} {@link java.util.concurrent.Flow.Publisher},
 * to be used interchangeably.
 *
 * @param <T> type of items
 */
public interface HybridPublisher<T> extends Flow.Publisher<T>, Publisher<T> {

    /**
     * Create new {@link io.helidon.microprofile.reactive.hybrid.HybridPublisher}
     * from {@link java.util.concurrent.Flow.Publisher}.
     *
     * @param publisher {@link java.util.concurrent.Flow.Publisher} to wrap
     * @param <T>       type of items
     * @return {@link io.helidon.microprofile.reactive.hybrid.HybridPublisher}
     * compatible with {@link org.reactivestreams Reactive Streams}
     * and {@link io.helidon.common.reactive Helidon reactive streams}
     */
    static <T> HybridPublisher<T> from(Publisher<T> publisher) {
        return new HybridPublisher<T>() {
            @Override
            public void subscribe(Subscriber<? super T> subscriber) {
                publisher.subscribe(subscriber);
            }
        };
    }

    /**
     * Create new {@link io.helidon.microprofile.reactive.hybrid.HybridPublisher}
     * from {@link org.reactivestreams.Publisher}.
     *
     * @param publisher {@link org.reactivestreams.Publisher} to wrap
     * @param <T>       type of items
     * @return {@link io.helidon.microprofile.reactive.hybrid.HybridPublisher}
     * compatible with {@link org.reactivestreams Reactive Streams}
     * and {@link io.helidon.common.reactive Helidon reactive streams}
     */
    static <T> HybridPublisher<T> from(Flow.Publisher<T> publisher) {
        return new HybridPublisher<T>() {
            @Override
            public void subscribe(Flow.Subscriber<? super T> subscriber) {
                publisher.subscribe(subscriber);
            }
        };
    }

    @Override
    default void subscribe(Flow.Subscriber<? super T> subscriber) {
        subscribe((Subscriber<? super T>) HybridSubscriber.from(subscriber));
    }

    @Override
    default void subscribe(Subscriber<? super T> subscriber) {
        subscribe((Flow.Subscriber<? super T>) HybridSubscriber.from(subscriber));
    }
}
