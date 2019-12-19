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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import io.helidon.common.reactive.RequestedCounter;
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
    private HybridSubscriber<? super Object> subscriber;
    private Subscription subscription;
    private RequestedCounter requestCounter = new RequestedCounter();
    private Subscription innerSubscription;
    private AtomicBoolean onCompleteReceivedAlready = new AtomicBoolean(false);

    private PublisherBuffer buffer;

    private Optional<Throwable> error = Optional.empty();

    private FlatMapProcessor() {
        buffer = new PublisherBuffer();
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

    private class FlatMapSubscription implements Subscription {
        @Override
        public void request(long n) {
            requestCounter.increment(n, FlatMapProcessor.this::onError);

            if (buffer.isComplete() || Objects.isNull(innerSubscription)) {
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
            return;
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
        buffer.offer(o);
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
        onCompleteReceivedAlready.set(true);
        if (buffer.isComplete()) {
            //Have to wait for all Publishers to be finished
            subscriber.onComplete();
        }
    }

    private class PublisherBuffer {
        private BlockingQueue<Object> buffer = new ArrayBlockingQueue<>(64);
        private InnerSubscriber lastSubscriber = null;

        public boolean isComplete() {
            return Objects.isNull(lastSubscriber) || (lastSubscriber.isDone() && buffer.isEmpty());
        }

        public void tryNext() {
            Object nextItem = buffer.poll();
            if (Objects.nonNull(nextItem)) {
                lastSubscriber = executeMapper(nextItem);
            } else if (onCompleteReceivedAlready.get()) {
                // Received onComplete and all Publishers are done
                subscriber.onComplete();
            }
        }

        public void offer(Object o) {
            if (buffer.isEmpty() && (Objects.isNull(lastSubscriber) || lastSubscriber.isDone())) {
                lastSubscriber = executeMapper(o);
            } else {
                buffer.offer(o);
            }
        }

        public InnerSubscriber executeMapper(Object item) {
            InnerSubscriber innerSubscriber = null;
            try {
                innerSubscriber = new InnerSubscriber();
                innerSubscriber.whenComplete(this::tryNext);
                mapper.apply(item).subscribe(innerSubscriber);
            } catch (Throwable t) {
                subscription.cancel();
                subscriber.onError(t);
            }
            return innerSubscriber;
        }
    }

    private class InnerSubscriber implements Subscriber<Object> {

        private AtomicBoolean subscriptionAcked = new AtomicBoolean(false);
        private AtomicBoolean done = new AtomicBoolean(false);

        private Optional<Runnable> whenCompleteObserver = Optional.empty();

        @Override
        public void onSubscribe(Subscription innerSubscription) {
            Objects.requireNonNull(innerSubscription);
            if (subscriptionAcked.get()) {
                innerSubscription.cancel();
                return;
            }
            subscriptionAcked.set(true);
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
            requestCounter.tryDecrement();
            long requestCount = requestCounter.get();
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
            done.set(true);
            whenCompleteObserver.ifPresent(Runnable::run);
            long requestCount = requestCounter.get();
            if (requestCount > 0) {
                subscription.request(requestCount);
            }
        }

        private void whenComplete(Runnable whenCompleteObserver) {
            this.whenCompleteObserver = Optional.of(whenCompleteObserver);
        }

        private boolean isDone() {
            return done.get();
        }
    }
}
