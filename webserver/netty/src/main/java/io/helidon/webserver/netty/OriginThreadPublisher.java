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

package io.helidon.webserver.netty;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Flow;

import io.netty.buffer.ByteBuf;

/**
 * The OriginThreadPublisher's nature is to always run {@link io.helidon.common.reactive.Flow.Subscriber#onNext(Object)}
 * on the very same thread as {@link #submit(ByteBuf)}. In other words, whenever the source of chunks sends data,
 * the same thread is used to deliver the data to the subscriber. Standard publisher implementations (such as
 * {@link io.helidon.common.reactive.SubmissionPublisher} or Reactor Flux would use the same thread as
 * {@link io.helidon.common.reactive.Subscription#request(long)} was called on to deliver the chunk when the data are
 * already available; this implementation however strictly uses the originating thread.
 * <p>
 * In order to be able to achieve such behavior, this publisher provides hooks on subscription methods:
 * {@link #hookOnCancel()} and {@link #hookOnRequested(long, long)}.
 * <p>
 * This publisher allows only a single subscriber.
 */
class OriginThreadPublisher implements Flow.Publisher<DataChunk> {

    private static final Logger LOGGER = Logger.getLogger(OriginThreadPublisher.class.getName());

    private final UnboundedSemaphore semaphore;
    private final AtomicBoolean hasSingleSubscriber = new AtomicBoolean(false);
    /** Required to achieve rule https://github.com/reactive-streams/reactive-streams-jvm#1.3 . */
    private final Lock reentrantLock = new ReentrantLock();

    private volatile Flow.Subscriber<? super DataChunk> singleSubscriber;
    private volatile boolean completed;
    private volatile Throwable t;

    private final BlockingQueue<ByteBufRequestChunk> queue = new ArrayBlockingQueue<>(256);
    private final ReferenceHoldingQueue<ByteBufRequestChunk> referenceQueue;

    private AtomicLong nextCount = new AtomicLong();
    private volatile long reqCount = 0;

    /**
     * Create same thread publisher.
     *
     * @param semaphore      the semaphore to indicate the amount of requested data. The owner of this publisher
     *                       is responsible to send the data as determined by the semaphore (i.e., to properly
     *                       acquire a permission to send the data; to not send when the number of permits is zero).
     * @param referenceQueue the reference queue to associate the
     *                       {@link io.helidon.webserver.netty.ReferenceHoldingQueue.ReleasableReference} instances with
     */
    OriginThreadPublisher(UnboundedSemaphore semaphore, ReferenceHoldingQueue<ByteBufRequestChunk> referenceQueue) {
        this.semaphore = semaphore;
        this.referenceQueue = referenceQueue;
    }

    /**
     * Create same thread publisher.
     *
     * @param referenceQueue the reference queue to associate the
     *                       {@link io.helidon.webserver.netty.ReferenceHoldingQueue.ReleasableReference} instances with
     */
    OriginThreadPublisher(ReferenceHoldingQueue<ByteBufRequestChunk> referenceQueue) {
        this(new UnboundedSemaphore(), referenceQueue);
    }

    @Override
    public void subscribe(Flow.Subscriber<? super DataChunk> originalSubscriber) {
        if (!hasSingleSubscriber.compareAndSet(false, true)) {
            originalSubscriber.onError(new IllegalStateException("Only single subscriber is allowed!"));
            return;
        }

        singleSubscriber = originalSubscriber;

        reentrantLock.lock();
        try {
            originalSubscriber.onSubscribe(new Flow.Subscription() {

                private boolean nexting;

                @Override
                public void request(long n) {
                    if (n <= 0) {
                        error(new IllegalArgumentException("[3.9] Illegal value requested: " + n));
                    }

                    try {
                        reentrantLock.lock();

                        reqCount += n;

                        long release = n;

                        if (nexting) {
                            return;
                        }

                        while (singleSubscriber != null && !queue.isEmpty() && reqCount > nextCount.get()) {
                            nextCount.incrementAndGet();
                            try {
                                nexting = true;
                                release--;

                                ByteBufRequestChunk item = queue.remove();
                                LOGGER.finest(() -> "Publishing request chunk: " + item.id());
                                singleSubscriber.onNext(item);
                            } finally {
                                nexting = false;
                            }
                        }

                        if (singleSubscriber == null) {
                            // subscriber has been canceled
                            return;
                        } else if (t != null) {
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
     * Hooks to the finished {@link io.helidon.common.reactive.Flow.Subscription#request(long)}.
     * The intention of this method is to be able to trigger {@link #submit(ByteBuf)} in order
     * call {@link io.helidon.common.reactive.Flow.Subscriber#onNext(Object)} as requested by the
     * request method.
     *
     * @param n      the requested count
     * @param result the current total cumulative requested count; ranges between [0, {@link Long#MAX_VALUE}]
     *               where the max indicates that this publisher is unbounded
     */
    void hookOnRequested(long n, long result) {
    }

    /**
     * Hooks to the finished {@link io.helidon.common.reactive.Flow.Subscription#request(long)}.
     * The intention of this method is to be able to additionally free associated resources.
     */
    void hookOnCancel() {
    }

    /**
     * Submit the data to the subscriber. The same thread is used to call
     * {@link io.helidon.common.reactive.Flow.Subscriber#onNext(Object)}. That is, the data are synchronously
     * passed to the subscriber.
     * <p>
     * Note that in order to maintain a consistency of this publisher, this method must be
     * called only once per a single permit that must be acquired by {@link #tryAcquire()}.
     *
     * @param data the chunk of data to send to the subscriber
     */
    void submit(ByteBuf data) {
        try {
            reentrantLock.lock();

            ByteBufRequestChunk chunk = new ByteBufRequestChunk(data, referenceQueue);

            if (!queue.offer(chunk)) {
                LOGGER.severe("Unable to add an element to the publisher cache.");
                error(new IllegalStateException("Unable to add an element to the publisher cache."));
                return;
            }

            if (nextCount.get() < reqCount) {
                nextCount.incrementAndGet();
                // the poll is never expected to return null
                ByteBufRequestChunk item = queue.poll();

                LOGGER.finest(() -> "Publishing request chunk: " + (null == item ? "null" : item.id()));
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
            referenceQueue.release();
        }
    }

    /**
     * Synchronously trigger {@link io.helidon.common.reactive.Flow.Subscriber#onError(Throwable)}.
     *
     * @param throwable the exception to send
     */
    void error(Throwable throwable) {
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
            referenceQueue.release();
        }
    }

    /**
     * Synchronously trigger {@link Flow.Subscriber#onComplete()}.
     */
    void complete() {
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
            referenceQueue.release();
        }
    }

    /**
     * In a non-blocking manner, try to acquire an allowance to publish next item.
     *
     * @return original number of requests on the very one associated subscriber's subscription;
     * if {@code 0} is returned, the requester didn't obtain a permit to publish
     * next item. In case a {@link Long#MAX_VALUE} is returned,
     * the requester is informed that unlimited number of items can be published.
     */
    long tryAcquire() {
        return semaphore.tryAcquire();
    }

    /**
     * Indicates that the only one possible associated subscriber has been completed.
     *
     * @return whether this publisher has successfully finished
     */
    boolean isCompleted() {
        return completed;
    }
}
