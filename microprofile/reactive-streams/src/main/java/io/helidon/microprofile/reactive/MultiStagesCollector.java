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

import java.util.ArrayList;
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

import io.helidon.common.reactive.FilterProcessor;
import io.helidon.common.reactive.Flow;
import io.helidon.common.reactive.LimitProcessor;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.MultiMappingProcessor;
import io.helidon.common.reactive.PeekProcessor;
import io.helidon.microprofile.reactive.hybrid.HybridSubscriber;

import org.eclipse.microprofile.reactive.streams.operators.spi.Graph;
import org.eclipse.microprofile.reactive.streams.operators.spi.Stage;
import org.eclipse.microprofile.reactive.streams.operators.spi.SubscriberWithCompletionStage;
import org.eclipse.microprofile.reactive.streams.operators.spi.UnsupportedStageException;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;


/**
 * Collect {@link org.reactivestreams Reactive Streams}
 * {@link org.eclipse.microprofile.reactive.streams.operators.spi.Stage Stages}
 * to {@link org.reactivestreams.Publisher}, {@link org.reactivestreams.Processor}
 * or {@link org.reactivestreams.Subscriber}.
 *
 * @param <T>
 */
class MultiStagesCollector<T> implements Collector<Stage, Multi<T>, CompletionStage<T>> {

    private Multi<T> multi = null;
    private List<Flow.Processor<Object, Object>> processorList = new ArrayList<>();
    private CompletionStage<Object> completionStage = null;
    private HelidonSubscriberWithCompletionStage<T> subscriberWithCompletionStage = null;

    @Override
    public Supplier<Multi<T>> supplier() {
        return () -> multi != null ? multi : Multi.empty();
    }

