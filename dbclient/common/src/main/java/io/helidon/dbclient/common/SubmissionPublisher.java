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
package io.helidon.dbclient.common;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.reactive.Flow;

/**
 * A single subscriber publisher that sends submitted events.
 *
 * @param <T> type of published items
 */
public class SubmissionPublisher<T> implements Flow.Publisher<T> {
    private final AtomicReference<Flow.Subscriber<? super T>> mySubscriber = new AtomicReference<>();
    private final AtomicBoolean finished = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean completed = new AtomicBoolean();
    private final AtomicLong requested = new AtomicLong();
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final MySubscription subscription = new MySubscription();
    private final Queue<T> buffer = new ArrayBlockingQueue<>(1024);
    private final Semaphore publishSemaphore = new Semaphore(1);

    private SubmissionPublisher() {
    }

    public static <T> SubmissionPublisher<T> create() {
        return new SubmissionPublisher<>();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        if (mySubscriber.compareAndSet(null, subscriber)) {
            subscriber.onSubscribe(subscription);
        } else {
            throw new IllegalStateException("SubmissionPublisher only supports a single subscriber");
        }
    }

    /**
     * Submit an item.
     *
     * @param item item to enqueue for processing
     * @throws java.lang.IllegalStateException in case the queue is on its capacity, or this publisher is already completed
     */
    public void submit(T item) {
        if (!offer(item)) {
            throw new IllegalStateException("Queue is full, cannot add item");
        }
    }

    public boolean offer(T item) {
        if (completed.get()) {
            throw new IllegalStateException("Publisher already completed");
        }
        attemptPublish();
        boolean result = buffer.offer(item);
        if (result) {
            attemptPublish();
        }
        return result;
    }

    public void complete() {
        completed.set(true);
        attemptPublish();
    }

    public void completeExceptionally(Throwable throwable) {
        if (completed.compareAndSet(false, true)) {
            error.set(throwable);
            buffer.clear();
        } else {
            throw new IllegalStateException("Publisher already completed");
        }
        attemptPublish();
    }

    // this method must run only once in parallel
    // as it may be called both from "offer" and "request"
    // we must make sure they do not conflict
    private void attemptPublish() {
        if (publishSemaphore.tryAcquire()) {
            try {
                if (checkNext()) {
                    Flow.Subscriber<? super T> subscriber = mySubscriber.get();
                    // now I am sure the subscriber is there and I am neither cancelled nor on error
                    // nor completed, and there is demand
                    T value;
                    while ((value = buffer.poll()) != null) {
                        requested.getAndDecrement();

                        // we have a subscriber, demand and data
                        subscriber.onNext(value);

                        if (!checkNext()) {
                            break;
                        }
                    }
                }
            } finally {
                publishSemaphore.release();
            }
        }
    }

    private boolean checkNext() {
        if (finished.get()) {
            // final state reached
            return false;
        }

        Flow.Subscriber<? super T> subscriber = mySubscriber.get();
        if (subscriber == null) {
            // only send if somebody expects it
            return false;
        }

        if (error.get() != null) {
            subscriber.onError(error.get());
            finished.set(true);
            return false;
        }

        if (buffer.isEmpty() && completed.get()) {
            subscriber.onComplete();
            finished.set(true);
            return false;
        }

        return requested.get() > 0;
    }

    private final class MySubscription implements Flow.Subscription {
        @Override
        public void request(long n) {
            requested.addAndGet(n);
            attemptPublish();
        }

        @Override
        public void cancel() {
            cancelled.set(true);
            finished.set(true);
            requested.set(0);
        }
    }
}
