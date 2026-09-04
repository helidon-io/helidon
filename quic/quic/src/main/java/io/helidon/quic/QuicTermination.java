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

package io.helidon.quic;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import io.helidon.common.Api;

/**
 * Immutable observation of selected QUIC connection termination.
 */
@Api.Internal
public final class QuicTermination {
    private final Origin origin;
    private final Kind kind;
    private final Layer layer;
    private final OptionalLong errorCode;
    private final OptionalLong frameType;
    private final QuicTLSEngine.KeySpace keySpace;
    private final OptionalLong streamId;
    private final Throwable cause;
    private final String outgoingDetail;
    private final String peerReason;
    private final String logMessage;
    private final QuicConnectionException closeCause;

    private QuicTermination(Origin origin,
                            Kind kind,
                            Layer layer,
                            OptionalLong errorCode,
                            OptionalLong frameType,
                            QuicTLSEngine.KeySpace keySpace,
                            OptionalLong streamId,
                            Throwable cause,
                            String outgoingDetail,
                            String peerReason,
                            String logMessage) {
        this.origin = origin;
        this.kind = kind;
        this.layer = layer;
        this.errorCode = errorCode;
        this.frameType = frameType;
        this.keySpace = keySpace;
        this.streamId = streamId;
        this.cause = cause;
        this.outgoingDetail = outgoingDetail;
        this.peerReason = peerReason;
        this.logMessage = logMessage;
        this.closeCause = cause instanceof QuicConnectionException connectionException
                ? connectionException
                : new QuicConnectionException(cause == null || cause.getMessage() == null
                                                      ? logMessage
                                                      : cause.getMessage(),
                                              cause);
    }

    static QuicTermination local(QuicCloseCommand command, QuicTLSEngine.KeySpace keySpace) {
        Objects.requireNonNull(command, "command");
        return new QuicTermination(Origin.LOCAL,
                                   command.kind() == QuicCloseCommand.Kind.SILENT
                                           ? Kind.SILENT
                                           : Kind.CONNECTION_CLOSE,
                                   command.layer() == QuicCloseCommand.Layer.APPLICATION
                                           ? Layer.APPLICATION
                                           : Layer.TRANSPORT,
                                   command.errorCode(),
                                   command.frameType(),
                                   keySpace,
                                   command.streamId(),
                                   command.cause().orElse(null),
                                   command.peerDetail().orElse(null),
                                   null,
                                   command.logMessage());
    }

    static QuicTermination peerConnectionClose(Layer layer,
                                                long errorCode,
                                                OptionalLong frameType,
                                                QuicTLSEngine.KeySpace keySpace,
                                                String peerReason,
                                                String logMessage) {
        return new QuicTermination(Origin.PEER,
                                   Kind.CONNECTION_CLOSE,
                                   Objects.requireNonNull(layer, "layer"),
                                   OptionalLong.of(errorCode),
                                   Objects.requireNonNull(frameType, "frameType"),
                                   Objects.requireNonNull(keySpace, "keySpace"),
                                   OptionalLong.empty(),
                                   null,
                                   null,
                                   Objects.requireNonNull(peerReason, "peerReason"),
                                   Objects.requireNonNull(logMessage, "logMessage"));
    }

    static QuicTermination peerStatelessReset(String logMessage) {
        return new QuicTermination(Origin.PEER,
                                   Kind.STATELESS_RESET,
                                   Layer.TRANSPORT,
                                   OptionalLong.empty(),
                                   OptionalLong.empty(),
                                   null,
                                   OptionalLong.empty(),
                                   null,
                                   null,
                                   null,
                                   Objects.requireNonNull(logMessage, "logMessage"));
    }

    /**
     * Endpoint which initiated termination.
     *
     * @return termination origin
     */
    public Origin origin() {
        return origin;
    }

    /**
     * Termination kind.
     *
     * @return termination kind
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Protocol layer which owns the close code.
     *
     * @return close-code layer
     */
    public Layer layer() {
        return layer;
    }

    /**
     * Close error code.
     *
     * @return error code, or empty when no close frame supplied one
     */
    public OptionalLong errorCode() {
        return errorCode;
    }

    /**
     * QUIC frame type associated with the failure.
     *
     * @return frame type when known
     */
    public OptionalLong frameType() {
        return frameType;
    }

    /**
     * QUIC packet key space which carried or was selected to carry the close.
     *
     * @return key space when applicable
     */
    public Optional<QuicTLSEngine.KeySpace> keySpace() {
        return Optional.ofNullable(keySpace);
    }

    /**
     * QUIC stream associated with the failure.
     *
     * @return source stream ID when known
     */
    public OptionalLong streamId() {
        return streamId;
    }

    /**
     * Original local failure.
     *
     * @return original failure for locally initiated termination
     */
    public Optional<Throwable> cause() {
        return Optional.ofNullable(cause);
    }

    /**
     * Sanitized detail sent to the peer for locally originated termination.
     *
     * @return outgoing peer detail when {@link #origin()} is {@link Origin#LOCAL}
     */
    public Optional<String> outgoingDetail() {
        return Optional.ofNullable(outgoingDetail);
    }

    /**
     * Reason received from the peer.
     *
     * <p>The value is untrusted peer input and is not included in {@link #logMessage()} or {@link #closeCause()}.
     *
     * @return peer reason for a received connection close frame
     */
    public Optional<String> peerReason() {
        return Optional.ofNullable(peerReason);
    }

    /**
     * Safe local diagnostic message.
     *
     * @return local diagnostic
     */
    public String logMessage() {
        return logMessage;
    }

    /**
     * Stable exception used to wake connection and stream consumers.
     *
     * <p>For peer-originated termination this exception does not contain the untrusted peer reason.
     *
     * @return close exception
     */
    public QuicConnectionException closeCause() {
        return closeCause;
    }

    /**
     * Termination origin.
     */
    public enum Origin {
        /**
         * Selected by the local endpoint.
         */
        LOCAL,
        /**
         * Observed from the peer.
         */
        PEER
    }

    /**
     * Termination kind.
     */
    public enum Kind {
        /**
         * Connection close frame termination.
         */
        CONNECTION_CLOSE,
        /**
         * Local silent termination.
         */
        SILENT,
        /**
         * Peer stateless reset.
         */
        STATELESS_RESET
    }

    /**
     * Layer which owns the close code.
     */
    public enum Layer {
        /**
         * QUIC transport layer.
         */
        TRANSPORT,
        /**
         * Application protocol layer.
         */
        APPLICATION
    }
}
