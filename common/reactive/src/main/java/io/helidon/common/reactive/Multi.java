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

import io.helidon.common.mapper.Mapper;
import io.helidon.common.reactive.Flow.Publisher;
import io.helidon.common.reactive.Flow.Subscriber;

import static io.helidon.common.CollectionsHelper.listOf;

/**
 * Multiple items publisher facility.
 * @param <T> item type
 */
public interface Multi<T> extends Subscribable<T> {

    /**
     * Map this {@link Multi} instance to a new {@link Multi} of another type using the given {@link Mapper}.
     *
     * @param <U> mapped item type
     * @param mapper mapper
     * @return Multi
     * @throws NullPointerException if mapper is {@code null}
     */
    default <U> Multi<U> map(Mapper<T, U> mapper) {
        MultiMappingProcessor<T, U> processor = new MultiMappingProcessor<>(mapper);
        this.subscribe(processor);
        return processor;
    }

    /**
     * Collect the items of this {@link Multi} instance into a {@link Single} of {@link List}.
     *
     * @return Single
     */
    default Single<List<T>> collectList() {
        return collect(new ListCollector<>());
    }

    /**
     * Collect the items of this {@link Multi} instance into a {@link Single}.
     *
     * @param <U> collector container type
     * @param collector collector to use
     * @return Single
     * @throws NullPointerException if collector is {@code null}
     */
    default <U> Single<U> collect(Collector<T, U> collector) {
        MultiCollectingProcessor<? super T, U> processor = new MultiCollectingProcessor<>(collector);
        this.subscribe(processor);
        return processor;
    }

    /**
     * Get the first item of this {@link Multi} instance as a {@link Single}.
     * @return Single
     */
    default Single<T> first() {
        MultiFirstProcessor<T> processor = new MultiFirstProcessor<>();
        this.subscribe(processor);
        return processor;
    }

    /**
     * Create a {@link Multi} instance wrapped around the given publisher.
     *
     * @param <T> item type
     * @param source source publisher
     * @return Multi
     * @throws NullPointerException if source is {@code null}
     */
    @SuppressWarnings("unchecked")
    static <T> Multi<T> from(Publisher<T> source) {
        if (source instanceof Multi) {
            return (Multi<T>) source;
        }
        return new MultiFromPublisher<>(source);
    }

    /**
     * Create a {@link Multi} instance that publishes the given items to a single subscriber.
     *
     * @param <T> item type
     * @param items items to publish
     * @return Multi
     * @throws NullPointerException if items is {@code null}
     */
    static <T> Multi<T> just(Collection<T> items) {
        return new MultiFromPublisher<>(new FixedItemsPublisher<>(items));
    }

    /**
     * Create a {@link Multi} instance that publishes the given items to a single subscriber.
     *
     * @param <T> item type
     * @param items items to publish
     * @return Multi
     * @throws NullPointerException if items is {@code null}
     */
    @SafeVarargs
    static <T> Multi<T> just(T... items) {
        return new MultiFromPublisher<>(new FixedItemsPublisher<>(listOf(items)));
    }

    /**
     * Create a {@link Multi} instance that reports the given exception to its subscriber(s). The exception is reported by
     * invoking {@link Subscriber#onError(java.lang.Throwable)} when {@link Publisher#subscribe(Subscriber)} is called.
     *
     * @param <T> item type
     * @param error exception to hold
     * @return Multi
     * @throws NullPointerException if error is {@code null}
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
     *
     * @param <T> item type
     * @return Multi
     */
    static <T> Multi<T> never() {
        return MultiNever.<T>instance();
    }
}
