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

package io.helidon.quic;

import java.util.Optional;
import java.util.stream.Stream;

import io.helidon.common.Api;

/**
 * An enum to model Quic transport errors.
 * Some errors have a single possible code value, some, like
 * {@link #CRYPTO_ERROR} have a range of possible values.
 * Usually, the value (a long) would be used instead of the
 * enum, but the enum itself can be useful - for instance in
 * switch statements.
 * This enum models QUIC transport error codes as defined in
 * <a href="https://www.rfc-editor.org/rfc/rfc9000#name-error-codes">RFC 9000, section 20.1</a>.
 */
@Api.Internal
public enum QuicTransportErrors {
    /**
     * No error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9000#name-error-codes">
     * RFC 9000, Section 20.1</a>:
     * <blockquote><pre>{@code
     * An endpoint uses this with CONNECTION_CLOSE to signal that
     * the connection is being closed abruptly in the absence
     * of any error.
     * }</pre></blockquote>
     */
    NO_ERROR(0x00),

    /**
     * Internal Error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9000#name-error-codes">
     * RFC 9000, Section 20.1</a>:
     * <blockquote><pre>{@code
     * The endpoint encountered an internal error and cannot
     * continue with the connection.
     * }</pre></blockquote>
     */
    INTERNAL_ERROR(0x01),

    /**
     * Connection refused error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9000#name-error-codes">
     * RFC 9000, Section 20.1</a>:
     * <blockquote><pre>{@code
     * The server refused to accept a new connection.
     * }</pre></blockquote>
     */
    CONNECTION_REFUSED(0x02),

    /**
     * Flow control error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9000#name-error-codes">
     * RFC 9000, Section 20.1</a>:
     * <blockquote><pre>{@code
     * An endpoint received more data than it permitted in its advertised data limits;
     * see Section 4.
     * }</pre></blockquote>
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-4">RFC 9000, Section 4</a>
     */
    FLOW_CONTROL_ERROR(0x03),

    /**
     * Stream limit error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9000#name-error-codes">
     * RFC 9000, Section 20.1</a>:
     * <blockquote><pre>{@code
     * An endpoint received a frame for a stream identifier that exceeded its advertised
     * stream limit for the corresponding stream type.
     * }</pre></blockquote>
     */
    STREAM_LIMIT_ERROR(0x04),

    /**
     * Stream state error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9000#name-error-codes">
     * RFC 9000, Section 20.1</a>:
     * <blockquote><pre>{@code
     * An endpoint received a frame for a stream that was not in a state that permitted
     * that frame; see Section 3.
     * }</pre></blockquote>
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc9000#section-3">RFC 9000, Section 3</a>.
     */
    STREAM_STATE_ERROR(0x05),

    /**
     * Final size error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9000#name-error-codes">
     * RFC 9000, Section 20.1</a>:
     * <blockquote><pre>{@code
     * (1) An endpoint received a STREAM frame containing data that exceeded the previously
     *    established final size,
     * (2) an endpoint received a STREAM frame or a RESET_STREAM frame containing a final
     *    size that was lower than the size of stream data that was already received, or
     * (3) an endpoint received a STREAM frame or a RESET_STREAM frame containing a
     *    different final size to the one already established.
     * }</pre></blockquote>
     */
    FINAL_SIZE_ERROR(0x06),

    /**
     * Frame encoding error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9000#name-error-codes">
     * RFC 9000, Section 20.1</a>:
     * <blockquote><pre>{@code
     * An endpoint received a frame that was badly formatted -- for instance,
     * a frame of an unknown type or an ACK frame that has more
     * acknowledgment ranges than the remainder of the packet could carry.
     * }</pre></blockquote>
     */
    FRAME_ENCODING_ERROR(0x07),

    /**
     * Transport parameter error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9000#name-error-codes">
     * RFC 9000, Section 20.1</a>:
     * <blockquote><pre>{@code
     * An endpoint received transport parameters that were badly
     * formatted, included an invalid value, omitted a mandatory
     * transport parameter, included a forbidden transport
     * parameter, or were otherwise in error.
     * }</pre></blockquote>
     */
    TRANSPORT_PARAMETER_ERROR(0x08),

    /**
     * Connection id limit error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9000#name-error-codes">
     * RFC 9000, Section 20.1</a>:
     * <blockquote><pre>{@code
     * The number of connection IDs provided by the peer exceeds
     * the advertised active_connection_id_limit.
     * }</pre></blockquote>
     */
    CONNECTION_ID_LIMIT_ERROR(0x09),

    /**
     * Protocol violiation error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9000#name-error-codes">
     * RFC 9000, Section 20.1</a>:
     * <blockquote><pre>{@code
     * An endpoint detected an error with protocol compliance that
     * was not covered by more specific error codes.
     * }</pre></blockquote>
     */
    PROTOCOL_VIOLATION(0x0a),

    /**
     * Invalid token error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9000#name-error-codes">
     * RFC 9000, Section 20.1</a>:
     * <blockquote><pre>{@code
     * A server received a client Initial that contained an invalid Token field.
     * }</pre></blockquote>
     */
    INVALID_TOKEN(0x0b),

    /**
     * Application error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9000#name-error-codes">
     * RFC 9000, Section 20.1</a>:
     * <blockquote><pre>{@code
     * The application or application protocol caused the connection to be closed.
     * }</pre></blockquote>
     */
    APPLICATION_ERROR(0x0c),

