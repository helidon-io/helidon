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

import io.helidon.common.reactive.FilterProcessor;
import io.helidon.common.reactive.Flow;
import io.helidon.common.reactive.LimitProcessor;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.MultiMappingProcessor;
import io.helidon.common.reactive.PeekProcessor;
import io.helidon.microprofile.reactive.hybrid.HybridSubscriber;
import org.eclipse.microprofile.reactive.streams.operators.spi.Stage;
import org.eclipse.microprofile.reactive.streams.operators.spi.SubscriberWithCompletionStage;
import org.eclipse.microprofile.reactive.streams.operators.spi.UnsupportedStageException;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

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

public class MultiStagesCollector<T> implements Collector<Stage, Multi<T>, CompletionStage<T>> {

    private Multi<T> multi = null;
    private List<Flow.Processor<Object, Object>> processorList = new ArrayList<>();
    private CompletionStage<Object> completionStage = null;
    private HelidonSubscriberWithCompletionStage<T> subscriberWithCompletionStage = null;

    @Override
    public Supplier<Multi<T>> supplier() {
        return () -> multi != null ? multi : Multi.empty();
    }

    private void subscribeUpStream() {
        // If producer was supplied
        if (multi != null) {
            for (Flow.Processor p : processorList) {
                multi.subscribe(p);
                multi = (Multi<T>) p;
            }
        } else {
            throw new RuntimeException("No producer was supplied");
        }
    }

    public Publisher<T> getPublisher() {
        subscribeUpStream();
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
                Function<Object, Object> mapper = (Function<Object, Object>) mapStage.getMapper();
                processorList.add(new MultiMappingProcessor<>(mapper::apply));

            } else if (stage instanceof Stage.Filter) {
                //Filter stream
                Stage.Filter stageFilter = (Stage.Filter) stage;
                Predicate<T> predicate = (Predicate<T>) stageFilter.getPredicate();
                processorList.add(new FilterProcessor(predicate));

            } else if (stage instanceof Stage.Peek) {
                Stage.Peek peekStage = (Stage.Peek) stage;
                Consumer<Object> peekConsumer = (Consumer<Object>) peekStage.getConsumer();
                processorList.add(new PeekProcessor<Object>(peekConsumer::accept));

            } else if (stage instanceof Stage.Limit) {
                Stage.Limit limitStage = (Stage.Limit) stage;
                processorList.add(new LimitProcessor(limitStage.getLimit()));

            } else if (stage instanceof Stage.SubscriberStage) {
                //Subscribe to stream
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
                //getPublisher().subscribe(HybridSubscriber.from(subscriberWithCompletionStage.getSubscriber()));

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
        return t -> getCompletableStage();
    }

    @Override
    public Set<Characteristics> characteristics() {
        return new HashSet<>(Collections.singletonList(Characteristics.IDENTITY_FINISH));
    }

    public <U, W> SubscriberWithCompletionStage<U, W> getSubscriberWithCompletionStage() {
        return (SubscriberWithCompletionStage<U, W>) subscriberWithCompletionStage;
    }

    public <U> CompletionStage<U> getCompletableStage() {
        return (CompletionStage<U>) (completionStage != null ? completionStage : subscriberWithCompletionStage.getCompletion());
    }
}
