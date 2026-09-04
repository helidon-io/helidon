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

import io.helidon.common.Api;
import io.helidon.quic.SequentialScheduler;

/**
 * An interface that represents the receiving part of a stream.
 * <p> From RFC 9000:
 * <blockquote>
 * On the receiving part of a stream, an application protocol can:
 * <ul>
 *  <li> read data; and </li>
 *  <li> abort reading of the stream and request closure, possibly
 *       resulting in a STOP_SENDING frame (Section 19.5). </li>
 * </ul>
 * </blockquote>
 */
@Api.Internal
public non-sealed interface QuicReceiverStream extends QuicStream {

    /**
     * Returns the receiving state of the stream.
     *
     * @return receiving state
     */
    ReceivingStreamState receivingState();

    /**
     * Connects an {@linkplain QuicStreamReader#started() unstarted} reader
     * to the receiver end of this stream.
     *
     * @param scheduler a sequential scheduler invoked after the reader is started when data, end-of-stream, reset,
     *                  cancellation, or connection termination may have changed the reader state
     * @return a {@code QuicStreamReader} to read data from this
     *        stream.
     * @throws IllegalStateException if a reader is already connected.
     */
    QuicStreamReader connectReader(SequentialScheduler scheduler);

    /**
     * Disconnect the reader, so that a new reader can be connected.
     *
     * @param reader the reader to be disconnected
     * @throws IllegalStateException if the given reader is not currently
     *                              connected to the stream
     * <p>Note: This can be useful for handing the stream over after having read
     *        or peeked at some bytes.
     */
    void disconnectReader(QuicStreamReader reader);

    /**
     * Cancels the reading side of this stream by sending
     * a STOP_SENDING frame.
     *
     * @param errorCode the application error code
     *
     */
    void requestStopSending(long errorCode);

    /**
     * Returns the amount of data that has been received so far.
     *
     * <p>Note: This may include data that has not been read by the
     *        application yet, but does not count any data that may have
     *        been received twice.
     *
     * @return amount of data that has been received so far
     */
    long dataReceived();

    /**
     * Returns the maximum amount of data that can be received on this stream.
     *
     * <p>Note: This corresponds to the maximum amount of data that
     *        the peer has been allowed to send.
     *
     * @return maximum amount of data that can be received on this stream
     */
    long maxStreamData();

    /**
     * Returns the error code for this stream.
     *
     * @return error code for this stream, or {@code -1}
     */
    long rcvErrorCode();

    /**
     * Returns whether a STOP_SENDING request was already issued for this stream.
     *
     * @return {@code true} when a STOP_SENDING request was already issued for this stream
     */
    default boolean isStopSendingRequested() {
        return false;
    }

    @Override
    default boolean hasError() {
        return rcvErrorCode() >= 0;
    }

    /**
     * An enum that models the state of the receiving part of a stream.
     */
    enum ReceivingStreamState implements QuicStream.StreamState {
        /**
         * The initial state for the receiving part of a
         * stream is "Recv".
         * <p>
         * In the "Recv" state, the endpoint receives STREAM
         * and STREAM_DATA_BLOCKED frames. Incoming data is buffered
         * and can be reassembled into the correct order for delivery
         * to the application. As data is consumed by the application
         * and buffer space becomes available, the endpoint sends
         * MAX_STREAM_DATA frames to allow the peer to send more data.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        RECV,
        /**
         * When a STREAM frame with a FIN bit is received, the final size of
         * the stream is known; see Section 4.5. The receiving part of the
         * stream then enters the "Size Known" state. In this state, the
         * endpoint no longer needs to send MAX_STREAM_DATA frames; it only
         * receives any retransmissions of stream data.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        SIZE_KNOWN,
        /**
         * Once all data for the stream has been received, the receiving part
         * enters the "Data Recvd" state. This might happen as a result of
         * receiving the same STREAM frame that causes the transition to
         * "Size Known". After all data has been received, any STREAM or
         * STREAM_DATA_BLOCKED frames for the stream can be discarded.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        DATA_RECVD,
        /**
         * The "Data Recvd" state persists until stream data has been delivered
         * to the application. Once stream data has been delivered, the stream
         * enters the "Data Read" state, which is a terminal state.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        DATA_READ,
        /**
         * Receiving a RESET_STREAM frame in the "Recv" or "Size Known" state
         * causes the stream to enter the "Reset Recvd" state. This might
         * cause the delivery of stream data to the application to be
         * interrupted.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        RESET_RECVD,
        /**
         * Once the application receives the signal indicating that the
         * stream was reset, the receiving part of the stream transitions to
         * the "Reset Read" state, which is a terminal state.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        RESET_READ;

        @Override
        public boolean isTerminal() {
            return this == DATA_READ || this == RESET_READ;
        }

        /**
         * Returns whether this state indicates that the stream has been reset by the sender.
         *
         * @return {@code true} if this state indicates that the stream has been reset by the sender
         */
        public boolean isReset() {
            return this == RESET_RECVD || this == RESET_READ;
        }
    }
}
