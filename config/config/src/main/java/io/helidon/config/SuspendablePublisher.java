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

package io.helidon.config;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.reactive.Flow;

/**
 * Publisher with "suspended" and "running" behavior.
 * <p>
 * Additional hooks:
 * <ul>
 * <li>{@link #onFirstSubscriptionRequest()} - invoked when the first subscriber
 * requests the publisher
 * </li>
 * <li>{@link #onLastSubscriptionCancel()} - invoked when the last subscribed
 * subscriber cancels its subscription
 * </li>
 * </ul>
 *
 * @param <T> the published item type
 */
abstract class SuspendablePublisher<T> implements Flow.Publisher<T> {

    private final Flow.Publisher<T> delegatePublisher;

    private final AtomicBoolean running;
    private final AtomicInteger numberOfSubscribers;
    private final Object lock = new Object();

    /**
     * Creates a new SuspendablePublisher using the given Executor for
     * async delivery to subscribers, with the given maximum buffer size
     * for each subscriber, and, if non-null, the given handler invoked
     * when any Subscriber throws an exception in method {@link
     * Flow.Subscriber#onNext(Object) onNext}.
     *
     * @param delegatePublisher publisher used to delegate to
     */
    SuspendablePublisher(Flow.Publisher<T> delegatePublisher) {
        this.delegatePublisher = delegatePublisher;

        running = new AtomicBoolean(false);
        numberOfSubscribers = new AtomicInteger(0);
    }

    /**
     * Hook invoked in case the first subscriber has requested the publisher.
     */
    protected abstract void onFirstSubscriptionRequest();

    /**
     * Hook invoked in case the last subscribed subscriber has canceled it's subscriptions.
     */
    protected abstract void onLastSubscriptionCancel();

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        delegatePublisher.subscribe(new SuspendableSubscriber<>(subscriber, this::beforeRequestHook, this::afterCancelHook));
        numberOfSubscribers.incrementAndGet();
    }

    private void beforeRequestHook() {
        if (!running.get()) {
            synchronized (lock) {
                if (!running.get()) {
                    running.set(true);
                    onFirstSubscriptionRequest();
                }
            }
        }
    }

    private void afterCancelHook() {
        numberOfSubscribers.decrementAndGet();

        if (numberOfSubscribers.intValue() == 0 && running.get()) {
            synchronized (lock) {
                if (running.get()) {
                    onLastSubscriptionCancel();
                    running.set(false);
                }
            }
        }
    }

    private static class SuspendableSubscriber<T> implements Flow.Subscriber<T> {

        private final Flow.Subscriber<? super T> delegate;
        private final Runnable beforeRequestHook;
        private final Runnable afterCancelHook;

        private SuspendableSubscriber(Flow.Subscriber<? super T> delegate, Runnable beforeRequestHook, Runnable afterCancelHook) {
            this.delegate = delegate;
            this.beforeRequestHook = beforeRequestHook;
            this.afterCancelHook = afterCancelHook;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            delegate.onSubscribe(new SuspendableSubscription(subscription, beforeRequestHook, afterCancelHook));
        }

        @Override
        public void onNext(T item) {
            delegate.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            delegate.onError(throwable);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }
    }

    private static class SuspendableSubscription implements Flow.Subscription {

        private final Flow.Subscription subscription;
        private final Runnable beforeRequestHook;
        private final Runnable afterCancelHook;

        private SuspendableSubscription(Flow.Subscription subscription, Runnable beforeRequestHook, Runnable afterCancelHook) {
            this.subscription = subscription;
            this.beforeRequestHook = beforeRequestHook;
            this.afterCancelHook = afterCancelHook;
        }

        @Override
        public void request(long n) {
            beforeRequestHook.run();
            subscription.request(n);
        }

        @Override
        public void cancel() {
            subscription.cancel();
            afterCancelHook.run();
        }
    }

}
