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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.reactive.Flow.Publisher;
import io.helidon.common.reactive.Flow.Subscriber;

/**
 * Single item publisher.
 * @param <T> published type
 */
public interface Single<T> extends Publisher<T> {

    /**
     * Map this {@link Single} instance to a new {@link Single} of another type
     * using the given {@link Mapper}.
     * @param <U> mapped item type
     * @param mapper mapper
     * @return Single
     */
    default <U> Single<U> map(Mapper<T, U> mapper) {
        SingleMapperProcessor<T, U> processor = new SingleMapperProcessor<>(mapper);
        this.subscribe(processor);
        return processor;
    }

    /**
     * Map this {@link Single} instance to a publisher using the given
     * {@link Mapper}.
     *
     * @param <U> mapped items type
     * @param mapper mapper
     * @return Publisher
     */
    default <U> Publisher<U> mapMany(Mapper<T, Publisher<U>> mapper) {
        SingleMultiMapperProcessor<T, U> processor = new SingleMultiMapperProcessor<>(mapper);
        this.subscribe(processor);
        return processor;
    }

    /**
     * Exposes this {@link Single} instance as a {@link CompletableFuture}.
     * @return CompletableFuture
     */
    default CompletableFuture<T> toFuture() {
        try {
            SingleToCompletableFuture<T> subscriber =
                    new SingleToCompletableFuture<>();
            this.subscribe(subscriber);
            return subscriber;
        } catch (Throwable ex) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(ex);
            return future;
        }
    }

    /**
     * Create a {@link Single} instance from a {@link CompletionStage}.
     * @param <T> item type
     * @param future source future
     * @return Single
     */
    static <T> Single<T> fromFuture(CompletionStage<? extends T> future) {
        return new SingleFromCompletionStage<>(future);
    }

    /**
     * Create a {@link Single} instance that publishes the first item received
     * from the given publisher.
     * @param <T> item type
     * @param source source publisher
     * @return Single
     */
    @SuppressWarnings("unchecked")
    static <T> Single<T> from(Publisher<? extends T> source) {
        if (source instanceof Single) {
            return (Single<T>) source;
        }
        return new SingleNext<>(source);
    }

    /**
     * Create a {@link Single} instance that publishes the given item to its
     * subscriber(s).
     *
     * @param <T> item type
     * @param item item to publish
     * @return Single
     */
    static <T> Single<T> just(T item) {
        return new SingleJust<>(item);
    }

    /**
     * Create a {@link Single} instance that reports the given given exception to
     * its subscriber(s). The exception is reported by invoking
     * {@link Subscriber#onError(java.lang.Throwable)} when
     * {@link Publisher#subscribe(Subscriber)} is called.
     *
     * @param <T> item type
     * @param error exception to hold
     * @return Single
     */
    static <T> Single<T> error(Throwable error) {
        return new SingleError<>(error);
    }

    /**
     * Get a {@link Single} instance that completes immediately.
     *
     * @param <T> item type
     * @return Single
     */
    static <T> Single<T> empty() {
        return SingleEmpty.<T>instance();
    }

    /**
     * Get a {@link Single} instance that never completes.
     * @param <T> item type
     * @return Single
     */
    static <T> Single<T> never() {
        return SingleNever.<T>instance();
    }
}
