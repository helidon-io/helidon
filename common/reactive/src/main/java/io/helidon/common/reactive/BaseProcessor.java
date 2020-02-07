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
    private Subscriber<? super U> subscriber;
    private final ReentrantLock subscriptionLock = new ReentrantLock();
    private final ReentrantLock errorLock = new ReentrantLock();
    private AtomicBoolean ready = new AtomicBoolean(false);
    private AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile boolean completed;
    private Throwable error;
    private boolean strictMode = DEFAULT_STRICT_MODE;


    /**
     * Generic processor used for the implementation of {@link Multi} and {@link Single}.
     */
    protected BaseProcessor() {
        subscriber = null;
    }

    @Override
    public BaseProcessor<T, U> strictMode(boolean strictMode) {
        this.strictMode = strictMode;
        return this;
    }

    @Override
    public void request(long n) {
        StreamValidationUtils.checkRequestParam(n, this::onError);
        if (!cancelled.get()) {
            subscription.request(n);
        }
    }

    @Override
    public void cancel() {
        hookOnCancel(subscription);
        if (!cancelled.getAndSet(true)) {
            subscription.cancel();
        }
    }

    @Override
    public void onSubscribe(Subscription s) {
        Objects.requireNonNull(s);
        subscriptionLock(() -> {
            if (subscription == null) {
                this.subscription = s;
                tryOnSubscribe();
            } else {
                s.cancel();
            }
        });
    }

    @Override
    public void subscribe(Subscriber<? super U> sub) {
        subscriptionLock(() -> {
            if (Objects.nonNull(subscriber)) {
                sub.onError(new IllegalStateException("This publisher only supports a single subscriber!"));
                return;
            }
            subscriber = sub;
            if (strictMode) {
                subscriber = SequentialSubscriber.create(sub);
            }
            tryOnSubscribe();
            tryComplete();
        });
    }


    private boolean tryOnSubscribe() {
        if (Objects.nonNull(subscription)
                && Objects.nonNull(subscriber)) {
            if (!ready.getAndSet(true)) {
                subscriber.onSubscribe(this);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onNext(T item) {
        Objects.requireNonNull(item);
        if (completed) {
            throw new IllegalStateException("Subscriber is closed!");
        }
        try {
            hookOnNext(item);
        } catch (Throwable ex) {
            Optional.ofNullable(subscription).ifPresent(Subscription::cancel);
            onError(ex);
        }
    }

    /**
     * Submit an item to the subscriber.
     *
     * @param item item to be submitted
     */
    protected void submit(U item) {
        try {
            subscriber.onNext(item);
        } catch (Throwable ex) {
            onError(ex);
        }
    }

    @Override
    public void onError(Throwable ex) {
        Objects.requireNonNull(ex);
        errorLock(() -> {
            if (error == null) {
                error = ex;
            } else if (!Objects.equals(ex, error)) {
                error.addSuppressed(ex);
            }
            tryComplete();
        });
    }

    @Override
    public void onComplete() {
        completed = true;
        tryComplete();
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
     * Try close processor's subscriber.
     */
    protected void tryComplete() {
        if (Objects.isNull(subscriber)) return;
        if (Objects.nonNull(error)) {
            try {
                hookOnError(error);
            } catch (Throwable ex) {
                error.addSuppressed(ex);
            }
            subscriber.onError(error);
        } else if (completed) {
            try {
                hookOnComplete();
            } catch (Throwable ex) {
                subscriber.onError(ex);
                return;
            }
            subscriber.onComplete();
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

    private void errorLock(Runnable guardedBlock) {
        if (!strictMode) {
            guardedBlock.run();
            return;
        }
        try {
            errorLock.lock();
            guardedBlock.run();
        } finally {
            errorLock.unlock();
        }
    }

}
