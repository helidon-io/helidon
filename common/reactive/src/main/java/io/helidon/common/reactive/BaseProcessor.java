/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Processor;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A generic processor used for the implementation of {@link Multi} and {@link Single}.
 *
 * @param <T> subscribed type (input)
 * @param <U> published type (output)
 */
abstract class BaseProcessor<T, U> implements Processor<T, U>, Subscription {

    private Subscription subscription;
    private final SingleSubscriberHolder<U> subscriber;
    private final RequestedCounter requested;
    private final AtomicBoolean ready;
    private final AtomicBoolean subscribed;
    private SubscriberReference<? super U> referencedSubscriber;
    private ReentrantLock publisherSequentialLock = new ReentrantLock();
    private volatile boolean done;
    private Throwable error;

    /**
     * Generic processor used for the implementation of {@link Multi} and {@link Single}.
     */
    BaseProcessor() {
        requested = new RequestedCounter();
        ready = new AtomicBoolean();
        subscribed = new AtomicBoolean();
        subscriber = new SingleSubscriberHolder<>();
    }

    @Override
    public void request(long n) {
        StreamValidationUtils.checkRequestParam(n, this::failAndCancel);
        StreamValidationUtils.checkRecursionDepth(5, (actDepth, t) -> failAndCancel(t));
        requested.increment(n, this::failAndCancel);
        tryRequest(subscription);
        if (done) {
            tryComplete();
        }
    }

    @Override
    public void cancel() {
        subscriber.cancel();
        try {
            hookOnCancel(subscription);
        } catch (Throwable ex) {
            failAndCancel(ex);
        }
    }

    @Override
    public void onSubscribe(Subscription s) {
        try {
            // https://github.com/reactive-streams/reactive-streams-jvm#1.3
            publisherSequentialLock.lock();
            // https://github.com/reactive-streams/reactive-streams-jvm#2.13
            Objects.requireNonNull(s);
            if (subscription == null) {
                this.subscription = s;
                tryRequest(s);
            } else {
                // https://github.com/reactive-streams/reactive-streams-jvm#2.5
                s.cancel();
            }
        } finally {
            publisherSequentialLock.unlock();
        }
    }

    @Override
    public void onNext(T item) {
        try {
            publisherSequentialLock.lock();
            if (done) {
                throw new IllegalStateException("Subscriber is closed!");
            }
            // https://github.com/reactive-streams/reactive-streams-jvm#2.13
            Objects.requireNonNull(item);
            try {
                hookOnNext(item);
            } catch (Throwable ex) {
                failAndCancel(ex);
            }
        } finally {
            publisherSequentialLock.unlock();
        }
    }

    /**
     * OnError downstream.
     *
     * @param ex Exception to be reported downstream
     */
    protected void fail(Throwable ex) {
        // https://github.com/reactive-streams/reactive-streams-jvm#2.13
        Objects.requireNonNull(ex);
        done = true;
        if (error == null) {
            error = ex;
        }
        tryComplete();
    }

    /**
     * OnError downstream and cancel upstream.
     *
     * @param ex Exception to be reported downstream
     */
    protected void failAndCancel(Throwable ex) {
        getSubscription().ifPresent(Flow.Subscription::cancel);
        fail(ex);
    }

    @Override
    public void onError(Throwable ex) {
        fail(ex);
    }

    @Override
    public void onComplete() {
        done = true;
        tryComplete();
    }

    @Override
    public void subscribe(Subscriber<? super U> s) {
        // https://github.com/reactive-streams/reactive-streams-jvm#3.13
        referencedSubscriber = SubscriberReference.create(s);
        try {
            publisherSequentialLock.lock();
            if (subscriber.register(s)) {
                ready.set(true);
                s.onSubscribe(this);
                if (done) {
                    tryComplete();
                }
            }
        } finally {
            publisherSequentialLock.unlock();
        }
    }

