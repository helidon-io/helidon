/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.common.reactive.valve;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.reactive.Flow;

/**
 * The {@link Valve} implementation on top of {@link io.helidon.common.reactive.Flow.Publisher}.
 *
 * @param <T> Type of {@code Valve} and {@code Publisher} items
 */
class PublisherValve<T> implements Valve<T> {

    private static final Logger LOGGER = Logger.getLogger(PublisherValve.class.getName());

    private final ReentrantLock lock = new ReentrantLock();
    private final Flow.Publisher<T> publisher;

    private volatile Subscriber subscriber;
    private volatile boolean paused = false;

    private boolean recordedDemand = false;

    /**
     * Creates new instance.
     *
     * @param publisher a publisher as a base of this {@code Valve}
     */
    PublisherValve(Flow.Publisher<T> publisher) {
        Objects.requireNonNull(publisher, "Parameter 'publisher' is null!");
        this.publisher = publisher;
    }

    @Override
    public void handle(BiConsumer<T, Pausable> onData, Consumer<Throwable> onError, Runnable onComplete) {
        synchronized (this) {
            if (this.subscriber != null) {
                throw new IllegalStateException("Handler is already registered!");
            }
            this.subscriber = new Subscriber(onData, onError, onComplete);
        }
        this.paused = false;
        publisher.subscribe(this.subscriber);
    }

    @Override
    public void pause() {
        lock.lock();
        try {
            this.paused = true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void resume() {
        boolean processDemand = false;
        lock.lock();
        try {
            if (paused && subscriber != null) {
                paused = false;
                if (recordedDemand) {
                    processDemand = true;
                    recordedDemand = false;
                }
            }
        } finally {
            lock.unlock();
            if (processDemand) {
                subscriber.subscription.request(1);
            }
        }
    }

    private boolean recordDemand() {
        lock.lock();
        try {
            if (paused) {
                this.recordedDemand = true;
                return true;
            } else {
                return false;
            }
        } finally {
            lock.unlock();
        }
    }

    private class Subscriber implements Flow.Subscriber<T> {

        private final BiConsumer<T, Pausable> onData;
        private final Consumer<Throwable> onError;
        private final Runnable onComplete;

        private volatile Flow.Subscription subscription;

        Subscriber(BiConsumer<T, Pausable> onData, Consumer<Throwable> onError, Runnable onComplete) {
            Objects.requireNonNull(onData, "Parameter 'onData' is null!");
            this.onData = onData;
            this.onError = onError;
            this.onComplete = onComplete;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(T item) {
            onData.accept(item, PublisherValve.this);
            if (!paused || !recordDemand()) {
                subscription.request(1);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (onError != null) {
                onError.accept(throwable);
            } else {
                LOGGER.log(Level.WARNING, "Unhandled throwable!", throwable);
            }
        }

        @Override
        public void onComplete() {
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }
}
