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

import io.helidon.common.reactive.Flow.Publisher;
import io.helidon.common.reactive.Flow.Subscriber;
import java.util.function.Consumer;

/**
 * Single item publisher utility.
 *
 * @param <T> item type
 */
public interface Single<T> extends Publisher<T> {

    /**
     * Subscribe to this {@link Single} instance with the given delegate functions.
     *
     * @param consumer onNext delegate function
     */
    default void subscribe(Consumer<? super T> consumer) {
        this.subscribe(new FunctionalSubscriber<>(consumer, null, null, null));
    }

    /**
     * Subscribe to this {@link Single} instance with the given delegate functions.
     *
     * @param consumer onNext delegate function
     * @param errorConsumer onError delegate function
     */
    default void subscribe(Consumer<? super T> consumer, Consumer<? super Throwable> errorConsumer) {
        this.subscribe(new FunctionalSubscriber<>(consumer, errorConsumer, null, null));
    }

    /**
     * Subscribe to this {@link Single} instance with the given delegate functions.
     *
     * @param consumer onNext delegate function
     * @param errorConsumer onError delegate function
     * @param completeConsumer onComplete delegate function
     */
    default void subscribe(Consumer<? super T> consumer, Consumer<? super Throwable> errorConsumer, Runnable completeConsumer) {
        this.subscribe(new FunctionalSubscriber<>(consumer, errorConsumer, completeConsumer, null));
    }

    /**
     * Subscribe to this {@link Single} instance with the given delegate functions.
     *
     * @param consumer onNext delegate function
     * @param errorConsumer onError delegate function
     * @param completeConsumer onComplete delegate function
     * @param subscriptionConsumer onSusbcribe delegate function
     */
    default void subscribe(Consumer<? super T> consumer, Consumer<? super Throwable> errorConsumer, Runnable completeConsumer,
            Consumer<? super Flow.Subscription> subscriptionConsumer) {

        this.subscribe(new FunctionalSubscriber<>(consumer, errorConsumer, completeConsumer, subscriptionConsumer));
    }

    /**
     * Map this {@link Single} instance to a new {@link Single} of another type using the given {@link Mapper}.
     *
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
     * Map this {@link Single} instance to a publisher using the given {@link Mapper}.
     *
     * @param <U> mapped items type
     * @param mapper mapper
     * @return Publisher
     */
    default <U> Multi<U> mapMany(Mapper<T, Publisher<U>> mapper) {
        SingleMultiMapperProcessor<T, U> processor = new SingleMultiMapperProcessor<>(mapper);
        this.subscribe(processor);
        return processor;
    }

    /**
     * Exposes this {@link Single} instance as a {@link CompletableFuture}. Note that if this {@link Single} completes without a
     * value, the resulting {@link CompletableFuture} will be completed exceptionally with an {@link IllegalStateException}
     *
     * @return CompletableFuture
     */
    default CompletableFuture<T> toFuture() {
        try {
            SingleToCompletableFuture<T> subscriber = new SingleToCompletableFuture<>();
            this.subscribe(subscriber);
            return subscriber;
        } catch (Throwable ex) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(ex);
            return future;
        }
    }

    /**
     * Create a {@link Single} instance that publishes the first and only item received from the given publisher. Note that if the
     * publisher publishes more than one item, the resulting {@link Single} will hold an error. Use {@link Multi#first()} instead
     * in order to get the first item of a publisher that may publish more than one item.
     *
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
     * Create a {@link Single} instance that publishes the given item to its subscriber(s).
     *
     * @param <T> item type
     * @param item item to publish
     * @return Single
     */
    static <T> Single<T> just(T item) {
        return new SingleJust<>(item);
    }

    /**
     * Create a {@link Single} instance that reports the given given exception to its subscriber(s). The exception is reported by
     * invoking {@link Subscriber#onError(java.lang.Throwable)} when {@link Publisher#subscribe(Subscriber)} is called.
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
     *
     * @param <T> item type
     * @return Single
     */
    static <T> Single<T> never() {
        return SingleNever.<T>instance();
    }
}
