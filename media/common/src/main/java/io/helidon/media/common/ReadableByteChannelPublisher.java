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

import io.helidon.common.LazyValue;
import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.RequestedCounter;
import io.helidon.common.reactive.RetrySchema;
import io.helidon.common.reactive.SingleSubscriberHolder;

/**
 * Publish a channel content to a single {@link Flow.Subscriber subscriber}. If channel doesn't offer data, then it is requested
 * again after some period defined be retry schema.
 * <p>
 * Only first subscriber is accepted.
 *
 * @deprecated Will be removed. Please use {@link io.helidon.common.reactive.IoMulti} methods instead
 */
@Deprecated(since = "2.0.0", forRemoval = true)
class ReadableByteChannelPublisher implements Flow.Publisher<DataChunk> {

    private static final Logger LOGGER = Logger.getLogger(ReadableByteChannelPublisher.class.getName());

    private static final int DEFAULT_CHUNK_CAPACITY = 1024 * 8;

    private final ReadableByteChannel channel;
    private final RetrySchema retrySchema;
    private final boolean externalExecutor;
    private final int chunkCapacity;
    private final LazyValue<ScheduledExecutorService> executor;

    private final SingleSubscriberHolder<DataChunk> subscriber = new SingleSubscriberHolder<>();
    private final RequestedCounter requested = new RequestedCounter();
    private final AtomicBoolean publishing = new AtomicBoolean(false);
    private final AtomicInteger retryCounter = new AtomicInteger();

    private volatile long lastRetryDelay = 0;
    private volatile DataChunk currentChunk;

    /**
     * Creates new instance.
     *
     * @param channel a channel to read and publish
     * @param retrySchema a retry schema functional interface used in case, that channel read retrieved zero bytes.
     * @deprecated please use {@link #builder(java.nio.channels.ReadableByteChannel)} or
     *   {@link #create(java.nio.channels.ReadableByteChannel)} instead
     *
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    public ReadableByteChannelPublisher(ReadableByteChannel channel, RetrySchema retrySchema) {
        this.channel = channel;
        this.retrySchema = retrySchema;
        this.executor = LazyValue.create(() -> Executors.newScheduledThreadPool(1));
        this.externalExecutor = false;
        this.chunkCapacity = DEFAULT_CHUNK_CAPACITY;
    }

    private ReadableByteChannelPublisher(Builder builder) {
        this.channel = builder.theChannel;
        this.retrySchema = builder.retrySchema;
        this.executor = builder.executor;
        this.externalExecutor = builder.externalExecutor;
        this.chunkCapacity = builder.bufferCapacity;
    }

    /**
     * Create a a new instance with default retry schema and executor service.
     *
     * @param channel channel to read and publish from
     * @return a new publisher for the channel
     */
    public static ReadableByteChannelPublisher create(ReadableByteChannel channel) {
        return builder(channel).build();
    }

    /**
     * Create a new builder to configure the publisher.
     *
     * @param channel channel to read data from
     * @return a new builder instance
     */
    public static Builder builder(ReadableByteChannel channel) {
        return new Builder(channel);
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
                        closeExecutor();
                    }
                });
            } finally {
                publishing.set(false);
            }

            tryPublish(); // give onNext a chance in case request has been invoked in onSubscribe
        }
    }

    // for tests
    LazyValue<ScheduledExecutorService> executor() {
        return executor;
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
        if (currentChunk == null) {
            chunk = allocateNewChunk();
        } else {
            chunk = currentChunk;
            currentChunk = null;
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
            currentChunk = chunk;
        }
        // Last or not
        if (count < 0) {
            try {
                channel.close();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Cannot close readable byte channel! (Close attempt after fully read channel.)", e);
            }
            tryComplete();
            if (currentChunk != null) {
                currentChunk.release();
            }
            return true;
        } else {
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
        executor.get().schedule(this::tryPublish, afterMillis, TimeUnit.MILLISECONDS);
    }

    private void tryComplete() {
        subscriber.close(Flow.Subscriber::onComplete);
        closeExecutor();
    }

    private void tryComplete(Throwable t) {
        subscriber.close(sub -> sub.onError(t));
        closeExecutor();
    }

    private synchronized void closeExecutor() {
        if (!externalExecutor && executor.isLoaded()) {
            executor.get().shutdownNow();
        }
    }

    /**
     * Fluent API builder for {@link io.helidon.media.common.ReadableByteChannelPublisher}.
     * <p>
     * Obtain an instance using
     * {@link io.helidon.media.common.ReadableByteChannelPublisher#builder(java.nio.channels.ReadableByteChannel)}
     */
    public static final class Builder implements io.helidon.common.Builder<ReadableByteChannelPublisher> {
        private final ReadableByteChannel theChannel;

        private LazyValue<ScheduledExecutorService> executor = LazyValue.create(() -> Executors.newScheduledThreadPool(1));
        private RetrySchema retrySchema;
        private boolean externalExecutor;
        private int bufferCapacity = DEFAULT_CHUNK_CAPACITY;

        private Builder(ReadableByteChannel theChannel) {
            this.theChannel = theChannel;
        }

        @Override
        public ReadableByteChannelPublisher build() {
            if (null == retrySchema) {
                retrySchema = RetrySchema.linear(0, 10, 250);
            }

            return new ReadableByteChannelPublisher(this);
        }

        /**
         * Configure executor service to use for scheduling reads from the channel.
         * If an executor is configured using this method, it will not be terminated when the publisher completes.
         *
         * @param executor to use for scheduling
         * @return updated builder instance
         */
        public Builder executor(ScheduledExecutorService executor) {
            this.executor = LazyValue.create(executor);
            this.externalExecutor = true;
            return this;
        }

        /**
         * Retry schema to use when reading from the channel.
         * If a channel read fails (e.g. no data is read), the read is scheduled using
         * {@link #executor} using the provided retry schema, to prolong the delays between retries.
         * <p>
         * By default the first delay is {@code 0} milliseconds, incrementing by {@code 50 milliseconds} up
         * to {@code 250} milliseconds.
         *
         * @param retrySchema schema to use
         * @return updated builder instance
         */
        public Builder retrySchema(RetrySchema retrySchema) {
            this.retrySchema = retrySchema;
            return this;
        }

        /**
         * Capacity of byte buffer used to create {@link io.helidon.common.http.DataChunk data chunks}.
         *
         * @param bufferCapacity capacity of the buffer, defaults to 8 Kb
         * @return updated builder instance
         */
        public Builder bufferCapacity(int bufferCapacity) {
            this.bufferCapacity = bufferCapacity;
            return this;
        }
    }
}
