/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * The OriginThreadPublisher's nature is to always run {@link Subscriber#onNext(Object)} on the very same thread as
 * {@link #submit(Object)}. In other words, whenever the source of chunks sends data, the same thread is used to deliver the data
 * to the subscriber.
 * <p>
 * Standard publisher implementations (such as {@link java.util.concurrent.SubmissionPublisher} or Reactor Flux would use
 * the same thread as {@link Subscription#request(long)} was called on to deliver the chunk when the data are already available;
 * this implementation however strictly uses the originating thread.<p>
 * In order to be able to achieve such behavior, this publisher provides hooks on subscription methods: {@link #hookOnCancel()}
 * and {@link #hookOnRequested(long, long)}.
 * </p>
 * <p>
 * <strong>This publisher allows only a single subscriber</strong>.
 * </p>
 *
 * @deprecated Use {@link BufferedEmittingPublisher} or {@link EmittingPublisher} instead.
 *
 * @param <T> type of published items
 * @param <U> type of submitted items
 */
@Deprecated
public abstract class OriginThreadPublisher<T, U> implements Publisher<T> {

    private static final Logger LOGGER = Logger.getLogger(OriginThreadPublisher.class.getName());

    private final UnboundedSemaphore semaphore;
    private final AtomicBoolean hasSingleSubscriber = new AtomicBoolean(false);

    /**
     * Required to achieve rule https://github.com/reactive-streams/reactive-streams-jvm#1.3 .
     */
    private final Lock reentrantLock = new ReentrantLock();

    private volatile Subscriber<? super T> singleSubscriber;
    private volatile boolean completed;
    private volatile Throwable t;
    private final BlockingQueue<T> queue = new ArrayBlockingQueue<>(256);
    private final AtomicLong nextCount = new AtomicLong();
    private volatile long reqCount = 0;

    /**
     * Create same thread publisher.
     *
     * @param semaphore the semaphore to indicate the amount of requested data. The owner of this publisher is responsible to send
     * the data as determined by the semaphore (i.e., to properly acquire a permission to send the data; to not send when the
     * number of permits is zero).
     */
    protected OriginThreadPublisher(UnboundedSemaphore semaphore) {
        this.semaphore = semaphore;
    }

    /**
     * Create same thread publisher.
     */
    protected OriginThreadPublisher() {
        this(UnboundedSemaphore.create());
    }

    @Override
    public void subscribe(Subscriber<? super T> originalSubscriber) {
        if (!hasSingleSubscriber.compareAndSet(false, true)) {
            originalSubscriber.onError(new IllegalStateException("Only single subscriber is allowed!"));
            return;
        }

        singleSubscriber = originalSubscriber;

        reentrantLock.lock();
        try {
            originalSubscriber.onSubscribe(new Subscription() {

                private boolean nexting;

                @Override
                public void request(long n) {
                    if (n <= 0) {
                        error(new IllegalArgumentException("Illegal value requested: " + n));
                        return;
                    }

                    try {
                        reentrantLock.lock();

                        reqCount += n;
                        long release = n;

                        if (nexting) {
                            return;
                        }

                        while (singleSubscriber != null
                                && !queue.isEmpty()
                                && reqCount > nextCount.get()) {

                            nextCount.incrementAndGet();
                            try {
                                nexting = true;
                                release--;
                                T item = queue.remove();
                                LOGGER.finest(() -> "Publishing item: " + item);
                                singleSubscriber.onNext(item);
                            } finally {
                                nexting = false;
                            }
                        }

                        if (singleSubscriber == null) {
                            // subscriber has been canceled
                            return;
                        }
                        if (t != null) {
                            LOGGER.finest("Completing with an error from request.");
                            singleSubscriber.onError(t);
                        } else if (completed && queue.isEmpty()) {
                            LOGGER.finest("Completing from request.");
                            singleSubscriber.onComplete();
                        } else if (queue.isEmpty()) {
                            long released = n == Long.MAX_VALUE ? Long.MAX_VALUE : release;
                            long result = semaphore.release(released);
                            LOGGER.finest(() -> "Semaphore released: " + result);
                            hookOnRequested(released, result);
                        }
                    } finally {
                        reentrantLock.unlock();
                    }
                }

                @Override
                public void cancel() {
                    hookOnCancel();
                    singleSubscriber = null;
                }
            });
        } finally {
            reentrantLock.unlock();
        }
    }

    /**
     * Wrap the submitted data into an item that can be published. This implementation casts {@code U} to {@code T}.
     *
     * @param data submitted data
     * @return item to publish
     * @throws ClassCastException if {@code U} cannot be cast to {@code T}
     */
    @SuppressWarnings("unchecked")
    protected T wrap(U data) {
        return (T) data;
    }

    /**
     * Submit the data to the subscriber. The same thread is used to call {@link Subscriber#onNext(Object)}. That is, the data are
     * synchronously passed to the subscriber.
     * <p>
     * Note that in order to maintain a consistency of this publisher, this method must be called only once per a single permit
     * that must be acquired by {@link #tryAcquire()}.
     *
     * @param data the chunk of data to send to the subscriber
     */
    public void submit(U data) {
        try {
            reentrantLock.lock();
            if (!queue.offer(wrap(data))) {
                LOGGER.severe("Unable to add an element to the publisher cache.");
                error(new IllegalStateException("Unable to add an element to the publisher cache."));
                return;
            }

            if (nextCount.get() < reqCount) {
                nextCount.incrementAndGet();
                // the poll is never expected to return null
                T item = queue.poll();

                LOGGER.finest(() -> "Publishing item: " + (null == item ? "null" : item));
                singleSubscriber.onNext(item);
            } else {
                LOGGER.finest(() -> "Not publishing due to low request count: " + nextCount + " <= " + reqCount);
            }
        } catch (RuntimeException e) {
            if (singleSubscriber == null) {
                t = e;
            } else {
                error(new IllegalStateException("An error occurred when submitting data.", e));
            }
        } finally {
            reentrantLock.unlock();
        }
    }

    /**
     * If not subscribed to, consume all the items from this publisher.
     */
    public void drain() {
        if (!hasSingleSubscriber.get() && !(completed && queue.isEmpty())) {
            LOGGER.fine(() -> "No one registered to consumer request");

            // if anyone races and wins, this subscriber is going to receive onError, and be done
            // otherwise, this subscriber is going to release all the chunks, and anyone who
            // attempts to subscribe is going to receive onError.
            subscribe(new Subscriber<T>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(T item) {
                    drain(item);
                }

                @Override
                public void onError(Throwable throwable) {
                }

                @Override
                public void onComplete() {
                }
            });
        }
    }

    /**
     * Process a drained item. This default implementation of this method is a no-op, it is meant to be overridden by sub-classes
     * to customize the draining process.
     *
     * @param item drained item
     */
    protected void drain(T item){
    }

    /**
     * Synchronously trigger {@link Subscriber#onError(Throwable)}.
     *
     * @param throwable the exception to send
     */
    public void error(Throwable throwable) {
        try {
            reentrantLock.lock();

            if (singleSubscriber != null && queue.isEmpty()) {
                singleSubscriber.onError(throwable);
                singleSubscriber = null;
            } else {
                t = throwable;
            }
        } catch (RuntimeException e) {
            // throwable consumption emitted another exception
            throw new IllegalStateException("On error threw an exception!", e);
        } finally {
            reentrantLock.unlock();
        }
    }

    /**
     * Synchronously trigger {@link Subscriber#onComplete()}.
     */
    public void complete() {
        try {
            reentrantLock.lock();

            completed = true;
            if (singleSubscriber != null && queue.isEmpty()) {
                LOGGER.finest("Completing by the producing thread.");
                singleSubscriber.onComplete();
                singleSubscriber = null;
            } else {
                LOGGER.finest("Not completing by the producing thread.");
            }
        } finally {
            reentrantLock.unlock();
        }
    }

    /**
     * In a non-blocking manner, try to acquire an allowance to publish next item.
     *
     * @return original number of requests on the very one associated subscriber's subscription; if {@code 0} is returned, the
     * requester didn't obtain a permit to publish next item. In case a {@link Long#MAX_VALUE} is returned, the requester is
     * informed that unlimited number of items can be published.
     */
    public long tryAcquire() {
        return semaphore.tryAcquire();
    }

    /**
     * Indicates that the only one possible associated subscriber has been completed.
     *
     * @return whether this publisher has successfully finished
     */
    public boolean isCompleted() {
        return completed;
    }

    /**
     * Indicate that more items should be published in order to meet the current demand of the subscriber.
     *
     * @return whether this publisher currently satisfies the subscriber
     */
    public boolean requiresMoreItems() {
        return reqCount - (nextCount.get() + queue.size()) > 0;
    }

    /**
     * Hook invoked after calls to {@link Subscription#request(long)}.
     *
     * @param n the requested count
     * @param result the current total cumulative requested count; ranges between [0, {@link Long#MAX_VALUE}] where the max
     * indicates that this publisher is unbounded
     */
    protected void hookOnRequested(long n, long result) {
    }

    /**
     * Hook invoked after calls to {@link Subscription#cancel()}.
     */
    protected void hookOnCancel() {
    }
}
