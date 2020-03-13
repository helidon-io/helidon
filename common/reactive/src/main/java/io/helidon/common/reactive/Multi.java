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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
        Objects.requireNonNull(mapper, "mapper is null");
        return new MultiMapperPublisher<>(this, mapper);
    }

    /**
     * Invoke provided consumer for every item in stream.
     *
     * @param consumer consumer to be invoked
     * @return Multi
     */
    default Multi<T> peek(Consumer<T> consumer) {
        return new MultiTappedPublisher<>(this, null, consumer,
                null, null, null, null);
    }

    /**
     * Filter out all duplicates.
     *
     * @return Multi
     */
    default Multi<T> distinct() {
        return new MultiDistinctPublisher<>(this, v -> v);
    }

    /**
     * Filter stream items with provided predicate.
     *
     * @param predicate predicate to filter stream with
     * @return Multi
     */
    default Multi<T> filter(Predicate<T> predicate) {
        return new MultiFilterPublisher<>(this, predicate);
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
        return new MultiTakeWhilePublisher<>(this, predicate);
    }

    /**
     * Drop the longest prefix of elements from this stream that satisfy the given predicate.
     * As long as predicate returns true, items from upstream are NOT sent to downstream but being dropped,
     * predicate is never called again after it returns false for the first time.
     *
     * @param predicate predicate to filter stream with
     * @return Multi
     */
    default Multi<T> dropWhile(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        return new MultiDropWhilePublisher<>(this, predicate);
    }

    /**
     * Limit stream to allow only specified number of items to pass.
     *
     * @param limit with expected number of items to be produced
     * @return Multi
     */
    default Multi<T> limit(long limit) {
        return new MultiLimitPublisher<>(this, limit);
    }

    /**
     * Skip first n items, all the others are emitted.
     *
     * @param skip number of items to be skipped
     * @return Multi
     */
    default Multi<T> skip(long skip) {
        return new MultiSkipPublisher<>(this, skip);
    }

    /**
     * Transform item with supplied function and flatten resulting {@link Flow.Publisher} to downstream.
     *
     * @param publisherMapper {@link Function} receiving item as parameter and returning {@link Flow.Publisher}
     * @param <U>             output item type
     * @return Multi
     */
    default <U> Multi<U> flatMap(Function<T, Flow.Publisher<U>> publisherMapper) {
        return new MultiFlatMapPublisher<>(this, publisherMapper, 32, 32, false);
    }
    /**
     * Transform item with supplied function and flatten resulting {@link Flow.Publisher} to downstream
     * while limiting the maximum number of concurrent inner {@link Flow.Publisher}s and their in-flight
     * item count, optionally aggregating and delaying all errors until all sources terminate.
     *
     * @param mapper {@link Function} receiving item as parameter and returning {@link Flow.Publisher}
     * @param <U>             output item type
     * @param maxConcurrency the maximum number of inner sources to run
     * @param delayErrors if true, any error from the main and inner sources are aggregated and delayed until
     *                    all of them terminate
     * @param prefetch the number of items to request upfront from the inner sources, then request 75% more after 75%
     *                 has been delivered
     * @return Multi
     */
    default <U> Multi<U> flatMap(Function<T, Flow.Publisher<U>> mapper, long maxConcurrency, boolean delayErrors, long prefetch) {
        return new MultiFlatMapPublisher<>(this, mapper, maxConcurrency, prefetch, delayErrors);
    }

    /**
     * Transform item with supplied function and flatten resulting {@link Iterable} to downstream.
     *
     * @param iterableMapper {@link Function} receiving item as parameter and returning {@link Iterable}
     * @param <U>            output item type
     * @return Multi
     */
    default <U> Multi<U> flatMapIterable(Function<? super T, ? extends Iterable<? extends U>> iterableMapper) {
        return flatMapIterable(iterableMapper, 32);
    }

    /**
     * Transform item with supplied function and flatten resulting {@link Iterable} to downstream.
     *
     * @param iterableMapper {@link Function} receiving item as parameter and returning {@link Iterable}
     * @param prefetch the number of upstream items to request upfront, then 75% of this value after
     *                 75% received and mapped
     * @param <U>            output item type
     * @return Multi
     */
    default <U> Multi<U> flatMapIterable(Function<? super T, ? extends Iterable<? extends U>> iterableMapper,
                                         int prefetch) {
        Objects.requireNonNull(iterableMapper, "iterableMapper is null");
        return new MultiFlatMapIterable<>(this, iterableMapper, prefetch);
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
        return collect(ArrayList::new, List::add);
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
        return collect(() -> collector, Collector::collect).map(Collector::value);
    }

    /**
     * Collect the items of this {@link Multi} into a collection provided via a {@link Supplier}
     * and mutated by a {@code BiConsumer} callback.
     * @param collectionSupplier the {@link Supplier} that is called for each incoming {@link Subscriber}
     *                           to create a fresh collection to collect items into
     * @param accumulator the {@link BiConsumer} that receives the collection and the current item to put in
     * @param <U> the type of the collection and result
     * @return Single
     * @throws NullPointerException if {@code collectionSupplier} or {@code combiner} is {@code null}
     */
    default <U> Single<U> collect(Supplier<U> collectionSupplier, BiConsumer<U, T> accumulator) {
        Objects.requireNonNull(collectionSupplier, "collectionSupplier is null");
        Objects.requireNonNull(accumulator, "combiner is null");
        return new MultiCollectPublisher<>(this, collectionSupplier, accumulator);
    }

    /**
     * Collects up upstream items with the help of a the callbacks of
     * a {@link java.util.stream.Collector}.
     * @param collector the collector whose {@code supplier()}, {@code accumulator()} and {@code finisher()} callbacks
     *                  are used for collecting upstream items into a final form.
     * @param <A> the accumulator type
     * @param <R> the result type
     * @return Single
     * @throws NullPointerException if {@code collector} is {@code null}
     */
    default <A, R> Single<R> collectStream(java.util.stream.Collector<T, A, R> collector) {
        Objects.requireNonNull(collector, "collector is null");
        return new MultiCollectorPublisher<>(this, collector);
    }

    /**
     * Get the first item of this {@link Multi} instance as a {@link Single}.
     *
     * @return Single
     */
    default Single<T> first() {
        return new MultiFirstPublisher<>(this);
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
        return new MultiFromIterable<>(iterable);
    }


    /**
     * Create a {@link Multi} instance that publishes the given items to a single subscriber.
     *
     * @param <T>   item type
     * @param items items to publish
     * @return Multi
     * @throws NullPointerException if {@code items} is {@code null}
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
     * @throws NullPointerException if {@code items} is {@code null}
     */
    @SafeVarargs
    static <T> Multi<T> just(T... items) {
        if (items.length == 0) {
            return empty();
        }
        if (items.length == 1) {
            return singleton(items[0]);
        }
        return new MultiFromArrayPublisher<>(items);
    }

    /**
     * Create a {@link Multi} that emits a pre-existing item and then completes.
     * @param item the item to emit.
     * @param <T> the type of the item
     * @return Multi
     * @throws NullPointerException if {@code item} is {@code null}
     */
    static <T> Multi<T> singleton(T item) {
        Objects.requireNonNull(item, "item is null");
        return new MultiJustPublisher<>(item);
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

    /**
     * Executes given {@link java.lang.Runnable} when any of signals onComplete, onCancel or onError is received.
     *
     * @param onTerminate {@link java.lang.Runnable} to be executed.
     * @return Multi
     */
    default Multi<T> onTerminate(Runnable onTerminate) {
        return new MultiTappedPublisher<>(this,
                null,
                null,
                e -> onTerminate.run(),
                onTerminate,
                null,
                onTerminate);
    }

    /**
     * Executes given {@link java.lang.Runnable} when onComplete signal is received.
     *
     * @param onTerminate {@link java.lang.Runnable} to be executed.
     * @return Multi
     */
    default Multi<T> onComplete(Runnable onTerminate) {
        return new MultiTappedPublisher<>(this,
                null,
                null,
                null,
                onTerminate,
                null,
                null);
    }

    /**
     * Executes given {@link java.lang.Runnable} when onError signal is received.
     *
     * @param onErrorConsumer {@link java.lang.Runnable} to be executed.
     * @return Multi
     */
    default Multi<T> onError(Consumer<Throwable> onErrorConsumer) {
        return new MultiTappedPublisher<>(this,
                null,
                null,
                onErrorConsumer,
                null,
                null,
                null);
    }

    /**
     * Relay upstream items until the other source signals an item or completes.
     * @param other the other sequence to signal the end of the main sequence
     * @param <U> the element type of the other sequence
     * @return Multi
     * @throws NullPointerException if {@code other} is {@code null}
     */
    default <U> Multi<T> takeUntil(Flow.Publisher<U> other) {
        Objects.requireNonNull(other, "other is null");
        return new MultiTakeUntilPublisher<>(this, other);
    }

    /**
     * Emits a range of ever increasing integers.
     * @param start the initial integer value
     * @param count the number of integers to emit
     * @return Multi
     * @throws IllegalArgumentException if {@code count} is negative
     */
    static Multi<Integer> range(int start, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count >= required");
        }
        if (count == 0) {
            return empty();
        }
        if (count == 1) {
            return singleton(start);
        }
        return new MultiRangePublisher(start, start + count);
    }

    /**
     * Emits a range of ever increasing longs.
     * @param start the initial long value
     * @param count the number of longs to emit
     * @return Multi
     * @throws IllegalArgumentException if {@code count} is negative
     */
    static Multi<Long> rangeLong(long start, long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count >= required");
        }
        if (count == 0) {
            return empty();
        }
        if (count == 1) {
            return singleton(start);
        }
        return new MultiRangeLongPublisher(start, start + count);
    }

    /**
     * {@link java.util.function.Function} providing one item to be submitted as onNext in case of onError signal is received.
     *
     * @param onError Function receiving {@link java.lang.Throwable} as argument and producing one item to resume stream with.
     * @return Multi
     */
    default Multi<T> onErrorResume(Function<? super Throwable, ? extends T> onError) {
        return onErrorResumeWith(e -> Multi.singleton(onError.apply(e)));
    }

    /**
     * Resume stream from supplied publisher if onError signal is intercepted.
     *
     * @param onError supplier of new stream publisher
     * @return Multi
     */
    default Multi<T> onErrorResumeWith(Function<? super Throwable, ? extends Flow.Publisher<? extends T>> onError) {
        return new MultiOnErrorResumeWith<>(this, onError);
    }
}
