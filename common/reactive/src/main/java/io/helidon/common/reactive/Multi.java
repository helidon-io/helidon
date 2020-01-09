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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import io.helidon.common.mapper.Mapper;

/**
 * Multiple items publisher facility.
 *
 * @param <T> item type
 */
public interface Multi<T> extends Subscribable<T> {

    /**
     * Map this {@link Multi} instance to a new {@link Multi} of another type using the given {@link Mapper}.
     *
     * @param <U>    mapped item type
     * @param mapper mapper
     * @return Multi
     * @throws NullPointerException if mapper is {@code null}
     */
    default <U> Multi<U> map(Mapper<T, U> mapper) {
        MultiMapProcessor<T, U> processor = MultiMapProcessor.create(mapper);
        this.subscribe(processor);
        return processor;
    }

    /**
     * Invoke provided consumer for every item in stream.
     *
     * @param consumer consumer to be invoked
     * @return Multi
     */
    default Multi<T> peek(Consumer<T> consumer) {
        MultiPeekProcessor<T> processor = MultiPeekProcessor.create(consumer);
        this.subscribe(processor);
        return processor;
    }

    /**
     * Filter out all duplicates.
     *
     * @return Multi
     */
    default Multi<T> distinct() {
        MultiDistinctProcessor<T> processor = MultiDistinctProcessor.create();
        this.subscribe(processor);
        return processor;
    }

    /**
     * Filter stream items with provided predicate.
     *
     * @param predicate predicate to filter stream with
     * @return Multi
     */
    default Multi<T> filter(Predicate<T> predicate) {
        MultiFilterProcessor<T> processor = MultiFilterProcessor.create(predicate);
        this.subscribe(processor);
        return processor;
    }

    /**
     * Take the longest prefix of elements from this stream that satisfy the given predicate.
     * As long as predicate returns true, items from upstream are sent to downstream,
     * when predicate returns false stream is completed.
     *
     * @param predicate predicate to filter stream with
     * @return Multi
     */
    default Multi<T> takeWhile(Predicate<T> predicate) {
        MultiTakeWhileProcessor<T> processor = MultiTakeWhileProcessor.create(predicate);
        this.subscribe(processor);
        return processor;
    }

    /**
     * Drop the longest prefix of elements from this stream that satisfy the given predicate.
     * As long as predicate returns true, items from upstream are NOT sent to downstream but being dropped,
     * predicate is never called again after it returns false for the first time.
     *
     * @param predicate predicate to filter stream with
     * @return Multi
     */
    default Multi<T> dropWhile(Predicate<T> predicate) {
        MultiDropWhileProcessor<T> processor = MultiDropWhileProcessor.create(predicate);
        this.subscribe(processor);
        return processor;
    }

    /**
     * Limit stream to allow only specified number of items to pass.
     *
     * @param limit with expected number of items to be produced
     * @return Multi
     */
    default Multi<T> limit(long limit) {
        MultiLimitProcessor<T> processor = MultiLimitProcessor.create(limit);
        this.subscribe(processor);
        return processor;
    }

    /**
     * Skip first n items, all the others are emitted.
     *
     * @param skip number of items to be skipped
     * @return Multi
     */
    default Multi<T> skip(long skip) {
        MultiSkipProcessor<T> processor = MultiSkipProcessor.create(skip);
        this.subscribe(processor);
        return processor;
    }

    /**
     * Transform item with supplied function and flatten resulting {@link Flow.Publisher} to downstream.
     *
     * @param publisherMapper {@link Function} receiving item as parameter and returning {@link Flow.Publisher}
     * @return Multi
     */
    default Multi<T> flatMap(Function<T, Flow.Publisher<T>> publisherMapper) {
        MultiFlatMapProcessor<T> processor = MultiFlatMapProcessor.fromPublisherMapper(publisherMapper);
        this.subscribe(processor);
        return processor;
    }

    /**
     * Transform item with supplied function and flatten resulting {@link Iterable} to downstream.
     *
     * @param iterableMapper {@link Function} receiving item as parameter and returning {@link Iterable}
     * @return Multi
     */
    default Multi<T> flatMapIterable(Function<T, Iterable<T>> iterableMapper) {
        MultiFlatMapProcessor<T> processor = MultiFlatMapProcessor.fromIterableMapper(iterableMapper);
        this.subscribe(processor);
        return processor;
    }

    /**
     * Terminal stage, invokes provided consumer for every item in the stream.
     *
     * @param consumer consumer to be invoked for each item
     */
    default void forEach(Consumer<T> consumer) {
        FunctionalSubscriber<T> subscriber = new FunctionalSubscriber<>(consumer, null, null, null);
        this.subscribe(subscriber);
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
     * @param <U>       collector container type
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
     *
     * @return Single
     */
    default Single<T> first() {
        MultiFirstProcessor<T> processor = MultiFirstProcessor.create();
        this.subscribe(processor);
        return processor;
    }

    /**
     * Create a {@link Multi} instance wrapped around the given publisher.
     *
     * @param <T>    item type
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
     * Create a {@link Multi} instance that publishes the given iterable.
     *
     * @param <T>      item type
     * @param iterable iterable to publish
     * @return Multi
     * @throws NullPointerException if iterable is {@code null}
     */
    static <T> Multi<T> from(Iterable<T> iterable) {
        return Multi.from(IterablePublisher.create(iterable));
    }


    /**
     * Create a {@link Multi} instance that publishes the given items to a single subscriber.
     *
     * @param <T>   item type
     * @param items items to publish
     * @return Multi
     * @throws NullPointerException if items is {@code null}
     */
    static <T> Multi<T> just(Collection<T> items) {
        return Multi.from(items);
    }

    /**
     * Create a {@link Multi} instance that publishes the given items to a single subscriber.
     *
     * @param <T>   item type
     * @param items items to publish
     * @return Multi
     * @throws NullPointerException if items is {@code null}
     */
    @SafeVarargs
    static <T> Multi<T> just(T... items) {
        return Multi.from(List.of(items));
    }

    /**
     * Create a {@link Multi} instance that reports the given exception to its subscriber(s). The exception is reported by
     * invoking {@link Subscriber#onError(java.lang.Throwable)} when {@link Publisher#subscribe(Subscriber)} is called.
     *
     * @param <T>   item type
     * @param error exception to hold
     * @return Multi
     * @throws NullPointerException if error is {@code null}
     */
    static <T> Multi<T> error(Throwable error) {
        return MultiError.create(error);
    }

    /**
     * Get a {@link Multi} instance that completes immediately.
     *
     * @param <T> item type
     * @return Multi
     */
    static <T> Multi<T> empty() {
        return MultiEmpty.instance();
    }

    /**
     * Get a {@link Multi} instance that never completes.
     *
     * @param <T> item type
     * @return Multi
     */
    static <T> Multi<T> never() {
        return MultiNever.instance();
    }

    /**
     * Concat streams to one.
     *
     * @param firstMulti  first stream
     * @param secondMulti second stream
     * @param <T>         item type
     * @return Multi
     */
    static <T> Multi<T> concat(Multi<T> firstMulti, Multi<T> secondMulti) {
        return ConcatPublisher.create(firstMulti, secondMulti);
    }
}
