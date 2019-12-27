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
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;

import io.helidon.common.mapper.Mapper;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

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
        MultiMapProcessor<T, U> processor = new MultiMapProcessor<>(mapper);
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
        PeekProcessor<T> processor = new PeekProcessor<T>(consumer);
        this.subscribe(processor);
        return processor;
    }

    /**
     * Filter out all duplicates.
     *
     * @return Multi
     */
    default Multi<T> distinct() {
        DistinctProcessor<T> processor = new DistinctProcessor<>();
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
        FilterProcessor<T> processor = new FilterProcessor<>(predicate);
        this.subscribe(processor);
        return processor;
    }

    /**
     * Take the longest prefix of elements from this stream that satisfy the given predicate.
     *
     * @param predicate predicate to filter stream with
     * @return Multi
     */
    default Multi<T> takeWhile(Predicate<T> predicate) {
        TakeWhileProcessor<T> processor = new TakeWhileProcessor<>(predicate);
        this.subscribe(processor);
        return processor;
    }

    /**
     * Drop the longest prefix of elements from this stream that satisfy the given predicate.
     *
     * @param predicate predicate to filter stream with
     * @return Multi
     */
    default Multi<T> dropWhile(Predicate<T> predicate) {
        DropWhileProcessor<T> processor = new DropWhileProcessor<>(predicate);
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
        LimitProcessor<T> processor = new LimitProcessor<>(limit);
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
        SkipProcessor<T> processor = new SkipProcessor<>(skip);
        this.subscribe(processor);
        return processor;
    }

    /**
     * Coupled processor sends items received to the passed in subscriber, and emits items received from the passed in publisher.
     * <pre>
     *     +
     *     |  Inlet/upstream publisher
     * +-------+
     * |   |   |   passed in subscriber
     * |   +-------------------------->
     * |       |   passed in publisher
     * |   +--------------------------+
     * |   |   |
     * +-------+
     *     |  Outlet/downstream subscriber
     *     v
     * </pre>
     *
     * @param <T>                Inlet and passed in subscriber item type
     * @param <R>                Outlet and passed in publisher item type
     * @param passedInSubscriber gets all items from upstream/inlet
     * @param passedInPublisher  emits to downstream/outlet
     * @return Multi
     */
    default <R> Multi<R> coupled(Flow.Subscriber<T> passedInSubscriber, Flow.Publisher<R> passedInPublisher) {
        MultiCoupledProcessor<T, R> processor = new MultiCoupledProcessor<>(passedInSubscriber, passedInPublisher);
        this.subscribe(processor);
        return processor;
    }

    /**
     * Executes given {@link java.lang.Runnable} when any of signals onComplete, onCancel or onError is received.
     *
     * @param onTerminate {@link java.lang.Runnable} to be executed.
     * @return Multi
     */
    default Multi<T> onTerminate(Runnable onTerminate) {
        TappedProcessor<T> processor = TappedProcessor.<T>create()
                .onComplete(onTerminate)
                .onCancel((s) -> onTerminate.run())
                .onError((t) -> onTerminate.run());
        this.subscribe(processor);
        return processor;
    }

    /**
     * Executes given {@link java.lang.Runnable} when onComplete signal is received.
     *
     * @param onTerminate {@link java.lang.Runnable} to be executed.
     * @return Multi
     */
    default Multi<T> onComplete(Runnable onTerminate) {
        TappedProcessor<T> processor = TappedProcessor.<T>create()
                .onComplete(onTerminate);
        this.subscribe(processor);
        return processor;
    }

    /**
     * Executes given {@link java.lang.Runnable} when onError signal is received.
     *
     * @param onErrorConsumer {@link java.lang.Runnable} to be executed.
     * @return Multi
     */
    default Multi<T> onError(Consumer<Throwable> onErrorConsumer) {
        TappedProcessor<T> processor = TappedProcessor.<T>create()
                .onError(onErrorConsumer);
        this.subscribe(processor);
        return processor;
    }

    /**
     * {@link java.util.function.Function} providing one item to be submitted as onNext in case of onError signal is received.
     *
     * @param onError Function receiving {@link java.lang.Throwable} as argument and producing one item to resume stream with.
     * @return Multi
     */
    default Multi<T> onErrorResume(Function<Throwable, T> onError) {
        OnErrorResumeProcessor<T> processor = OnErrorResumeProcessor.resume(onError);
        this.subscribe(processor);
        return processor;
    }

    default Multi<T> onErrorResumeWith(Function<Throwable, Publisher<T>> onError) {
        OnErrorResumeProcessor<T> processor = OnErrorResumeProcessor.resumeWith(onError);
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
        MultiFirstProcessor<T> processor = new MultiFirstProcessor<>();
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
        return Multi.from(new OfPublisher<T>(iterable));
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
        return new MultiFromPublisher<>(new FixedItemsPublisher<>(items));
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
        return new MultiFromPublisher<>(new FixedItemsPublisher<>(List.of(items)));
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
        return new FailedPublisher<T>(error);
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

    static <T> Multi<T> concat(Multi<T> firstMulti, Multi<T> secondMulti) {
        return new ConcatPublisher<>(firstMulti, secondMulti);
    }
}
