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

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.reactive.Flow.Publisher;
import io.helidon.common.reactive.Flow.Subscriber;
import io.helidon.common.reactive.Flow.Subscription;

import static io.helidon.common.CollectionsHelper.listOf;

/**
 * Multiple items publisher facility.
 * @param <T> item type
 */
public interface Multi<T> extends Publisher<T> {

    /**
     * Subscribe to this {@link Multi} instance with the given delegate
     * functions.
     *
     * @param consumer onNext delegate function
     */
    default void subscribe(Consumer<? super T> consumer) {
        this.subscribe(new FunctionalSubscriber<>(consumer, null, null, null));
    }

    /**
     * Subscribe to this {@link Multi} instance with the given delegate
     * functions.
     *
     * @param consumer onNext delegate function
     * @param errorConsumer onError delegate function
     */
    default void subscribe(Consumer<? super T> consumer,
            Consumer<? super Throwable> errorConsumer) {

        this.subscribe(new FunctionalSubscriber<>(consumer, errorConsumer,
                null, null));
    }

    /**
     * Subscribe to this {@link Multi} instance with the given delegate
     * functions.
     *
     * @param consumer onNext delegate function
     * @param errorConsumer onError delegate function
     * @param completeConsumer onComplete delegate function
     */
    default void subscribe(Consumer<? super T> consumer,
            Consumer<? super Throwable> errorConsumer,
            Runnable completeConsumer) {

        this.subscribe(new FunctionalSubscriber<>(consumer, errorConsumer,
                completeConsumer, null));
    }

    /**
     * Subscribe to this {@link Multi} instance with the given delegate
     * functions.
     *
     * @param consumer onNext delegate function
     * @param errorConsumer onError delegate function
     * @param completeConsumer onComplete delegate function
     * @param subscriptionConsumer onSusbcribe delegate function
     */
    default void subscribe(Consumer<? super T> consumer,
            Consumer<? super Throwable> errorConsumer,
            Runnable completeConsumer,
            Consumer<? super Subscription> subscriptionConsumer) {

        this.subscribe(new FunctionalSubscriber<>(consumer, errorConsumer,
                completeConsumer, subscriptionConsumer));
    }

    /**
     * Map this {@link Multi} instance to a new {@link Multi} of another type
     * using the given {@link MultiMapper}.
     * @param <U> mapped item type
     * @param mapper mapper
     * @return Multi
     */
    default <U> Multi<U> map(MultiMapper<T, U> mapper) {
        return mapper;
    }

    /**
     * Map this {@link Multi} instance to a new {@link Multi} of another type
     * using the given java {@link Function}.
     *
     * @param <U> mapped item type
     * @param function mapper function
     * @return Multi
     */
    default <U> Multi<U> map(Function<T, U> function) {
        MultiMapperFunctional<T, U> mapper = new MultiMapperFunctional<>(function);
        this.subscribe(mapper);
        return mapper;
    }

    /**
     * Collect the items of this {@link Multi} instance into a {@link Mono} of
     * {@link List}.
     *
     * @return Mono
     */
    default Mono<List<T>> collectList() {
        MonoListCollector<T> collector = new MonoListCollector<>();
        this.subscribe(collector);
        return collector;
    }

    /**
     * Collect the items of this {@link Multi} instance into a {@link Mono} of
     * {@link String}.
     *
     * @return Mono
     */
    default Mono<String> collectString() {
        MonoStringCollector<T> collector = new MonoStringCollector<>();
        this.subscribe(collector);
        return collector;
    }

    /**
     * Collect the items of this {@link Multi} instance into a {@link Mono}.
     * @param <U> collector container type
     * @param collector collector to use
     * @return Mono
     */
    default <U> Mono<U> collect(MonoCollector<? super T, U> collector) {
        this.subscribe(collector);
        return collector;
    }

    /**
     * Create a {@link Multi} instance wrapped around the given publisher.
     *
     * @param <T> item type
     * @param source source publisher
     * @return Multi
     */
    static <T> Multi<T> from(Publisher<T> source) {
        return new MultiFromPublisher<>(source);
    }

    /**
     * Create a {@link Multi} instance that publishes the given items to a
     * single subscriber.
     *
     * @param <T> item type
     * @param items items to publish
     * @return Multi
     */
    static <T> Multi<T> just(Collection<T> items) {
        return new MultiFromPublisher<>(new FixedItemsPublisher<>(items));
    }

    /**
     * Create a {@link Multi} instance that publishes the given items to a
     * single subscriber.
     *
     * @param <T> item type
     * @param items items to publish
     * @return Multi
     */
    @SafeVarargs
    static <T> Multi<T> just(T... items) {
        return new MultiFromPublisher<>(new FixedItemsPublisher<>(listOf(items)));
    }

    /**
     * Create a {@link Multi} instance that reports the given given exception to
     * its subscriber(s). The exception is reported by invoking
     * {@link Subscriber#onError(java.lang.Throwable)} when
     * {@link Publisher#subscribe(Subscriber)} is called.
     *
     * @param <T> item type
     * @param error exception to hold
     * @return Multi
     */
    static <T> Multi<T> error(Throwable error) {
        return new MultiError<>(error);
    }

    /**
     * Get a {@link Multi} instance that completes immediately.
     *
     * @param <T> item type
     * @return Multi
     */
    static <T> Multi<T> empty() {
        return MultiEmpty.<T>instance();
    }

    /**
     * Get a {@link Multi} instance that never completes.
     * @param <T> item type
     * @return Multi
     */
    static <T> Multi<T> never() {
        return MultiNever.<T>instance();
    }
}
