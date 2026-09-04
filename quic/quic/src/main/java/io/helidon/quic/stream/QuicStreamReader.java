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
import java.util.Queue;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;
import io.helidon.quic.SequentialScheduler;
import io.helidon.quic.stream.QuicReceiverStream.ReceivingStreamState;

/**
 * An abstract class to model a reader plugged into
 * a QuicStream from which data can be read.
 */
@Api.Internal
public abstract class QuicStreamReader {

    /**
     * A sentinel inserted into the queue after the FIN it has been received.
     */
    public static final BufferData EOF = BufferData.createReadOnly(BufferData.EMPTY_BYTES, 0, 0);

    // The scheduler to invoke when reader state may have changed.
    private final SequentialScheduler scheduler;

    /**
     * Creates a new instance of a QuicStreamReader.
     * The given scheduler will not be invoked until the reader
     * is {@linkplain #start() started}.
     *
     * @param scheduler a sequential scheduler that notifies the consumer that reader state may have changed
     */
    protected QuicStreamReader(SequentialScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Returns the scheduler that notifies this reader's consumer.
     *
     * @return reader scheduler
     */
    protected final SequentialScheduler scheduler() {
        return scheduler;
    }

    /**
     * Returns the receiving state of the stream.
     * This method returns the state of the {@link QuicReceiverStream}
     * to which this reader is {@linkplain
     * QuicReceiverStream#connectReader(SequentialScheduler) connected}.
     *
     * @return the receiving state of the stream
     * @throws IllegalStateException if this reader is {@linkplain
     *                              QuicReceiverStream#disconnectReader(QuicStreamReader) no longer connected}
     *                              to its stream
     */
    public abstract ReceivingStreamState receivingState();

    /**
     * Returns the BufferData at the head of the queue, if data is available.
     * If the end of the stream is
     * reached then {@link #EOF} is returned.
     *
     * <p>Polling a non-EOF buffer transfers that complete buffer to the application and marks it processed for stream and
     * connection flow control, allowing receive credit to be restored. A consumer should poll only when it is ready to process
     * another complete buffer. Receive buffers are read-only. Consuming reads advance their cursor, inspection operations do
     * not, and {@link BufferData#rewind()} remains available. Write, {@code readFrom}, reset, clear, trim, and capacity
     * operations are unsupported.
     *
     * @return the next buffer if data is available, {@link #EOF} if the end of the stream is reached,
     *        or an empty optional otherwise
     * @throws QuicStreamException   if the stream was closed locally or reset by the peer
     * @throws IllegalStateException if this reader is {@linkplain
     *                              QuicReceiverStream#disconnectReader(QuicStreamReader) no longer connected}
     *                              to its stream
     * @implSpec This method behave just like {@link Queue#poll()}.
     */
    public abstract Optional<BufferData> poll();

    /**
     * Returns the BufferData at the head of the queue, if data is available.
     * Receive buffers are read-only. Consuming reads advance their cursor, inspection operations do not, and
     * {@link BufferData#rewind()} remains available. Write, {@code readFrom}, reset, clear, trim, and capacity operations are
     * unsupported.
     *
     * @return the buffer at the head of the queue if data is available, or an empty optional otherwise
     * @throws QuicStreamException   if the stream was closed locally or reset by the peer
     * @throws IllegalStateException if this reader is {@linkplain
     *                              QuicReceiverStream#disconnectReader(QuicStreamReader) no longer connected}
     *                              to its stream
     * @implSpec This method behave just like {@link Queue#peek()}.
     */
    public abstract Optional<BufferData> peek();

    /**
     * Returns the stream this reader is connected to if this reader is currently
     * {@linkplain #connected() connected}.
     *
     * @return the connected stream, or an empty optional if this reader is not connected
     */
    public abstract Optional<QuicReceiverStream> stream();

    /**
     * Returns whether this reader is connected to its stream.
     *
     * @return true if this reader is connected to its stream
     * @see QuicReceiverStream#connectReader(SequentialScheduler)
     * @see QuicReceiverStream#disconnectReader(QuicStreamReader)
     */
    public abstract boolean connected();

    /**
     * Returns whether this reader has been {@linkplain #start() started}.
     *
     * @return true if this reader has been started
     */
    public abstract boolean started();

    /**
     * Starts the reader. The {@linkplain
     * QuicReceiverStream#connectReader(SequentialScheduler) scheduler}
     * will not be invoked until the reader is {@linkplain #start() started}.
     */
    public abstract void start();

    /**
     * Returns whether reset was received or read by this reader.
     *
     * @return whether reset was received or read by this reader
     */
    public boolean isReset() {
        return stream().orElseThrow().receivingState().isReset();
    }
}
