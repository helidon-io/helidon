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


import java.util.concurrent.CompletionStage;

import org.eclipse.microprofile.reactive.streams.operators.spi.Graph;
import org.eclipse.microprofile.reactive.streams.operators.spi.ReactiveStreamsEngine;
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

    @Override
    @SuppressWarnings("unchecked")
    public <T> Publisher<T> buildPublisher(Graph graph) throws UnsupportedStageException {
        return GraphBuilder.create().from(graph).getPublisher();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, R> SubscriberWithCompletionStage<T, R> buildSubscriber(Graph graph) throws UnsupportedStageException {
        return GraphBuilder.create().from(graph).getSubscriberWithCompletionStage();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, R> Processor<T, R> buildProcessor(Graph graph) throws UnsupportedStageException {
        return GraphBuilder.create().from(graph).getProcessor();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> CompletionStage<T> buildCompletion(Graph graph) throws UnsupportedStageException {
        return GraphBuilder.create().from(graph).getCompletionStage();
    }
}


