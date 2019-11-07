package io.helidon.microprofile.reactive;

import io.helidon.common.reactive.Multi;
import org.eclipse.microprofile.reactive.streams.operators.spi.Graph;
import org.eclipse.microprofile.reactive.streams.operators.spi.ReactiveStreamsEngine;
import org.eclipse.microprofile.reactive.streams.operators.spi.Stage;
import org.eclipse.microprofile.reactive.streams.operators.spi.SubscriberWithCompletionStage;
import org.eclipse.microprofile.reactive.streams.operators.spi.UnsupportedStageException;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;

import java.util.Collection;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class HelidonReactiveStreamEngine implements ReactiveStreamsEngine {

    private static final Logger LOGGER = Logger.getLogger(HelidonReactiveStreamEngine.class.getName());

    @Override
    public <T> Publisher<T> buildPublisher(Graph graph) throws UnsupportedStageException {
        Collection<Stage> stages = graph.getStages();
        if (stages.size() != 1) {
            //TODO: Support more than one stage
            throw new RuntimeException("Exactly one stage is supported for now");
        }

        Stage firstStage = stages.iterator().next();
        if (firstStage instanceof Stage.PublisherStage) {
            Stage.PublisherStage publisherStage = (Stage.PublisherStage) firstStage;
            return (Publisher<T>) publisherStage.getRsPublisher();
        } else if (firstStage instanceof Stage.Of) {
            //Collection
            Stage.Of stageOf = (Stage.Of) firstStage;
            return Multi.<T>justMP(StreamSupport.stream(stageOf.getElements().spliterator(), false)
                    .map(e -> (T) e)
                    .collect(Collectors.toList()));
        } else {
            throw new UnsupportedStageException(firstStage);
        }
    }

    @Override
    public <T, R> SubscriberWithCompletionStage<T, R> buildSubscriber(Graph graph) throws UnsupportedStageException {
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
        completionStage.exceptionally(t -> {throw new RuntimeException(t);});
        return completionStage;
    }
}


