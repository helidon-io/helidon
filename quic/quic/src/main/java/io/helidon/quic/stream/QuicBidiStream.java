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

/**
 * An interface that represents a bidirectional stream.
 * A bidirectional stream implements both {@link QuicSenderStream}
 * and {@link QuicReceiverStream}.
 */
@Api.Internal
public non-sealed interface QuicBidiStream extends QuicStream, QuicReceiverStream, QuicSenderStream {

    /**
     * Returns a composed simplified state computed from the state of
     * the receiving part and sending part of the stream.
     * <p>
     * See RFC 9000, [Section 3.4]
     * (https://www.rfc-editor.org/rfc/rfc9000#name-bidirectional-stream-states)
     *
     * @return a composed simplified state computed from the state of the receiving part and sending part of the stream
     */
    default BidiStreamState bidiStreamState() {
        return switch (sendingState()) {
            case READY -> switch (receivingState()) {
                case RECV -> dataReceived() == 0
                        ? BidiStreamState.IDLE
                        : BidiStreamState.OPENED;
                case SIZE_KNOWN -> BidiStreamState.OPENED;
                case DATA_RECVD, DATA_READ, RESET_RECVD, RESET_READ -> BidiStreamState.HALF_CLOSED_REMOTE;
            };
            case SEND, DATA_SENT -> switch (receivingState()) {
                case RECV, SIZE_KNOWN -> BidiStreamState.OPENED;
                case DATA_RECVD, DATA_READ, RESET_RECVD, RESET_READ -> BidiStreamState.HALF_CLOSED_REMOTE;
            };
            case DATA_RECVD, RESET_RECVD, RESET_SENT -> switch (receivingState()) {
                case RECV, SIZE_KNOWN -> BidiStreamState.HALF_CLOSED_LOCAL;
                case DATA_RECVD, DATA_READ, RESET_RECVD, RESET_READ -> BidiStreamState.CLOSED;
            };
        };
    }

    @Override
    default StreamState state() {
        return bidiStreamState();
    }

    @Override
    default boolean hasError() {
        return rcvErrorCode() >= 0 || sndErrorCode() >= 0;
    }

    /**
     * The state of a bidirectional stream can be obtained by combining
     * the state of its sending part and receiving part.
     * <blockquote>
     * A bidirectional stream is composed of sending and receiving
     * parts. Implementations can represent states of the bidirectional
     * stream as composites of sending and receiving stream states.
     * The simplest model presents the stream as "open" when either
     * sending or receiving parts are in a non-terminal state and
     * "closed" when both sending and receiving streams are in
     * terminal states.
     * </blockquote>
     * See RFC 9000, [Section 3.4]
     * (https://www.rfc-editor.org/rfc/rfc9000#name-bidirectional-stream-states)
     */
    enum BidiStreamState implements QuicStream.StreamState {
        /**
         * A bidirectional stream is considered "idle" if no
         * data has been sent or received on that stream.
         */
        IDLE,
        /**
         * A bidirectional stream is considered "open" until all data
         * has been received, or all data has been sent, and no reset
         * has been sent or received.
         */
        OPENED,
        /**
         * A bidirectional stream is considered locally half closed
         * if the sending part is locally closed:
         * all data has been sent and acknowledged, or a reset has
         * been sent, but the receiving part is still receiving.
         */
        HALF_CLOSED_LOCAL,
        /**
         * A bidirectional stream is considered remotely half closed
         * if the receiving part is closed:
         * all data has been read or received on the receiving part,
         * or reset has been read or received on the receiving part, but
         * the sending part is still sending.
         */
        HALF_CLOSED_REMOTE,
        /**
         * A bidirectional stream is considered closed when both parts
         * have been reset or all data has been sent and acknowledged
         * and all data has been received.
         */
        CLOSED;

        /**
         * Returns whether this state is terminal.
         * A bidirectional stream may be considered closed (which is a terminal state),
         * even if the sending or receiving part of a stream haven't reached a terminal
         * state. Typically, if the sending part has sent a RESET frame, the stream
         * may be considered closed even if the acknowledgement hasn't been received
         * yet.
         *
         * @return true if this state is terminal
         */
        @Override
        public boolean isTerminal() {
            return this == CLOSED;
        }
    }
}
