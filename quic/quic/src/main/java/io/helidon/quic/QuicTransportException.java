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

package io.helidon.quic;

import java.io.Serial;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import io.helidon.common.Api;

import static io.helidon.quic.frame.QuicFrame.MAX_VL_INTEGER;

/**
 * Exception that wraps QUIC transport error codes.
 * Thrown in response to packets or frames that violate QUIC protocol.
 * This is a fatal exception; connection is always closed when this exception is caught.
 *
 * <p>For a list of errors see:
 * https://www.rfc-editor.org/rfc/rfc9000.html#name-transport-error-codes
 */
@Api.Internal
public final class QuicTransportException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 5259674758792412464L;

    /**
     * TLS key space where the transport error occurred.
     */
    private final Optional<QuicTLSEngine.KeySpace> keySpace;
    /**
     * QUIC frame type associated with the error, or {@code 0} if not frame-specific.
     */
    private final long frameType;
    /**
     * QUIC transport error code.
     */
    private final long errorCode;
    /**
     * QUIC stream associated with the transport error, when known.
     */
    private final OptionalLong sourceStreamId;

    /**
     * Constructs a new {@code QuicTransportException} without key-space context.
     *
     * @param reason    the reason why the exception occurred
     * @param frameType the frame type whose parsing or handling caused the error, or {@code 0}
     * @param errorCode QUIC transport error
     */
    public QuicTransportException(String reason,
                                  long frameType,
                                  QuicTransportErrors errorCode) {
        this(reason,
             Optional.empty(),
             frameType,
             Objects.requireNonNull(errorCode, "errorCode").code(),
             Optional.empty(),
             OptionalLong.empty());
    }

    /**
     * Constructs a new {@code QuicTransportException} without key-space or cause context.
     *
     * @param reason    the reason why the exception occurred
     * @param frameType the frame type whose parsing or handling caused the error, or {@code 0}
     * @param errorCode QUIC transport error code
     */
    public QuicTransportException(String reason,
                                  long frameType,
                                  long errorCode) {
        this(reason,
             Optional.empty(),
             frameType,
             errorCode,
             Optional.empty(),
             OptionalLong.empty());
    }

    /**
     * Constructs a new {@code QuicTransportException}.
     *
     * @param reason    the reason why the exception occurred
     * @param keySpace  the key space in which the frame appeared
     * @param frameType the frame type of the frame whose parsing / handling
     *                 caused the error.
     *                 May be 0 if not related to any specific frame.
     * @param errorCode a quic transport error
     */
    public QuicTransportException(String reason, QuicTLSEngine.KeySpace keySpace,
                                  long frameType, QuicTransportErrors errorCode) {
        this(reason,
             Optional.of(Objects.requireNonNull(keySpace, "keySpace")),
             frameType,
             Objects.requireNonNull(errorCode, "errorCode").code(),
             Optional.empty(),
             OptionalLong.empty());
    }

    /**
     * Constructs a new {@code QuicTransportException} associated with a QUIC stream.
     *
     * @param reason         local diagnostic describing the failure
     * @param keySpace       key space in which the frame appeared
     * @param frameType      frame type whose processing caused the error
     * @param errorCode      QUIC transport error
     * @param sourceStreamId stream associated with the failure
     */
    public QuicTransportException(String reason,
                                  QuicTLSEngine.KeySpace keySpace,
                                  long frameType,
                                  QuicTransportErrors errorCode,
                                  long sourceStreamId) {
        this(reason,
             Optional.of(Objects.requireNonNull(keySpace, "keySpace")),
             frameType,
             Objects.requireNonNull(errorCode, "errorCode").code(),
             Optional.empty(),
             sourceStreamId(sourceStreamId));
    }

    /**
     * Constructs a new {@code QuicTransportException} without key-space context.
     *
     * @param reason    the reason why the exception occurred
     * @param frameType the frame type whose parsing or handling caused the error, or {@code 0}
     * @param errorCode QUIC transport error code
     * @param cause     the cause
     */
    public QuicTransportException(String reason,
                                  long frameType,
                                  long errorCode,
                                  Throwable cause) {
        this(reason,
             Optional.empty(),
             frameType,
             errorCode,
             Optional.of(Objects.requireNonNull(cause, "cause")),
             OptionalLong.empty());
    }

    /**
     * Constructs a new {@code QuicTransportException}. For use with TLS alerts.
     *
     * @param reason    the reason why the exception occurred
     * @param keySpace  the key space in which the frame appeared
     * @param frameType the frame type of the frame whose parsing / handling
     *                 caused the error.
     *                 May be 0 if not related to any specific frame.
     * @param errorCode a quic transport error code
     * @param cause     the cause
     */
    public QuicTransportException(String reason, QuicTLSEngine.KeySpace keySpace,
                                  long frameType, long errorCode, Throwable cause) {
        this(reason,
             Optional.of(Objects.requireNonNull(keySpace, "keySpace")),
             frameType,
             errorCode,
             Optional.of(Objects.requireNonNull(cause, "cause")),
             OptionalLong.empty());
    }

    /**
     * Constructs a new {@code QuicTransportException} with a cause and source stream.
     *
     * @param reason         local diagnostic describing the failure
     * @param keySpace       key space in which the failure occurred
     * @param frameType      frame type whose processing caused the error
     * @param errorCode      QUIC transport error code
     * @param cause          underlying failure
     * @param sourceStreamId stream associated with the failure
     */
    public QuicTransportException(String reason,
                                  QuicTLSEngine.KeySpace keySpace,
                                  long frameType,
                                  long errorCode,
                                  Throwable cause,
                                  long sourceStreamId) {
        this(reason,
             Optional.of(Objects.requireNonNull(keySpace, "keySpace")),
             frameType,
             errorCode,
             Optional.of(Objects.requireNonNull(cause, "cause")),
             sourceStreamId(sourceStreamId));
    }

    private QuicTransportException(String reason,
                                   Optional<QuicTLSEngine.KeySpace> keySpace,
                                   long frameType,
                                   long errorCode,
                                   Optional<Throwable> cause,
                                   OptionalLong sourceStreamId) {
        super(Objects.requireNonNull(reason, "reason"), Objects.requireNonNull(cause, "cause").orElse(null));
        this.keySpace = Objects.requireNonNull(keySpace, "keySpace");
        this.frameType = frameType;
        this.errorCode = errorCode;
        this.sourceStreamId = Objects.requireNonNull(sourceStreamId, "sourceStreamId");
    }

    /**
     * Returns the local diagnostic describing the failure.
     *
     * <p>The diagnostic is never included in a {@code ConnectionCloseFrame} automatically.
     *
     * @return local diagnostic
     */
    public String reason() {
        return getMessage();
    }

    /**
     * Returns the key space for which the error occurred when present.
     *
     * @return the key space for which the error occurred when present
     */
    public Optional<QuicTLSEngine.KeySpace> keySpace() {
        return keySpace;
    }

    /**
     * Returns the frame type for which the error occurred, or 0.
     *
     * @return the frame type for which the error occurred, or 0
     */
    public long frameType() {
        return frameType;
    }

    /**
     * Returns the transport error that occurred.
     *
     * @return the transport error that occurred
     */
    public long errorCode() {
        return errorCode;
    }

    /**
     * Returns the QUIC stream associated with the transport error.
     *
     * @return source stream ID when known
     */
    public OptionalLong sourceStreamId() {
        return sourceStreamId;
    }

    private static OptionalLong sourceStreamId(long streamId) {
        if (streamId < 0 || streamId > MAX_VL_INTEGER) {
            throw new IllegalArgumentException("Invalid QUIC stream ID: " + streamId);
        }
        return OptionalLong.of(streamId);
    }
}
