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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.common.Api;
import io.helidon.quic.SequentialScheduler;

/**
 * An interface that represents the sending part of a stream.
 * <p> From RFC 9000:
 * <blockquote>
 * On the sending part of a stream, an application protocol can:
 * <ul>
 *  <li> write data, understanding when stream flow control credit
 *       (Section 4.1) has successfully been reserved to send the
 *       written data; </li>
 *  <li> end the stream (clean termination), resulting in a STREAM frame
 *       (Section 19.8) with the FIN bit set; and </li>
 *  <li> reset the stream (abrupt termination), resulting in a RESET_STREAM
 *       frame (Section 19.4) if the stream was not already in a terminal
 *       state. </li>
 * </ul>
 * </blockquote>
 */
@Api.Internal
public non-sealed interface QuicSenderStream extends QuicStream {

    /**
     * Returns the sending state of the stream.
     *
     * @return sending state
     */
    SendingStreamState sendingState();

    /**
     * Connects a writer to the sending end of this stream.
     *
     * @param scheduler A sequential scheduler that will
     *                 push data on the returned  {@linkplain
     *                 QuicStreamWriter#QuicStreamWriter(SequentialScheduler)
     *                 writer}.
     * @return a {@code QuicStreamWriter} to write data to this
     *        stream.
     * @throws IllegalStateException if a writer is already connected.
     */
    QuicStreamWriter connectWriter(SequentialScheduler scheduler);

    /**
     * Disconnect the writer, so that a new writer can be connected.
     *
     * @param writer the writer to be disconnected
     * @throws IllegalStateException if the given writer is not currently
     *                              connected to the stream
     * <p>Note: This can be useful for handing the stream over after having written
     *        some bytes.
     */
    void disconnectWriter(QuicStreamWriter writer);

    /**
     * Abruptly closes the writing side of a stream by sending
     * a RESET_STREAM frame. If stream output is already terminal,
     * this operation has no effect.
     *
     * @param errorCode the application error code
     */
    void reset(long errorCode);

    /**
     * Returns the amount of data that has been sent.
     *
     * <p>Note: This may include data that has not been acknowledged.
     *
     * @return amount of data that has been sent
     */
    long dataSent();

    /**
     * Returns the error code for this stream.
     *
     * @return error code for this stream, or {@code -1}
     */
    long sndErrorCode();

    /**
     * Returns whether STOP_SENDING was received.
     *
     * @return {@code true} if STOP_SENDING was received
     */
    boolean stopSendingReceived();

    /**
     * Returns a stage that completes with the application error code when the peer requests that this endpoint
     * stop sending on the stream. The stage completes exceptionally if the connection terminates before that signal
     * is received.
     *
     * @return peer STOP_SENDING observation
     */
    CompletionStage<Long> whenStopSendingReceived();

    /**
     * Returns a future that completes when the sending side reaches a terminal state or completes exceptionally
     * if the connection terminates before that happens.
     *
     * @return sending-completion future
     */
    CompletableFuture<SendingStreamState> futureSendingCompletion();

    @Override
    default boolean hasError() {
        return sndErrorCode() >= 0;
    }

    /**
     * An enum that models the state of the sending part of a stream.
     */
    enum SendingStreamState implements QuicStream.StreamState {
        /**
         * The "Ready" state represents a newly created stream that is able
         * to accept data from the application. Stream data might be
         * buffered in this state in preparation for sending.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        READY,
        /**
         * In the "Send" state, an endpoint transmits -- and retransmits as
         * necessary -- stream data in STREAM frames. The endpoint respects
         * the flow control limits set by its peer and continues to accept
         * and process MAX_STREAM_DATA frames. An endpoint in the "Send" state
         * generates STREAM_DATA_BLOCKED frames if it is blocked from sending
         * by stream flow control limits (Section 4.1).
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        SEND,
        /**
         * After the application indicates that all stream data has been sent
         * and a STREAM frame containing the FIN bit is sent, the sending part
         * of the stream enters the "Data Sent" state. From this state, the
         * endpoint only retransmits stream data as necessary. The endpoint
         * does not need to check flow control limits or send STREAM_DATA_BLOCKED
         * frames for a stream in this state. MAX_STREAM_DATA frames might be received
         * until the peer receives the final stream offset. The endpoint can safely
         * ignore any MAX_STREAM_DATA frames it receives from its peer for a
         * stream in this state.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        DATA_SENT,
        /**
         * From any state that is one of "Ready", "Send", or "Data Sent", an
         * application can signal that it wishes to abandon transmission of
         * stream data. Alternatively, an endpoint might receive a STOP_SENDING
         * frame from its peer. In either case, the endpoint sends a RESET_STREAM
         * frame, which causes the stream to enter the "Reset Sent" state.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        RESET_SENT,
        /**
         * Once all stream data has been successfully acknowledged, the sending
         * part of the stream enters the "Data Recvd" state, which is a
         * terminal state.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        DATA_RECVD,
        /**
         * Once a packet containing a RESET_STREAM has been acknowledged, the
         * sending part of the stream enters the "Reset Recvd" state, which
         * is a terminal state.
         * <p>
         * [RFC 9000, Section 3.1]
         * (https://www.rfc-editor.org/rfc/rfc9000#name-sending-stream-states)
         */
        RESET_RECVD;

        @Override
        public boolean isTerminal() {
            return this == DATA_RECVD || this == RESET_RECVD;
        }

        /**
         * Returns whether a stream in this state can be used for sending.
         *
         * @return {@code true} if this state is either {@link #READY} or {@link #SEND}
         */
        public boolean isSending() {
            return this == READY || this == SEND;
        }

        /**
         * Returns whether this state indicates that the stream has been reset by the sender.
         *
         * @return {@code true} if this state indicates that the stream has been reset by the sender
         */
        public boolean isReset() {
            return this == RESET_SENT || this == RESET_RECVD;
        }
    }

}
