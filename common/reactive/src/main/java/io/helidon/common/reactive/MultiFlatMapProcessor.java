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

    private boolean strictMode = true;
    private Function<T, Flow.Publisher<X>> mapper;
    private RequestedCounter requestCounter = new RequestedCounter(strictMode);
    private RequestedCounter requestCounterUpstream = new RequestedCounter(strictMode);
    private Flow.Subscriber<? super X> subscriber;
    private Flow.Subscription subscription;
    private volatile Flow.Subscription innerSubscription;
    private volatile Flow.Publisher<X> innerPublisher;
    private AtomicBoolean upstreamCompleted = new AtomicBoolean(false);
    private Optional<Throwable> error = Optional.empty();
    private ReentrantLock subscriptionLock = new ReentrantLock();


    /**
     * Create new {@link MultiFlatMapProcessor}.
     */
    protected MultiFlatMapProcessor() {
    }

    /**
     * Create new {@link MultiFlatMapProcessor} with item to {@link java.lang.Iterable} mapper.
     *
     * @param mapper to provide iterable for every item from upstream
     */
    protected MultiFlatMapProcessor(Function<T, Flow.Publisher<X>> mapper) {
        Objects.requireNonNull(mapper);
        this.mapper = t -> (Flow.Publisher<X>) mapper.apply(t);
    }

    /**
     * Set mapper used for publisher creation.
     *
     * @param mapper function used for publisher creation
     * @return {@link MultiFlatMapProcessor}
     */
    protected MultiFlatMapProcessor<T, X> mapper(Function<T, Flow.Publisher<X>> mapper) {
        Objects.requireNonNull(mapper);
        this.mapper = mapper;
        return this;
    }

    /**
     * Return received error if any.
     *
     * @return Optional with received error if any
     */
    protected Optional<Throwable> error() {
        return this.error;
    }

    /**
     * Create new {@link MultiFlatMapProcessor} with item to {@link java.lang.Iterable} mapper.
     *
     * @param mapper to provide iterable for every item from upstream
     * @param <T>    input item type
     * @param <R>    output item type
     * @return {@link MultiFlatMapProcessor}
     */
    public static <T, R> MultiFlatMapProcessor<T, R> fromIterableMapper(Function<T, Iterable<R>> mapper) {
        return new MultiFlatMapProcessor<>(o -> Multi.from(mapper.apply(o)));
    }

    /**
     * Create new {@link MultiFlatMapProcessor} with item to {@link java.util.concurrent.Flow.Publisher} mapper.
     *
     * @param mapper to provide iterable for every item from upstream
     * @param <T>    input item type
     * @param <U>    output item type
     * @return {@link MultiFlatMapProcessor}
     */
    public static <T, U> MultiFlatMapProcessor<T, U> fromPublisherMapper(Function<T, Flow.Publisher<U>> mapper) {
        return new MultiFlatMapProcessor<>(mapper);
    }

    private class FlatMapSubscription implements Flow.Subscription {
        @Override
        public void request(long n) {
            requestCounter.increment(n, MultiFlatMapProcessor.this::onError);
            if (Objects.nonNull(innerSubscription) && Objects.nonNull(innerPublisher)) {
                innerSubscription.request(n);
            } else {
                requestCounterUpstream.increment(1, MultiFlatMapProcessor.this::onError);
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
        subscriptionLock(() -> {
            this.subscriber = SequentialSubscriber.create(subscriber);
            if (Objects.nonNull(this.subscription)) {
                this.subscriber.onSubscribe(new FlatMapSubscription());
            }
            error.ifPresent(this.subscriber::onError);
        });
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        subscriptionLock(() -> {
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
        requestCounterUpstream.tryDecrement();
        try {
            var mapperReturnedPublisher = mapper.apply(o);
            if (Objects.isNull(mapperReturnedPublisher)) {
                throw new IllegalStateException("Mapper returned a null value!");
            }
            subscriptionLock(() -> {
                innerPublisher = mapperReturnedPublisher;
                innerPublisher.subscribe(new InnerSubscriber());
            });
        } catch (Throwable t) {
            subscription.cancel();
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
        upstreamCompleted.set(true);
        subscriptionLock(() -> {
            try {
                requestCounter.lock();
                if (requestCounter.get() == 0 || Objects.isNull(innerPublisher)) {
                    //Have to wait for all Publishers to be finished
                    subscriber.onComplete();
                }
            } finally {
                requestCounter.unlock();
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
            try {
                requestCounter.lock();
                if (requestCounter.get() > 0) {
                    innerSubscription.request(requestCounter.get());
                }
            } finally {
                requestCounter.unlock();
            }
        }

        @Override
        public void onNext(X item) {
            Objects.requireNonNull(item);
            if (requestCounter.tryDecrement()) {
                subscriber.onNext(item);
            } else {
                onError(new IllegalStateException("Not enough request to submit item"));
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
            if (upstreamCompleted.get()) {
                subscriber.onComplete();
                return;
            }
            subscriptionLock(() -> {
                innerPublisher = null;
            });
            try {
                requestCounter.lock();
                try {
                    requestCounterUpstream.lock();

                    if (requestCounter.get() > 0 && requestCounterUpstream.get() == 0) {
                        requestCounterUpstream.increment(1, MultiFlatMapProcessor.this::onError);
                        subscription.request(1);
                    }
                } finally {
                    requestCounterUpstream.unlock();
                }
            } finally {
                requestCounter.unlock();
            }
        }
    }

    /**
     * Protect critical sections when working with states of innerPublisher.
     */
    private void subscriptionLock(Runnable runnable) {
        if (!strictMode) {
            runnable.run();
            return;
        }
        try {
            subscriptionLock.lock();
            runnable.run();
        } finally {
            subscriptionLock.unlock();
        }
    }
}
