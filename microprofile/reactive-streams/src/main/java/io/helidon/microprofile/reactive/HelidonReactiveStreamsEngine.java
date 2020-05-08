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

import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import io.helidon.common.reactive.Multi;

import org.eclipse.microprofile.reactive.streams.operators.spi.Graph;
import org.eclipse.microprofile.reactive.streams.operators.spi.ReactiveStreamsEngine;
import org.eclipse.microprofile.reactive.streams.operators.spi.Stage;
import org.eclipse.microprofile.reactive.streams.operators.spi.SubscriberWithCompletionStage;
import org.eclipse.microprofile.reactive.streams.operators.spi.UnsupportedStageException;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * Implementation of {@link org.reactivestreams Reactive Streams} with operators
 * backed by {@link io.helidon.common.reactive Helidon reactive streams}.
 *
 * @see #INSTANCE
 * @see org.eclipse.microprofile.reactive.streams.operators.spi.ReactiveStreamsEngine
 */
public final class HelidonReactiveStreamsEngine implements ReactiveStreamsEngine {

    /** The singleton instance. */
    public static final HelidonReactiveStreamsEngine INSTANCE = new HelidonReactiveStreamsEngine();

    @Override
    public <T> Publisher<T> buildPublisher(Graph graph) throws UnsupportedStageException {
        return buildPublisher(graph.getStages());
    }

    @Override
    public <T, R> SubscriberWithCompletionStage<T, R> buildSubscriber(Graph graph) throws UnsupportedStageException {
        return buildSubscriber(graph.getStages());
    }

    @Override
    public <T, R> Processor<T, R> buildProcessor(Graph graph) throws UnsupportedStageException {
        return buildProcessor(graph.getStages());
    }

    @Override
    public <T> CompletionStage<T> buildCompletion(Graph graph) throws UnsupportedStageException {
        return buildCompletion(graph.getStages());
    }

    @SuppressWarnings("unchecked")
    <T> Publisher<T> buildPublisher(Iterable<Stage> stages) throws UnsupportedStageException {
        return (Publisher<T>) build(stages, Mode.PUBLISHER);
    }

    @SuppressWarnings("unchecked")
    <T, R> Processor<T, R> buildProcessor(Iterable<Stage> stages) throws UnsupportedStageException {
        return (Processor<T, R>) build(stages, Mode.PROCESSOR);
    }

    @SuppressWarnings("unchecked")
    <T, R> SubscriberWithCompletionStage<T, R> buildSubscriber(Iterable<Stage> stages) throws UnsupportedStageException {
        return (SubscriberWithCompletionStage<T, R>) build(stages, Mode.SUBSCRIBER);
    }

    @SuppressWarnings("unchecked")
    <T> CompletionStage<T> buildCompletion(Iterable<Stage> stages) throws UnsupportedStageException {
        return (CompletionStage<T>) build(stages, Mode.COMPLETION);
    }

    /**
     * How should the graph be built?
     * <p>
     * Some graph building modes have shared stages to consider.
     */
    enum Mode {
        PUBLISHER,
        PROCESSOR,
        SUBSCRIBER,
        COMPLETION
    }

    static void requireNullSource(Object o, Stage stage) {
        if (o != null) {
            throw new IllegalArgumentException("Graph already has a source-like stage! Found " + stage.getClass().getSimpleName());
        }
    }

    static void requireNullFront(Object o, Stage stage) {
        if (o != null) {
            throw new IllegalArgumentException("Graph already has an inlet Subscriber! Found " + stage.getClass().getSimpleName());
        }
    }

    static void requireSource(Object o, Stage stage) {
        if (o == null) {
            throw new IllegalArgumentException("Graph is missing a source-like stage! Found " + stage.getClass().getSimpleName());
        }
    }

