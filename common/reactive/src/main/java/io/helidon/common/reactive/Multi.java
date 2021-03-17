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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Represents a {@link Flow.Publisher} emitting zero or more items, optionally followed by
 * an error or completion.
 *
 * @param <T> item type
 * @see Single
 */
public interface Multi<T> extends Subscribable<T> {

    // --------------------------------------------------------------------------------------------------------
    // Factory (source-like) methods
    // --------------------------------------------------------------------------------------------------------

    /**
     * Concat streams to one.
     *
     * @param firstMulti  first stream
     * @param secondMulti second stream
     * @param <T>         item type
     * @return Multi
     */
    static <T> Multi<T> concat(Flow.Publisher<T> firstMulti, Flow.Publisher<T> secondMulti) {
        return ConcatPublisher.create(firstMulti, secondMulti);
    }

    /**
     * Concat streams to one.
     *
     * @param firstPublisher  first stream
     * @param secondPublisher second stream
     * @param morePublishers  more publishers to concat
     * @param <T>             item type
     * @return Multi
     */
    @SafeVarargs
    @SuppressWarnings({"varargs", "unchecked"})
    static <T> Multi<T> concat(Flow.Publisher<T> firstPublisher,
                               Flow.Publisher<T> secondPublisher,
                               Flow.Publisher<T>... morePublishers) {
        Flow.Publisher<T>[] prefixed = new Flow.Publisher[2 + morePublishers.length];
        prefixed[0] = firstPublisher;
        prefixed[1] = secondPublisher;
        System.arraycopy(morePublishers, 0, prefixed, 2, morePublishers.length);
        return concatArray(prefixed);
    }

