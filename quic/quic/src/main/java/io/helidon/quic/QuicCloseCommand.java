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

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import io.helidon.common.Api;

import static io.helidon.quic.frame.QuicFrame.MAX_VL_INTEGER;

/**
 * Immutable command requesting local QUIC connection termination.
 *
 * <p>The command contains local diagnostic information only. Local exception messages are never used automatically as a
 * peer-visible QUIC close reason; an explicitly enabled higher-layer policy must select detail for disclosure.
 */
@Api.Internal
public final class QuicCloseCommand {
    private final Kind kind;
    private final Layer layer;
    private final OptionalLong errorCode;
    private final OptionalLong frameType;
    private final QuicTLSEngine.KeySpace keySpace;
    private final OptionalLong streamId;
    private final Throwable cause;
    private final String logMessage;
    private final String peerDetail;
    private final Delivery delivery;

    private QuicCloseCommand(Kind kind,
                             Layer layer,
                             OptionalLong errorCode,
                             OptionalLong frameType,
                             QuicTLSEngine.KeySpace keySpace,
                             OptionalLong streamId,
                             Throwable cause,
                             String logMessage,
                             String peerDetail) {
        this(kind,
             layer,
             errorCode,
             frameType,
             keySpace,
             streamId,
             cause,
             logMessage,
             peerDetail,
             Delivery.CURRENT_LEVEL);
    }

    private QuicCloseCommand(Kind kind,
                             Layer layer,
                             OptionalLong errorCode,
                             OptionalLong frameType,
                             QuicTLSEngine.KeySpace keySpace,
                             OptionalLong streamId,
                             Throwable cause,
                             String logMessage,
                             String peerDetail,
                             Delivery delivery) {
        this.kind = kind;
        this.layer = layer;
        this.errorCode = errorCode;
        this.frameType = frameType;
        this.keySpace = keySpace;
        this.streamId = streamId;
        this.cause = cause;
        this.logMessage = logMessage;
        this.peerDetail = peerDetail;
        this.delivery = delivery;
    }

    /**
     * Creates a command for a known QUIC transport error.
     *
     * @param error transport error
     * @return close command
     */
    public static QuicCloseCommand transport(QuicTransportErrors error) {
        Objects.requireNonNull(error, "error");
        return transport(error, error.text());
    }

    /**
     * Creates a command for a known QUIC transport error with an explicit local diagnostic.
     *
     * @param error      transport error
     * @param logMessage local diagnostic
     * @return close command
     */
    public static QuicCloseCommand transport(QuicTransportErrors error, String logMessage) {
        Objects.requireNonNull(error, "error");
        return new QuicCloseCommand(Kind.CONNECTION_CLOSE,
                                    Layer.TRANSPORT,
                                    OptionalLong.of(error.code()),
                                    OptionalLong.empty(),
                                    null,
                                    OptionalLong.empty(),
                                    null,
                                    Objects.requireNonNull(logMessage, "logMessage"),
                                    null);
    }

    /**
     * Creates a transport close command from a local failure.
     *
     * <p>A {@link QuicTransportException} supplies its transport error and protocol context. Other failures use
     * {@link QuicTransportErrors#INTERNAL_ERROR}.
     *
     * @param cause local failure
     * @return close command
     */
    public static QuicCloseCommand transport(Throwable cause) {
        Objects.requireNonNull(cause, "cause");
        return transport(cause, Utils.throwableText(cause));
    }

    /**
     * Creates a transport close command from a local failure with an explicit local diagnostic.
     *
     * @param cause      local failure
     * @param logMessage local diagnostic
     * @return close command
     */
    public static QuicCloseCommand transport(Throwable cause, String logMessage) {
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(logMessage, "logMessage");
        if (cause instanceof QuicTransportException transportException) {
            long frameType = transportException.frameType();
            return new QuicCloseCommand(Kind.CONNECTION_CLOSE,
                                        Layer.TRANSPORT,
                                        OptionalLong.of(transportException.errorCode()),
                                        frameType == 0 ? OptionalLong.empty() : OptionalLong.of(frameType),
                                        transportException.keySpace().orElse(null),
                                        transportException.sourceStreamId(),
                                        cause,
                                        logMessage,
                                        null);
        }
        return new QuicCloseCommand(Kind.CONNECTION_CLOSE,
                                    Layer.TRANSPORT,
                                    OptionalLong.of(QuicTransportErrors.INTERNAL_ERROR.code()),
                                    OptionalLong.empty(),
                                    null,
                                    OptionalLong.empty(),
                                    cause,
                                    logMessage,
                                    null);
    }

    /**
     * Creates a graceful application-layer close command.
     *
     * @param errorCode application error code
     * @return close command
     */
    public static QuicCloseCommand application(long errorCode) {
        return application(errorCode, "application connection close");
    }

    /**
     * Creates a graceful application-layer close command with an explicit local diagnostic.
     *
     * @param errorCode  application error code
     * @param logMessage local diagnostic
     * @return close command
     */
    public static QuicCloseCommand application(long errorCode, String logMessage) {
        return new QuicCloseCommand(Kind.CONNECTION_CLOSE,
                                    Layer.APPLICATION,
                                    OptionalLong.of(errorCode),
                                    OptionalLong.empty(),
                                    null,
                                    OptionalLong.empty(),
                                    null,
                                    Objects.requireNonNull(logMessage, "logMessage"),
                                    null);
    }

