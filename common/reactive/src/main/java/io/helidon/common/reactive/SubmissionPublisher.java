/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
 */

package io.helidon.common.reactive;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.UnicastProcessor;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * A {@link Flow.Publisher} that asynchronously issues submitted (non-null) items to current subscribers until it is closed.
 *
 * @param <T> the published item type
 * @deprecated This class will be removed in the next major release.
 */
@Deprecated
public class SubmissionPublisher<T> implements Flow.Publisher<T>, AutoCloseable {

    static {
        // prevent reactor from using "console" logging
        System.setProperty("reactor.logging.fallback", "JDK");
    }

    private final Flux<T> flux;
    private final FluxSink<T> sink;
    private final AtomicInteger numberOfSubscribers;

    /**
     * Creates a new SubmissionPublisher using the given Executor for
     * async delivery to subscribers, with the given maximum buffer size
     * for each subscriber.
     *
     * @param executor the executor to use for async delivery,
     * supporting creation of at least one independent thread
     * @param maxBufferCapacity the maximum capacity for each
     * subscriber's buffer
     * @throws IllegalArgumentException if maxBufferCapacity not
     * positive
     */
    public SubmissionPublisher(Executor executor, int maxBufferCapacity){
        this(Schedulers.fromExecutor(executor), maxBufferCapacity);
    }

    /**
     * Creates a new SubmissionPublisher using the current thread for delivery
     * to subscribers, with the given maximum buffer size for each subscriber.
     * @param maxBufferCapacity the maximum capacity for each
     * subscriber's buffer
     * @throws IllegalArgumentException if maxBufferCapacity not
     */
    public SubmissionPublisher(int maxBufferCapacity){
        this(Schedulers.immediate(), maxBufferCapacity);
    }

    /**
     * Creates a new SubmissionPublisher using the current thread for delivery
     * to subscribers, with maximum buffer capacity of
     *  {@link Flow#defaultBufferSize}.
     */
    public SubmissionPublisher(){
        this(Schedulers.immediate(), Flow.defaultBufferSize());
    }

    private SubmissionPublisher(Scheduler scheduler, int maxBufferCapacity) {
        if (scheduler == null){
            throw new NullPointerException();
        }
        if (maxBufferCapacity <= 0){
            throw new IllegalArgumentException("capacity must be positive");
        }
        UnicastProcessor<T> processor = UnicastProcessor.<T>create();
        sink = processor.sink();
        flux = processor
                .publish(maxBufferCapacity)
                .autoConnect()
                .subscribeOn(Schedulers.immediate())
                .publishOn(scheduler);
        numberOfSubscribers = new AtomicInteger(0);
    }

    /**
     * Adds the given Subscriber.
     *
     * @param subscriber the subscriber
     * @throws NullPointerException if subscriber is null
     */
    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        if (subscriber == null) throw new NullPointerException();
        flux.subscribe(new OnCancelSubscriber<>(subscriber, this::onCancel));
        numberOfSubscribers.incrementAndGet();
    }

    private void onCancel(Subscription subscription){
        numberOfSubscribers.decrementAndGet();
    }

    /**
     * Publishes the given item to each current subscriber.
     *
     * @param item the (non-null) item to publish
     * @throws NullPointerException if item is null
     */
    public void submit(T item) {
        if (item == null) throw new NullPointerException();
        sink.next(item);
    }

    /**
     * Publishes the given item to each current subscriber.
     *
     * @param item the (non-null) item to publish
     * @param onDrop not supported in the current implementation
     * @throws NullPointerException if item is null
     */
    public void offer(T item, BiPredicate<Flow.Subscriber<? super T>, ? super T> onDrop) {
        submit(item);
    }

    /**
     * Unless already closed, issues {@link
     * Flow.Subscriber#onError(Throwable) onError} signals to current
     * subscribers with the given error, and disallows subsequent
     * attempts to publish.  Future subscribers also receive the given
     * error. Upon return, this method does <em>NOT</em> guarantee
     * that all subscribers have yet completed.
     *
     * @param error the {@code onError} argument sent to subscribers
     * @throws NullPointerException if error is null
     */
    public void closeExceptionally(Throwable error) {
        if (error == null){
            throw new NullPointerException();
        }
        sink.error(error);
    }

    @Override
    public void close() {
        sink.complete();
    }

    /**
     * Returns the number of current subscribers.
     *
     * @return the number of current subscribers
     */
    public int getNumberOfSubscribers() {
        return numberOfSubscribers.get();
    }

    /**
     * Returns true if this publisher has any subscribers.
     *
     * @return true if this publisher has any subscribers
     */
    public boolean hasSubscribers() {
        return getNumberOfSubscribers() > 0;
    }

    private static class OnCancelSubscriber<T> implements Subscriber<T> {

        private final Subscriber<T> delegate;
        private final Consumer<Subscription> onCancel;

        OnCancelSubscriber(Flow.Subscriber<T> subscriber, Consumer<Subscription> onCancel) {
            this.delegate = ReactiveStreamsAdapter.subscriberFromFlow(subscriber);
            this.onCancel = onCancel;
        }

        @Override
        public void onSubscribe(final Subscription s) {
            delegate.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    s.request(n);
                }

                @Override
                public void cancel() {
                    s.cancel();
                    onCancel.accept(s);
                }
            });
        }

        @Override
        public void onNext(T t) {
            delegate.onNext(t);
        }

        @Override
        public void onError(Throwable t) {
            delegate.onError(t);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }
    }
}
