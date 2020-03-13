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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

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
        return add(new HelidonReactiveStage.HRSMap(mapper));
    }

    @Override
    public <S> PublisherBuilder<S> flatMap(Function<? super T, ? extends PublisherBuilder<? extends S>> mapper) {
        return add(new HelidonReactiveStage.HRSFlatMap<>(mapper));
    }

    @Override
    public <S> PublisherBuilder<S> flatMapRsPublisher(Function<? super T, ? extends Publisher<? extends S>> mapper) {
        return add(new HelidonReactiveStage.HRSFlatMapRs<>(mapper));
    }

    @Override
    public <S> PublisherBuilder<S> flatMapCompletionStage(
            Function<? super T, ? extends CompletionStage<? extends S>> mapper) {
        return add(new HelidonReactiveStage.HRSFlatMapCompletionStage<>(mapper));
    }

    @Override
    public <S> PublisherBuilder<S> flatMapIterable(Function<? super T, ? extends Iterable<? extends S>> mapper) {
        return add(new HelidonReactiveStage.HRSFlatMapIterable<>(mapper));
    }

    @Override
    public PublisherBuilder<T> filter(Predicate<? super T> predicate) {
        return add(new HelidonReactiveStage.HRSFilter(predicate));
    }

    @Override
    public PublisherBuilder<T> distinct() {
        return add(HelidonReactiveStage.HRSDistinct.INSTANCE);
    }

    @Override
    public PublisherBuilder<T> limit(long maxSize) {
        return add(new HelidonReactiveStage.HRSLimit(maxSize));
    }

    @Override
    public PublisherBuilder<T> skip(long n) {
        return add(new HelidonReactiveStage.HRSSkip(n));
    }

    @Override
    public PublisherBuilder<T> takeWhile(Predicate<? super T> predicate) {
        return add(new HelidonReactiveStage.HRSTakeWhile(predicate));
    }

    @Override
    public PublisherBuilder<T> dropWhile(Predicate<? super T> predicate) {
        return add(new HelidonReactiveStage.HRSDropWhile(predicate));
    }

    @Override
    public PublisherBuilder<T> peek(Consumer<? super T> consumer) {
        return add(new HelidonReactiveStage.HRSPeek(consumer));
    }

    @Override
    public PublisherBuilder<T> onError(Consumer<Throwable> errorHandler) {
        return add(new HelidonReactiveStage.HRSOnError(errorHandler));
    }

    @Override
    public PublisherBuilder<T> onTerminate(Runnable action) {
        return add(new HelidonReactiveStage.HRSOnTerminate(action));
    }

    @Override
    public PublisherBuilder<T> onComplete(Runnable action) {
        return add(new HelidonReactiveStage.HRSOnComplete(action));
    }

    @Override
    public CompletionRunner<Void> forEach(Consumer<? super T> action) {
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
        return new HelidonReactiveCompletionRunner<>(stages, new HelidonReactiveStage.HRSReduceFull<>(identity, accumulator));
    }

    @Override
    public CompletionRunner<Optional<T>> reduce(BinaryOperator<T> accumulator) {
        return new HelidonReactiveCompletionRunner<>(stages, new HelidonReactiveStage.HRSReduceOptional<>(accumulator));
    }

    @Override
    public CompletionRunner<Optional<T>> findFirst() {
        return new HelidonReactiveCompletionRunner<>(stages, HelidonReactiveStage.HRSFindFirst.INSTANCE);
    }

    @Override
    public <R, A> CompletionRunner<R> collect(Collector<? super T, A, R> collector) {
        return new HelidonReactiveCompletionRunner<>(stages, new HelidonReactiveStage.HRSCollect(collector));
    }

    @Override
    public <R> CompletionRunner<R> collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator) {
        return new HelidonReactiveCompletionRunner<>(stages, new HelidonReactiveStage.HRSCollectFull<>(supplier, accumulator));
    }

    @Override
    public CompletionRunner<List<T>> toList() {
        return collect(Collectors.toList());
    }

    @Override
    public PublisherBuilder<T> onErrorResume(Function<Throwable, ? extends T> errorHandler) {
        return add(new HelidonReactiveStage.HRSOnErrorResume(errorHandler));
    }

    @Override
    public PublisherBuilder<T> onErrorResumeWith(Function<Throwable, ? extends PublisherBuilder<? extends T>> errorHandler) {
        return add(new HelidonReactiveStage.HRSOnErrorResumeWith<>(errorHandler));
    }

    @Override
    public PublisherBuilder<T> onErrorResumeWithRsPublisher(Function<Throwable, ? extends Publisher<? extends T>> errorHandler) {
        return add(new HelidonReactiveStage.HRSOnErrorResumeWithRs<>(errorHandler));
    }

    @Override
    public CompletionRunner<Void> to(Subscriber<? super T> subscriber) {
        return new HelidonReactiveCompletionRunner<>(stages, new HelidonReactiveStage.HRSSubscriber(subscriber));
    }

    @Override
    public <R> CompletionRunner<R> to(SubscriberBuilder<? super T, ? extends R> subscriber) {
        return new HelidonReactiveCompletionRunner<>(stages, subscriber);
    }

    @Override
    public <R> PublisherBuilder<R> via(ProcessorBuilder<? super T, ? extends R> processor) {
        HelidonReactivePublisherBuilder<R> result = add();
        result.stages.addAll(HelidonReactiveStage.getGraph(processor).getStages());
        return result;
    }

    @Override
    public <R> PublisherBuilder<R> via(Processor<? super T, ? extends R> processor) {
        return add(new HelidonReactiveStage.HRSProcessor(processor));
    }

    @Override
    public Publisher<T> buildRs() {
        return HelidonReactiveStreamsEngine.INSTANCE.buildPublisher(stages);
    }

    @Override
    public Publisher<T> buildRs(ReactiveStreamsEngine engine) {
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
