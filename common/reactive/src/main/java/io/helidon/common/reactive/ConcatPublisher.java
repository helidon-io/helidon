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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Concat streams to one.
 *
 * @param <T> item type
 */
public class ConcatPublisher<T> implements Flow.Publisher<T>, Multi<T> {
    private FirstSubscriber firstSubscriber;
    private SecondSubscriber secondSubscriber;
    private Flow.Subscriber<T> subscriber;
    private Flow.Publisher<T> firstPublisher;
    private Flow.Publisher<T> secondPublisher;
    private RequestedCounter requested = new RequestedCounter(true);
    private ReentrantLock firstPublisherCompleteLock = new ReentrantLock();
    private CompletableFuture<Void> firstSubscriberCompleted = new CompletableFuture<>();
    private CompletableFuture<Flow.Subscriber<T>> downstreamReady = new CompletableFuture<>();

    private ConcatPublisher(Flow.Publisher<T> firstPublisher, Flow.Publisher<T> secondPublisher) {
        this.firstPublisher = firstPublisher;
        this.secondPublisher = secondPublisher;
    }

    /**
     * Create new {@link ConcatPublisher}.
     *
     * @param firstPublisher  first stream
     * @param secondPublisher second stream
     * @param <T>             item type
     * @return {@link ConcatPublisher}
     */
    public static <T> ConcatPublisher<T> create(Flow.Publisher<T> firstPublisher, Flow.Publisher<T> secondPublisher) {
        return new ConcatPublisher<>(firstPublisher, secondPublisher);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        this.subscriber = (Flow.Subscriber<T>) subscriber;

        this.firstSubscriber = new FirstSubscriber();
        this.secondSubscriber = new SecondSubscriber();

        firstPublisher.subscribe(firstSubscriber);

        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
                if (!StreamValidationUtils.checkRequestParam(n, subscriber::onError)) {
                    return;
                }
                requested.increment(n, subscriber::onError);
                firstCompleteLock(() -> {
                    if (!firstSubscriber.complete) {
                        firstSubscriber.subscription.request(n);
                    } else {
                        secondSubscriber.subscription.request(n);
                    }
                });
            }

            @Override
            public void cancel() {
                firstSubscriber.subscription.cancel();
                secondSubscriber.subscription.cancel();
            }
        });
        downstreamReady.complete(this.subscriber);
    }

    private class FirstSubscriber implements Flow.Subscriber<T> {

        private Flow.Subscription subscription;
        private boolean complete = false;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            Objects.requireNonNull(subscription);
            this.subscription = subscription;
            secondPublisher.subscribe(secondSubscriber);
        }

        @Override
        public void onNext(T o) {
            requested.tryDecrement();
            ConcatPublisher.this.subscriber.onNext(o);
        }

        @Override
        public void onError(Throwable t) {
            firstCompleteLock(() -> complete = true);
            secondSubscriber.subscription.cancel();
            subscription.cancel();
            ConcatPublisher.this.subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            firstSubscriberCompleted.complete(null);
            firstCompleteLock(() -> complete = true);
            try {
                requested.lock();
                long n = requested.get();
                if (n > 0) {
                    secondSubscriber.subscription.request(n);
                }
            } finally {
                requested.unlock();
            }
        }
    }

    private class SecondSubscriber implements Flow.Subscriber<T> {

        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            Objects.requireNonNull(subscription);
            this.subscription = subscription;
        }

        @Override
        public void onNext(T o) {
            ConcatPublisher.this.subscriber.onNext(o);
        }

        @Override
        public void onError(Throwable t) {
            firstSubscriber.subscription.cancel();
            subscription.cancel();
            ConcatPublisher.this.subscriber.onError(t);
        }

        @Override
        public void onComplete() {
            downstreamReady.whenComplete((downStreamSubscriber, th) -> {
                firstSubscriberCompleted.whenComplete((aVoid, t) -> ConcatPublisher.this.subscriber.onComplete());
            });
        }
    }

    private void firstCompleteLock(Runnable runnable) {
        try {
            firstPublisherCompleteLock.lock();
            runnable.run();
        } finally {
            firstPublisherCompleteLock.unlock();
        }
    }
}
