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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Flatten the elements emitted by publishers produced by the mapper function to this stream.
 *
 * @param <T> item type
 */
public class MultiFlatMapProcessor<T> implements Flow.Processor<T, T>, Multi<T> {

    private static final int DEFAULT_BUFFER_SIZE = 64;

    private Function<T, Flow.Publisher<T>> mapper;
    private SubscriberReference<? super T> subscriber;
    private Flow.Subscription subscription;
    private RequestedCounter requestCounter = new RequestedCounter();
    private Flow.Subscription innerSubscription;
    private AtomicBoolean onCompleteReceivedAlready = new AtomicBoolean(false);
    private PublisherBuffer<T> buffer;
    private Optional<Throwable> error = Optional.empty();

    private MultiFlatMapProcessor() {
        buffer = new PublisherBuffer<>();
    }

    /**
     * Create new {@link MultiFlatMapProcessor} with item to {@link java.lang.Iterable} mapper.
     *
     * @param mapper to provide iterable for every item from upstream
     * @param <T>    item type
     * @return {@link MultiFlatMapProcessor}
     */
    @SuppressWarnings("unchecked")
    public static <T> MultiFlatMapProcessor<T> fromIterableMapper(Function<T, Iterable<T>> mapper) {
        MultiFlatMapProcessor<T> flatMapProcessor = new MultiFlatMapProcessor<>();
        flatMapProcessor.mapper = o -> (Multi<T>) Multi.from(mapper.apply(o));
        return flatMapProcessor;
    }

    /**
     * Create new {@link MultiFlatMapProcessor} with item to {@link java.util.concurrent.Flow.Publisher} mapper.
     *
     * @param mapper to provide iterable for every item from upstream
     * @param <T>    item type
     * @return {@link MultiFlatMapProcessor}
     */
    @SuppressWarnings("unchecked")
    public static <T> MultiFlatMapProcessor<T> fromPublisherMapper(Function<?, Flow.Publisher<T>> mapper) {
        Function<T, Flow.Publisher<T>> publisherMapper = (Function<T, Flow.Publisher<T>>) mapper;
        MultiFlatMapProcessor<T> flatMapProcessor = new MultiFlatMapProcessor<T>();
        flatMapProcessor.mapper = t -> (Flow.Publisher<T>) publisherMapper.apply(t);
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
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        this.subscriber = SubscriberReference.create(subscriber);
        if (Objects.nonNull(this.subscription)) {
            subscriber.onSubscribe(new FlatMapSubscription());
        }
        error.ifPresent(subscriber::onError);
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
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
    public void onNext(T o) {
        Objects.requireNonNull(o);
        try {
            buffer.offer(o);
        } catch (Throwable t) {
            onError(t);
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
        onCompleteReceivedAlready.set(true);
        if (buffer.isComplete()) {
            //Have to wait for all Publishers to be finished
            subscriber.onComplete();
        }
    }

    private class PublisherBuffer<U> {

        private int bufferSize = Integer.parseInt(
                System.getProperty("helidon.common.reactive.flatMap.buffer.size", String.valueOf(DEFAULT_BUFFER_SIZE)));
        private BlockingQueue<U> buffer = new ArrayBlockingQueue<>(bufferSize);
        private InnerSubscriber<? super T> lastSubscriber = null;

        public boolean isComplete() {
            return Objects.isNull(lastSubscriber) || (lastSubscriber.isDone() && buffer.isEmpty());
        }

        public void tryNext() {
            U nextItem = buffer.poll();
            if (Objects.nonNull(nextItem)) {
                lastSubscriber = executeMapper(nextItem);
            } else if (onCompleteReceivedAlready.get()) {
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
        public InnerSubscriber<? super T> executeMapper(U item) {
            InnerSubscriber<? super T> innerSubscriber = null;
            try {
                innerSubscriber = new InnerSubscriber<>();
                innerSubscriber.whenComplete(this::tryNext);
                mapper.apply((T) item).subscribe(innerSubscriber);
            } catch (Throwable t) {
                subscription.cancel();
                subscriber.onError(t);
            }
            return innerSubscriber;
        }
    }

    private class InnerSubscriber<R> implements Flow.Subscriber<R> {

        private AtomicBoolean subscriptionAcked = new AtomicBoolean(false);
        private AtomicBoolean done = new AtomicBoolean(false);

        private Optional<Runnable> whenCompleteObserver = Optional.empty();

        @Override
        public void onSubscribe(Flow.Subscription innerSubscription) {
            Objects.requireNonNull(innerSubscription);
            if (subscriptionAcked.get()) {
                innerSubscription.cancel();
                return;
            }
            subscriptionAcked.set(true);
            MultiFlatMapProcessor.this.innerSubscription = innerSubscription;
            innerSubscription.request(Long.MAX_VALUE);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onNext(R o) {
            Objects.requireNonNull(o);
            MultiFlatMapProcessor.this.subscriber.onNext((T) o);
            //just counting leftovers
            requestCounter.tryDecrement();
        }

        @Override
        public void onError(Throwable t) {
            Objects.requireNonNull(t);
            MultiFlatMapProcessor.this.subscription.cancel();
            MultiFlatMapProcessor.this.onError(t);
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