    /**
     * Concatenates an array of source {@link Flow.Publisher}s by relaying items
     * in order, non-overlappingly, one after the other finishes.
     * @param publishers  more publishers to concat
     * @param <T>         item type
     * @return Multi
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    static <T> Multi<T> concatArray(Flow.Publisher<T>... publishers) {
        if (publishers.length == 0) {
            return empty();
        } else if (publishers.length == 1) {
            return Multi.create(publishers[0]);
        }
        return new MultiConcatArray<>(publishers);
    }

    /**
     * Call the given supplier function for each individual downstream Subscriber
     * to return a Flow.Publisher to subscribe to.
     * @param supplier the callback to return a Flow.Publisher for each Subscriber
     * @param <T> the element type of the sequence
     * @return Multi
     * @throws NullPointerException if {@code supplier} is {@code null}
     */
    static <T> Multi<T> defer(Supplier<? extends Flow.Publisher<? extends T>> supplier) {
        Objects.requireNonNull(supplier, "supplier is null");
        return new MultiDefer<>(supplier);
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
     * Wrap a CompletionStage into a Multi and signal its outcome non-blockingly.
     * <p>
     *     A null result from the CompletionStage will yield a
     *     {@link NullPointerException} signal.
     * </p>
     * @param completionStage the CompletionStage to
     * @param <T> the element type of the stage and result
     * @return Multi
     * @see #create(CompletionStage, boolean)
     * @deprecated use {@link #create(java.util.concurrent.CompletionStage)} instead
     */
    @Deprecated
    static <T> Multi<T> from(CompletionStage<T> completionStage) {
        return create(completionStage);
    }

    /**
     * Wrap a CompletionStage into a Multi and signal its outcome non-blockingly.
     * @param completionStage the CompletionStage to
     * @param nullMeansEmpty if true, a null result is interpreted to be an empty sequence
     *                       if false, the resulting sequence fails with {@link NullPointerException}
     * @param <T> the element type of the stage and result
     * @return Multi
     * @deprecated use {@link #create(java.util.concurrent.CompletionStage, boolean)} instead
     */
    @Deprecated
    static <T> Multi<T> from(CompletionStage<T> completionStage, boolean nullMeansEmpty) {
        return create(completionStage, nullMeansEmpty);
    }

    /**
     * Create a {@link Multi} instance that publishes the given iterable.
     *
     * @param <T>      item type
     * @param iterable iterable to publish
     * @return Multi
     * @throws NullPointerException if iterable is {@code null}
     * @deprecated use {@link #create(java.lang.Iterable)} instead
     */
    @Deprecated
    static <T> Multi<T> from(Iterable<T> iterable) {
        return create(iterable);
    }

    /**
     * Create a {@link Multi} instance wrapped around the given publisher.
     *
     * @param <T>    item type
     * @param source source publisher
     * @return Multi
     * @throws NullPointerException if source is {@code null}
     * @deprecated use {@link #create(java.util.concurrent.Flow.Publisher)} instead
     */
    @Deprecated
    static <T> Multi<T> from(Publisher<T> source) {
        return create(source);
    }

    /**
     * Create a {@link Multi} instance wrapped around the given {@link Single}.
     *
     * @param <T>    item type
     * @param single source {@link Single} publisher
     * @return Multi
     * @throws NullPointerException if source is {@code null}
     * @deprecated use {@link #create(io.helidon.common.reactive.Single)} instead
     */
    @Deprecated
    static <T> Multi<T> from(Single<T> single) {
        return create(single);
    }

    /**
     * Create a {@link Multi} instance that publishes the given {@link Stream}.
     * <p>
     *     Note that Streams can be only consumed once, therefore, the
     *     returned Multi will signal {@link IllegalStateException} if
     *     multiple subscribers try to consume it.
     * <p>
     *     The operator calls {@link Stream#close()} when the stream finishes,
     *     fails or the flow gets canceled. To avoid closing the stream automatically,
     *     it is recommended to turn the {@link Stream} into an {@link Iterable}
     *     via {@link Stream#iterator()} and use {@link #create(Iterable)}:
     *     <pre>{@code
     *     Stream<T> stream = ...
     *     Multi<T> multi = Multi.create(stream::iterator);
     *     }</pre>
     *
     * @param <T>      item type
     * @param stream the Stream to publish
     * @return Multi
     * @throws NullPointerException if {@code stream} is {@code null}
     * @deprecated use {@link #create(java.util.stream.Stream)} instead
     */
    @Deprecated
    static <T> Multi<T> from(Stream<T> stream) {
        return create(stream);
    }

    /**
     * Wrap a CompletionStage into a Multi and signal its outcome non-blockingly.
     * <p>
     *     A null result from the CompletionStage will yield a
     *     {@link NullPointerException} signal.
     * </p>
     * @param completionStage the CompletionStage to
     * @param <T> the element type of the stage and result
     * @return Multi
     * @see #create(CompletionStage, boolean)
     */
    static <T> Multi<T> create(CompletionStage<T> completionStage) {
        return create(completionStage, false);
    }

    /**
     * Wrap a CompletionStage into a Multi and signal its outcome non-blockingly.
     * @param completionStage the CompletionStage to
     * @param nullMeansEmpty if true, a null result is interpreted to be an empty sequence
     *                       if false, the resulting sequence fails with {@link NullPointerException}
     * @param <T> the element type of the stage and result
     * @return Multi
     */
    static <T> Multi<T> create(CompletionStage<T> completionStage, boolean nullMeansEmpty) {
        Objects.requireNonNull(completionStage, "completionStage is null");
        return new MultiFromCompletionStage<>(completionStage, nullMeansEmpty);
    }

    /**
     * Create a {@link Multi} instance that publishes the given iterable.
     *
     * @param <T>      item type
     * @param iterable iterable to publish
     * @return Multi
     * @throws NullPointerException if iterable is {@code null}
     */
    static <T> Multi<T> create(Iterable<T> iterable) {
        return new MultiFromIterable<>(iterable);
    }

    /**
     * Create a {@link Multi} instance wrapped around the given publisher.
     *
     * @param <T>    item type
     * @param source source publisher
     * @return Multi
     * @throws NullPointerException if source is {@code null}
     */
    static <T> Multi<T> create(Publisher<T> source) {
        if (source instanceof Multi) {
            return (Multi<T>) source;
        }
        return new MultiFromPublisher<>(source);
    }

    /**
     * Create a {@link Multi} instance wrapped around the given {@link Single}.
     *
     * @param <T>    item type
     * @param single source {@link Single} publisher
     * @return Multi
     * @throws NullPointerException if source is {@code null}
     */
    static <T> Multi<T> create(Single<T> single) {
        return create((Publisher<T>) single);
    }

    /**
     * Create a {@link Multi} instance that publishes the given {@link Stream}.
     * <p>
     *     Note that Streams can be only consumed once, therefore, the
     *     returned Multi will signal {@link IllegalStateException} if
     *     multiple subscribers try to consume it.
     * <p>
     *     The operator calls {@link Stream#close()} when the stream finishes,
     *     fails or the flow gets canceled. To avoid closing the stream automatically,
     *     it is recommended to turn the {@link Stream} into an {@link Iterable}
     *     via {@link Stream#iterator()} and use {@link #create(Iterable)}:
     *     <pre>{@code
     *     Stream<T> stream = ...
     *     Multi<T> multi = Multi.create(stream::iterator);
     *     }</pre>
     *
     * @param <T>      item type
     * @param stream the Stream to publish
     * @return Multi
     * @throws NullPointerException if {@code stream} is {@code null}
     */
    static <T> Multi<T> create(Stream<T> stream) {
        Objects.requireNonNull(stream, "stream is null");
        return new MultiFromStream<>(stream);
    }

    /**
     * Signal 0L, 1L and so on periodically to the downstream.
     * <p>
     *     Note that if the downstream applies backpressure,
     *     subsequent values may be delivered instantly upon
     *     further requests from the downstream.
     * </p>
     * @param period the initial and in-between time
     * @param unit the time unit
     * @param executor the scheduled executor to use for the periodic emission
     * @return Multi
     * @throws NullPointerException if {@code unit} or {@code executor} is {@code null}
     */
    static Multi<Long> interval(long period, TimeUnit unit, ScheduledExecutorService executor) {
        return interval(period, period, unit, executor);
    }

    /**
     * Signal 0L after an initial delay, then 1L, 2L and so on periodically to the downstream.
     * <p>
     *     Note that if the downstream applies backpressure,
     *     subsequent values may be delivered instantly upon
     *     further requests from the downstream.
     * </p>
     * @param initialDelay the time before signaling 0L
     * @param period the in-between wait time for values 1L, 2L and so on
     * @param unit the time unit
     * @param executor the scheduled executor to use for the periodic emission
     * @return Multi
     * @throws NullPointerException if {@code unit} or {@code executor} is {@code null}
     */
    static Multi<Long> interval(long initialDelay, long period, TimeUnit unit, ScheduledExecutorService executor) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(executor, "executor is null");
        return new MultiInterval(initialDelay, period, unit, executor);
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
        return Multi.create(items);
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
     * Get a {@link Multi} instance that never completes.
     *
     * @param <T> item type
     * @return Multi
     */
    static <T> Multi<T> never() {
        return MultiNever.instance();
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
     * Signal 0L and complete the sequence after the given time elapsed.
     * @param time the time to wait before signaling 0L and completion
     * @param unit the unit of time
     * @param executor the executor to run the waiting on
     * @return Multi
     * @throws NullPointerException if {@code unit} or {@code executor} is {@code null}
     */
    static Multi<Long> timer(long time, TimeUnit unit, ScheduledExecutorService executor) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(executor, "executor is null");
        return new MultiTimer(time, unit, executor);
    }

    // --------------------------------------------------------------------------------------------------------
    // Instance Operators
    // --------------------------------------------------------------------------------------------------------

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
    default <U> Single<U> collect(Supplier<? extends U> collectionSupplier, BiConsumer<U, T> accumulator) {
        Objects.requireNonNull(collectionSupplier, "collectionSupplier is null");
        Objects.requireNonNull(accumulator, "combiner is null");
        return new MultiCollectPublisher<>(this, collectionSupplier, accumulator);
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
     * Apply the given {@code composer} function to the current {@code Multi} instance and
     * return a{@code Multi} wrapping the returned {@link Flow.Publisher} of this function.
     * <p>
     *     Note that the {@code composer} function is executed upon calling this method
     *     immediately and not when the resulting sequence gets subscribed to.
     * </p>
     * @param composer the function that receives the current {@code Multi} instance and
     *                 should return a {@code Flow.Publisher} to be wrapped into a
     *                 {@code Multie} to be returned by the method
     * @param <U> the output element type
     * @return Multi
     * @throws NullPointerException if {@code composer} is {@code null}
     */
    @SuppressWarnings("unchecked")
    default <U> Multi<U> compose(Function<? super Multi<T>, ? extends Flow.Publisher<? extends U>> composer) {
        return create((Flow.Publisher<U>) to(composer));
    }

    /**
     * Signals the default item if the upstream is empty.
     * @param defaultItem the item to signal if the upstream is empty
     * @return Multi
     * @throws NullPointerException if {@code defaultItem} is {@code null}
     */
    default Multi<T> defaultIfEmpty(T defaultItem) {
        Objects.requireNonNull(defaultItem, "defaultItem is null");
        return new MultiDefaultIfEmpty<>(this, defaultItem);
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
     * Filter stream items with provided predicate.
     *
     * @param predicate predicate to filter stream with
     * @return Multi
     */
    default Multi<T> filter(Predicate<? super T> predicate) {
        return new MultiFilterPublisher<>(this, predicate);
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
     * Transform item with supplied function and flatten resulting {@link Flow.Publisher} to downstream.
     *
     * @param publisherMapper {@link Function} receiving item as parameter and returning {@link Flow.Publisher}
     * @param <U>             output item type
     * @return Multi
     */
    default <U> Multi<U> flatMap(Function<? super T, ? extends Flow.Publisher<? extends U>> publisherMapper) {
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
    default <U> Multi<U> flatMap(Function<? super T, ? extends Flow.Publisher<? extends U>> mapper,
                                 long maxConcurrency, boolean delayErrors, long prefetch) {
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
     * Limit stream to allow only specified number of items to pass.
     *
     * @param limit with expected number of items to be produced
     * @return Multi
     */
    default Multi<T> limit(long limit) {
        return new MultiLimitPublisher<>(this, limit);
    }

    /**
     * Map this {@link Multi} instance to a new {@link Multi} of another type using the given {@link Function}.
     *
     * @param <U>    mapped item type
     * @param mapper mapper
     * @return Multi
     * @throws NullPointerException if mapper is {@code null}
     */
    default <U> Multi<U> map(Function<? super T, ? extends U> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        return new MultiMapperPublisher<>(this, mapper);
    }

    /**
     * Re-emit the upstream's signals to the downstream on the given executor's thread
     * using a default buffer size of 32 and errors skipping ahead of items.
     * @param executor the executor to signal the downstream from.
     * @return Multi
     * @throws NullPointerException if {@code executor} is {@code null}
     * @see #observeOn(Executor, int, boolean)
     */
    default Multi<T> observeOn(Executor executor) {
        return observeOn(executor, 32, false);
    }

    /**
     * Re-emit the upstream's signals to the downstream on the given executor's thread.
     * @param executor the executor to signal the downstream from.
     * @param bufferSize the number of items to prefetch and buffer at a time
     * @param delayError if {@code true}, errors are emitted after items,
     *                   if {@code false}, errors may cut ahead of items during emission
     * @return Multi
     * @throws NullPointerException if {@code executor} is {@code null}
     */
    default Multi<T> observeOn(Executor executor, int bufferSize, boolean delayError) {
        Objects.requireNonNull(executor, "executor is null");
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize > 0 required");
        }
        return new MultiObserveOn<>(this, executor, bufferSize, delayError);
    }

    /**
     * Executes given {@link java.lang.Runnable} when a cancel signal is received.
     *
     * @param onCancel {@link java.lang.Runnable} to be executed.
     * @return Multi
     */
    default Multi<T> onCancel(Runnable onCancel) {
        return new MultiTappedPublisher<>(this,
                null,
                null,
                null,
                null,
                null,
                onCancel);
    }

    /**
     * Executes given {@link java.lang.Runnable} when onComplete signal is received.
     *
     * @param onComplete {@link java.lang.Runnable} to be executed.
     * @return Multi
     */
    default Multi<T> onComplete(Runnable onComplete) {
        return new MultiTappedPublisher<>(this,
                null,
                null,
                null,
                onComplete,
                null,
                null);
    }

    /**
     * Executes given {@link java.lang.Runnable} when onError signal is received.
     *
     * @param onErrorConsumer {@link java.util.function.Consumer} to be executed.
     * @return Multi
     */
    default Multi<T> onError(Consumer<? super Throwable> onErrorConsumer) {
        return new MultiTappedPublisher<>(this,
                null,
                null,
                onErrorConsumer,
                null,
                null,
                null);
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

    /**
     * Resume stream from single item if onComplete signal is intercepted. Effectively do an {@code append} to the stream.
     *
     * @param item one item to resume stream with
     * @return Multi
     */
    default Multi<T> onCompleteResume(T item) {
        Objects.requireNonNull(item, "item is null");
        return onCompleteResumeWith(Multi.singleton(item));
    }

    /**
     * Resume stream from supplied publisher if onComplete signal is intercepted.
     *
     * @param publisher new stream publisher
     * @return Multi
     */
    default Multi<T> onCompleteResumeWith(Flow.Publisher<? extends T> publisher) {
        Objects.requireNonNull(publisher, "publisher is null");
        return new MultiOnCompleteResumeWith<>(this, publisher);
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
     * Invoke provided consumer for every item in stream.
     *
     * @param consumer consumer to be invoked
     * @return Multi
     */
    default Multi<T> peek(Consumer<? super T> consumer) {
        return new MultiTappedPublisher<>(this, null, consumer,
                null, null, null, null);
    }

    /**
     * Log all signals {@code onSubscribe}, {@code onNext},
     * {@code onError}, {@code onComplete}, {@code cancel} and {@code request}
     * coming to and from preceding operator.
     *
     * @return Multi
     */
    default Multi<T> log() {
        return new MultiLoggingPublisher<>(this, Level.INFO, false);
    }

    /**
     * Log all signals {@code onSubscribe}, {@code onNext},
     * {@code onError}, {@code onComplete}, {@code cancel} and {@code request}
     * coming to and from preceding operator.
     *
     * @param level a logging level value
     * @return Multi
     */
    default Multi<T> log(Level level) {
        return new MultiLoggingPublisher<>(this, level, false);
    }

    /**
     * Log all signals {@code onSubscribe}, {@code onNext},
     * {@code onError}, {@code onComplete}, {@code cancel} and {@code request}
     * coming to and from preceding operator.
     *
     * @param level a logging level value
     * @param loggerName custom logger name
     * @return Multi
     */
    default Multi<T> log(Level level, String loggerName) {
        return new MultiLoggingPublisher<>(this, level, loggerName);
    }

    /**
     * Log all signals {@code onSubscribe}, {@code onNext},
     * {@code onError}, {@code onComplete}, {@code cancel} and {@code request}
     * coming to and from preceding operator.
     * <p>
     * Enabled <b>trace</b> option has a negative impact on performance and should <b>NOT</b> be used in production.
     *</p>
     * @param level a logging level value
     * @param trace if true position of operator is looked up from stack and logged
     * @return Multi
     */
    default Multi<T> log(Level level, boolean trace) {
        return new MultiLoggingPublisher<>(this, level, trace);
    }

    /**
     * Combine subsequent items via a callback function and emit
     * the final value result as a Single.
     * <p>
     *     If the upstream is empty, the resulting Single is also empty.
     *     If the upstream contains only one item, the reducer function
     *     is not invoked and the resulting Single will have only that
     *     single item.
     * </p>
     * @param reducer the function called with the first value or the previous result,
     *                the current upstream value and should return a new value
     * @return Single
     */
    default Single<T> reduce(BiFunction<T, T, T> reducer) {
        Objects.requireNonNull(reducer, "reducer is null");
        return new MultiReduce<>(this, reducer);
    }

    /**
     * Combine every upstream item with an accumulator value to produce a new accumulator
     * value and emit the final accumulator value as a Single.
     * @param supplier the function to return the initial accumulator value for each incoming
     *                 Subscriber
     * @param reducer the function that receives the current accumulator value, the current
     *                upstream value and should return a new accumulator value
     * @param <R> the accumulator and result type
     * @return Single
     */
    default <R> Single<R> reduce(Supplier<? extends R> supplier, BiFunction<R, T, R> reducer) {
        Objects.requireNonNull(supplier, "supplier is null");
        Objects.requireNonNull(reducer, "reducer is null");
        return new MultiReduceFull<>(this, supplier, reducer);
    }

    /**
     * Retry a failing upstream at most the given number of times before giving up.
     * @param count the number of times to retry; 0 means no retry at all
     * @return Multi
     * @throws IllegalArgumentException if {@code count} is negative
     * @see #retryWhen(BiFunction)
     */
    default Multi<T> retry(long count) {
        if (count < 0L) {
            throw new IllegalArgumentException("count >= 0L required");
        }
        return new MultiRetry<>(this, count);
    }

    /**
     * Retry a failing upstream if the predicate returns true.
     * @param predicate the predicate that receives the latest failure {@link Throwable}
     *                  the number of times the retry happened so far (0-based) and
     *                  should return {@code true} to retry the upstream again or
     *                  {@code false} to signal the latest failure
     * @return Multi
     * @throws NullPointerException if {@code predicate} is {@code null}
     * @see #retryWhen(BiFunction)
     */
    default Multi<T> retry(BiPredicate<? super Throwable, ? super Long> predicate) {
        Objects.requireNonNull(predicate, "whenFunction is null");
        return new MultiRetry<>(this, predicate);
    }

    /**
     * Retry a failing upstream when the given function returns a publisher that
     * signals an item.
     * <p>
     *     If the publisher returned by the function completes, the repetition stops
     *     and this Multi is completed.
     *     If the publisher signals an error, the repetition stops
     *     and this Multi will signal this error.
     * </p>
     * @param whenFunction the function that receives the latest failure {@link Throwable}
     *                     the number of times the retry happened so far (0-based) and
     *                     should return a {@link Flow.Publisher} that should signal an item
     *                     to retry again, complete to stop and complete this Multi
     *                     or signal an error to have this Multi emit that error as well.
     * @param <U> the element type of the retry-signal sequence
     * @return Multi
     * @throws NullPointerException if {@code whenFunction} is {@code null}
     */
    default <U> Multi<T> retryWhen(
            BiFunction<? super Throwable, ? super Long, ? extends Flow.Publisher<U>> whenFunction) {
        Objects.requireNonNull(whenFunction, "whenFunction is null");
        return new MultiRetry<>(this, whenFunction);
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
     * Switch to the other publisher if the upstream is empty.
     * @param other the publisher to switch to if the upstream is empty.
     * @return Multi
     * @throws NullPointerException if {@code other} is {@code null}
     */
    default Multi<T> switchIfEmpty(Flow.Publisher<T> other) {
        Objects.requireNonNull(other, "other is null");
        return new MultiSwitchIfEmpty<>(this, other);
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
     * Take the longest prefix of elements from this stream that satisfy the given predicate.
     * As long as predicate returns true, items from upstream are sent to downstream,
     * when predicate returns false stream is completed.
     *
     * @param predicate predicate to filter stream with
     * @return Multi
     */
    default Multi<T> takeWhile(Predicate<? super T> predicate) {
        return new MultiTakeWhilePublisher<>(this, predicate);
    }

    /**
     * Signals a {@link TimeoutException} if the upstream doesn't signal the next item, error
     * or completion within the specified time.
     * @param timeout the time to wait for the upstream to signal
     * @param unit the time unit
     * @param executor the executor to use for waiting for the upstream signal
     * @return Multi
     * @throws NullPointerException if {@code unit} or {@code executor} is {@code null}
     */
    default Multi<T> timeout(long timeout, TimeUnit unit, ScheduledExecutorService executor) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(executor, "executor is null");
        return new MultiTimeout<>(this, timeout, unit, executor, null);
    }

    /**
     * Switches to a fallback single if the upstream doesn't signal the next item, error
     * or completion within the specified time.
     * @param timeout the time to wait for the upstream to signal
     * @param unit the time unit
     * @param executor the executor to use for waiting for the upstream signal
     * @param fallback the Single to switch to if the upstream doesn't signal in time
     * @return Multi
     * @throws NullPointerException if {@code unit}, {@code executor}
     *                              or {@code fallback} is {@code null}
     */
    default Multi<T> timeout(long timeout, TimeUnit unit, ScheduledExecutorService executor, Flow.Publisher<T> fallback) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(executor, "executor is null");
        Objects.requireNonNull(fallback, "fallback is null");
        return new MultiTimeout<>(this, timeout, unit, executor, fallback);
    }

    /**
     * Apply the given {@code converter} function to the current {@code Multi} instance
     * and return the value returned by this function.
     * <p>
     *     Note that the {@code converter} function is executed upon calling this method
     *     immediately and not when the resulting sequence gets subscribed to.
     * </p>
     * @param converter the function that receives the current {@code Multi} instance and
     *                  should return a value to be returned by the method
     * @param <U> the output type
     * @return the value returned by the function
     * @throws NullPointerException if {@code converter} is {@code null}
     */
    default <U> U to(Function<? super Multi<T>, ? extends U> converter) {
        return converter.apply(this);
    }

    // --------------------------------------------------------------------------------------------------------
    // Terminal operators
    // --------------------------------------------------------------------------------------------------------

    /**
     * Terminal stage, invokes provided consumer for every item in the stream.
     *
     * @param consumer consumer to be invoked for each item
     * @return Single completed when the stream terminates
     */
    default Single<Void> forEach(Consumer<? super T> consumer) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Single<Void> single = Single.create(future, true);
        FunctionalSubscriber<T> subscriber = new FunctionalSubscriber<>(consumer,
                future::completeExceptionally,
                () -> future.complete(null),
                subscription -> {
                    single.onCancel(subscription::cancel);
                    subscription.request(Long.MAX_VALUE);
                }
        );

        this.subscribe(subscriber);
        return single;
    }

    /**
     * Terminal stage, ignore all items and complete returned {@code Single<Void>}.
     *
     * @return Single completed when the stream terminates
     */
    default Single<Void> ignore() {
        return forEach(t -> {});
    }

}
