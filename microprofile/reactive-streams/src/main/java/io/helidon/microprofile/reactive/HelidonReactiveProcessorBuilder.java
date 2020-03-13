/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

final class HelidonReactiveProcessorBuilder<T, R> implements ProcessorBuilder<T, R>, ToGraphable, Graph {

    private final List<Stage> stages;

    HelidonReactiveProcessorBuilder() {
        this.stages = new ArrayList<>();
    }

    HelidonReactiveProcessorBuilder(Stage initialStage) {
        this();
        stages.add(initialStage);
    }


    <U, V> HelidonReactiveProcessorBuilder<U, V> add() {
        HelidonReactiveProcessorBuilder<U, V> result = new HelidonReactiveProcessorBuilder<>();
        result.stages.addAll(this.stages);
        return result;
    }

    <U, V> HelidonReactiveProcessorBuilder<U, V> add(Stage newStage) {
        HelidonReactiveProcessorBuilder<U, V> result = add();
        result.stages.add(newStage);
        return result;
    }

    @Override
    public <S> ProcessorBuilder<T, S> map(Function<? super R, ? extends S> mapper) {
        return add(new HelidonReactiveStage.HRSMap(mapper));
    }

    @Override
    public <S> ProcessorBuilder<T, S> flatMap(Function<? super R, ? extends PublisherBuilder<? extends S>> mapper) {
        return add(new HelidonReactiveStage.HRSFlatMap<>(mapper));
    }

    @Override
    public <S> ProcessorBuilder<T, S> flatMapRsPublisher(
            Function<? super R, ? extends Publisher<? extends S>> mapper) {
        return add(new HelidonReactiveStage.HRSFlatMapRs<>(mapper));
    }

    @Override
    public <S> ProcessorBuilder<T, S> flatMapCompletionStage(
            Function<? super R, ? extends CompletionStage<? extends S>> mapper) {
        return add(new HelidonReactiveStage.HRSFlatMapCompletionStage<>(mapper));
    }

    @Override
    public <S> ProcessorBuilder<T, S> flatMapIterable(Function<? super R, ? extends Iterable<? extends S>> mapper) {
        return add(new HelidonReactiveStage.HRSFlatMapIterable<>(mapper));
    }

    @Override
    public ProcessorBuilder<T, R> filter(Predicate<? super R> predicate) {
        return add(new HelidonReactiveStage.HRSFilter(predicate));
    }

    @Override
    public ProcessorBuilder<T, R> distinct() {
        return add(HelidonReactiveStage.HRSDistinct.INSTANCE);
    }

    @Override
    public ProcessorBuilder<T, R> limit(long maxSize) {
        return add(new HelidonReactiveStage.HRSLimit(maxSize));
    }

    @Override
    public ProcessorBuilder<T, R> skip(long n) {
        return add(new HelidonReactiveStage.HRSSkip(n));
    }

    @Override
    public ProcessorBuilder<T, R> takeWhile(Predicate<? super R> predicate) {
        return add(new HelidonReactiveStage.HRSTakeWhile(predicate));
    }

    @Override
    public ProcessorBuilder<T, R> dropWhile(Predicate<? super R> predicate) {
        return add(new HelidonReactiveStage.HRSDropWhile(predicate));
    }

    @Override
    public ProcessorBuilder<T, R> peek(Consumer<? super R> consumer) {
        return add(new HelidonReactiveStage.HRSPeek(consumer));
    }

    @Override
    public ProcessorBuilder<T, R> onError(Consumer<Throwable> errorHandler) {
        return add(new HelidonReactiveStage.HRSOnError(errorHandler));
    }

    @Override
    public ProcessorBuilder<T, R> onTerminate(Runnable action) {
        return add(new HelidonReactiveStage.HRSOnTerminate(action));
    }

    @Override
    public ProcessorBuilder<T, R> onComplete(Runnable action) {
        return add(new HelidonReactiveStage.HRSOnComplete(action));
    }

    @Override
    public SubscriberBuilder<T, Void> forEach(Consumer<? super R> action) {
        return new HelidonReactiveSubscriberBuilder<>(stages, new HelidonReactiveStage.HRSForEach<>(action));
    }

