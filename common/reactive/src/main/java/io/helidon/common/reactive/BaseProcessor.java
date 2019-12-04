/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow.Processor;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A generic processor used for the implementation of {@link Multi} and {@link Single}.
 *
 * @param <T> subscribed type (input)
 * @param <U> published type (output)
 */
public abstract class BaseProcessor<T, U> implements Processor<T, U>, Subscription {

    private Subscription subscription;
    private final SingleSubscriberHolder<U> subscriber;
    private final RequestedCounter requested;
    private final AtomicBoolean ready;
    private final AtomicBoolean subscribed;
    private volatile boolean done;
    private Throwable error;

    /**
     * Generic processor used for the implementation of {@link Multi} and {@link Single}.
     */
    public BaseProcessor() {
        requested = new RequestedCounter();
        ready = new AtomicBoolean();
        subscribed = new AtomicBoolean();
        subscriber = new SingleSubscriberHolder<>();
    }

    @Override
    public void request(long n) {
        requested.increment(n, ex -> onError(ex));
        tryRequest(subscription);
        if (done) {
            tryComplete();
        }
    }

    @Override
    public final void cancel() {
        subscriber.cancel();
        try {
            hookOnCancel(subscription);
        } catch (Throwable ex) {
            onError(ex);
        }
    }

    @Override
    public final void onSubscribe(Subscription s) {
        if (subscription == null) {
            this.subscription = s;
            tryRequest(s);
        }
    }

    @Override
    public void onNext(T item) {
        if (isSubscriberClosed()) {
            throw new IllegalStateException("Subscriber is closed!");
        }
        try {
            hookOnNext(item);
        } catch (Throwable ex) {
            onError(ex);
        }
    }

    protected boolean isSubscriberClosed() {
        return subscriber.isClosed();
    }

    @Override
    public void onError(Throwable ex) {
        done = true;
        if (error == null) {
            error = ex;
        }
        tryComplete();
    }

    @Override
    public final void onComplete() {
        done = true;
        tryComplete();
    }

    @Override
    public void subscribe(Subscriber<? super U> s) {
        if (subscriber.register(s)) {
            ready.set(true);
            s.onSubscribe(this);
            if (done) {
                tryComplete();
            }
        }
    }

    /**
     * Processor's {@link io.helidon.common.reactive.Flow.Subscription} registered by
     * {@link BaseProcessor#onSubscribe(io.helidon.common.reactive.Flow.Subscription)}.
     *
     * @return {@link io.helidon.common.reactive.Flow.Subscription}
     */
    protected Subscription getSubscription() {
        return subscription;
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
                onError(ex);
            } catch (ExecutionException ex) {
                onError(ex);
            } catch (Throwable ex) {
                onError(ex);
            }
        } else {
            onError(new IllegalStateException("Not enough request to submit item"));
        }
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
                    BaseProcessor.this.onError(ex);
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
     * Responsible for calling {@link io.helidon.common.reactive.Flow.Subscription#request(long)}.
     *
     * @param subscription {@link io.helidon.common.reactive.Flow.Subscription} to make a request from
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
