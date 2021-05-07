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

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder;
import org.eclipse.microprofile.reactive.streams.operators.PublisherBuilder;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreamsFactory;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Singleton factory for creating various builders out of sources.
 * @see #INSTANCE
 */
public final class HelidonReactivePublisherFactory implements ReactiveStreamsFactory {

    /** The singleton instance. */
    public static final HelidonReactivePublisherFactory INSTANCE = new HelidonReactivePublisherFactory();

    @Override
    public <T> PublisherBuilder<T> fromPublisher(Publisher<? extends T> publisher) {
        Objects.requireNonNull(publisher, "publisher is null");
        return new HelidonReactivePublisherBuilder<>(new HelidonReactiveStage.HRSPublisher(publisher));
    }

    @Override
    public <T> PublisherBuilder<T> of(T t) {
        Objects.requireNonNull(t, "t is null");
        return fromIterable(Collections.singletonList(t));
    }

    @Override
    @SafeVarargs
    public final <T> PublisherBuilder<T> of(T... ts) {
        Objects.requireNonNull(ts, "ts is null");
        return fromIterable(Arrays.asList(ts));
    }

    @Override
    public <T> PublisherBuilder<T> empty() {
        return new HelidonReactivePublisherBuilder<>(HelidonReactiveStage.EMPTY);
    }

    @Override
    public <T> PublisherBuilder<T> ofNullable(T t) {
        return t == null ? empty() : of(t);
    }

    @Override
    public <T> PublisherBuilder<T> fromIterable(Iterable<? extends T> ts) {
        Objects.requireNonNull(ts, "ts is null");
        return new HelidonReactivePublisherBuilder<>(new HelidonReactiveStage.HRSIterable(ts));
    }

    @Override
    public <T> PublisherBuilder<T> failed(Throwable t) {
        Objects.requireNonNull(t, "t is null");
        return new HelidonReactivePublisherBuilder<>(new HelidonReactiveStage.HRSFailed(t));
    }

    @Override
    public <T> ProcessorBuilder<T, T> builder() {
        return new HelidonReactiveProcessorBuilder<>();
    }

    @Override
    public <T, R> ProcessorBuilder<T, R> fromProcessor(Processor<? super T, ? extends R> processor) {
        Objects.requireNonNull(processor, "processor is null");
        return new HelidonReactiveProcessorBuilder<>(new HelidonReactiveStage.HRSProcessor(processor));
    }

    @Override
    public <T> SubscriberBuilder<T, Void> fromSubscriber(Subscriber<? extends T> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber is null");
        return new HelidonReactiveSubscriberBuilder<>(new HelidonReactiveStage.HRSSubscriber(subscriber));
    }

    @Override
    public <T> PublisherBuilder<T> iterate(T seed, UnaryOperator<T> f) {
        Objects.requireNonNull(f, "f is null");
        return new HelidonReactivePublisherBuilder<>(new HelidonReactiveStage.HRSIterate<>(seed, f));
    }

    @Override
    public <T> PublisherBuilder<T> generate(Supplier<? extends T> s) {
        Objects.requireNonNull(s, "s is null");
        return new HelidonReactivePublisherBuilder<>(new HelidonReactiveStage.HRSGenerate<>(s));
    }

    @Override
    public <T> PublisherBuilder<T> concat(PublisherBuilder<? extends T> a, PublisherBuilder<? extends T> b) {
        Objects.requireNonNull(a, "a is null");
        Objects.requireNonNull(b, "b is null");
        return new HelidonReactivePublisherBuilder<>(new HelidonReactiveStage.HRSConcat(
                HelidonReactiveStage.getGraph(a), HelidonReactiveStage.getGraph(b)
        ));
    }

    @Override
    public <T> PublisherBuilder<T> fromCompletionStage(CompletionStage<? extends T> completionStage) {
        Objects.requireNonNull(completionStage, "completionStage is null");
        return new HelidonReactivePublisherBuilder<>(new HelidonReactiveStage.HRSCompletionStage(completionStage));
    }

    @Override
    public <T> PublisherBuilder<T> fromCompletionStageNullable(CompletionStage<? extends T> completionStage) {
        Objects.requireNonNull(completionStage, "completionStage is null");
        return new HelidonReactivePublisherBuilder<>(new HelidonReactiveStage.HRSCompletionStageNullable(completionStage));
    }

    @Override
    public <T, R> ProcessorBuilder<T, R> coupled(SubscriberBuilder<? super T, ?> subscriber,
                                                 PublisherBuilder<? extends R> publisher) {
        Objects.requireNonNull(subscriber, "subscriber is null");
        Objects.requireNonNull(publisher, "publisher is null");
        return new HelidonReactiveProcessorBuilder<>(new HelidonReactiveStage.HRSCoupled(
                HelidonReactiveStage.getGraph(subscriber), HelidonReactiveStage.getGraph(publisher)
        ));
    }

    @Override
    public <T, R> ProcessorBuilder<T, R> coupled(Subscriber<? super T> subscriber,
                                                 Publisher<? extends R> publisher) {
        Objects.requireNonNull(subscriber, "subscriber is null");
        Objects.requireNonNull(publisher, "publisher is null");
        return new HelidonReactiveProcessorBuilder<>(new HelidonReactiveStage.HRSCoupled(
                subscriber, publisher
        ));
    }
}
