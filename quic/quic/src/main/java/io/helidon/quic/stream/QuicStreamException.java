/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.io.Serial;
import java.util.OptionalLong;

import io.helidon.common.Api;

/**
 * Unchecked signal that an operation cannot continue because a QUIC stream reached a terminal state.
 */
@Api.Internal
public final class QuicStreamException extends IllegalStateException {
    @Serial
    private static final long serialVersionUID = 5605676636689196101L;

    /**
     * Stream identifier.
     */
    private final long streamId;
    /**
     * Terminal stream condition.
     */
    private final Kind kind;
    /**
     * Application error code associated with the condition.
     */
    private final OptionalLong errorCode;

    private QuicStreamException(long streamId, Kind kind, OptionalLong errorCode, String message) {
        super(message);
        this.streamId = streamId;
        this.kind = kind;
        this.errorCode = errorCode;
    }

    static QuicStreamException closed(long streamId) {
        return new QuicStreamException(streamId,
                                       Kind.CLOSED,
                                       OptionalLong.empty(),
                                       "QUIC stream " + streamId + " is closed");
    }

    static QuicStreamException resetLocally(long streamId, long errorCode) {
        return new QuicStreamException(streamId,
                                       Kind.RESET_LOCALLY,
                                       OptionalLong.of(errorCode),
                                       "QUIC stream " + streamId + " was reset locally: errorCode " + errorCode);
    }

    static QuicStreamException resetByPeer(long streamId, long errorCode) {
        return new QuicStreamException(streamId,
                                       Kind.RESET_BY_PEER,
                                       OptionalLong.of(errorCode),
                                       "QUIC stream " + streamId + " was reset by peer: errorCode " + errorCode);
    }

    static QuicStreamException stopSending(long streamId, long errorCode) {
        return new QuicStreamException(streamId,
                                       Kind.STOP_SENDING,
                                       OptionalLong.of(errorCode),
                                       "QUIC stream " + streamId + " received STOP_SENDING: errorCode " + errorCode);
    }

    /**
     * Stream identifier.
     *
     * @return stream identifier
     */
    public long streamId() {
        return streamId;
    }

    /**
     * Terminal stream condition.
     *
     * @return terminal condition
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Application error code associated with the terminal condition.
     *
     * @return application error code, when present
     */
    public OptionalLong errorCode() {
        return errorCode;
    }

    /**
     * Terminal stream condition.
     */
    public enum Kind {
        /**
         * Stream output or input was already closed.
         */
        CLOSED,
        /**
         * Local endpoint reset the stream.
         */
        RESET_LOCALLY,
        /**
         * Peer reset the stream.
         */
        RESET_BY_PEER,
        /**
         * Peer requested that stream sending stop.
         */
        STOP_SENDING
    }
}
