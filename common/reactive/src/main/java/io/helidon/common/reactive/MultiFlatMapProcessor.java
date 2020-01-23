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

package io.helidon.common.reactive;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Flatten the elements emitted by publishers produced by the mapper function to this stream.
 *
 * @param <T> input item type
 * @param <X> output item type
 */
public class MultiFlatMapProcessor<T, X> implements Flow.Processor<T, X>, Multi<X> {

    private Function<T, Flow.Publisher<X>> mapper;
    private SubscriberReference<? super X> subscriber;
    private Flow.Subscription subscription;
    private RequestedCounter requestCounter = new RequestedCounter();
    private Flow.Subscription innerSubscription;
    private volatile boolean onCompleteReceivedAlready = false;
    private PublisherBuffer<T> buffer;
    private Optional<Throwable> error = Optional.empty();
    private ReentrantLock publisherSequentialLock = new ReentrantLock();


    private MultiFlatMapProcessor() {
        buffer = new PublisherBuffer<>();
    }

    /**
     * Create new {@link MultiFlatMapProcessor} with item to {@link java.lang.Iterable} mapper.
     *
     * @param mapper to provide iterable for every item from upstream
     * @param <T>    input item type
     * @param <R>    output item type
     * @return {@link MultiFlatMapProcessor}
     */
    @SuppressWarnings("unchecked")
    public static <T, R> MultiFlatMapProcessor<T, R> fromIterableMapper(Function<T, Iterable<R>> mapper) {
        MultiFlatMapProcessor<T, R> flatMapProcessor = new MultiFlatMapProcessor<>();
        flatMapProcessor.mapper = o -> (Multi<R>) Multi.from(mapper.apply(o));
        return flatMapProcessor;
    }

    /**
     * Create new {@link MultiFlatMapProcessor} with item to {@link java.util.concurrent.Flow.Publisher} mapper.
     *
     * @param mapper to provide iterable for every item from upstream
     * @param <T>    input item type
     * @param <U>    output item type
     * @return {@link MultiFlatMapProcessor}
     */
    @SuppressWarnings("unchecked")
    public static <T, U> MultiFlatMapProcessor<T, U> fromPublisherMapper(Function<T, Flow.Publisher<U>> mapper) {
        MultiFlatMapProcessor<T, U> flatMapProcessor = new MultiFlatMapProcessor<>();
        flatMapProcessor.mapper = t -> (Flow.Publisher<U>) mapper.apply(t);
        return flatMapProcessor;
    }

    private class FlatMapSubscription implements Flow.Subscription {
        @Override
        public void request(long n) {
            if (buffer.isComplete() || Objects.isNull(innerSubscription)) {
                subscription.request(n);
            } else {
                requestCounter.increment(n, MultiFlatMapProcessor.this::onError);
                innerSubscription.request(n);
            }
        }

        @Override
        public void cancel() {
            subscription.cancel();
            Optional.ofNullable(innerSubscription).ifPresent(Flow.Subscription::cancel);
            // https://github.com/reactive-streams/reactive-streams-jvm#3.13
            subscriber.releaseReference();
        }
    }

    @Override
    public void subscribe(Flow.Subscriber<? super X> subscriber) {
        this.subscriber = SubscriberReference.create(subscriber);
        if (Objects.nonNull(this.subscription)) {
            subscriber.onSubscribe(new FlatMapSubscription());
        }
        error.ifPresent(subscriber::onError);
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        try {
            // https://github.com/reactive-streams/reactive-streams-jvm#1.3
            publisherSequentialLock.lock();
            if (Objects.nonNull(this.subscription)) {
                subscription.cancel();
                return;
            }
            this.subscription = subscription;
            if (Objects.nonNull(subscriber)) {
                subscriber.onSubscribe(new FlatMapSubscription());
            }
        } finally {
            publisherSequentialLock.unlock();
        }
    }

    @Override
    public void onNext(T o) {
        Objects.requireNonNull(o);
        try {
            // https://github.com/reactive-streams/reactive-streams-jvm#1.3
            publisherSequentialLock.lock();
            buffer.offer(o);
        } catch (Throwable t) {
            onError(t);
        } finally {
            publisherSequentialLock.unlock();
        }
    }

    @Override
    public void onError(Throwable t) {
        try {
            // https://github.com/reactive-streams/reactive-streams-jvm#1.3
            publisherSequentialLock.lock();
            this.error = Optional.of(t);
            if (Objects.nonNull(subscriber)) {
                subscriber.onError(t);
            }

        } finally {
            publisherSequentialLock.unlock();
        }
    }

