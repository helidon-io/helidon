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
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private RequestedCounter requestCounter = new RequestedCounter();
    private SubscriberReference<? super X> subscriber;
    private Flow.Subscription subscription;
    private volatile Flow.Subscription innerSubscription;
    private volatile Flow.Publisher<X> innerPublisher;
    private volatile boolean upstreamsCompleted;
    private Optional<Throwable> error = Optional.empty();
    private ReentrantLock seqLock = new ReentrantLock();
    private ReentrantLock stateLock = new ReentrantLock();


    private MultiFlatMapProcessor() {
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
            stateLock(() -> requestCounter.increment(n, MultiFlatMapProcessor.this::onError));
            if (Objects.nonNull(innerSubscription)) {
                innerSubscription.request(n);
            } else {
                subscription.request(1);
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
        stateLock(() -> {
            this.subscriber = SubscriberReference.create(subscriber);
            if (Objects.nonNull(this.subscription)) {
                seqLock(() -> subscriber.onSubscribe(new FlatMapSubscription()));
            }
            error.ifPresent(subscriber::onError);
        });
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        stateLock(() -> {
            if (Objects.nonNull(this.subscription)) {
                subscription.cancel();
                return;
            }
            this.subscription = subscription;
            if (Objects.nonNull(subscriber)) {
                subscriber.onSubscribe(new FlatMapSubscription());
            }
        });
    }

    @Override
    public void onNext(T o) {
        Objects.requireNonNull(o);
        try {
            innerPublisher = mapper.apply(o);
            innerPublisher.subscribe(new InnerSubscriber());
        } catch (Throwable t) {
            seqLock(() -> {
                subscription.cancel();
                subscriber.onError(t);
            });
        }
    }

    @Override
    public void onError(Throwable t) {
        this.error = Optional.of(t);
        if (Objects.nonNull(subscriber)) {
            seqLock(() -> subscriber.onError(t));
        }
    }

    @Override
    public void onComplete() {
        stateLock(() -> {
            upstreamsCompleted = true;
            if (requestCounter.get() == 0 || Objects.isNull(innerPublisher)) {
                //Have to wait for all Publishers to be finished
                subscriber.onComplete();
            }
        });
    }

    private class InnerSubscriber implements Flow.Subscriber<X> {
        private AtomicBoolean alreadySubscribed = new AtomicBoolean(false);

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            seqLock(() -> {
                if (alreadySubscribed.getAndSet(true)) {
                    subscription.cancel();
                    return;
                }
                innerSubscription = subscription;
                innerSubscription.request(requestCounter.get());
            });
        }

        @Override
        public void onNext(X item) {
            Objects.requireNonNull(item);
            stateLock(() -> {
                if (requestCounter.tryDecrement()) {
                    seqLock(() -> subscriber.onNext(item));
                }
            });
        }

        @Override
        public void onError(Throwable throwable) {
            Objects.requireNonNull(throwable);
            seqLock(() -> {
                subscription.cancel();
                subscriber.onError(throwable);
            });
        }

        @Override
        public void onComplete() {
            stateLock(() -> {
                innerPublisher = null;
                if (upstreamsCompleted) {
                    seqLock(() -> subscriber.onComplete());
                }
                if (requestCounter.get() > 0) {
                    subscription.request(1);
                }
            });
        }
    }

    /**
     * OnSubscribe, onNext, onError and onComplete signaled to a Subscriber MUST be signaled serially.
     * https://github.com/reactive-streams/reactive-streams-jvm#1.3
     */
    private void seqLock(Runnable runnable) {
        try {
            seqLock.lock();
            runnable.run();
        } finally {
            seqLock.unlock();
        }
    }

    /**
     * Protect critical sections when working with states of innerPublisher.
     */
    private void stateLock(Runnable runnable) {
        try {
            stateLock.lock();
            runnable.run();
        } finally {
            stateLock.unlock();
        }
    }
}