    /**
     * Crypto buffer exceeded error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9000#name-error-codes">
     * RFC 9000, Section 20.1</a>:
     * <blockquote><pre>{@code
     * An endpoint has received more data in CRYPTO frames than it can buffer.
     * }</pre></blockquote>
     */
    CRYPTO_BUFFER_EXCEEDED(0x0d),

    /**
     * Key update error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9000#name-error-codes">
     * RFC 9000, Section 20.1</a>:
     * <blockquote><pre>{@code
     * An endpoint detected errors in performing key updates; see Section 6 of [QUIC-TLS].
     * }</pre></blockquote>
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc9001#section-6">Section 6 of RFC 9001 [QUIC-TLS]</a>
     */
    KEY_UPDATE_ERROR(0x0e),

    /**
     * AEAD limit reached error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9000#name-error-codes">
     * RFC 9000, Section 20.1</a>:
     * <blockquote><pre>{@code
     * An endpoint has reached the confidentiality or integrity limit
     * for the AEAD algorithm used by the given connection.
     * }</pre></blockquote>
     */
    AEAD_LIMIT_REACHED(0x0f),

    /**
     * No viable path error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9000#name-error-codes">
     * RFC 9000, Section 20.1</a>:
     * <blockquote><pre>{@code
     * An endpoint has determined that the network path is incapable of
     * supporting QUIC. An endpoint is unlikely to receive a
     * CONNECTION_CLOSE frame carrying this code except when the
     * path does not support a large enough MTU.
     * }</pre></blockquote>
     */
    NO_VIABLE_PATH(0x10),

    /**
     * Error negotiating version.
     *
     * <p>Specification: https://www.rfc-editor.org/rfc/rfc9368#name-version-downgrade-preventio
     *        RFC 9368, Section 4
     */
    VERSION_NEGOTIATION_ERROR(0x11),

    /**
     * Crypto error.
     * <p>
     * From <a href="https://www.rfc-editor.org/rfc/rfc9000#name-error-codes">
     * RFC 9000, Section 20.1</a>:
     * <blockquote><pre>{@code
     * The cryptographic handshake failed. A range of 256 values is
     * reserved for carrying error codes specific to the cryptographic
     * handshake that is used. Codes for errors occurring when
     * TLS is used for the cryptographic handshake are described
     * in Section 4.8 of [QUIC-TLS].
     * }</pre></blockquote>
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc9001#section-6">Section 4.8 of RFC 9001 [QUIC-TLS]</a>
     */
    CRYPTO_ERROR(0x0100, 0x01ff);

    private final long from;
    private final long to;

    QuicTransportErrors(long code) {
        this(code, code);
    }

    QuicTransportErrors(long from, long to) {
        this.from = from;
        this.to = to;
    }

    /**
     * Returns the transport error corresponding to the given code value.
     *
     * @param code a {@code code} value
     * @return the {@link QuicTransportErrors} instance corresponding
     *         to the given {@code code} value, if any
     */
    public static Optional<QuicTransportErrors> ofCode(long code) {
        return Stream.of(values()).filter(e -> e.isFor(code)).findAny();
    }

    /**
     * Returns canonical text for a transport error code.
     *
     * @param code transport error code
     * @return canonical error text, optionally enriched with code details
     */
    public static String text(long code) {
        Optional<QuicTransportErrors> error = ofCode(code);
        if (error.isEmpty()) {
            return "Unknown [0x" + Long.toHexString(code) + "]";
        }
        QuicTransportErrors transportError = error.get();
        if (transportError.hasCode()) {
            return transportError.text();
        }
        if (transportError == CRYPTO_ERROR) {
            return transportError.text() + "|" + SslAlerts.alertName((int) code);
        }
        return transportError.text() + " [0x" + Long.toHexString(code) + "]";

    }

    /**
     * Returns the code for this transport error, if this error
     * {@linkplain #hasCode() has a single possible code value},
     * {@code -1} otherwise.
     *
     * @return the code for this transport error, or {@code -1} if this
     *         error does not have a single possible code value
     */
    public long code() {
        return hasCode() ? from : -1;
    }

    /**
     * Returns whether this error has a single possible code value.
     *
     * @return true if this error has a single possible code value
     */
    public boolean hasCode() {
        return from == to;
    }

    /**
     * Returns whether this error has a range of possible code values.
     *
     * @return true if this error has a range of possible code values
     */
    public boolean hasRange() {
        return from < to;
    }

    /**
     * Returns the first possible code value in the range.
     *
     * @return the first possible code value in the range, or the
     *         code value if this error has a single possible code value
     */
    public long from() {
        return from;
    }

    /**
     * Returns the last possible code value in the range.
     *
     * @return the last possible code value in the range, or the
     *         code value if this error has a single possible code value
     */
    public long to() {
        return to;
    }

    /**
     * Returns the canonical transport-error text.
     *
     * @return the canonical transport-error text
     */
    public String text() {
        return name();
    }

    /**
     * Tells whether the given {@code code} value corresponds to
     * this error.
     *
     * @param code an error code value
     * @return true if the given {@code code} value corresponds to
     *        this error.
     */
    boolean isFor(long code) {
        return code >= from && code <= to;
    }
}