    /**
     * Creates an application-layer close command caused by a local failure.
     *
     * @param errorCode application error code
     * @param cause     local failure
     * @return close command
     */
    public static QuicCloseCommand application(long errorCode, Throwable cause) {
        Objects.requireNonNull(cause, "cause");
        return application(errorCode, cause, Utils.throwableText(cause));
    }

    /**
     * Creates an application-layer close command caused by a local failure with an explicit local diagnostic.
     *
     * @param errorCode  application error code
     * @param cause      local failure
     * @param logMessage local diagnostic
     * @return close command
     */
    public static QuicCloseCommand application(long errorCode, Throwable cause, String logMessage) {
        return new QuicCloseCommand(Kind.CONNECTION_CLOSE,
                                    Layer.APPLICATION,
                                    OptionalLong.of(errorCode),
                                    OptionalLong.empty(),
                                    null,
                                    OptionalLong.empty(),
                                    Objects.requireNonNull(cause, "cause"),
                                    Objects.requireNonNull(logMessage, "logMessage"),
                                    null);
    }

    static QuicCloseCommand silent(String logMessage) {
        return silent(null, logMessage);
    }

    static QuicCloseCommand silent(Throwable cause, String logMessage) {
        return new QuicCloseCommand(Kind.SILENT,
                                    Layer.TRANSPORT,
                                    OptionalLong.empty(),
                                    OptionalLong.empty(),
                                    null,
                                    OptionalLong.empty(),
                                    cause,
                                    Objects.requireNonNull(logMessage, "logMessage"),
                                    null);
    }

    static QuicCloseCommand serverHandshakeTimeout(Throwable cause, String logMessage) {
        return new QuicCloseCommand(Kind.CONNECTION_CLOSE,
                                    Layer.TRANSPORT,
                                    OptionalLong.of(QuicTransportErrors.NO_ERROR.code()),
                                    OptionalLong.empty(),
                                    null,
                                    OptionalLong.empty(),
                                    Objects.requireNonNull(cause, "cause"),
                                    Objects.requireNonNull(logMessage, "logMessage"),
                                    null,
                                    Delivery.VALIDATED_HANDSHAKE_LEVELS);
    }

    QuicCloseCommand silentFallback() {
        return silent(cause, logMessage);
    }

    /**
     * Returns a copy which exposes a sanitized, bounded detail to the peer.
     *
     * <p>Control and formatting characters are replaced with spaces. The resulting UTF-8 payload is truncated to at most
     * 256 bytes on a Unicode code-point boundary. This operation never derives detail from {@link #cause()}.
     *
     * @param detail detail requested by an explicitly enabled higher-layer disclosure policy
     * @return copied command carrying sanitized peer detail
     */
    public QuicCloseCommand withPeerDetail(String detail) {
        Objects.requireNonNull(detail, "detail");
        StringBuilder sanitized = new StringBuilder(Math.min(detail.length(), 256));
        int byteCount = 0;
        for (int index = 0; index < detail.length();) {
            int codePoint = detail.codePointAt(index);
            index += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            if (type == Character.CONTROL
                    || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR
                    || type == Character.SURROGATE) {
                codePoint = ' ';
            }
            int codePointBytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (byteCount + codePointBytes > 256) {
                break;
            }
            sanitized.appendCodePoint(codePoint);
            byteCount += codePointBytes;
        }
        return new QuicCloseCommand(kind,
                                    layer,
                                    errorCode,
                                    frameType,
                                    keySpace,
                                    streamId,
                                    cause,
                                    logMessage,
                                    sanitized.toString(),
                                    delivery);
    }

    /**
     * Returns a copy associated with the triggering QUIC stream.
     *
     * @param streamId QUIC variable-length stream ID
     * @return copied command carrying stream context
     * @throws IllegalArgumentException if the stream ID is outside the QUIC variable-length integer range
     */
    public QuicCloseCommand withStream(long streamId) {
        if (streamId < 0 || streamId > MAX_VL_INTEGER) {
            throw new IllegalArgumentException("Invalid QUIC stream ID: " + streamId);
        }
        return new QuicCloseCommand(kind,
                                    layer,
                                    errorCode,
                                    frameType,
                                    keySpace,
                                    OptionalLong.of(streamId),
                                    cause,
                                    logMessage,
                                    peerDetail,
                                    delivery);
    }

    /**
     * Termination command kind.
     *
     * @return command kind
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
     * @return error code, or empty for silent termination
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
     * QUIC packet key space associated with the failure.
     *
     * @return key space when known
     */
    public Optional<QuicTLSEngine.KeySpace> keySpace() {
        return Optional.ofNullable(keySpace);
    }

    /**
     * QUIC stream associated with the failure.
     *
     * @return stream ID when known
     */
    public OptionalLong streamId() {
        return streamId;
    }

    /**
     * Original local failure.
     *
     * @return failure when present
     */
    public Optional<Throwable> cause() {
        return Optional.ofNullable(cause);
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
     * Sanitized detail explicitly selected for peer disclosure.
     *
     * @return peer detail when configured
     */
    public Optional<String> peerDetail() {
        return Optional.ofNullable(peerDetail);
    }

    Delivery delivery() {
        return delivery;
    }

    /**
     * Command kind.
     */
    public enum Kind {
        /**
         * Send a connection close frame and terminate.
         */
        CONNECTION_CLOSE,
        /**
         * Terminate without sending a connection close frame.
         */
        SILENT
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

    enum Delivery {
        CURRENT_LEVEL,
        VALIDATED_HANDSHAKE_LEVELS
    }
}
