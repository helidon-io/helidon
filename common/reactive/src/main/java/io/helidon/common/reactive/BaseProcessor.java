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
import java.util.concurrent.atomic.AtomicBoolean;

import io.helidon.common.reactive.Flow.Processor;
import io.helidon.common.reactive.Flow.Publisher;
import io.helidon.common.reactive.Flow.Subscriber;
import io.helidon.common.reactive.Flow.Subscription;

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
    private volatile boolean done;
    private Throwable error;

    BaseProcessor() {
        requested = new RequestedCounter();
        ready = new AtomicBoolean();
        subscribed = new AtomicBoolean();
        subscriber = new SingleSubscriberHolder<>();
    }

    @Override
    public final void request(long n) {
        requested.increment(n, ex -> onError(ex));
        tryRequest(subscription);
        if (done) {
            tryComplete();
        }
    }

    @Override
    public final void cancel() {
        subscriber.cancel();
    }

    @Override
    public final void onSubscribe(Subscription s) {
        if (subscription == null) {
            this.subscription = s;
            tryRequest(s);
        }
    }

    @Override
    public final void onNext(T item) {
        if (!subscriber.isClosed()) {
            try {
                hookOnNext(item);
            } catch (Throwable ex) {
                onError(ex);
            }
        }
    }

    @Override
    public final void onError(Throwable ex) {
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
     * Submit an item to the subscriber.
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
     * @param item next item
     */
    protected void hookOnNext(T item) {
    }

    /**
     * Hook for {@link Subscriber#onError(java.lang.Throwable)}.
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
     * Subscribe the subscriber after the given delegate publisher.
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

    private void tryComplete() {
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

    private void tryRequest(Subscription s) {
        if (s != null && !subscriber.isClosed()) {
            long n = requested.get();
            if (n > 0) {
                s.request(n);
            }
        }
    }
}
