/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.media.common;

import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.RequestedCounter;
import io.helidon.common.reactive.RetrySchema;
import io.helidon.common.reactive.SingleSubscriberHolder;

/**
 * Publish a channel content to a single {@link Flow.Subscriber subscriber}. If channel doesn't offer data, then it is requested
 * again after some period defined be retry schema.
 * <p>
 * Only first subscriber is accepted.
 */
public class ReadableByteChannelPublisher implements Flow.Publisher<DataChunk> {

    private static final Logger LOGGER = Logger.getLogger(ReadableByteChannelPublisher.class.getName());

    private static final int DEFAULT_CHUNK_CAPACITY = 1024 * 8;

    private final ReadableByteChannel channel;
    private final RetrySchema retrySchema;

    private final SingleSubscriberHolder<DataChunk> subscriber = new SingleSubscriberHolder<>();
    private final RequestedCounter requested = new RequestedCounter();
    private final int chunkCapacity = DEFAULT_CHUNK_CAPACITY;

    private final AtomicBoolean publishing = new AtomicBoolean(false);
    private volatile AtomicInteger retryCounter = new AtomicInteger();
    private volatile long lastRetryDelay = 0;

    private ScheduledExecutorService scheduledExecutorService;
    private volatile DataChunk curentChunk;

    /**
     * Creates new instance.
     *
     * @param channel a channel to read and publish
     * @param retrySchema a retry schema functional interface used in case, that channel read retrieved zero bytes.
     */
    public ReadableByteChannelPublisher(ReadableByteChannel channel, RetrySchema retrySchema) {
        this.channel = channel;
        this.retrySchema = retrySchema;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super DataChunk> subscriberParam) {
        if (subscriber.register(subscriberParam)) {
            publishing.set(true); // prevent onNext from inside of onSubscribe

            try {
                subscriberParam.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {
                        requested.increment(n, t -> tryComplete(t));
                        tryPublish();
                    }

                    @Override
                    public void cancel() {
                        subscriber.cancel();
                    }
                });
            } finally {
                publishing.set(false);
            }

            tryPublish(); // give onNext a chance in case request has been invoked in onSubscribe
        }
    }

    private DataChunk allocateNewChunk() {
        return DataChunk.create(false, ByteBuffer.allocate(chunkCapacity));
    }

    /**
     * It publish a single item or complete or both. If next item is not yet available but it can be in the future then returns
     * {@code false} and call will be rescheduled based on {@link RetrySchema}.
     *
     * @param subscr a subscriber to publish on
     * @return {@code true} if next item was published or subscriber was completed otherwise {@code false}
     * @throws Exception if any error happens and {@code onError()} must be called on the subscriber
     */
    private boolean publishSingleOrFinish(Flow.Subscriber<? super DataChunk> subscr) throws Exception {
        DataChunk chunk;
        if (curentChunk == null) {
            chunk = allocateNewChunk();
        } else {
            chunk = curentChunk;
            curentChunk = null;
        }

        ByteBuffer bb = chunk.data()[0];
        int count = 0;
        while (bb.remaining() > 0) {
            count = channel.read(bb);
            if (count <= 0) {
                break;
            }
        }
        // Send or store
        if (bb.capacity() > bb.remaining()) {
            bb.flip();
            subscr.onNext(chunk);
        } else {
            curentChunk = chunk;
        }
        // Last or not
        if (count < 0) {
            try {
                channel.close();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Cannot close readable byte channel! (Close attempt after fully read channel.)", e);
            }
            tryComplete();
            if (curentChunk != null) {
                curentChunk.release();
            }
            return true;
        } else  {
            return count > 0;
        }
    }

    private void tryPublish() {
        boolean immediateRetry = true;
        while (immediateRetry) {
            immediateRetry = false;

            // Publish, if can
            if (!subscriber.isClosed() && requested.get() > 0 && publishing.compareAndSet(false, true)) {
                try {
                    Flow.Subscriber<? super DataChunk> sub = this.subscriber.get(); // blocking retrieval
                    while (!subscriber.isClosed() && requested.tryDecrement()) {
                        if (!publishSingleOrFinish(sub)) {
                            // Not yet published but can be done in the future
                            requested.increment(1, this::tryComplete);
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    tryComplete(e);
                } catch (Exception e) {
                    tryComplete(e);
                } finally {
                    publishing.set(false); // give a chance to some other thread to publish
                }

                // Execute in different thread if needed
                if (!subscriber.isClosed() && requested.get() > 0) {
                    long nextDelay = retrySchema.nextDelay(retryCounter.getAndIncrement(), lastRetryDelay);
                    lastRetryDelay = nextDelay;
                    if (nextDelay < 0) {
                        tryComplete(new TimeoutException("Wait for the next item timeout!"));
                    } else if (nextDelay == 0) {
                        immediateRetry = true;
                    } else {
                        planNextTry(nextDelay);
                    }
                }
            }
        }
    }

    private synchronized void planNextTry(long afterMillis) {
        if (scheduledExecutorService == null) {
            scheduledExecutorService = Executors.newScheduledThreadPool(1);
        }
        scheduledExecutorService.schedule(this::tryPublish, afterMillis, TimeUnit.MILLISECONDS);
    }

    private void tryComplete() {
        subscriber.close(Flow.Subscriber::onComplete);
    }

    private void tryComplete(Throwable t) {
        subscriber.close(sub -> sub.onError(t));
    }

}
