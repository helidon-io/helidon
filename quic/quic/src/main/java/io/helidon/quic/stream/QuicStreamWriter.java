/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;
import io.helidon.quic.QuicConnectionException;
import io.helidon.quic.SequentialScheduler;
import io.helidon.quic.frame.StreamFrame;
import io.helidon.quic.stream.QuicSenderStream.SendingStreamState;

/**
 * An abstract class to model a writer plugged into
 * a QuicStream to which data can be written. The data
 * is wrapped in {@link StreamFrame}
 * before being written.
 */
@Api.Internal
public abstract class QuicStreamWriter {

    // The scheduler to invoke when flow credit
    // become available.
    private final SequentialScheduler scheduler;

    /**
     * Creates a new instance of a QuicStreamWriter.
     *
     * @param scheduler A sequential scheduler that will
     *                 push data into this writer.
     */
    protected QuicStreamWriter(SequentialScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Returns the scheduler that produces data for this writer.
     *
     * @return writer scheduler
     */
    protected final SequentialScheduler scheduler() {
        return scheduler;
    }

    /**
     * Returns the sending state of the stream.
     *
     * @return sending state
     * @throws IllegalStateException if this writer is {@linkplain
     *                              QuicSenderStream#disconnectWriter(QuicStreamWriter) no longer connected}
     *                              to its stream
     * <p>Note: This method returns the state of the {@link QuicSenderStream}
     *        to which this writer is {@linkplain
     *        QuicSenderStream#connectWriter(SequentialScheduler) connected}.
     */
    public abstract SendingStreamState sendingState();

    /**
     * Pushes buffer data to be scheduled for writing on the stream.
     * The data will be wrapped in a StreamFrame before being
     * sent. Data that cannot be sent due to a lack of flow
     * credit will be buffered.
     * <p>
     * This method synchronously consumes all unread bytes from {@code buffer} and independently copies them into writer-owned
     * storage before a normal return. The writer retains neither {@code buffer} nor its backing storage after that point, so
     * the caller may mutate or otherwise reuse either without changing the submitted bytes.
     *
     * @param buffer A byte buffer to schedule for writing
     * @param last   Whether that's the last data that will be sent
     *              through this stream.
     * @throws QuicStreamException   if the stream has been reset or its output is already closed
     * @throws QuicConnectionException if the connection terminates before the submission is accepted
     * @throws IllegalStateException if this writer is {@linkplain
     *                              QuicSenderStream#disconnectWriter(QuicStreamWriter) no longer connected}
     *                              to its stream or final stream data was already submitted
     */
    public abstract void scheduleForWriting(BufferData buffer, boolean last);

    /**
     * Pushes buffer data to be scheduled for writing and returns an asynchronous receipt for this submission. The receipt
     * completes when all submitted bytes and, when {@code last} is {@code true}, the FIN marker have been handed to the
     * connection packet path. Completion does not imply that the data was acknowledged by the peer. The receipt completes
     * exceptionally with {@link QuicStreamException} if the stream is reset, or with
     * {@link QuicConnectionException} if the connection is terminated, before the complete submission is handed off.
     * This method synchronously consumes all unread bytes from {@code buffer} and independently copies them into writer-owned
     * storage before a normal return. The writer retains neither {@code buffer} nor its backing storage after that point, so
     * the caller may mutate or otherwise reuse either without changing the submitted bytes. This ownership boundary is the
     * method return, not completion of the returned receipt.
     *
     * @param buffer byte buffer to schedule
     * @param last whether this is the last stream data
     * @return asynchronous dispatch receipt
     * @throws QuicStreamException if the stream has been reset or its output is already closed
     * @throws QuicConnectionException if the connection terminates before the submission is accepted
     * @throws IllegalStateException if this writer is no longer connected to its stream or final stream data was already
     *                               submitted
     */
    public abstract CompletableFuture<Void> scheduleForWritingAndGetDispatchCompletion(BufferData buffer, boolean last);

    /**
     * Queues buffer data on the writing queue for this stream.
     * The consumer will not be woken up. More data should be submitted
     * using {@link #scheduleForWriting(BufferData, boolean)} in order
     * to wake the consumer.
     * <p>
     * This method synchronously consumes all unread bytes from {@code buffer} and independently copies them into writer-owned
     * storage before a normal return. The writer retains neither {@code buffer} nor its backing storage after that point, so
     * the caller may mutate or otherwise reuse either without changing the submitted bytes.
     *
     * @param buffer A byte buffer to schedule for writing
     * @throws QuicStreamException   if the stream has been reset or its output is already closed
     * @throws QuicConnectionException if the connection terminates before the submission is accepted
     * @throws IllegalStateException if this writer is {@linkplain
     *                              QuicSenderStream#disconnectWriter(QuicStreamWriter) no longer connected}
     *                              to its stream or final stream data was already submitted
     * <p>Note: Use this method as a hint that more data will be
     *        upcoming shortly that might be aggregated with
     *        the data being queued in order to reduce the number
     *        of packets that will be sent to the peer.
     *        This is useful when a small number of bytes
     *        need to be written to the stream before actual stream
     *        data. Typically, this can be used for writing the
     *        HTTP/3 stream type for a unidirectional HTTP/3 stream
     *        before starting to send stream data.
     */
    public abstract void queueForWriting(BufferData buffer);

    /**
     * Indicates how many bytes the writer is prepared to receive for sending.
     * This is advisory local staging capacity, not an enforced submission limit:
     * {@link #scheduleForWriting(BufferData, boolean)} may accept and buffer more data. Producers that can generate
     * arbitrary amounts of data must bound their own submissions or wait for
     * {@link #scheduleForWritingAndGetDispatchCompletion(BufferData, boolean) dispatch receipts}.
     * When that value grows from 0, and if the queue has
     * no pending data, the {@code scheduler}
     * is triggered to elicit more calls to
     * {@link #scheduleForWriting(BufferData, boolean)}.
     *
     * @return writable byte credit currently available for this stream
     * @throws IllegalStateException if this writer is {@linkplain
     *                              QuicSenderStream#disconnectWriter(QuicStreamWriter) no longer connected}
     *                              to its stream
     * <p>Note: This information is used to avoid
     *        buffering too much data while waiting for flow
     *        credit on the underlying stream. When flow credit
     *        is available, the {@code scheduler} loop is
     *        invoked to resume writing. The scheduler can then
     *        call this method to figure out how much data to
     *        request from upstream.
     */
    public abstract long credit();

    /**
     * Abruptly resets the stream. If stream output is already terminal,
     * this operation has no effect.
     *
     * @param errorCode the application error code
     * @throws IllegalStateException if this writer is {@linkplain
     *                              QuicSenderStream#disconnectWriter(QuicStreamWriter) no longer connected}
     *                              to its stream
     */
    public abstract void reset(long errorCode);

    /**
     * Returns the stream this writer is connected to.
     *
     * @return stream this writer is connected to if this writer is currently
     *         {@linkplain #connected() connected}
     */
    public abstract Optional<QuicSenderStream> stream();

    /**
     * Returns whether this writer is connected to its stream.
     *
     * @return {@code true} if this writer is connected to its stream
     * @see QuicSenderStream#connectWriter(SequentialScheduler)
     * @see QuicSenderStream#disconnectWriter(QuicStreamWriter)
     */
    public abstract boolean connected();

    /**
     * Returns whether STOP_SENDING was received.
     *
     * @return {@code true} if STOP_SENDING was received
     */
    public boolean stopSendingReceived() {
        return stream().map(QuicSenderStream::stopSendingReceived).orElse(false);
    }

}
