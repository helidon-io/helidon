/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.microprofile.reactive.streams.operators.CompletionSubscriber;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.eclipse.microprofile.reactive.streams.operators.spi.Graph;
import org.eclipse.microprofile.reactive.streams.operators.spi.ReactiveStreamsEngine;
import org.eclipse.microprofile.reactive.streams.operators.spi.Stage;
import org.eclipse.microprofile.reactive.streams.operators.spi.SubscriberWithCompletionStage;
import org.eclipse.microprofile.reactive.streams.operators.spi.ToGraphable;

/**
 * Builds a chain running a Subscriber and CompletionStage.
 * @param <T> the input value type
 * @param <R> the result value type
 */
final class HelidonReactiveSubscriberBuilder<T, R> implements SubscriberBuilder<T, R>, ToGraphable, Graph {

    private final List<Stage> stages;

    HelidonReactiveSubscriberBuilder(Stage initialStage) {
        this.stages = new ArrayList<>();
        this.stages.add(initialStage);
    }

    HelidonReactiveSubscriberBuilder(List<Stage> stages, Stage lastStage) {
        this.stages = new ArrayList<>();
        this.stages.addAll(stages);
        this.stages.add(lastStage);
    }

    HelidonReactiveSubscriberBuilder(List<Stage> stages, SubscriberBuilder<?, ?> builder) {
        this.stages = new ArrayList<>();
        this.stages.addAll(stages);
        this.stages.addAll(HelidonReactiveStage.getGraph(builder).getStages());
    }

    @Override
    public CompletionSubscriber<T, R> build() {
        SubscriberWithCompletionStage<T, R> scs = HelidonReactiveStreamsEngine.INSTANCE.buildSubscriber(this);
        return CompletionSubscriber.of(scs.getSubscriber(), scs.getCompletion());
    }

    @Override
    public CompletionSubscriber<T, R> build(ReactiveStreamsEngine engine) {
        if (engine == HelidonReactiveStreamsEngine.INSTANCE) {
            return build();
        }
        SubscriberWithCompletionStage<T, R> scs = engine.buildSubscriber(this);
        return CompletionSubscriber.of(scs.getSubscriber(), scs.getCompletion());
    }

    @Override
    public Collection<Stage> getStages() {
        return stages;
    }

    @Override
    public Graph toGraph() {
        return this;
    }
}