    /**
     * Processor's {@link Flow.Subscription} registered by
     * {@link BaseProcessor#onSubscribe(Flow.Subscription)}.
     *
     * @return {@link Flow.Subscription}
     */
    protected Optional<Subscription> getSubscription() {
        return Optional.ofNullable(subscription);
    }

    /**
     * Processor's {@link SingleSubscriberHolder}.
     *
     * @return {@link SingleSubscriberHolder}
     */
    protected SingleSubscriberHolder<U> getSubscriber() {
        return subscriber;
    }

    /**
     * Returns {@link RequestedCounter} with information about requested vs. submitted items.
     *
     * @return {@link RequestedCounter}
     */
    protected RequestedCounter getRequestedCounter() {
        return requested;
    }

    /**
     * Submit an item to the subscriber.
     *
     * @param item item to be submitted
     */
    protected void submit(U item) {
        if (requested.tryDecrement()) {
            try {
                subscriber.get().onNext(item);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                failAndCancel(ex);
            } catch (Throwable ex) {
                failAndCancel(ex);
            }
        } else {
            notEnoughRequest(item);
        }
    }

    /**
     * Define what to do when there is not enough requests to submit item.
     *
     * @param item Item for submitting
     */
    protected void notEnoughRequest(U item) {
        onError(new IllegalStateException("Not enough request to submit item"));
    }

    /**
     * Hook for {@link Subscriber#onNext(java.lang.Object)}.
     *
     * @param item next item
     */
    protected void hookOnNext(T item) {
    }

    /**
     * Hook for {@link Subscriber#onError(java.lang.Throwable)}.
     *
     * @param error error received
     */
    protected void hookOnError(Throwable error) {
    }

    /**
     * Hook for {@link Subscriber#onComplete()}.
     */
    protected void hookOnComplete() {
    }

    /**
     * Hook for {@link SingleSubscriberHolder#cancel()}.
     *
     * @param subscription of the processor for optional passing cancel event
     */
    protected void hookOnCancel(Flow.Subscription subscription) {
        Optional.ofNullable(subscription).ifPresent(Flow.Subscription::cancel);
        // https://github.com/reactive-streams/reactive-streams-jvm#3.13
        referencedSubscriber.releaseReference();
    }

    /**
     * Subscribe the subscriber after the given delegate publisher.
     *
     * @param delegate delegate publisher
     */
    protected final void doSubscribe(Publisher<U> delegate) {
        if (subscribed.compareAndSet(false, true)) {
            delegate.subscribe(new Subscriber<U>() {
                @Override
                public void onSubscribe(Subscription subscription) {
                    tryRequest(subscription);
                }

                @Override
                public void onNext(U item) {
                    submit(item);
                }

                @Override
                public void onError(Throwable ex) {
                    BaseProcessor.this.failAndCancel(ex);
                }

                @Override
                public void onComplete() {
                    BaseProcessor.this.onComplete();
                }
            });
        }
    }

    private void completeOnError(Subscriber<? super U> sub, Throwable ex) {
        hookOnError(ex);
        sub.onError(ex);
    }

    /**
     * Try close processor's subscriber.
     */
    protected void tryComplete() {
        if (ready.get() && !subscriber.isClosed()) {
            if (error != null) {
                subscriber.close(sub -> completeOnError(sub, error));
            } else {
                try {
                    hookOnComplete();
                } catch (Throwable ex) {
                    subscriber.close(sub -> completeOnError(sub, ex));
                    return;
                }
                subscriber.close(Subscriber::onComplete);
            }
        }
    }

    /**
     * Responsible for calling {@link Flow.Subscription#request(long)}.
     *
     * @param subscription {@link Flow.Subscription} to make a request from
     */
    protected void tryRequest(Subscription subscription) {
        if (subscription != null && !subscriber.isClosed()) {
            long n = requested.get();
            if (n > 0) {
                subscription.request(n);
            }
        }
    }
}
