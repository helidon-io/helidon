/*
 * Copyright (c)  2019 Oracle and/or its affiliates. All rights reserved.
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

import org.eclipse.microprofile.reactive.streams.operators.spi.Graph;
import org.eclipse.microprofile.reactive.streams.operators.spi.ReactiveStreamsEngine;
import org.eclipse.microprofile.reactive.streams.operators.spi.Stage;
import org.eclipse.microprofile.reactive.streams.operators.spi.SubscriberWithCompletionStage;
import org.eclipse.microprofile.reactive.streams.operators.spi.UnsupportedStageException;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

public class HelidonReactiveStreamEngine implements ReactiveStreamsEngine {

    private static final Logger LOGGER = Logger.getLogger(HelidonReactiveStreamEngine.class.getName());

    @Override
    public <T> Publisher<T> buildPublisher(Graph graph) throws UnsupportedStageException {
        MultiStagesCollector<T> multiStagesCollector = new MultiStagesCollector<>();
        Collection<Stage> stages = graph.getStages();
        stages.stream().collect(multiStagesCollector);
        return multiStagesCollector.getPublisher();
    }

    @Override
    public <T, R> SubscriberWithCompletionStage<T, R> buildSubscriber(Graph graph) throws UnsupportedStageException {
        Collection<Stage> stages = graph.getStages();
        if (stages.size() != 1) {
            //TODO: Support more than one stage
            throw new RuntimeException("Exactly one stage is supported for now");
        }
        Stage firstStage = stages.iterator().next();
        if (firstStage instanceof Stage.Collect) {
            // Foreach
            Stage.Collect collectStage = (Stage.Collect) firstStage;
            CompletableFuture<R> completableFuture = new CompletableFuture<>();
            return new SubscriberWithCompletionStage<T, R>() {
                @Override
                public CompletionStage<R> getCompletion() {
                    return completableFuture;
                }

                @Override
                public Subscriber<T> getSubscriber() {
                    return new Subscriber<T>() {

                        private Subscription subscription;
                        private Long chunkSize = 5L;
                        private Long chunkPosition = 0L;

                        @Override
                        public void onSubscribe(Subscription s) {
                            this.subscription = s;
                            subscription.request(chunkSize);
                        }

                        @Override
                        public void onNext(Object t) {
                            BiConsumer<Object, Object> accumulator = (BiConsumer) collectStage.getCollector().accumulator();
                            accumulator.accept(null, t);
                            accumulator.andThen((o, o2) -> {
                                incrementAndCheckChunkPosition();
                            });
                        }

                        @Override
                        public void onError(Throwable t) {
                            throw new RuntimeException(t);
                        }

                        @Override
                        public void onComplete() {
                            completableFuture.complete(null);
                        }

                        private void incrementAndCheckChunkPosition() {
                            chunkPosition++;
                            if (chunkPosition >= chunkSize) {
                                chunkPosition = 0L;
                                subscription.request(chunkSize);
                            }
                        }
                    };
                }
            };
        }
        throw new UnsupportedOperationException("Not implemented yet!!!");
    }

    @Override
    public <T, R> Processor<T, R> buildProcessor(Graph graph) throws UnsupportedStageException {
        throw new UnsupportedOperationException("Not implemented yet!!!");
    }

    @Override
    public <T> CompletionStage<T> buildCompletion(Graph graph) throws UnsupportedStageException {
        MultiStagesCollector multiStagesCollector = new MultiStagesCollector();
        graph.getStages().stream().collect(multiStagesCollector);
        CompletionStage<T> completionStage = (CompletionStage<T>) multiStagesCollector.toCompletableStage();
        return completionStage;
    }
}


