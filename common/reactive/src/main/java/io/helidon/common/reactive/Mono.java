/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.helidon.common.reactive.Flow.Publisher;
import io.helidon.common.reactive.Flow.Subscriber;

/**
 * Single item publisher.
 * @param <T> published type
 */
public interface Mono<T> extends Publisher<T> {

    /**
     * Retrieve the value of this {@link Mono} instance in a blocking manner.
     *
     * @return value
     * @throws IllegalStateException if the Mono wraps an error or if
     * interrupted
     */
    default T block() {
        MonoBlockingSubscriber<T> subscriber = new MonoBlockingSubscriber<>();
        this.subscribe(subscriber);
        return subscriber.blockingGet();
    }

    /**
     * Retrieve the value of this {@link Mono} instance in a blocking manner.
     *
     * @param timeout timeout value
     * @return value
     * @throws IllegalStateException if the Mono wraps an error, or the
     * timeout is reached or if interrupted
     */
    default T block(Duration timeout) {
        MonoBlockingSubscriber<T> subscriber = new MonoBlockingSubscriber<>();
        this.subscribe(subscriber);
        return subscriber.blockingGet(timeout.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * Map this {@link Mono} instance to a new {@link Mono} of another type
     * using the given {@link MonoMapper}.
     * @param <U> mapped item type
     * @param mapper mapper
     * @return Mono
     */
    default <U> Mono<U> map(MonoMapper<T, U> mapper) {
        this.subscribe(mapper);
        return mapper;
    }

    /**
     * Map this {@link Mono} instance to a new {@link Mono} of another type
     * using the given java {@link Function}.
     * @param <U> mapped item type
     * @param function mapper function
     * @return Mono
     */
    default <U> Mono<U> map(Function<T, U> function) {
        MonoMapperFunctional<T, U> mapper = new MonoMapperFunctional<>(function);
        this.subscribe(mapper);
        return mapper;
    }

    /**
     * Map this {@link Mono} instance to a publisher using the given
     * {@link MonoMultiMapper}.
     *
     * @param <U> mapped items type
     * @param mapper mapper
     * @return Publisher
     */
    default <U> Publisher<U> mapMany(MonoMultiMapper<T, U> mapper) {
        this.subscribe(mapper);
        return mapper;
    }

    /**
     * Map this {@link Mono} instance to a publisher using the given
     * java {@link Function}.
     *
     * @param <U> mapped items type
     * @param function mapper function
     * @return Publisher
     */
    default <U> Publisher<U> mapMany(
            Function<T, Publisher<U>> function) {

        MonoMultiMapperFunctional<T, U> mapper =
                new MonoMultiMapperFunctional<>(function);
        this.subscribe(mapper);
        return mapper;
    }

    /**
     * Exposes this {@link Mono} instance as a {@link CompletableFuture}.
     * @return CompletableFuture
     */
    default CompletableFuture<T> toFuture() {
        try {
            MonoToCompletableFuture<T> subscriber =
                    new MonoToCompletableFuture<>();
            this.subscribe(subscriber);
            return subscriber;
        } catch (Throwable ex) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(ex);
            return future;
        }
    }

    /**
     * Create a {@link Mono} instance from a {@link CompletionStage}.
     * @param <T> item type
     * @param future source future
     * @return Mono
     */
    static <T> Mono<T> fromFuture(CompletionStage<? extends T> future) {
        return new MonoFromCompletionStage<>(future);
    }

    /**
     * Create a {@link Mono} instance that publishes the first item received
     * from the given publisher.
     * @param <T> item type
     * @param source source publisher
     * @return Mono
     */
    @SuppressWarnings("unchecked")
    static <T> Mono<T> from(Publisher<? extends T> source) {
        if (source instanceof Mono) {
            return (Mono<T>) source;
        }
        return new MonoNext<>(source);
    }

    /**
     * Create a {@link Mono} instance that publishes the given item to its
     * subscriber(s).
     *
     * @param <T> item type
     * @param item item to publish
     * @return Mono
     */
    static <T> Mono<T> just(T item) {
        return new MonoJust<>(item);
    }

    /**
     * Create a {@link Mono} instance that reports the given given exception to
     * its subscriber(s). The exception is reported by invoking
     * {@link Subscriber#onError(java.lang.Throwable)} when
     * {@link Publisher#subscribe(Subscriber)} is called.
     *
     * @param <T> item type
     * @param error exception to hold
     * @return Mono
     */
    static <T> Mono<T> error(Throwable error) {
        return new MonoError<>(error);
    }

    /**
     * Get a {@link Mono} instance that completes immediately.
     *
     * @param <T> item type
     * @return Mono
     */
    static <T> Mono<T> empty() {
        return MonoEmpty.<T>instance();
    }

    /**
     * Get a {@link Mono} instance that never completes.
     * @param <T> item type
     * @return Mono
     */
    static <T> Mono<T> never() {
        return MonoNever.<T>instance();
    }
}
