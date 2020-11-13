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
import java.util.concurrent.CompletionStage;

import org.eclipse.microprofile.reactive.streams.operators.CompletionRunner;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.eclipse.microprofile.reactive.streams.operators.spi.Graph;
import org.eclipse.microprofile.reactive.streams.operators.spi.ReactiveStreamsEngine;
import org.eclipse.microprofile.reactive.streams.operators.spi.Stage;
import org.eclipse.microprofile.reactive.streams.operators.spi.ToGraphable;

/**
 * Holds onto a chain of Stages to be run.
 * @param <T> the result type
 */
final class HelidonReactiveCompletionRunner<T> implements CompletionRunner<T>, ToGraphable, Graph {

    private final List<Stage> stages;

    HelidonReactiveCompletionRunner(List<Stage> stages, SubscriberBuilder<?, ?> builder) {
        this.stages = new ArrayList<>(stages);
        this.stages.addAll(HelidonReactiveStage.getGraph(builder).getStages());
    }

    HelidonReactiveCompletionRunner(List<Stage> stages, Stage lastStage) {
        this.stages = new ArrayList<>(stages);
        this.stages.add(lastStage);
    }

    @Override
    public CompletionStage<T> run() {
        return HelidonReactiveStreamsEngine.INSTANCE.buildCompletion(stages);
    }

    @Override
    public CompletionStage<T> run(ReactiveStreamsEngine engine) {
        if (engine == HelidonReactiveStreamsEngine.INSTANCE) {
            return run();
        }
        return engine.buildCompletion(this);
    }

    @Override
    public Graph toGraph() {
        return this;
    }

    @Override
    public Collection<Stage> getStages() {
        return stages;
    }
}
