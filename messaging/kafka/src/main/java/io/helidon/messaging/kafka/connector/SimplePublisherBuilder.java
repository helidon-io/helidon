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

package io.helidon.messaging.kafka.connector;

import org.eclipse.microprofile.reactive.streams.operators.CompletionRunner;
import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.eclipse.microprofile.reactive.streams.operators.spi.ReactiveStreamsEngine;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * Simple stub to create MicroProfile Reactive Messaging connector without reactive streams
 *
 * @param <K> kafka record key type
 * @param <V> kafka record value type
 */
public class SimplePublisherBuilder<K, V> implements PublisherBuilder<KafkaMessage<K, V>> {

    private Consumer<Subscriber<? super KafkaMessage<K, V>>> publisher;

    public SimplePublisherBuilder(Consumer<Subscriber<? super KafkaMessage<K, V>>> publisher) {
        this.publisher = publisher;
    }

    @Override
    public Publisher<KafkaMessage<K, V>> buildRs() {
        //TODO: Implement ReactiveStreamsEngine instead if simple stub
        return new SimplePublisher<K, V>(publisher);
    }

    @Override
    public Publisher<KafkaMessage<K, V>> buildRs(ReactiveStreamsEngine engine) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <R> PublisherBuilder<R> map(Function<? super KafkaMessage<K, V>, ? extends R> mapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S> PublisherBuilder<S> flatMap(Function<? super KafkaMessage<K, V>, ? extends PublisherBuilder<? extends S>> mapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S> PublisherBuilder<S> flatMapRsPublisher(Function<? super KafkaMessage<K, V>, ? extends Publisher<? extends S>> mapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S> PublisherBuilder<S> flatMapCompletionStage(Function<? super KafkaMessage<K, V>, ? extends CompletionStage<? extends S>> mapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <S> PublisherBuilder<S> flatMapIterable(Function<? super KafkaMessage<K, V>, ? extends Iterable<? extends S>> mapper) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PublisherBuilder<KafkaMessage<K, V>> filter(Predicate<? super KafkaMessage<K, V>> predicate) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PublisherBuilder<KafkaMessage<K, V>> distinct() {
        throw new UnsupportedOperationException();
    }

    @Override
    public PublisherBuilder<KafkaMessage<K, V>> limit(long maxSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PublisherBuilder<KafkaMessage<K, V>> skip(long n) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PublisherBuilder<KafkaMessage<K, V>> takeWhile(Predicate<? super KafkaMessage<K, V>> predicate) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PublisherBuilder<KafkaMessage<K, V>> dropWhile(Predicate<? super KafkaMessage<K, V>> predicate) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PublisherBuilder<KafkaMessage<K, V>> peek(Consumer<? super KafkaMessage<K, V>> consumer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PublisherBuilder<KafkaMessage<K, V>> onError(Consumer<Throwable> errorHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PublisherBuilder<KafkaMessage<K, V>> onTerminate(Runnable action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PublisherBuilder<KafkaMessage<K, V>> onComplete(Runnable action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionRunner<Void> forEach(Consumer<? super KafkaMessage<K, V>> action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionRunner<Void> ignore() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionRunner<Void> cancel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionRunner<KafkaMessage<K, V>> reduce(KafkaMessage<K, V> identity, BinaryOperator<KafkaMessage<K, V>> accumulator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionRunner<Optional<KafkaMessage<K, V>>> reduce(BinaryOperator<KafkaMessage<K, V>> accumulator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionRunner<Optional<KafkaMessage<K, V>>> findFirst() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <R, A> CompletionRunner<R> collect(Collector<? super KafkaMessage<K, V>, A, R> collector) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <R> CompletionRunner<R> collect(Supplier<R> supplier, BiConsumer<R, ? super KafkaMessage<K, V>> accumulator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionRunner<List<KafkaMessage<K, V>>> toList() {
        throw new UnsupportedOperationException();
    }

    @Override
    public PublisherBuilder<KafkaMessage<K, V>> onErrorResume(Function<Throwable, ? extends KafkaMessage<K, V>> errorHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PublisherBuilder<KafkaMessage<K, V>> onErrorResumeWith(Function<Throwable, ? extends PublisherBuilder<? extends KafkaMessage<K, V>>> errorHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PublisherBuilder<KafkaMessage<K, V>> onErrorResumeWithRsPublisher(Function<Throwable, ? extends Publisher<? extends KafkaMessage<K, V>>> errorHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletionRunner<Void> to(Subscriber<? super KafkaMessage<K, V>> subscriber) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <R> CompletionRunner<R> to(SubscriberBuilder<? super KafkaMessage<K, V>, ? extends R> subscriber) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <R> PublisherBuilder<R> via(ProcessorBuilder<? super KafkaMessage<K, V>, ? extends R> processor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <R> PublisherBuilder<R> via(Processor<? super KafkaMessage<K, V>, ? extends R> processor) {
        throw new UnsupportedOperationException();
    }


}
