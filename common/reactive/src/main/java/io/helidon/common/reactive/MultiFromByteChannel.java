/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.LazyValue;

/**
 * This class is copied from media support, to be refactored later.
 * This is to have the implementation in correct module.
 */
class MultiFromByteChannel implements Multi<ByteBuffer> {
    private static final Logger LOGGER = Logger.getLogger(MultiFromByteChannel.class.getName());

    private final ReadableByteChannel channel;
    private final RetrySchema retrySchema;
    private final boolean externalExecutor;
    private final int chunkCapacity;
    private final LazyValue<ScheduledExecutorService> executor;

    private final SingleSubscriberHolder<ByteBuffer> subscriber = new SingleSubscriberHolder<>();
    private final RequestedCounter requested = new RequestedCounter();
    private final AtomicBoolean publishing = new AtomicBoolean(false);
    private final AtomicInteger retryCounter = new AtomicInteger();

    private volatile long lastRetryDelay = 0;
    private volatile ByteBuffer currentBuffer;

    MultiFromByteChannel(IoMulti.MultiFromByteChannelBuilder builder) {
        this.channel = builder.theChannel();
        this.retrySchema = builder.retrySchema();
        this.executor = builder.executor();
        this.externalExecutor = builder.isExternalExecutor();
        this.chunkCapacity = builder.bufferCapacity();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriberParam) {
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

    private ByteBuffer allocateNewBuffer() {
        return ByteBuffer.allocate(chunkCapacity);
    }

    /**
     * It publish a single item or complete or both. If next item is not yet available but it can be in the future then returns
     * {@code false} and call will be rescheduled based on {@link RetrySchema}.
     *
     * @param subscr a subscriber to publish on
     * @return {@code true} if next item was published or subscriber was completed otherwise {@code false}
     * @throws Exception if any error happens and {@code onError()} must be called on the subscriber
     */
    private boolean publishSingleOrFinish(Flow.Subscriber<? super ByteBuffer> subscr) throws Exception {
        ByteBuffer bb;
        if (currentBuffer == null) {
            bb = allocateNewBuffer();
        } else {
            bb = currentBuffer;
            currentBuffer = null;
        }

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
            subscr.onNext(bb);
        } else {
            currentBuffer = bb;
        }
        // Last or not
        if (count < 0) {
            try {
                channel.close();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Cannot close readable byte channel! (Close attempt after fully read channel.)", e);
            }
            tryComplete();
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
                    Flow.Subscriber<? super ByteBuffer> sub = this.subscriber.get(); // blocking retrieval
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
}