    static void requireNullTerminal(Object o, Stage stage) {
        if (o != null) {
            throw new IllegalArgumentException("Graph already has a terminal stage! Found " + stage.getClass().getSimpleName());
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static Object build(Iterable<Stage> graph, Mode mode) throws UnsupportedStageException {
        Flow.Subscriber graphInlet = null;
        Multi result = null;
        CompletionStage completion = null;

        Iterator<Stage> stages = graph.iterator();
        Stage stage = null;

        if (stages.hasNext()) {
            stage = stages.next();
        }

        // PublisherFactory.builder() is not allowed to start with an
        // identity processor stage apparently as it would have too many
        // nodes in the graph
        // we'll patch in an identity processor here
        if (mode == Mode.PROCESSOR || mode == Mode.SUBSCRIBER) {
            if (stage == null
                    || !(
                    (stage instanceof Stage.ProcessorStage)
                            || (stage instanceof Stage.Coupled)
            )) {
                DeferredProcessor processor = new DeferredProcessor<>();
                graphInlet = processor;
                result = processor;
            }
        }

        if (stage != null) {
            boolean once = false;
            for (;;) {

                if (once) {
                    if (!stages.hasNext()) {
                        break;
                    }
                    stage = stages.next();
                }
                once = true;

                if (stage instanceof Stage.PublisherStage) {
                    requireNullSource(result, stage);

                    Publisher publisher = ((Stage.PublisherStage) stage).getRsPublisher();
                    result = Multi.from(FlowAdapters.toFlowPublisher(publisher));
                    continue;
                }
                if (stage instanceof Stage.Of) {
                    requireNullSource(result, stage);

                    Iterable iterable = ((Stage.Of) stage).getElements();
                    result = Multi.from(iterable);
                    continue;
                }
                if (stage instanceof Stage.ProcessorStage) {
                    if (result == null) {
                        requireNullFront(graphInlet, stage);
                        // act as a source
                        Processor processor = ((Stage.ProcessorStage) stage).getRsProcessor();
                        graphInlet = FlowAdapters.toFlowSubscriber(processor);
                        result = Multi.from(FlowAdapters.toFlowPublisher(processor));
                    } else {
                        // act as a middle operator
                        Processor processor = ((Stage.ProcessorStage) stage).getRsProcessor();
                        // FIXME should this be deferred for when the downstream actually subscribes?
                        result = new DeferredViaProcessor(result, FlowAdapters.toFlowProcessor(processor));
                    }
                    continue;
                }
                if (stage instanceof Stage.Failed) {
                    requireNullSource(result, stage);

                    Throwable throwable = ((Stage.Failed) stage).getError();
                    result = Multi.error(throwable);
                    continue;
                }
                if (stage instanceof Stage.Concat) {
                    requireNullSource(result, stage);
                    Graph g1 = ((Stage.Concat) stage).getFirst();
                    Graph g2 = ((Stage.Concat) stage).getSecond();
                    result = Multi.concat(
                            FlowAdapters.toFlowPublisher((Publisher) build(g1.getStages(), Mode.PUBLISHER)),
                            FlowAdapters.toFlowPublisher((Publisher) build(g2.getStages(), Mode.PUBLISHER)));
                    continue;
                }
                if (stage instanceof Stage.FromCompletionStage) {
                    requireNullSource(result, stage);
                    CompletionStage cs = ((Stage.FromCompletionStage) stage).getCompletionStage();
                    result = Multi.from(cs);
                    continue;
                }
                if (stage instanceof Stage.FromCompletionStageNullable) {
                    requireNullSource(result, stage);
                    CompletionStage cs = ((Stage.FromCompletionStageNullable) stage).getCompletionStage();
                    result = Multi.from(cs, true);
                    continue;
                }
                if (stage instanceof Stage.Coupled) {
                    Stage.Coupled coupled = (Stage.Coupled) stage;
                    if (result == null) {
                        requireNullFront(graphInlet, stage);
                    }

                    Flow.Subscriber s = FlowAdapters.toFlowSubscriber(((SubscriberWithCompletionStage) build(
                            coupled.getSubscriber().getStages(), Mode.SUBSCRIBER))
                            .getSubscriber());
                    Multi f = Multi.from(FlowAdapters.toFlowPublisher(
                            (Publisher) build(coupled.getPublisher().getStages(), Mode.PUBLISHER)));

                    Flow.Processor processor = coupledBuildProcessor(s, f);
                    if (result == null) {
                        graphInlet = processor;
                        result = Multi.from(processor);
                    } else {
                        result = new DeferredViaProcessor(result, processor);
                    }

                    continue;
                }

                // ------------------------------------------------------------------------------

                if (stage instanceof Stage.Map) {
                    requireSource(result, stage);

                    Function mapper = ((Stage.Map) stage).getMapper();
                    result = result.map(mapper::apply);
                    continue;
                }
                if (stage instanceof Stage.Peek) {
                    requireSource(result, stage);

                    Consumer consumer = ((Stage.Peek) stage).getConsumer();
                    result = result.peek(consumer);
                    continue;
                }
                if (stage instanceof Stage.Filter) {
                    requireSource(result, stage);

                    Predicate predicate = ((Stage.Filter) stage).getPredicate();
                    result = result.filter(predicate);
                    continue;
                }
                if (stage instanceof Stage.DropWhile) {
                    requireSource(result, stage);

                    Predicate predicate = ((Stage.DropWhile) stage).getPredicate();
                    result = result.dropWhile(predicate);
                    continue;
                }
                if (stage instanceof Stage.Skip) {
                    requireSource(result, stage);

                    long n = ((Stage.Skip) stage).getSkip();
                    result = result.skip(n);
                    continue;
                }
                if (stage instanceof Stage.Limit) {
                    requireSource(result, stage);

                    long n = ((Stage.Limit) stage).getLimit();
                    result = result.limit(n);
                    continue;
                }
                if (stage instanceof Stage.Distinct) {
                    requireSource(result, stage);

                    result = result.distinct();
                    continue;
                }
                if (stage instanceof Stage.TakeWhile) {
                    requireSource(result, stage);

                    Predicate predicate = ((Stage.TakeWhile) stage).getPredicate();
                    result = result.takeWhile(predicate);
                    continue;
                }
                if (stage instanceof Stage.FlatMap) {
                    requireSource(result, stage);

                    Function mapper = ((Stage.FlatMap) stage).getMapper();
                    // FIXME dedicated concatMap
                    result = result.flatMap(v -> new MultiNullGuard<>(
                            FlowAdapters.toFlowPublisher(
                                    (Publisher) build(((Graph) mapper.apply(v)).getStages(), Mode.PUBLISHER)
                            )
                    ), 1, false, Flow.defaultBufferSize());
                    continue;
                }
                if (stage instanceof Stage.FlatMapCompletionStage) {
                    requireSource(result, stage);

                    Function mapper = ((Stage.FlatMapCompletionStage) stage).getMapper();
                    // FIXME dedicated concatMap
                    result = result.flatMap(v -> Multi.from((CompletionStage) mapper.apply(v)), 1, false, 1);
                    continue;
                }
                if (stage instanceof Stage.FlatMapIterable) {
                    requireSource(result, stage);

                    Function mapper = ((Stage.FlatMapIterable) stage).getMapper();
                    result = result.flatMapIterable(mapper);
                    continue;
                }
                if (stage instanceof Stage.OnError) {
                    requireSource(result, stage);

                    Consumer consumer = ((Stage.OnError) stage).getConsumer();
                    result = result.onError(consumer);
                    continue;
                }
                if (stage instanceof Stage.OnTerminate) {
                    requireSource(result, stage);

                    Runnable runnable = ((Stage.OnTerminate) stage).getAction();
                    result = result.onTerminate(runnable);
                    continue;
                }
                if (stage instanceof Stage.OnComplete) {
                    requireSource(result, stage);

                    Runnable runnable = ((Stage.OnComplete) stage).getAction();
                    result = result.onComplete(runnable);
                    continue;
                }
                if (stage instanceof Stage.OnErrorResume) {
                    requireSource(result, stage);

                    Function mapper = ((Stage.OnErrorResume) stage).getFunction();
                    result = result.onErrorResume(mapper);
                    continue;
                }
                if (stage instanceof Stage.OnErrorResumeWith) {
                    requireSource(result, stage);

                    Function mapper = ((Stage.OnErrorResumeWith) stage).getFunction();
                    result = result.onErrorResumeWith(e -> {
                        Graph g = (Graph) mapper.apply(e);
                        return FlowAdapters.toFlowPublisher(
                                (Publisher) build(g.getStages(), Mode.PUBLISHER));
                    });
                    continue;
                }

                if (stage instanceof Stage.FindFirst) {
                    if (mode == Mode.SUBSCRIBER || mode == Mode.COMPLETION) {
                        if (graphInlet != null) {
                            requireSource(result, stage);
                            requireNullTerminal(completion, stage);
                        }

                        BasicFindFirstSubscriber cs = new BasicFindFirstSubscriber();
                        completion = cs.completable();
                        if (result != null) {
                            result.subscribe(cs);
                        } else {
                            graphInlet = cs;
                        }

                        continue;
                    }
                    throw new IllegalArgumentException(
                            "Stage.FindFirst is only supported when building via buildSubscriber or buildCompletion");
                }
                if (stage instanceof Stage.SubscriberStage) {
                    if (mode == Mode.SUBSCRIBER || mode == Mode.COMPLETION) {
                        if (graphInlet != null) {
                            requireSource(result, stage);
                            requireNullTerminal(completion, stage);
                        }

                        Subscriber s = ((Stage.SubscriberStage) stage).getRsSubscriber();
                        BasicCompletionSubscriber cs = new BasicCompletionSubscriber(FlowAdapters.toFlowSubscriber(s));
                        completion = cs.completable();
                        if (result != null) {
                            result.subscribe(cs);
                        } else {
                            graphInlet = cs;
                        }
                        continue;
                    }
                    throw new IllegalArgumentException(
                            "Stage.FindFirst is only supported when building via buildSubscriber or buildCompletion");
                }
                if (stage instanceof Stage.Collect) {
                    if (mode == Mode.SUBSCRIBER || mode == Mode.COMPLETION) {
                        if (graphInlet != null) {
                            requireSource(result, stage);
                            requireNullTerminal(completion, stage);
                        }

                        Stage.Collect collect = (Stage.Collect) stage;
                        BasicCollectSubscriber cs = new BasicCollectSubscriber(collect.getCollector());
                        completion = cs.completable();
                        if (result != null) {
                            result.subscribe(cs);
                        } else {
                            graphInlet = cs;
                        }
                        continue;
                    }
                    throw new IllegalArgumentException(
                            "Stage.FindFirst is only supported when building via buildSubscriber or buildCompletion");
                }
                if (stage instanceof Stage.Cancel) {
                    if (mode == Mode.SUBSCRIBER || mode == Mode.COMPLETION) {
                        if (graphInlet != null) {
                            requireSource(result, stage);
                            requireNullTerminal(completion, stage);
                        }

                        BasicCancelSubscriber cs = new BasicCancelSubscriber();
                        completion = cs.completable();
                        if (result != null) {
                            result.subscribe(cs);
                        } else {
                            graphInlet = cs;
                        }

                        continue;
                    }
                    throw new IllegalArgumentException(
                            "Stage.FindFirst is only supported when building via buildSubscriber or buildCompletion");
                }

                throw new UnsupportedStageException(stage);
            }
        }

        if (mode == Mode.PUBLISHER) {
            if (result == null) {
                throw new IllegalArgumentException("The graph had no usable stages for building a Publisher.");
            }
            return FlowAdapters.toPublisher(result);
        }
        if (mode == Mode.PROCESSOR) {
            if (graphInlet == null || result == null) {
                throw new IllegalArgumentException("The graph had no usable stages for building a Processor.");
            }
            return FlowAdapters.toProcessor(new BridgeProcessor(graphInlet, result));
        }
        if (mode == Mode.COMPLETION) {
            if (completion == null) {
                throw new IllegalArgumentException("The graph had no usable stages for building a CompletionStage.");
            }
            return completion;
        }
        if (graphInlet == null || completion == null) {
            throw new IllegalArgumentException("The graph had no usable stages for building a Subscriber.");
        }
        return new InnerSubscriberWithCompletionStage(graphInlet, completion);
    }

    static final class InnerSubscriberWithCompletionStage<T, R>
            implements SubscriberWithCompletionStage<T, R> {

        private final CompletionStage<R> completion;

        private final Subscriber<T> front;

        InnerSubscriberWithCompletionStage(Flow.Subscriber<T> front, CompletionStage<R> completion) {
            this.front = FlowAdapters.toSubscriber(front);
            this.completion = completion;
        }

        @Override
        public CompletionStage<R> getCompletion() {
            return completion;
        }

        @Override
        public Subscriber<T> getSubscriber() {
            return front;
        }
    }
    static <T, R> Flow.Processor<T, R> coupledBuildProcessor(Flow.Subscriber<? super T> subscriber,
                                                        Flow.Publisher<? extends R> publisher) {

        BasicProcessor<T> inlet = new BasicProcessor<>();
        CompletableFuture<Object> publisherActivity = new CompletableFuture<>();
        CompletableFuture<Object> subscriberActivity = new CompletableFuture<>();

        inlet
                .onComplete(() -> complete(subscriberActivity))
                .onError(e -> fail(subscriberActivity, e))
                .compose(upstream -> new MultiCancelOnExecutor<>(upstream, coupledExecutor))
                .takeUntil(Multi.from(publisherActivity, true))
                .onCancel(() -> complete(subscriberActivity))
                .subscribe(subscriber);

        Multi<? extends R> outlet = Multi.from(publisher)
                .onComplete(() -> complete(publisherActivity))
                .onError(e -> fail(publisherActivity, e))
                .compose(upstream -> new MultiCancelOnExecutor<>(upstream, coupledExecutor))
                .takeUntil(Multi.from(subscriberActivity, true))
                .onCancel(() -> complete(publisherActivity));

        return new BridgeProcessor<>(inlet, outlet);
    }

    static void complete(CompletableFuture<Object> cf) {
        cf.complete(null);
    }

    static void fail(CompletableFuture<Object> cf, Throwable ex) {
        cf.completeExceptionally(ex);
    }

    // Workaround for a TCK bug when calling cancel() from any method named onComplete().
    private static volatile ExecutorService coupledExecutor = ForkJoinPool.commonPool();

    /**
     * Override the ExecutorService used by the cross-termination and cross-cancellation
     * of a Coupled stage.
     * @param executor the executor to use, null resets it to the default ForkJoinPool
     */
    public static void setCoupledExecutor(ExecutorService executor) {
        if (executor == null) {
            coupledExecutor = ForkJoinPool.commonPool();
        } else {
            coupledExecutor = executor;
        }
    }
}


