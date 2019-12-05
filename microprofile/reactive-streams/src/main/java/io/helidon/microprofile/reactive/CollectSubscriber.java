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

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import io.helidon.common.reactive.Flow;
import io.helidon.microprofile.reactive.hybrid.HybridProcessor;

import org.eclipse.microprofile.reactive.streams.operators.spi.Stage;
import org.eclipse.microprofile.reactive.streams.operators.spi.SubscriberWithCompletionStage;
import org.reactivestreams.Processor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Subscriber with preceding processors included,
 * automatically makes all downstream subscriptions when its subscribe method is called.
 *
 * @param <T> type of streamed item
 */
public class CollectSubscriber<T> implements SubscriberWithCompletionStage<T, Object> {

    private final Processor<Object, Object> connectingProcessor;
    private final BiConsumer accumulator;
    private final BinaryOperator combiner;
    private Object cumulatedVal;
    private final Function finisher;
    private Subscriber<Object> subscriber;
    private CompletableFuture<Object> completableFuture = new CompletableFuture<>();
    private Stage.Collect collectStage;
    private LinkedList<Processor<Object, Object>> processorList = new LinkedList<>();


    /**
     * Subscriber with preceding processors included,
     * automatically makes all downstream subscriptions when its subscribe method is called.
     *
     * @param collectStage           {@link org.eclipse.microprofile.reactive.streams.operators.spi.Stage.Collect}
     *                               for example {@link org.eclipse.microprofile.reactive.streams.operators.ProcessorBuilder#forEach(java.util.function.Consumer)}
     * @param precedingProcessorList ordered list of preceding processors(needed for automatic subscription in case of incomplete graph)
     */
    @SuppressWarnings("unchecked")
    CollectSubscriber(Stage.Collect collectStage,
                      List<Flow.Processor<Object, Object>> precedingProcessorList) {
        this.collectStage = collectStage;
        accumulator = (BiConsumer) collectStage.getCollector().accumulator();
        combiner = (BinaryOperator) collectStage.getCollector().combiner();
        finisher = (Function) collectStage.getCollector().finisher();
        //preceding processors
        precedingProcessorList.forEach(fp -> this.processorList.add(HybridProcessor.from(fp)));
        subscriber = (Subscriber<Object>) prepareSubscriber();
        connectingProcessor = prepareConnectingProcessor();
    }

    @Override
    public CompletionStage<Object> getCompletion() {
        return completableFuture;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Subscriber<T> getSubscriber() {
        return (Subscriber<T>) connectingProcessor;
    }


    private Subscriber<T> prepareSubscriber() {
        return new Subscriber<T>() {

            private Subscription subscription;
            private final AtomicBoolean closed = new AtomicBoolean(false);

            @Override
            public void onSubscribe(Subscription s) {
                Objects.requireNonNull(s);
                // https://github.com/reactive-streams/reactive-streams-jvm#2.5
                if (Objects.nonNull(this.subscription)) {
                    s.cancel();
                }
                try {
                    cumulatedVal = collectStage.getCollector().supplier().get();
                } catch (Throwable t) {
                    onError(t);
                    s.cancel();
                }
                this.subscription = s;
                subscription.request(1);
            }

            @Override
            @SuppressWarnings("unchecked")
            public void onNext(Object item) {
                if (!closed.get()) {
                    try {
                        accumulator.accept(cumulatedVal, item);
                        subscription.request(1);
                    } catch (Throwable t) {
                        onError(t);
                        subscription.cancel();
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                completableFuture.completeExceptionally(t);
            }

            @Override
            @SuppressWarnings("unchecked")
            public void onComplete() {
                closed.set(true);
                try {
                    completableFuture.complete(finisher.apply(cumulatedVal));
                } catch (Throwable t) {
                    onError(t);
                }
                if (Objects.nonNull(subscription)) {
                    subscription.cancel();
                }
            }
        };
    }

    /**
     * Artificial processor, in case of incomplete graph does subscriptions downstream automatically.
     *
     * @return Artificial {@link org.reactivestreams.Processor}
     */
    private Processor<Object, Object> prepareConnectingProcessor() {
        return new Processor<Object, Object>() {
            @Override
            public void subscribe(Subscriber<? super Object> s) {
                processorList.getFirst().subscribe(s);
            }

            @Override
            public void onSubscribe(Subscription s) {
                // This is a time for connecting all pre-processors and subscriber
                Processor<Object, Object> lastProcessor = null;
                for (Processor<Object, Object> processor : processorList) {
                    if (lastProcessor != null) {
                        lastProcessor.subscribe(processor);
                    }
                    lastProcessor = processor;
                }
                if (!processorList.isEmpty()) {
                    processorList.getLast().subscribe(subscriber);
                    // First preprocessor act as subscriber
                    subscriber = processorList.getFirst();
                }
                //No processors just forward to subscriber
                subscriber.onSubscribe(s);
            }

            @Override
            public void onNext(Object o) {
                subscriber.onNext(o);
            }

            @Override
            public void onError(Throwable t) {
                subscriber.onError(t);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        };
    }
}
