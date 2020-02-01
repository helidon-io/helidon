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
public class MultiFlatMapProcessor<T, X> implements Flow.Processor<T, X>, Multi<X>, StrictProcessor {

    private Function<T, Flow.Publisher<X>> mapper;
    private RequestedCounter requestCounter = new RequestedCounter();
    private Flow.Subscriber<? super X> subscriber;
    private Flow.Subscription subscription;
    private volatile Flow.Subscription innerSubscription;
    private volatile Flow.Publisher<X> innerPublisher;
    private AtomicBoolean upstreamsCompleted = new AtomicBoolean(false);
    private Optional<Throwable> error = Optional.empty();
    private ReentrantLock stateLock = new ReentrantLock();
    private boolean strictMode = BaseProcessor.DEFAULT_STRICT_MODE;


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

    @Override
    public MultiFlatMapProcessor<T, X> strictMode(boolean strictMode) {
        this.strictMode = strictMode;
        return this;
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
        }
    }

    @Override
    public void subscribe(Flow.Subscriber<? super X> subscriber) {
        this.subscriber = SequentialSubscriber.create(subscriber);
        stateLock(() -> {
            if (Objects.nonNull(this.subscription)) {
                this.subscriber.onSubscribe(new FlatMapSubscription());
            }
            error.ifPresent(this.subscriber::onError);
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
            subscription.cancel();
            subscriber.onError(t);
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
        upstreamsCompleted.set(true);
        stateLock(() -> {
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
            if (alreadySubscribed.getAndSet(true)) {
                subscription.cancel();
                return;
            }
            innerSubscription = subscription;
            innerSubscription.request(requestCounter.get());
        }

        @Override
        public void onNext(X item) {
            Objects.requireNonNull(item);
            if (requestCounter.tryDecrement()) {
                subscriber.onNext(item);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            Objects.requireNonNull(throwable);
            subscription.cancel();
            subscriber.onError(throwable);
        }

        @Override
        public void onComplete() {
            if (upstreamsCompleted.get()) {
                subscriber.onComplete();
            }
            stateLock(() -> {
                innerPublisher = null;
            });
            try {
                requestCounter.lock();
                if (requestCounter.get() > 0) {
                    subscription.request(1);
                }
            } finally {
                requestCounter.unlock();
            }
        }
    }

    /**
     * Protect critical sections when working with states of innerPublisher.
     */
    private void stateLock(Runnable runnable) {
        if (!strictMode) {
            runnable.run();
            return;
        }
        try {
            stateLock.lock();
            runnable.run();
        } finally {
            stateLock.unlock();
        }
    }
}