    @Override
    public void onComplete() {
        try {
            // https://github.com/reactive-streams/reactive-streams-jvm#1.3
            publisherSequentialLock.lock();
            onCompleteReceivedAlready = true;
            if (buffer.isComplete()) {
                //Have to wait for all Publishers to be finished
                subscriber.onComplete();
            }
        } finally {
            publisherSequentialLock.unlock();
        }
    }

    private class PublisherBuffer<U> {

        private int bufferSize = Integer.parseInt(
                System.getProperty("helidon.common.reactive.flatMap.buffer.size", String.valueOf(Flow.defaultBufferSize())));
        private BlockingQueue<U> buffer = new ArrayBlockingQueue<>(bufferSize);
        private InnerSubscriber lastSubscriber = null;

        public boolean isComplete() {
            return Objects.isNull(lastSubscriber) || (lastSubscriber.isDone() && buffer.isEmpty());
        }

        public void tryNext() {
            U nextItem = buffer.poll();
            if (Objects.nonNull(nextItem)) {
                lastSubscriber = executeMapper(nextItem);
            } else if (onCompleteReceivedAlready) {
                // Received onComplete and all Publishers are done
                subscriber.onComplete();
            }
        }

        public void offer(U o) {
            if (buffer.isEmpty() && (Objects.isNull(lastSubscriber) || lastSubscriber.isDone())) {
                lastSubscriber = executeMapper(o);
            } else {
                buffer.add(o);
            }
        }

        @SuppressWarnings("unchecked")
        public InnerSubscriber executeMapper(U item) {
            InnerSubscriber innerSubscriber = null;
            try {
                innerSubscriber = new InnerSubscriber();
                innerSubscriber.whenComplete(this::tryNext);
                mapper.apply((T) item).subscribe(innerSubscriber);
            } catch (Throwable t) {
                subscription.cancel();
                subscriber.onError(t);
            }
            return innerSubscriber;
        }
    }

    private class InnerSubscriber implements Flow.Subscriber<X> {

        private volatile boolean subscriptionAcked = false;
        private volatile boolean done = false;
        private ReentrantLock innerSubscriberSequentialLock = new ReentrantLock();
        private Optional<Runnable> whenCompleteObserver = Optional.empty();

        @Override
        public void onSubscribe(Flow.Subscription innerSubscription) {
            try {
                // https://github.com/reactive-streams/reactive-streams-jvm#1.3
                innerSubscriberSequentialLock.lock();
                Objects.requireNonNull(innerSubscription);
                if (subscriptionAcked) {
                    innerSubscription.cancel();
                    return;
                }
                subscriptionAcked = true;
                MultiFlatMapProcessor.this.innerSubscription = innerSubscription;
                innerSubscription.request(Long.MAX_VALUE);
            } finally {
                innerSubscriberSequentialLock.unlock();
            }
        }

        @Override
        public void onNext(X o) {
            try {
                // https://github.com/reactive-streams/reactive-streams-jvm#1.3
                innerSubscriberSequentialLock.lock();

                Objects.requireNonNull(o);
                MultiFlatMapProcessor.this.subscriber.onNext(o);
                //just counting leftovers
                requestCounter.tryDecrement();
            } finally {
                innerSubscriberSequentialLock.unlock();
            }
        }

        @Override
        public void onError(Throwable t) {
            try {
                // https://github.com/reactive-streams/reactive-streams-jvm#1.3
                innerSubscriberSequentialLock.lock();

                Objects.requireNonNull(t);
                MultiFlatMapProcessor.this.subscription.cancel();
                MultiFlatMapProcessor.this.onError(t);
            } finally {
                innerSubscriberSequentialLock.unlock();
            }
        }

        @Override
        public void onComplete() {
            try {
                // https://github.com/reactive-streams/reactive-streams-jvm#1.3
                innerSubscriberSequentialLock.lock();

                done = true;
                whenCompleteObserver.ifPresent(Runnable::run);
                long requestCount = requestCounter.get();
                if (requestCount > 0) {
                    subscription.request(requestCount);
                }
            } finally {
                innerSubscriberSequentialLock.unlock();
            }
        }

        private void whenComplete(Runnable whenCompleteObserver) {
            this.whenCompleteObserver = Optional.of(whenCompleteObserver);
        }

        private boolean isDone() {
            return done;
        }
    }
}
