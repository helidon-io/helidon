/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

package io.helidon.microprofile.reactive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.eclipse.microprofile.reactive.streams.operators.CompletionRunner;
import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.eclipse.microprofile.reactive.streams.operators.spi.Graph;
import org.eclipse.microprofile.reactive.streams.operators.spi.ReactiveStreamsEngine;
import org.eclipse.microprofile.reactive.streams.operators.spi.Stage;
import org.eclipse.microprofile.reactive.streams.operators.spi.ToGraphable;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

final class HelidonReactivePublisherBuilder<T> implements PublisherBuilder<T>, ToGraphable, Graph {

    private final List<Stage> stages;

    HelidonReactivePublisherBuilder() {
        this.stages = new ArrayList<>();
    }

    HelidonReactivePublisherBuilder(Stage initialStage) {
        this();
        stages.add(initialStage);
    }

    <U> HelidonReactivePublisherBuilder<U> add() {
        HelidonReactivePublisherBuilder<U> result = new HelidonReactivePublisherBuilder<>();
        result.stages.addAll(this.stages);
        return result;
    }

    <U> HelidonReactivePublisherBuilder<U> add(Stage newStage) {
        HelidonReactivePublisherBuilder<U> result = add();
        result.stages.add(newStage);
        return result;
    }

    @Override
    public <R> PublisherBuilder<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        return add(new HelidonReactiveStage.HRSMap(mapper));
    }

    @Override
    public <S> PublisherBuilder<S> flatMap(Function<? super T, ? extends PublisherBuilder<? extends S>> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        return add(new HelidonReactiveStage.HRSFlatMap<>(mapper));
    }

    @Override
    public <S> PublisherBuilder<S> flatMapRsPublisher(Function<? super T, ? extends Publisher<? extends S>> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        return add(new HelidonReactiveStage.HRSFlatMapRs<>(mapper));
    }

    @Override
    public <S> PublisherBuilder<S> flatMapCompletionStage(
            Function<? super T, ? extends CompletionStage<? extends S>> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        return add(new HelidonReactiveStage.HRSFlatMapCompletionStage<>(mapper));
    }

    @Override
    public <S> PublisherBuilder<S> flatMapIterable(Function<? super T, ? extends Iterable<? extends S>> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        return add(new HelidonReactiveStage.HRSFlatMapIterable<>(mapper));
    }

    @Override
    public PublisherBuilder<T> filter(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        return add(new HelidonReactiveStage.HRSFilter(predicate));
    }

    @Override
    public PublisherBuilder<T> distinct() {
        return add(HelidonReactiveStage.HRSDistinct.INSTANCE);
    }

    @Override
    public PublisherBuilder<T> limit(long maxSize) {
        if (maxSize < 0L) {
            throw new IllegalArgumentException("maxSize >= 0L required");
        }
        return add(new HelidonReactiveStage.HRSLimit(maxSize));
    }

    @Override
    public PublisherBuilder<T> skip(long n) {
        if (n < 0L) {
            throw new IllegalArgumentException("n >= 0L required");
        }
        return add(new HelidonReactiveStage.HRSSkip(n));
    }

    @Override
    public PublisherBuilder<T> takeWhile(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        return add(new HelidonReactiveStage.HRSTakeWhile(predicate));
    }

    @Override
    public PublisherBuilder<T> dropWhile(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate is null");
        return add(new HelidonReactiveStage.HRSDropWhile(predicate));
    }

    @Override
    public PublisherBuilder<T> peek(Consumer<? super T> consumer) {
        Objects.requireNonNull(consumer, "consumer is null");
        return add(new HelidonReactiveStage.HRSPeek(consumer));
    }

    @Override
    public PublisherBuilder<T> onError(Consumer<Throwable> errorHandler) {
        Objects.requireNonNull(errorHandler, "errorHandler is null");
        return add(new HelidonReactiveStage.HRSOnError(errorHandler));
    }

    @Override
    public PublisherBuilder<T> onTerminate(Runnable action) {
        Objects.requireNonNull(action, "action is null");
        return add(new HelidonReactiveStage.HRSOnTerminate(action));
    }

    @Override
    public PublisherBuilder<T> onComplete(Runnable action) {
        Objects.requireNonNull(action, "action is null");
        return add(new HelidonReactiveStage.HRSOnComplete(action));
    }

    @Override
    public CompletionRunner<Void> forEach(Consumer<? super T> action) {
        Objects.requireNonNull(action, "action is null");
        return new HelidonReactiveCompletionRunner<>(stages, new HelidonReactiveStage.HRSForEach<>(action));
    }

    @Override
    public CompletionRunner<Void> ignore() {
        return new HelidonReactiveCompletionRunner<>(stages, HelidonReactiveStage.HRSIgnore.INSTANCE);
    }

    @Override
    public CompletionRunner<Void> cancel() {
        return new HelidonReactiveCompletionRunner<>(stages, HelidonReactiveStage.HRSCancel.INSTANCE);
    }

    @Override
    public CompletionRunner<T> reduce(T identity, BinaryOperator<T> accumulator) {
        Objects.requireNonNull(accumulator, "accumulator is null");
        return new HelidonReactiveCompletionRunner<>(stages, new HelidonReactiveStage.HRSReduceFull<>(identity, accumulator));
    }

    @Override
    public CompletionRunner<Optional<T>> reduce(BinaryOperator<T> accumulator) {
        Objects.requireNonNull(accumulator, "accumulator is null");
        return new HelidonReactiveCompletionRunner<>(stages, new HelidonReactiveStage.HRSReduceOptional<>(accumulator));
    }

    @Override
    public CompletionRunner<Optional<T>> findFirst() {
        return new HelidonReactiveCompletionRunner<>(stages, HelidonReactiveStage.HRSFindFirst.INSTANCE);
    }

    @Override
    public <R, A> CompletionRunner<R> collect(Collector<? super T, A, R> collector) {
        Objects.requireNonNull(collector, "collector is null");
        return new HelidonReactiveCompletionRunner<>(stages, new HelidonReactiveStage.HRSCollect(collector));
    }

    @Override
    public <R> CompletionRunner<R> collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator) {
        Objects.requireNonNull(supplier, "supplier is null");
        Objects.requireNonNull(accumulator, "accumulator is null");
        return new HelidonReactiveCompletionRunner<>(stages, new HelidonReactiveStage.HRSCollectFull<>(supplier, accumulator));
    }

    @Override
    public CompletionRunner<List<T>> toList() {
        return collect(Collectors.toList());
    }

    @Override
    public PublisherBuilder<T> onErrorResume(Function<Throwable, ? extends T> errorHandler) {
        Objects.requireNonNull(errorHandler, "errorHandler is null");
        return add(new HelidonReactiveStage.HRSOnErrorResume(errorHandler));
    }

    @Override
    public PublisherBuilder<T> onErrorResumeWith(
            Function<Throwable, ? extends PublisherBuilder<? extends T>> errorHandler) {
        Objects.requireNonNull(errorHandler, "errorHandler is null");
        return add(new HelidonReactiveStage.HRSOnErrorResumeWith<>(errorHandler));
    }

    @Override
    public PublisherBuilder<T> onErrorResumeWithRsPublisher(
            Function<Throwable, ? extends Publisher<? extends T>> errorHandler) {
        Objects.requireNonNull(errorHandler, "errorHandler is null");
        return add(new HelidonReactiveStage.HRSOnErrorResumeWithRs<>(errorHandler));
    }

    @Override
    public CompletionRunner<Void> to(Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");
        return new HelidonReactiveCompletionRunner<>(stages, new HelidonReactiveStage.HRSSubscriber(subscriber));
    }

    @Override
    public <R> CompletionRunner<R> to(SubscriberBuilder<? super T, ? extends R> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");
        return new HelidonReactiveCompletionRunner<>(stages, subscriber);
    }

    @Override
    public <R> PublisherBuilder<R> via(ProcessorBuilder<? super T, ? extends R> processor) {
        Objects.requireNonNull(processor, "processor is null");
        HelidonReactivePublisherBuilder<R> result = add();
        result.stages.addAll(HelidonReactiveStage.getGraph(processor).getStages());
        return result;
    }

    @Override
    public <R> PublisherBuilder<R> via(Processor<? super T, ? extends R> processor) {
        Objects.requireNonNull(processor, "processor is null");
        return add(new HelidonReactiveStage.HRSProcessor(processor));
    }

    @Override
    public Publisher<T> buildRs() {
        return HelidonReactiveStreamsEngine.INSTANCE.buildPublisher(stages);
    }

    @Override
    public Publisher<T> buildRs(ReactiveStreamsEngine engine) {
        Objects.requireNonNull(engine, "engine is null");
        if (engine == HelidonReactiveStreamsEngine.INSTANCE) {
            return buildRs();
        }
        return engine.buildPublisher(this);
    }

    @Override
    public Graph toGraph() {
        return this;
    }

    @Override
    public Collection<Stage> getStages() {
        return stages;
    }
}
