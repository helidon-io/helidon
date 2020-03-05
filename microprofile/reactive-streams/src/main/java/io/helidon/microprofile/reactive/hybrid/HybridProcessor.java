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

import io.helidon.common.reactive.Multi;

import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Wrapper for {@link org.reactivestreams Reactive Streams} {@link org.reactivestreams.Processor}
 * or {@link io.helidon.common.reactive Helidon reactive streams} {@link java.util.concurrent.Flow.Processor},
 * to be used interchangeably.
 *
 * @param <T> type of items processor consumes
 * @param <R> type of items processor emits
 */
public interface HybridProcessor<T, R> extends Flow.Processor<T, R>, Processor<T, R>, Multi<R> {

    /**
     * Create new {@link io.helidon.microprofile.reactive.hybrid.HybridProcessor}
     * from {@link java.util.concurrent.Flow.Processor}.
     *
     * @param processor {@link java.util.concurrent.Flow.Processor} to wrap
     * @param <T>       type of items processor consumes
     * @param <R>       type of items processor emits
     * @return {@link io.helidon.microprofile.reactive.hybrid.HybridProcessor}
     * compatible with {@link org.reactivestreams Reactive Streams}
     * and {@link io.helidon.common.reactive Helidon reactive streams}
     */
    static <T, R> HybridProcessor<T, R> from(Flow.Processor<T, R> processor) {
        return new HybridProcessor<T, R>() {
            @Override
            public void subscribe(Flow.Subscriber<? super R> subscriber) {
                processor.subscribe(subscriber);
            }

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                processor.onSubscribe(subscription);
            }

            @Override
            public void onNext(T item) {
                processor.onNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
                processor.onError(throwable);
            }

            @Override
            public void onComplete() {
                processor.onComplete();
            }

            @Override
            public String toString() {
                return processor.toString();
            }
        };
    }

    /**
     * Create new {@link io.helidon.microprofile.reactive.hybrid.HybridProcessor}
     * from {@link org.reactivestreams.Processor}.
     *
     * @param processor {@link org.reactivestreams.Processor} to wrap
     * @param <T>       type of items processor consumes
     * @param <R>       type of items processor emits
     * @return {@link io.helidon.microprofile.reactive.hybrid.HybridProcessor}
     * compatible with {@link org.reactivestreams Reactive Streams}
     * and {@link io.helidon.common.reactive Helidon reactive streams}
     */
    static <T, R> HybridProcessor<T, R> from(Processor<T, R> processor) {
        return new HybridProcessor<T, R>() {

            @Override
            public void subscribe(Subscriber<? super R> subscriber) {
                processor.subscribe(subscriber);
            }

            @Override
            public void onSubscribe(Subscription subscription) {
                processor.onSubscribe(subscription);
            }

            @Override
            public void onNext(T item) {
                processor.onNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
                processor.onError(throwable);
            }

            @Override
            public void onComplete() {
                processor.onComplete();
            }

            @Override
            public String toString() {
                return processor.toString();
            }
        };
    }

    @Override
    default void subscribe(Flow.Subscriber<? super R> subscriber) {
        subscribe((Subscriber<? super R>) HybridSubscriber.from(subscriber));
    }

    @Override
    default void subscribe(Subscriber<? super R> subscriber) {
        subscribe((Flow.Subscriber<? super R>) HybridSubscriber.from(subscriber));
    }

    @Override
    default void onSubscribe(Flow.Subscription subscription) {
        onSubscribe((Subscription) HybridSubscription.from(subscription));
    }

    @Override
    default void onSubscribe(Subscription subscription) {
        onSubscribe((Flow.Subscription) HybridSubscription.from(subscription));
    }
}