    @SuppressWarnings("unchecked")
    private void subscribeUpStream() {
        if (multi != null) {
            for (Flow.Processor p : processorList) {
                multi.subscribe(p);
                multi = (Multi<T>) p;
            }
        } else {
            throw new RuntimeException("No producer was supplied");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public BiConsumer<Multi<T>, Stage> accumulator() {
        //MP Stages to Helidon multi streams mapping
        return (m, stage) -> {

            if (stage instanceof Stage.PublisherStage) {
                Stage.PublisherStage publisherStage = (Stage.PublisherStage) stage;
                Publisher<T> rsPublisher = (Publisher<T>) publisherStage.getRsPublisher();
                multi = MultiRS.toMulti(rsPublisher);

            } else if (stage instanceof Stage.Of) {
                Stage.Of stageOf = (Stage.Of) stage;
                List<?> fixedData = StreamSupport.stream(stageOf.getElements().spliterator(), false)
                        .collect(Collectors.toList());
                multi = (Multi<T>) Multi.just(fixedData);

            } else if (stage instanceof Stage.Map) {
                Stage.Map mapStage = (Stage.Map) stage;
                Function<Object, Object> mapper = (Function<Object, Object>) mapStage.getMapper();
                processorList.add(new MultiMappingProcessor<>(mapper::apply));

            } else if (stage instanceof Stage.Filter) {
                Stage.Filter stageFilter = (Stage.Filter) stage;
                Predicate<T> predicate = (Predicate<T>) stageFilter.getPredicate();
                processorList.add(new FilterProcessor(predicate));

            } else if (stage instanceof Stage.Peek) {
                Stage.Peek peekStage = (Stage.Peek) stage;
                Consumer<Object> peekConsumer = (Consumer<Object>) peekStage.getConsumer();
                processorList.add(new PeekProcessor<Object>(peekConsumer));

            } else if (stage instanceof Stage.Limit) {
                Stage.Limit limitStage = (Stage.Limit) stage;
                processorList.add(new LimitProcessor(limitStage.getLimit()));

            } else if (stage instanceof Stage.FlatMap) {
                Stage.FlatMap flatMapStage = (Stage.FlatMap) stage;
                Function<?, Graph> mapper = flatMapStage.getMapper();
                processorList.add(new FlatMapProcessor(mapper));

            } else if (stage instanceof Stage.SubscriberStage) {
                Stage.SubscriberStage subscriberStage = (Stage.SubscriberStage) stage;
                Subscriber<T> subscriber = (Subscriber<T>) subscriberStage.getRsSubscriber();
                this.completionStage = new CompletableFuture<>();
                CompletionSubscriber<T, Object> completionSubscriber = CompletionSubscriber.of(subscriber, completionStage);
                // If producer was supplied
                subscribeUpStream();
                multi.subscribe(HybridSubscriber.from(completionSubscriber));

            } else if (stage instanceof Stage.Collect) {
                // Foreach
                Stage.Collect collectStage = (Stage.Collect) stage;
                this.subscriberWithCompletionStage = new HelidonSubscriberWithCompletionStage<>(collectStage, processorList);
                // If producer was supplied
                if (multi != null) {
                    multi.subscribe(HybridSubscriber.from(subscriberWithCompletionStage.getSubscriber()));
                }

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
        return t -> getCompletionStage();
    }

    @Override
    public Set<Characteristics> characteristics() {
        return new HashSet<>(Collections.singletonList(Characteristics.IDENTITY_FINISH));
    }

    /**
     * Return subscriber from even incomplete graph,
     * in case of incomplete graph does subscriptions downstream automatically in the
     * {@link io.helidon.microprofile.reactive.HelidonSubscriberWithCompletionStage}.
     *
     * @param <U> type of items subscriber consumes
     * @param <W> type of items subscriber emits
     * @return {@link org.eclipse.microprofile.reactive.streams.operators.spi.SubscriberWithCompletionStage}
     */
    @SuppressWarnings("unchecked")
    <U, W> SubscriberWithCompletionStage<U, W> getSubscriberWithCompletionStage() {
        return (SubscriberWithCompletionStage<U, W>) subscriberWithCompletionStage;
    }

    /**
     * Return {@link java.util.concurrent.CompletionStage}
     * either from supplied {@link org.reactivestreams.Subscriber}
     * for example by {@link org.reactivestreams.Publisher#subscribe(org.reactivestreams.Subscriber)}
     * or from completion stage for example
     * {@link org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder#forEach(java.util.function.Consumer)}.
     *
     * @param <U> type of items subscriber consumes
     * @return {@link io.helidon.microprofile.reactive.HelidonSubscriberWithCompletionStage}
     */
    @SuppressWarnings("unchecked")
    <U> CompletionStage<U> getCompletionStage() {
        return (CompletionStage<U>) (completionStage != null ? completionStage : subscriberWithCompletionStage.getCompletion());
    }

    /**
     * Return {@link org.reactivestreams.Processor} wrapping all processor stages from processor builder.
     * <p/>See example:
     * <pre>{@code
     *   Processor<Integer, String> processor = ReactiveStreams.<Integer>builder()
     *       .map(i -> i + 1)
     *       .flatMap(i -> ReactiveStreams.of(i, i))
     *       .map(i -> Integer.toString(i))
     *       .buildRs();
     * }</pre>
     *
     * @param <T> type of items subscriber consumes
     * @param <R> type of items subscriber emits
     * @return {@link org.reactivestreams.Processor} wrapping all processor stages
     */
    @SuppressWarnings("unchecked")
    <T, R> Processor<T, R> getProcessor() {
        return (Processor<T, R>) new HelidonCumulativeProcessor(processorList);
    }

    /**
     * Returns {@link org.reactivestreams.Publisher} made from supplied stages.
     * <p/>See example:
     * <pre>{@code
     *   ReactiveStreams
     *      .of("10", "20", "30")
     *      .map(a -> a.replaceAll("0", ""))
     *      .map(Integer::parseInt)
     *      .buildRs()
     * }</pre>
     *
     * @return {@link org.reactivestreams.Publisher}
     */
    Publisher<T> getPublisher() {
        subscribeUpStream();
        return MultiRS.from(multi);
    }
}
