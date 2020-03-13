package io.helidon.microprofile.reactive;

import org.eclipse.microprofile.reactive.streams.operators.CompletionRunner;
import org.eclipse.microprofile.reactive.streams.operators.SubscriberBuilder;
import org.eclipse.microprofile.reactive.streams.operators.spi.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionStage;

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
