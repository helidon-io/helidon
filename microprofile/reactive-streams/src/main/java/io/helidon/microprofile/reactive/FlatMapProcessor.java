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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import io.helidon.microprofile.reactive.hybrid.HybridSubscriber;

import org.eclipse.microprofile.reactive.streams.operators.ReactiveStreams;
import org.eclipse.microprofile.reactive.streams.operators.spi.Graph;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Flatten the elements emitted by publishers produced by the mapper function to this stream.
 */
public class FlatMapProcessor implements Processor<Object, Object> {

    private Function<Object, Publisher> mapper;

    private final AtomicBoolean innerPublisherCompleted = new AtomicBoolean(true);
    private HybridSubscriber<? super Object> subscriber;
    private Subscription subscription;
    private final AtomicLong requestCounter = new AtomicLong();
    private Subscription innerSubscription;

    private Optional<Throwable> error = Optional.empty();

    private FlatMapProcessor() {
    }

    @SuppressWarnings("unchecked")
    static FlatMapProcessor fromIterableMapper(Function<?, Iterable<?>> mapper) {
        Function<Object, Iterable<?>> iterableMapper = (Function<Object, Iterable<?>>) mapper;
        FlatMapProcessor flatMapProcessor = new FlatMapProcessor();
        flatMapProcessor.mapper = o -> ReactiveStreams.fromIterable(iterableMapper.apply(o)).buildRs();
        return flatMapProcessor;
    }

    @SuppressWarnings("unchecked")
    static FlatMapProcessor fromPublisherMapper(Function<?, Graph> mapper) {
        Function<Object, Graph> publisherMapper = (Function<Object, Graph>) mapper;
        FlatMapProcessor flatMapProcessor = new FlatMapProcessor();
        flatMapProcessor.mapper = o -> new HelidonReactiveStreamEngine().buildPublisher(publisherMapper.apply(o));
        return flatMapProcessor;
    }

    @SuppressWarnings("unchecked")
    static FlatMapProcessor fromCompletionStage(Function<?, CompletionStage<?>> mapper) {
        Function<Object, CompletionStage<?>> csMapper = (Function<Object, CompletionStage<?>>) mapper;
        FlatMapProcessor flatMapProcessor = new FlatMapProcessor();
        flatMapProcessor.mapper = o -> (Publisher<Object>) s -> {
            AtomicBoolean requested = new AtomicBoolean(false);
            s.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    //Only one request supported
                    if (!requested.getAndSet(true)) {
                        csMapper.apply(o).whenComplete((payload, throwable) -> {
                            if (Objects.nonNull(throwable)) {
                                s.onError(throwable);
                            } else {
                                if (Objects.isNull(payload)) {
                                    s.onError(new NullPointerException());
                                } else {
                                    s.onNext(payload);
                                    s.onComplete();
                                }
                            }
                        });
                    }
                }

                @Override
                public void cancel() {
                }
            });
        };
        return flatMapProcessor;
    }

    private class FlatMapSubscription implements Subscription {
        @Override
        public void request(long n) {
            requestCounter.addAndGet(n);
            if (innerPublisherCompleted.getAndSet(false) || Objects.isNull(innerSubscription)) {
                subscription.request(requestCounter.get());
            } else {
                innerSubscription.request(requestCounter.get());
            }
        }

        @Override
        public void cancel() {
            subscription.cancel();
            Optional.ofNullable(innerSubscription).ifPresent(Subscription::cancel);
            // https://github.com/reactive-streams/reactive-streams-jvm#3.13
            subscriber.releaseReferences();
        }
    }

    @Override
    public void subscribe(Subscriber<? super Object> subscriber) {
        this.subscriber = HybridSubscriber.from(subscriber);
        if (Objects.nonNull(this.subscription)) {
            subscriber.onSubscribe(new FlatMapSubscription());
        }
        error.ifPresent(subscriber::onError);
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        if (Objects.nonNull(this.subscription)) {
            subscription.cancel();
        }
        this.subscription = subscription;
        if (Objects.nonNull(subscriber)) {
            subscriber.onSubscribe(new FlatMapSubscription());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onNext(Object o) {
        Objects.requireNonNull(o);
        try {
            Publisher<Object> publisher = mapper.apply(o);
            publisher.subscribe(new InnerSubscriber());
        } catch (Throwable e) {
            this.subscription.cancel();
            this.onError(e);
        }
    }

    @Override
    public void onError(Throwable t) {
        this.error = Optional.of(t);
        if (Objects.nonNull(subscriber)) {
            subscriber.onError(t);
        }
    }

    @Override
    public void onComplete() {
        subscriber.onComplete();
    }

    private class InnerSubscriber implements Subscriber<Object> {

        @Override
        public void onSubscribe(Subscription innerSubscription) {
            Objects.requireNonNull(innerSubscription);
            innerPublisherCompleted.set(false);
            FlatMapProcessor.this.innerSubscription = innerSubscription;
            long requestCount = requestCounter.get();
            if (requestCount > 0) {
                innerSubscription.request(requestCount);
            }
        }

        @Override
        public void onNext(Object o) {
            Objects.requireNonNull(o);
            FlatMapProcessor.this.subscriber.onNext(o);
            long requestCount = requestCounter.decrementAndGet();
            if (requestCount > 0) {
                innerSubscription.request(requestCount);
            }
        }

        @Override
        public void onError(Throwable t) {
            Objects.requireNonNull(t);
            FlatMapProcessor.this.subscription.cancel();
            FlatMapProcessor.this.onError(t);
        }

        @Override
        public void onComplete() {
            innerPublisherCompleted.set(true);
            long requestCount = requestCounter.get();
            if (requestCount > 0) {
                subscription.request(requestCount);
            }
        }
    }
}
