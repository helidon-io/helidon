package io.helidon.microprofile.reactive;

import io.helidon.common.reactive.Collector;
import io.helidon.common.reactive.Multi;
import io.helidon.microprofile.messaging.IncomingSubscriber;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.streams.operators.spi.Graph;
import org.eclipse.microprofile.reactive.streams.operators.spi.ReactiveStreamsEngine;
import org.eclipse.microprofile.reactive.streams.operators.spi.Stage;
import org.eclipse.microprofile.reactive.streams.operators.spi.SubscriberWithCompletionStage;
import org.eclipse.microprofile.reactive.streams.operators.spi.UnsupportedStageException;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletionStage;

public class HelidonReactiveStreamEngine implements ReactiveStreamsEngine {
    @Override
    public <T> Publisher<T> buildPublisher(Graph graph) throws UnsupportedStageException {
        //TODO: Stages
        return (Publisher<T>) ((Stage.PublisherStage) graph.getStages().stream().findFirst().get()).getRsPublisher();
        //return graph.getStages().stream().findFirst().get().
    }

    @Override
    public <T, R> SubscriberWithCompletionStage<T, R> buildSubscriber(Graph graph) throws UnsupportedStageException {
        return null;
    }

    @Override
    public <T, R> Processor<T, R> buildProcessor(Graph graph) throws UnsupportedStageException {
        return null;
    }

    @Override
    public <T> CompletionStage<T> buildCompletion(Graph graph) throws UnsupportedStageException {
        //TODO: Well this is ugly
        Multi multi = graph.getStages().stream()
                .filter(s -> s instanceof Stage.PublisherStage)
                .map(s -> ((Stage.PublisherStage) s).getRsPublisher())
                .filter(p -> p instanceof Multi)
                .map(p -> (Multi) p)
                .findFirst().get();

        IncomingSubscriber incomingSubscriber = graph.getStages().stream()
                .filter(s -> s instanceof Stage.SubscriberStage)
                .map(s -> ((Stage.SubscriberStage) s).getRsSubscriber())
                .map(s -> (IncomingSubscriber) s)
                .findFirst().get();

        return multi.collect(new Collector() {
            @Override
            public void collect(Object item) {
                incomingSubscriber.onNext(Message.of(item));
            }

            @Override
            public Object value() {
                return null;
            }
        }).toStage();

    }
}
