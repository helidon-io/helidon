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
    private SubscriberReference<? super X> subscriber;
    private Flow.Subscription subscription;
    private RequestedCounter requestCounter = new RequestedCounter();
    private Flow.Subscription innerSubscription;
    private Optional<Throwable> error = Optional.empty();
    private ReentrantLock publisherSeqLock = new ReentrantLock();
    private ReentrantLock innerPubSeqLock = new ReentrantLock();
    private volatile Flow.Publisher<X> innerPublisher;
    private AtomicBoolean upstreamsCompleted = new AtomicBoolean(false);


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
            requestCounter.increment(n, MultiFlatMapProcessor.this::onError);
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
            publisherSeqLock.lock();
            if (Objects.nonNull(this.subscription)) {
                subscription.cancel();
                return;
            }
            this.subscription = subscription;
            if (Objects.nonNull(subscriber)) {
                subscriber.onSubscribe(new FlatMapSubscription());
            }
        } finally {
            publisherSeqLock.unlock();
        }
    }

    @Override
    public void onNext(T o) {
        Objects.requireNonNull(o);
        try {
            // https://github.com/reactive-streams/reactive-streams-jvm#1.3
            publisherSeqLock.lock();
            innerPublisher = mapper.apply(o);
            innerPublisher.subscribe(new InnerSubscriber());
        } catch (Throwable t) {
            subscription.cancel();
            subscriber.onError(t);
        } finally {
            publisherSeqLock.unlock();
        }
    }

    @Override
    public void onError(Throwable t) {
        try {
            // https://github.com/reactive-streams/reactive-streams-jvm#1.3
            publisherSeqLock.lock();
            this.error = Optional.of(t);
            if (Objects.nonNull(subscriber)) {
                subscriber.onError(t);
            }

        } finally {
            publisherSeqLock.unlock();
        }
    }

    @Override
    public void onComplete() {
        try {
            // https://github.com/reactive-streams/reactive-streams-jvm#1.3
            publisherSeqLock.lock();
            upstreamsCompleted.set(true);
            if (requestCounter.get() == 0 || Objects.isNull(innerPublisher)) {
                //Have to wait for all Publishers to be finished
                subscriber.onComplete();
            }
        } finally {
            publisherSeqLock.unlock();
        }
    }

    private class InnerSubscriber implements Flow.Subscriber<X> {
        private AtomicBoolean alreadySubscribed = new AtomicBoolean(false);

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            try {
                innerPubSeqLock.lock();
                if (alreadySubscribed.getAndSet(true)) {
                    subscription.cancel();
                    return;
                }
                innerSubscription = subscription;
                innerSubscription.request(requestCounter.get());
            } finally {
                innerPubSeqLock.unlock();
            }
        }

        @Override
        public void onNext(X item) {
            try {
                innerPubSeqLock.lock();
                Objects.requireNonNull(item);
                if (requestCounter.tryDecrement()) {
                    subscriber.onNext(item);
                }
            } finally {
                innerPubSeqLock.unlock();
            }
        }

        @Override
        public void onError(Throwable throwable) {
            try {
                innerPubSeqLock.lock();
                Objects.requireNonNull(throwable);
                subscription.cancel();
                subscriber.onError(throwable);
            } finally {
                innerPubSeqLock.unlock();
            }
        }

        @Override
        public void onComplete() {
            try {
                innerPubSeqLock.lock();
                innerPublisher = null;
                // unblock onNext from upstream
                if (upstreamsCompleted.get()) {
                    subscriber.onComplete();
                }
                if (requestCounter.get() > 0) {
                    subscription.request(1);
                }
            } finally {
                innerPubSeqLock.unlock();
            }
        }
    }
}
