/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.quic.stream;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.quic.stream.QuicSenderStream.SendingStreamState;

/**
 * A class to handle the writing queue of a {@link QuicSenderStream}.
 * This class maintains a queue of byte buffer containing stream data
 * that has not yet been packaged for sending. It also keeps track of
 * the max stream data value.
 * It acts as a mailbox between a producer (typically a {@link QuicStreamWriter}),
 * and a consumer (typically a {@link io.helidon.quic.QuicConnectionImpl}).
 * This class is abstract: a concrete implementation of this class must only
 * implement {@link #wakeupProducer()} and {@link #wakeupConsumer()} which should
 * wake up the producer and consumer respectively, when data can be polled or
 * submitted from the queue.
 */
abstract class StreamWriterQueue {
    // The queue to buffer data before it's polled by the consumer
    private final ConcurrentLinkedQueue<ByteBuffer> queue = new ConcurrentLinkedQueue<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final List<DispatchWaiter> dispatchWaiters = new ArrayList<>();
    private final int bufferSize;
    // The current buffer containing data to send.
    private ByteBuffer current;
    // The offset of the data that has been consumed
    private volatile long bytesConsumed;
    // The offset of the data that has been supplied by the
    // producer.
    // bytesProduced >= bytesConsumed at all times.
    private volatile long bytesProduced;
    // The stream size, when known, -1 otherwise.
    // The stream size may be known at the creation of the stream,
    // or at the latest when the last ByteBuffer is provided by
    // the producer.
    private volatile long streamSize = -1;
    // true if reset was requested, false otherwise
    private volatile boolean resetRequested;
    private boolean endOfStreamConsumed;
    private long bytesDispatched;
    private boolean endOfStreamDispatched;
    // The maximum offset that will be accepted by the peer at this
    // time. bytesConsumed <= maxStreamData at all times.
    private volatile long maxStreamData;
    // negative if stop sending was received; contains -(errorCode + 1)
    private volatile long stopSending;
    private Throwable dispatchFailure;

