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

package io.helidon.quic.frame;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.quic.QuicTransportErrors;
import io.helidon.quic.QuicTransportException;

import static io.helidon.common.buffers.BufferData.EMPTY_BYTES;

/**
 * A CONNECTION_CLOSE frame.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
@Api.Internal
public final class ConnectionCloseFrame extends QuicFrame {

    /**
     * This variant indicates an error originating from the higher
     * level protocol, for instance, HTTP/3.
     */
    public static final int CONNECTION_CLOSE_VARIANT = 0x1d;
    /**
     * An immutable ConnectionCloseFrame of type 0x1c with no reason phrase
     * and an error of type APPLICATION_ERROR.
     *
     * <p>Note: From <a href="https://www.rfc-editor.org/rfc/rfc9000#section-10.2.3">
     *        RFC 9000 - section 10.2.3</a>:
     *        <blockquote>
     *        A CONNECTION_CLOSE of type 0x1d MUST be replaced by a CONNECTION_CLOSE
     *        of type 0x1c when sending the frame in Initial or Handshake packets.
     *        Otherwise, information about the application state might be revealed.
     *        Endpoints MUST clear the value of the Reason Phrase field and SHOULD
     *        use the APPLICATION_ERROR code when converting to a CONNECTION_CLOSE
     *        of type 0x1c.
     *        </blockquote>
     */
    public static final ConnectionCloseFrame APPLICATION_ERROR =
            new ConnectionCloseFrame(QuicTransportErrors.APPLICATION_ERROR.code(), 0, "");
    private final long errorCode;
    private final long errorFrameType;
    private final boolean variant;
    private final byte[] reason;
    private String cachedToString;
    private String cachedReason;

    /**
     * Incoming CONNECTION_CLOSE frame returned by QuicFrame.decode().
     *
     * @param buffer buffer containing the frame payload
     * @param type   encoded frame type
     * @throws QuicTransportException if the frame was malformed
     */
    ConnectionCloseFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(CONNECTION_CLOSE);
        errorCode = decodeVLField(buffer, "errorCode");
        if (type == CONNECTION_CLOSE) {
            variant = false;
            errorFrameType = decodeVLField(buffer, "errorFrameType");
        } else {
            errorFrameType = -1;
            variant = true;
        }
        int reasonLength = decodeVLFieldAsInt(buffer, "reasonLength");
        validateRemainingLength(buffer, reasonLength, type);
        reason = new byte[reasonLength];
        buffer.get(reason, 0, reasonLength);
    }

    private ConnectionCloseFrame(long errorCode, long errorFrameType, String reason) {
        super(CONNECTION_CLOSE);
        this.errorCode = requireVLRange(errorCode, "errorCode");
        this.errorFrameType = requireVLRange(errorFrameType, "errorFrameType");
        this.variant = false;
        this.cachedReason = reason;
        this.reason = reasonBytes(reason);
    }

    private ConnectionCloseFrame(long errorCode, String reason) {
        super(CONNECTION_CLOSE);
        this.errorCode = requireVLRange(errorCode, "errorCode");
        this.errorFrameType = -1;
        this.variant = true;
        this.cachedReason = reason;
        this.reason = reasonBytes(reason);
    }

    /**
     * Outgoing CONNECTION_CLOSE frame (variant with errorFrameType - 0x1c).
     * This indicates a {@linkplain io.helidon.quic.QuicTransportErrors
     * quic transport error}.
     *
     * @param errorCode      transport error code
     * @param errorFrameType frame type that triggered the error
     * @param reason         UTF-8 reason phrase
     * @return new transport-level CONNECTION_CLOSE frame
     */
    public static ConnectionCloseFrame create(long errorCode, long errorFrameType, String reason) {
        return new ConnectionCloseFrame(errorCode, errorFrameType, reason);
    }

    /**
     * Outgoing CONNECTION_CLOSE frame (variant without errorFrameType).
     * This indicates an error originating from the higher level protocol,
     * for instance HTTP/3.
     *
     * @param errorCode application error code
     * @param reason    UTF-8 reason phrase
     * @return new application-level CONNECTION_CLOSE frame
     */
    public static ConnectionCloseFrame create(long errorCode, String reason) {
        return new ConnectionCloseFrame(errorCode, reason);
    }

    /**
     * Returns a ConnectionCloseFrame suitable for inclusion in
     * an Initial or Handshake packet.
     *
     * @return ConnectionCloseFrame suitable for Initial or Handshake packets
     */
    public ConnectionCloseFrame clearApplicationState() {
        return this.variant ? APPLICATION_ERROR : this;
    }

    @Override
    public long typeField() {
        return variant ? CONNECTION_CLOSE_VARIANT : CONNECTION_CLOSE;
    }

    @Override
    public boolean isAckEliciting() {
        return false;
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        encodeVLField(buffer, typeField(), "type");
        encodeVLField(buffer, errorCode, "errorCode");
        if (!variant) {
            encodeVLField(buffer, errorFrameType, "errorFrameType");
        }
        encodeVLField(buffer, reason.length, "reasonLength");
        if (reason.length > 0) {
            buffer.put(reason);
        }
    }

    @Override
    public int size() {
        return variableLengthFieldLength(typeField())
                + variableLengthFieldLength(errorCode)
                + (variant ? 0 : variableLengthFieldLength(errorFrameType))
                + variableLengthFieldLength(reason.length)
                + reason.length;
    }

    /**
     * Returns the QUIC or application error code carried by this frame.
     *
     * @return QUIC or application error code
     */
    public long errorCode() {
        return errorCode;
    }

    /**
     * Returns the frame type associated with the transport error, or {@code -1} for application errors.
     *
     * @return frame type associated with the transport error, or {@code -1} for application errors
     */
    public long errorFrameType() {
        return errorFrameType;
    }

    /**
     * Checks whether this is the application-error variant of CONNECTION_CLOSE.
     *
     * @return {@code true} when this is the application-error variant of CONNECTION_CLOSE
     */
    public boolean variant() {
        return variant;
    }

    /**
     * Checks whether the error code belongs to the QUIC transport layer.
     *
     * @return {@code true} when the error code belongs to the QUIC transport layer
     */
    public boolean isQuicTransportCode() {
        return !variant;
    }

    /**
     * Checks whether the error code belongs to the application protocol.
     *
     * @return {@code true} when the error code belongs to the application protocol
     */
    public boolean isApplicationCode() {
        return variant;
    }

    /**
     * Returns the UTF-8 encoded reason phrase bytes.
     *
     * @return UTF-8 encoded reason phrase bytes
     */
    public byte[] reason() {
        return reason;
    }

    /**
     * Returns the decoded reason phrase.
     *
     * @return decoded reason phrase
     */
    public Optional<String> reasonString() {
        if (cachedReason != null) {
            return Optional.of(cachedReason);
        }
        if (reason == null) {
            return Optional.empty();
        }
        if (reason.length == 0) {
            return Optional.of("");
        }
        cachedReason = new String(reason, StandardCharsets.UTF_8);
        return Optional.of(cachedReason);
    }

    /**
     * Returns the encoded reason-phrase length.
     *
     * @return reason-phrase length in bytes
     */
    public int reasonLength() {
        return reason.length;
    }

    @Override
    public String toString() {
        if (cachedToString == null) {
            StringBuilder sb = new StringBuilder("ConnectionCloseFrame[type=0x");
            long type = typeField();
            sb.append(Long.toHexString(type))
                    .append(", errorCode=0x").append(Long.toHexString(errorCode));
            // CRYPTO_ERROR codes ranging 0x0100-0x01ff
            if (type == 0x1c) {
                if (errorCode >= 0x0100 && errorCode <= 0x01ff) {
                    // this represents a CRYPTO_ERROR which as per RFC-9001, section 4.8:
                    // A TLS alert is converted into a QUIC connection error. The AlertDescription
                    // value is added to 0x0100 to produce a QUIC error code from the range reserved for
                    // CRYPTO_ERROR; ... The resulting value is sent in a QUIC CONNECTION_CLOSE
                    // frame of type 0x1c

                    // find the tls alert code from the error code, by substracting 0x0100 from
                    // the error code
                    sb.append(", tlsAlertDescription=").append(errorCode - 0x0100);
                }
                sb.append(", errorFrameType=0x").append(Long.toHexString(errorFrameType));
            }
            sb.append(", reasonLength=").append(reason.length).append("]");

            cachedToString = sb.toString();
        }
        return cachedToString;
    }

    private static byte[] reasonBytes(String reason) {
        return reason != null
                ? reason.getBytes(StandardCharsets.UTF_8)
                : EMPTY_BYTES;
    }
}
