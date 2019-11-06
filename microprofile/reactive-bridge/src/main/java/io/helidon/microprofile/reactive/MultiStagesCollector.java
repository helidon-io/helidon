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

import io.helidon.common.mapper.Mapper;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;
import io.helidon.common.reactive.valve.Valves;
import org.eclipse.microprofile.reactive.streams.operators.spi.Stage;
import org.eclipse.microprofile.reactive.streams.operators.spi.UnsupportedStageException;
import org.reactivestreams.Subscriber;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class MultiStagesCollector implements Collector<Stage, Multi<Object>, CompletionStage<Object>> {

    private Multi<Object> multi = null;
    private Single<Object> single = null;

    @Override
    public Supplier<Multi<Object>> supplier() {
        return () -> multi != null ? multi : Multi.empty();
    }

    @Override
    public BiConsumer<Multi<Object>, Stage> accumulator() {
        //MP Stages to Helidon multi streams mapping
        return (m, stage) -> {

            // Create stream
            if (stage instanceof Stage.PublisherStage) {
                Stage.PublisherStage publisherStage = (Stage.PublisherStage) stage;
                if (publisherStage.getRsPublisher() instanceof Multi) {
                    multi = (Multi) ((Stage.PublisherStage) stage).getRsPublisher();
                } else {

                    throw new UnsupportedStageException(stage);
                }
            } else if (stage instanceof Stage.Of) {
                //Collection
                Stage.Of stageOf = (Stage.Of) stage;
                multi = Multi.just(StreamSupport.stream(stageOf.getElements().spliterator(), false)
                        .collect(Collectors.toList()));

            } else if (stage instanceof Stage.Map) {
                // Transform stream
                Stage.Map stageMap = (Stage.Map) stage;
                multi = multi.map(new Mapper<Object, Object>() {
                    @Override
                    public Object map(Object t) {
                        Function<Object, Object> mapper = (Function<Object, Object>) stageMap.getMapper();
                        return mapper.apply(t);
                    }
                });

            } else if (stage instanceof Stage.Filter) {
                //Filter stream
                Stage.Filter stageFilter = (Stage.Filter) stage;
                Predicate<Object> predicate = (Predicate<Object>) stageFilter.getPredicate();
                //TODO: Valve is deprecated, plan is implement filter in Multi
                multi = Multi.from(Valves.from(multi).filter(predicate).toPublisher());

            } else if (stage instanceof Stage.SubscriberStage) {
                //Subscribe to stream
                Stage.SubscriberStage subscriberStage = (Stage.SubscriberStage) stage;
                Subscriber<Object> subscriber = (Subscriber<Object>) subscriberStage.getRsSubscriber();
                single = multi.collect(new io.helidon.common.reactive.Collector<Object, Object>() {
                    @Override
                    public void collect(Object item) {
                        subscriber.onNext(item);
                    }

                    @Override
                    public Object value() {
                        return null;
                    }
                });

            } else if (stage instanceof Stage.Collect) {
                //Collect stream
                Stage.Collect stageFilter = (Stage.Collect) stage;
                Collector<Object, Object, Object> collector = (Collector<Object, Object, Object>) stageFilter.getCollector();
                single = multi.collect(new io.helidon.common.reactive.Collector<Object, Object>() {
                    @Override
                    public void collect(Object item) {
                        collector.finisher().apply(item);
                    }

                    @Override
                    public Object value() {
                        return null;
                    }
                });
            } else {
                throw new UnsupportedStageException(stage);
            }
        };
    }

    @Override
    public BinaryOperator<Multi<Object>> combiner() {
        return (a, b) -> null;
    }

    @Override
    public Function<Multi<Object>, CompletionStage<Object>> finisher() {
        return t -> toCompletableStage();
    }

    @Override
    public Set<Characteristics> characteristics() {
        return new HashSet<>(Collections.singletonList(Characteristics.IDENTITY_FINISH));
    }

    public Multi<Object> getMulti() {
        return this.multi;
    }

    public Single<Object> getSingle() {
        return this.single;
    }

    public CompletionStage<Object> toCompletableStage() {
        return this.single != null ? single.toStage() : null;
    }
}