    @Override
    public SubscriberBuilder<T, Void> ignore() {
        return new HelidonReactiveSubscriberBuilder<>(stages, HelidonReactiveStage.HRSIgnore.INSTANCE);
    }

    @Override
    public SubscriberBuilder<T, Void> cancel() {
        return new HelidonReactiveSubscriberBuilder<>(stages, HelidonReactiveStage.HRSCancel.INSTANCE);
    }

    @Override
    public SubscriberBuilder<T, R> reduce(R identity, BinaryOperator<R> accumulator) {
        return new HelidonReactiveSubscriberBuilder<>(stages, new HelidonReactiveStage.HRSReduceFull<>(identity, accumulator));
    }

    @Override
    public SubscriberBuilder<T, Optional<R>> reduce(BinaryOperator<R> accumulator) {
        return new HelidonReactiveSubscriberBuilder<>(stages, new HelidonReactiveStage.HRSReduceOptional<>(accumulator));
    }

    @Override
    public SubscriberBuilder<T, Optional<R>> findFirst() {
        return new HelidonReactiveSubscriberBuilder<>(stages, HelidonReactiveStage.HRSFindFirst.INSTANCE);
    }

    @Override
    public <S, A> SubscriberBuilder<T, S> collect(Collector<? super R, A, S> collector) {
        return new HelidonReactiveSubscriberBuilder<>(stages, new HelidonReactiveStage.HRSCollect(collector));
    }

    @Override
    public <S> SubscriberBuilder<T, S> collect(Supplier<S> supplier, BiConsumer<S, ? super R> accumulator) {
        return new HelidonReactiveSubscriberBuilder<>(stages, new HelidonReactiveStage.HRSCollectFull<>(supplier, accumulator));
    }

    @Override
    public SubscriberBuilder<T, List<R>> toList() {
        return collect(Collectors.toList());
    }

    @Override
    public ProcessorBuilder<T, R> onErrorResume(Function<Throwable, ? extends R> errorHandler) {
        return add(new HelidonReactiveStage.HRSOnErrorResume(errorHandler));
    }

    @Override
    public ProcessorBuilder<T, R> onErrorResumeWith(Function<Throwable, ? extends PublisherBuilder<? extends R>> errorHandler) {
        return add(new HelidonReactiveStage.HRSOnErrorResumeWith<>(errorHandler));
    }

    @Override
    public ProcessorBuilder<T, R> onErrorResumeWithRsPublisher(Function<Throwable, ? extends Publisher<? extends R>> errorHandler) {
        return add(new HelidonReactiveStage.HRSOnErrorResumeWithRs<>(errorHandler));
    }

    @Override
    public SubscriberBuilder<T, Void> to(Subscriber<? super R> subscriber) {
        return new HelidonReactiveSubscriberBuilder<>(stages, new HelidonReactiveStage.HRSSubscriber(subscriber));
    }

    @Override
    public <S> SubscriberBuilder<T, S> to(SubscriberBuilder<? super R, ? extends S> subscriber) {
        return new HelidonReactiveSubscriberBuilder<>(stages, subscriber);
    }

    @Override
    public <S> ProcessorBuilder<T, S> via(ProcessorBuilder<? super R, ? extends S> processor) {
        HelidonReactiveProcessorBuilder<T, S> result = add();
        result.stages.addAll(HelidonReactiveStage.getGraph(processor).getStages());
        return result;
    }

    @Override
    public <S> ProcessorBuilder<T, S> via(Processor<? super R, ? extends S> processor) {
        return add(new HelidonReactiveStage.HRSProcessor(processor));
    }

    @Override
    public Processor<T, R> buildRs() {
        return HelidonReactiveStreamsEngine.INSTANCE.buildProcessor(stages);
    }

    @Override
    public Processor<T, R> buildRs(ReactiveStreamsEngine engine) {
        if (engine == HelidonReactiveStreamsEngine.INSTANCE) {
            return buildRs();
        }
        return engine.buildProcessor(this);
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
