package io.helidon.microprofile.reactive;

import io.helidon.common.reactive.Multi;
import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
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
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class HelidonReactiveStreamEngine implements ReactiveStreamsEngine {

    private static final Logger LOGGER = Logger.getLogger(HelidonReactiveStreamEngine.class.getName());

    @Override
    public <T> Publisher<T> buildPublisher(Graph graph) throws UnsupportedStageException {
        Collection<Stage> stages = graph.getStages();

        Publisher<T> publisher = null;

        for (Stage stage : stages) {
            if (stage instanceof Stage.PublisherStage) {
                Stage.PublisherStage publisherStage = (Stage.PublisherStage) stage;
                publisher = (Publisher<T>) publisherStage.getRsPublisher();
            } else if (stage instanceof Stage.Map) {
                Stage.Map mapStage = (Stage.Map) stage;
                //TODO: maps...
                //mapStage.getMapper().apply(pub);
            } else if (stage instanceof Stage.Of) {
                //Collection
                Stage.Of stageOf = (Stage.Of) stage;
                return Multi.<T>justMP(StreamSupport.stream(stageOf.getElements().spliterator(), false)
                        .map(e -> (T) e)
                        .collect(Collectors.toList()));
            } else {
                throw new UnsupportedStageException(stage);
            }
        }

        return publisher;
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
        completionStage.exceptionally(t -> {
            throw new RuntimeException(t);
        });
        return completionStage;
    }
}


