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


import java.util.Collection;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

import org.eclipse.microprofile.reactive.streams.operators.spi.Graph;
import org.eclipse.microprofile.reactive.streams.operators.spi.ReactiveStreamsEngine;
import org.eclipse.microprofile.reactive.streams.operators.spi.Stage;
import org.eclipse.microprofile.reactive.streams.operators.spi.SubscriberWithCompletionStage;
import org.eclipse.microprofile.reactive.streams.operators.spi.UnsupportedStageException;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;

/**
 * Implementation of {@link org.reactivestreams Reactive Streams} with operators
 * backed by {@link io.helidon.common.reactive Helidon reactive streams}.
 *
 * @see org.eclipse.microprofile.reactive.streams.operators.spi.ReactiveStreamsEngine
 */
public class HelidonReactiveStreamEngine implements ReactiveStreamsEngine {

    private static final Logger LOGGER = Logger.getLogger(HelidonReactiveStreamEngine.class.getName());

    @Override
    @SuppressWarnings("unchecked")
    public <T> Publisher<T> buildPublisher(Graph graph) throws UnsupportedStageException {
        MultiStagesCollector<T> multiStagesCollector = new MultiStagesCollector<>();
        Collection<Stage> stages = graph.getStages();
        stages.stream().collect(multiStagesCollector);
        return multiStagesCollector.getPublisher();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, R> SubscriberWithCompletionStage<T, R> buildSubscriber(Graph graph) throws UnsupportedStageException {
        MultiStagesCollector multiStagesCollector = new MultiStagesCollector();
        graph.getStages().stream().collect(multiStagesCollector);
        return multiStagesCollector.getSubscriberWithCompletionStage();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, R> Processor<T, R> buildProcessor(Graph graph) throws UnsupportedStageException {
        MultiStagesCollector multiStagesCollector = new MultiStagesCollector();
        graph.getStages().stream().collect(multiStagesCollector);
        return multiStagesCollector.getProcessor();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> CompletionStage<T> buildCompletion(Graph graph) throws UnsupportedStageException {
        MultiStagesCollector multiStagesCollector = new MultiStagesCollector();
        graph.getStages().stream().collect(multiStagesCollector);
        CompletionStage<T> completionStage = (CompletionStage<T>) multiStagesCollector.getCompletionStage();
        return completionStage;
    }
}