    protected StreamWriterQueue(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    /**
     * This method is called by the consumer to poll data from the stream
     * queue. This method will return a {@code ByteBuffer} with at most
     * {@code maxbytes} remaining bytes. The {@code ByteBuffer} may contain
     * less bytes if not enough bytes are available, or if there is not
     * enough {@linkplain #consumerCredit() credit} to send {@code maxbytes}
     * to the peer. Only stream credit is taken into account. Taking into
     * account connection credit is the responsibility of the caller.
     * If there is no credit, or if there is no data available, {@code null}
     * is returned. An empty final buffer can be consumed without flow-control
     * credit so the FIN marker can be dispatched. When credit and data are
     * available again, {@link #wakeupConsumer()} is called to wake up the consumer.
     *
     * @param maxbytes the maximum number of bytes the consumer is prepared
     *                to consume.
     * @return a {@code ByteBuffer} containing at most {@code maxbytes}, or {@code null}
     *        if no data is available or data is blocked by flow control.
     * <p>Note: This method increases the consumer offset. It must not be called concurrently
     *        by two different threads.
     * @implNote If the producer was blocked due to full buffer before this method was called
     *        and the method removes enough of buffered data,
     *        {@link #wakeupProducer()} is called.
     */
    public final ByteBuffer poll(int maxbytes) {
        boolean producerWasBlocked;
        boolean producerUnblocked;
        long produced;
        long consumed;
        ByteBuffer buffer;
        long credit = consumerCredit();
        if (credit < maxbytes) {
            maxbytes = (int) credit;
        }
        if (maxbytes < 0) {
            return null;
        }
        lock();
        try {
            producerWasBlocked = producerBlocked();
            buffer = current;
            if (buffer == null) {
                buffer = queue.poll();
                current = buffer;
            }
            if (buffer == null) {
                return null;
            }
            int remaining = buffer.remaining();
            if (maxbytes == 0 && remaining > 0) {
                return null;
            }
            int position = buffer.position();
            consumed = bytesConsumed;
            if (remaining <= maxbytes) {
                current = queue.poll();
                consumed = Math.addExact(consumed, remaining);
                bytesConsumed = consumed;
            } else {
                buffer = buffer.slice(position, maxbytes);
                current.position(position + maxbytes);
                consumed = Math.addExact(consumed, maxbytes);
                bytesConsumed = consumed;
            }
            long size = streamSize;
            produced = bytesProduced;
            producerUnblocked = producerWasBlocked && !producerBlocked();
            if (size >= 0 && consumed == size) {
                endOfStreamConsumed = true;
                switchState(SendingStreamState.DATA_SENT);
            }
        } finally {
            unlock();
        }
        if (producerUnblocked) {
            logDebug("producer unblocked produced:%s, consumed:%s", produced, consumed);
            wakeupProducer();
        }
        return buffer;
    }

    /**
     * Records stream data that was handed to the connection packet path.
     *
     * @param offset exclusive end offset handed off for dispatch
     * @param endOfStream whether the dispatched frame carries FIN
     */
    final void markDispatched(long offset, boolean endOfStream) {
        List<CompletableFuture<Void>> dispatched = null;
        lock();
        try {
            bytesDispatched = Math.max(bytesDispatched, offset);
            endOfStreamDispatched |= endOfStream;
            if (!dispatchWaiters.isEmpty()) {
                Iterator<DispatchWaiter> iterator = dispatchWaiters.iterator();
                while (iterator.hasNext()) {
                    DispatchWaiter waiter = iterator.next();
                    if (waiter.offset() < bytesDispatched
                            || (waiter.offset() == bytesDispatched
                                    && (!waiter.endOfStream() || endOfStreamDispatched))) {
                        if (dispatched == null) {
                            dispatched = new ArrayList<>();
                        }
                        dispatched.add(waiter.completion());
                        iterator.remove();
                    }
                }
            }
        } finally {
            unlock();
        }
        if (dispatched != null) {
            dispatched.forEach(completion -> completion.complete(null));
        }
    }

    /**
     * Returns whether the queue has a FIN marker that needs no flow-control credit.
     *
     * @return whether a zero-length FIN is pending
     */
    final boolean hasPendingZeroLengthEndOfStream() {
        lock();
        try {
            return streamSize >= 0 && bytesConsumed == streamSize && !endOfStreamConsumed;
        } finally {
            unlock();
        }
    }

    /**
     * Updates the flow control credit for this queue.
     * The maximum offset that will be accepted by the consumer
     * can only increase. Value that are less or equal to the
     * current value of the max stream data are ignored.
     *
     * @param data the maximum offset that will be accepted by
     *            the consumer
     * @return the maximum offset that will be accepted by the
     *        consumer.
     * @implSpec If the consumer was blocked due to flow control before
     *        this method was called, and the new value of the max
     *        stream data allows to unblock the consumer, and data
     *        is available, {@link #wakeupConsumer()} is called.
     */
    public final long updateMaxStreamData(long data) {
        long max;
        long produced;
        long consumed;
        boolean consumerWasBlocked;
        boolean consumerUnblocked;
        lock();
        try {
            max = maxStreamData;
            consumed = bytesConsumed;
            produced = bytesProduced;
            consumerWasBlocked = consumerBlocked();
            if (data <= max) {
                return max;
            }
            max = data;
            maxStreamData = max;
            consumerUnblocked = consumerWasBlocked && !consumerBlocked();
        } finally {
            unlock();
        }
        logDebug("set max stream data: %s", max);
        if (consumerUnblocked && produced > 0) {
            logDebug("consumer unblocked produced:%s, consumed:%s, max stream data:%s", produced, consumed, max);
            wakeupConsumer();
        }
        return max;
    }

    /**
     * Whether the producer is blocked due to flow control.
     *
     * @return whether the producer is blocked due to full buffers
     */
    public final boolean producerBlocked() {
        return producerCredit() <= 0;
    }

    /**
     * Whether the consumer is blocked due to flow control.
     *
     * @return whether the producer is blocked due to flow control
     */
    public final boolean consumerBlocked() {
        return consumerCredit() <= 0;
    }

    /**
     * {@return the offset of the data consumed by the consumer}
     *
     * <p>Note: The returned value is only weakly consistent: it is subject
     *        to race conditions if {@link #poll(int)} is called concurrently
     *        by another thread.
     */
    public final long bytesConsumed() {
        return bytesConsumed;
    }

    /**
     * {@return the offset of the data provided by the producer}
     *
     * <p>Note: The returned value is only weakly consistent: it is subject
     *        to race conditions if {@link #submit(ByteBuffer, boolean)}
     *        or {@link #queue(ByteBuffer)} are called concurrently
     *        by another thread.
     */
    public final long bytesProduced() {
        return bytesProduced;
    }

    /**
     * {@return the amount of produced data which has not been consumed yet}
     * This is independent of flow control.
     *
     * <p>Note: The returned value is only weakly consistent: it is subject
     *        to race conditions if {@link #submit(ByteBuffer, boolean)}
     *        or {@link #queue(ByteBuffer)} or
     *        {@link #poll(int)} are called concurrently
     *        by another thread.
     */
    public final long available() {
        return bytesProduced - bytesConsumed;
    }

    /**
     * {@return the stream size if known, {@code -1} otherwise}
     *
     * <p>Note: The returned value is only weakly consistent: it is subject
     *        to race conditions if {@link #submit(ByteBuffer, boolean)}
     *        is called concurrently by another thread.
     */
    public final long streamSize() {
        return streamSize;
    }

    /**
     * {@return the maximum offset that the peer is prepared to accept}
     *
     * <p>Note: The returned value is only weakly consistent: it is subject
     *        to race conditions if {@link #updateMaxStreamData(long)} is called
     *        concurrently by another thread.
     */
    public final long maxStreamData() {
        return maxStreamData;
    }

    /**
     * {@return {@code true} if the consumer has reached the end of
     * this stream (equivalent to EOF)}
     * This is independent of flow control.
     *
     * <p>Note: The returned value is only weakly consistent: it is subject
     *        to race conditions if {@link #submit(ByteBuffer, boolean)}
     *        or {@link #poll(int)} are called concurrently
     *        by another thread.
     */
    public final boolean isConsumerDone() {
        long size = streamSize;
        long consumed = bytesConsumed;
        return size >= 0 && size <= consumed;
    }

    /**
     * {@return {@code true} if the producer has reached the end of
     * this stream (equivalent to EOF)}
     * This is independent of flow control.
     *
     * <p>Note: The returned value is only weakly consistent: it is subject
     *        to race conditions if {@link #submit(ByteBuffer, boolean)}
     *        is called concurrently by another thread.
     */
    public final boolean isProducerDone() {
        return streamSize >= 0;
    }

    /**
     * This method is called by the producer to submit data to this
     * stream. The producer should not modify the provided buffer
     * after this point. The provided buffer will be queued even if
     * the produced data exceeds the maximum offset that the peer
     * is prepared to accept.
     *
     * @param buffer a buffer containing data for the stream
     * @param last   whether this is the last buffer that will ever be
     *              provided by the provided
     * @throws QuicStreamException   if the stream was reset by peer
     * @throws IllegalStateException if the last data was submitted already
     * <p>Note: If sufficient credit is available, this method will wake
     *        up the consumer.
     */
    public final void submit(ByteBuffer buffer, boolean last) {
        offer(buffer, last, false, false);
    }

    /**
     * Submits data and returns an asynchronous receipt for this submission. The receipt completes when all submitted bytes and,
     * when {@code last} is {@code true}, the FIN marker have been handed to the connection packet path. Completion does not imply
     * peer acknowledgement. The receipt completes exceptionally if the stream is reset or the connection is terminated before
     * the complete submission is handed off.
     *
     * @param buffer data to submit
     * @param last whether this is the last data for the stream
     * @return asynchronous dispatch receipt
     * @throws QuicStreamException if the stream cannot accept the data
     */
    public final CompletableFuture<Void> submitAndGetDispatchCompletion(ByteBuffer buffer, boolean last) {
        DispatchTarget target = offer(buffer, last, false, true);
        if (target.failure() != null) {
            return CompletableFuture.failedFuture(target.failure());
        }
        lock();
        try {
            if (bytesDispatched > target.offset()
                    || (bytesDispatched == target.offset()
                            && (!target.endOfStream() || endOfStreamDispatched))) {
                return CompletableFuture.completedFuture(null);
            }
            if (dispatchFailure != null) {
                return CompletableFuture.failedFuture(dispatchFailure);
            }
            CompletableFuture<Void> completion = new CompletableFuture<>();
            dispatchWaiters.add(new DispatchWaiter(target.offset(), target.endOfStream(), completion));
            return completion;
        } finally {
            unlock();
        }
    }

    /**
     * This method is called by the producer to queue data to this
     * stream. The producer should not modify the provided buffer
     * after this point. The provided buffer will be queued even if
     * the produced data exceeds the maximum offset that the peer
     * is prepared to accept.
     *
     * @param buffer a buffer containing data for the stream
     * @throws QuicStreamException   if the stream was reset by peer
     * @throws IllegalStateException if the last data was submitted already
     * <p>Note: The consumer will not be woken, even if enough credit is
     *        available. More data should be submitted using
     *        {@link #submit(ByteBuffer, boolean)} in order to wake up the consumer.
     */
    public final void queue(ByteBuffer buffer) {
        offer(buffer, false, true, false);
    }

    /**
     * {@return the credit of the producer}
     *
     * @implSpec this is the desired buffer size minus the amount of data already buffered.
     */
    public final long producerCredit() {
        lock();
        try {
            return bufferSize - available();
        } finally {
            unlock();
        }
    }

    /**
     * {@return the credit of the consumer}
     *
     * @implSpec This is equivalent to {@link #maxStreamData()} - {@link #bytesConsumed()}.
     */
    public final long consumerCredit() {
        lock();
        try {
            return maxStreamData - bytesConsumed;
        } finally {
            unlock();
        }
    }

    /**
     * {@return the amount of available data that can be sent
     * with respect to flow control in this stream}.
     * This does not take into account the global connection
     * flow control.
     */
    public final long readyToSend() {
        long consumed;
        long produced;
        long max;
        lock();
        try {
            consumed = bytesConsumed;
            max = maxStreamData;
            produced = bytesProduced;
        } finally {
            unlock();
        }
        return Math.min(max - consumed, produced - consumed);
    }

    public final void markReset(long errorCode) {
        List<CompletableFuture<Void>> pending;
        Throwable failure;
        lock();
        try {
            resetRequested = true;
            if (dispatchFailure == null) {
                dispatchFailure = stopSending < 0
                        ? QuicStreamException.stopSending(streamId(), -stopSending - 1)
                        : QuicStreamException.resetLocally(streamId(), errorCode);
            }
            failure = dispatchFailure;
            pending = dispatchWaiters.stream().map(DispatchWaiter::completion).toList();
            dispatchWaiters.clear();
        } finally {
            unlock();
        }
        pending.forEach(completion -> completion.completeExceptionally(failure));
    }

    protected final void lock() {
        lock.lock();
    }

    protected final void unlock() {
        lock.unlock();
    }

    protected abstract void logDebug(String format, Object... arguments);

    final void close(Throwable cause) {
        List<CompletableFuture<Void>> pending;
        lock();
        try {
            dispatchFailure = cause;
            bytesProduced = bytesConsumed;
            queue.clear();
            current = null;
            pending = dispatchWaiters.stream().map(DispatchWaiter::completion).toList();
            dispatchWaiters.clear();
        } finally {
            unlock();
        }
        pending.forEach(completion -> completion.completeExceptionally(cause));
    }

    /**
     * Called when a stop sending frame is received for this stream.
     *
     * @param errorCode the error code
     */
    protected final boolean stopSending(long errorCode) {
        lock();
        try {
            if (resetRequested) {
                return false;
            }
            if (streamSize >= 0 && bytesConsumed == streamSize) {
                return false;
            }
            if (this.stopSending < 0) {
                return false;
            }
            this.stopSending = -(errorCode + 1);
        } finally {
            unlock();
        }
        return true;
    }

    /**
     * {@return -1 minus the error code that was supplied by the peer
     * when requesting for stop sending}
     *
     * <p>Note: a strictly negative value indicates that the stream was
     *        reset by the peer. The error code supplied by the peer
     *        can be obtained with the formula: <pre>{@code
     *           long errorCode = - (resetByPeer() + 1);
     *           }</pre>
     */
    final long resetByPeer() {
        return stopSending;
    }

    /**
     * This method is called to wake up the consumer when there is
     * credit and data available for the consumer.
     */
    protected abstract void wakeupConsumer();

    /**
     * This method is called to wake up the producer when there is
     * credit available for the producer.
     */
    protected abstract void wakeupProducer();

    /**
     * Called to switch the sending state when data has been sent.
     *
     * @param dataSent the new state - typically {@link SendingStreamState#DATA_SENT}
     */
    protected abstract void switchState(SendingStreamState dataSent);

    /**
     * {@return the stream id this queue was created for}
     */
    protected abstract long streamId();

    /**
     * Queues a buffer in the writing queue.
     *
     * @param buffer      the buffer to queue
     * @param last        whether this is the last data for the stream
     * @param waitForMore whether we should wait for the next submission before
     *                   waking up the consumer
     * @throws QuicStreamException   if the stream was reset by peer
     * @throws IllegalStateException if the last data was submitted already
     */
    private DispatchTarget offer(ByteBuffer buffer,
                                 boolean last,
                                 boolean waitForMore,
                                 boolean dispatchReceipt) {
        long length = buffer.remaining();
        long consumed;
        long produced;
        long max;
        boolean wakeupConsumer;
        lock();
        try {
            if (dispatchReceipt && dispatchFailure != null) {
                return new DispatchTarget(bytesProduced, last, dispatchFailure);
            }
            long stopSending = this.stopSending;
            if (stopSending < 0) {
                QuicStreamException failure = QuicStreamException.stopSending(streamId(), -stopSending - 1);
                if (dispatchReceipt) {
                    return new DispatchTarget(bytesProduced, last, failure);
                }
                throw failure;
            }
            if (resetRequested) {
                if (dispatchReceipt) {
                    return new DispatchTarget(bytesProduced, last, dispatchFailure);
                }
                return null;
            }
            if (streamSize >= 0) {
                throw new IllegalStateException("Too many bytes provided");
            }
            consumed = bytesConsumed;
            max = maxStreamData;
            produced = Math.addExact(bytesProduced, length);
            bytesProduced = produced;
            if (length > 0 || last) {
                // allow to queue a zero-length buffer if it's the last.
                queue.offer(buffer);
            }
            if (last) {
                streamSize = produced;
            }
            wakeupConsumer = consumed < max && consumed < produced
                    || consumed == produced && last;
        } finally {
            unlock();
        }
        if (wakeupConsumer && !waitForMore) {
            logDebug("consumer unblocked produced:%s, consumed:%s, max stream data:%s", produced, consumed, max);
            wakeupConsumer();
        }
        return dispatchReceipt ? new DispatchTarget(produced, last, null) : null;
    }

    private record DispatchTarget(long offset, boolean endOfStream, Throwable failure) {
    }

    private record DispatchWaiter(long offset, boolean endOfStream, CompletableFuture<Void> completion) {
    }

}
