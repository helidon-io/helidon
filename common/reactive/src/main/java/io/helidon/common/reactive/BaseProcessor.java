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
public abstract class BaseProcessor<T, U> implements Processor<T, U>, Subscription, StrictProcessor {

    private Subscription subscription;
    private final SingleSubscriberHolder<U> subscriber;
    private final RequestedCounter requested;
    private final RequestedCounter ableToSubmit;
    private final ReentrantLock subscriptionLock = new ReentrantLock();
    private final AtomicBoolean ready;
    private final AtomicBoolean subscribed;
    private volatile boolean done;
    private Throwable error;
    private boolean strictMode = DEFAULT_STRICT_MODE;


    /**
     * Generic processor used for the implementation of {@link Multi} and {@link Single}.
     */
    protected BaseProcessor() {
        requested = new RequestedCounter(strictMode);
        ableToSubmit = new RequestedCounter(strictMode);
        ready = new AtomicBoolean();
        subscribed = new AtomicBoolean();
        subscriber = new SingleSubscriberHolder<>();
    }

    @Override
    public BaseProcessor<T, U> strictMode(boolean strictMode) {
        this.strictMode = strictMode;
        return this;
    }

    @Override
    public void request(long n) {
        ableToSubmit.increment(n, this::failAndCancel);
        subscriptionLock(() -> {
            if (subscription != null && !subscriber.isClosed()) {
                subscription.request(n);
            } else {
                requested.increment(n, this::failAndCancel);
            }
        });
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
        Objects.requireNonNull(s);
        subscriptionLock(() -> {
            if (subscription == null) {
                this.subscription = s;
                tryRequest(s);
            } else {
                s.cancel();
            }
        });
    }

    @Override
    public void onNext(T item) {
        if (done) {
            throw new IllegalStateException("Subscriber is closed!");
        }
        Objects.requireNonNull(item);
        try {
            hookOnNext(item);
        } catch (Throwable ex) {
            failAndCancel(ex);
        }
    }

    /**
     * OnError downstream.
     *
     * @param ex Exception to be reported downstream
     */
    protected void fail(Throwable ex) {
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
    public void subscribe(Subscriber<? super U> sub) {
        subscriptionLock(() -> {
            var s = sub;
            if (strictMode) {
                s = SequentialSubscriber.create(sub);
            }
            if (subscriber.register(s)) {
                ready.set(true);
                s.onSubscribe(this);
                if (done) {
                    tryComplete();
                }
            }
        });
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
     * Submit an item to the subscriber.
     *
     * @param item item to be submitted
     */
    protected void submit(U item) {
        if (ableToSubmit.tryDecrement()) {
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
    }

    /**
     * Subscribe the subscriber after the given delegate publisher.
     *
     * @param delegate delegate publisher
     */
    protected final void doSubscribe(Publisher<U> delegate) {
        if (subscribed.compareAndSet(false, true)) {
            delegate.subscribe(new Subscriber<>() {
                @Override
                public void onSubscribe(Subscription subscription) {
                    tryRequest(ableToSubmit, subscription);
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
        tryRequest(requested, subscription);
    }

    private void tryRequest(RequestedCounter counter, Subscription subscription) {
        if (subscription == null || subscriber.isClosed()) {
            return;
        }

        long n;
        try {
            counter.lock();
            n = counter.get();
            if (n < 1) {
                return;
            }
            subscription.request(n);
        } finally {
            counter.unlock();
        }
    }

    private void subscriptionLock(Runnable guardedBlock) {
        if (!strictMode) {
            guardedBlock.run();
            return;
        }
        try {
            subscriptionLock.lock();
            guardedBlock.run();
        } finally {
            subscriptionLock.unlock();
        }
    }
}
