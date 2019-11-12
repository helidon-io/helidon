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

package io.helidon.microprofile.reactive;

import io.helidon.common.reactive.Multi;
import io.helidon.microprofile.reactive.hybrid.HybridSubscriber;
import org.eclipse.microprofile.reactive.streams.operators.spi.Stage;
import org.eclipse.microprofile.reactive.streams.operators.spi.UnsupportedStageException;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class MultiStagesCollector<T> implements Collector<Stage, Multi<T>, CompletionStage<T>> {

    private Multi<T> multi = null;
    private CompletableFuture<T> completableFuture = null;

    @Override
    public Supplier<Multi<T>> supplier() {
        return () -> multi != null ? multi : Multi.empty();
    }

    public Publisher<T> getPublisher() {
        return MultiRS.from(multi);
    }

    @Override
    public BiConsumer<Multi<T>, Stage> accumulator() {
        //MP Stages to Helidon multi streams mapping
        return (m, stage) -> {

            // Create stream
            if (stage instanceof Stage.PublisherStage) {
                Stage.PublisherStage publisherStage = (Stage.PublisherStage) stage;
                Publisher<T> rsPublisher = (Publisher<T>) publisherStage.getRsPublisher();
                multi = MultiRS.toMulti(rsPublisher);

            } else if (stage instanceof Stage.Of) {
                //Collection
                Stage.Of stageOf = (Stage.Of) stage;
                List<?> fixedData = StreamSupport.stream(stageOf.getElements().spliterator(), false)
                        .collect(Collectors.toList());
                multi = (Multi<T>) Multi.just(fixedData);

            } else if (stage instanceof Stage.Map) {
                // Transform stream
                Stage.Map mapStage = (Stage.Map) stage;
                Function<T, T> mapper = (Function<T, T>) mapStage.getMapper();
                multi = Multi.from(multi).map(mapper::apply);

            } else if (stage instanceof Stage.Filter) {
                //Filter stream
                Stage.Filter stageFilter = (Stage.Filter) stage;
                Predicate<T> predicate = (Predicate<T>) stageFilter.getPredicate();
                multi = multi.filter(predicate);

            } else if (stage instanceof Stage.Peek) {
                Stage.Peek peekStage = (Stage.Peek) stage;
                Consumer<T> peekConsumer = (Consumer<T>) peekStage.getConsumer();
                multi = multi.peek(peekConsumer::accept);

            } else if (stage instanceof Stage.SubscriberStage) {
                //Subscribe to stream
                Stage.SubscriberStage subscriberStage = (Stage.SubscriberStage) stage;
                Subscriber<T> subscriber = (Subscriber<T>) subscriberStage.getRsSubscriber();
                this.completableFuture = new CompletableFuture<>();
                CompletionSubscriber<T, T> completionSubscriber = CompletionSubscriber.of(subscriber, completableFuture);
                multi.subscribe(HybridSubscriber.from(completionSubscriber));

            } else if (stage instanceof Stage.Collect) {
                //Collect stream
                Stage.Collect stageFilter = (Stage.Collect) stage;
                Collector<T, T, T> collector = (Collector<T, T, T>) stageFilter.getCollector();
                multi.collect(new io.helidon.common.reactive.Collector<T, T>() {
                    @Override
                    public void collect(T item) {
                        collector.finisher().apply(item);
                    }

                    @Override
                    public T value() {
                        return null;
                    }
                });
            } else {
                throw new UnsupportedStageException(stage);
            }
        };
    }

    @Override
    public BinaryOperator<Multi<T>> combiner() {
        return (a, b) -> null;
    }

    @Override
    public Function<Multi<T>, CompletionStage<T>> finisher() {
        return t -> toCompletableStage();
    }

    @Override
    public Set<Characteristics> characteristics() {
        return new HashSet<>(Collections.singletonList(Characteristics.IDENTITY_FINISH));
    }

    public CompletionStage<T> toCompletableStage() {
        return completableFuture;
    }
}
