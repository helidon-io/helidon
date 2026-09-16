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

import java.nio.ByteBuffer;
import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.quic.QuicTransportErrors;
import io.helidon.quic.QuicTransportException;
import io.helidon.quic.VariableLengthEncoder;
import io.helidon.quic.packet.QuicPacket;
import io.helidon.quic.packet.QuicPacketDiscardException;

/**
 * A QUIC frame.
 *
 * <p>Specification: https://www.rfc-editor.org/info/rfc9000
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
@Api.Internal
public abstract sealed class QuicFrame permits
                                       AckFrame,
                                       DataBlockedFrame,
                                       ConnectionCloseFrame, CryptoFrame,
                                       HandshakeDoneFrame,
                                       MaxDataFrame, MaxStreamDataFrame, MaxStreamsFrame,
                                       NewConnectionIDFrame, NewTokenFrame,
                                       PaddingFrame, PathChallengeFrame, PathResponseFrame, PingFrame,
                                       ResetStreamFrame, RetireConnectionIDFrame,
                                       StreamsBlockedFrame, StreamDataBlockedFrame, StreamFrame, StopSendingFrame {

    /**
     * Largest value representable by a QUIC variable-length integer.
     */
    public static final long MAX_VL_INTEGER = (1L << 62) - 1;
    /**
     * Frame type for PADDING frames.
     */
    public static final int PADDING = 0x00;
    /**
     * Frame type for PING frames.
     */
    public static final int PING = 0x01;
    /**
     * Frame type for ACK frames.
     */
    public static final int ACK = 0x02;
    /**
     * Frame type for RESET_STREAM frames.
     */
    public static final int RESET_STREAM = 0x04;
    /**
     * Frame type for STOP_SENDING frames.
     */
    public static final int STOP_SENDING = 0x05;
    /**
     * Frame type for CRYPTO frames.
     */
    public static final int CRYPTO = 0x06;
    /**
     * Frame type for NEW_TOKEN frames.
     */
    public static final int NEW_TOKEN = 0x07;
    /**
     * Base frame type for STREAM frames.
     */
    public static final int STREAM = 0x08;
    /**
     * Frame type for MAX_DATA frames.
     */
    public static final int MAX_DATA = 0x10;
    /**
     * Frame type for MAX_STREAM_DATA frames.
     */
    public static final int MAX_STREAM_DATA = 0x11;
    /**
     * Base frame type for MAX_STREAMS frames.
     */
    public static final int MAX_STREAMS = 0x12;
    /**
     * Frame type for DATA_BLOCKED frames.
     */
    public static final int DATA_BLOCKED = 0x14;
    /**
     * Frame type for STREAM_DATA_BLOCKED frames.
     */
    public static final int STREAM_DATA_BLOCKED = 0x15;
    /**
     * Base frame type for STREAMS_BLOCKED frames.
     */
    public static final int STREAMS_BLOCKED = 0x16;
    /**
     * Frame type for NEW_CONNECTION_ID frames.
     */
    public static final int NEW_CONNECTION_ID = 0x18;
    /**
     * Frame type for RETIRE_CONNECTION_ID frames.
     */
    public static final int RETIRE_CONNECTION_ID = 0x19;
    /**
     * Frame type for PATH_CHALLENGE frames.
     */
    public static final int PATH_CHALLENGE = 0x1a;
    /**
     * Frame type for PATH_RESPONSE frames.
     */
    public static final int PATH_RESPONSE = 0x1b;
    /**
     * Base frame type for CONNECTION_CLOSE frames.
     */
    public static final int CONNECTION_CLOSE = 0x1c;
    /**
     * Frame type for HANDSHAKE_DONE frames.
     */
    public static final int HANDSHAKE_DONE = 0x1e;
    private static final int MAX_KNOWN_FRAME_TYPE = HANDSHAKE_DONE;
    private final int frameType;

    /**
     * Concrete frame types normally have two constructors which call this.
     *
     * <p>{@code FrameSubclass(ByteBuffer, int firstByte)} is called for incoming frames
     * after reading the first byte to determine the type. The first byte is also
     * supplied to the constructor because it can contain additional state information.
     *
     * <p>{@code FrameSubclass(...)} is called to instantiate outgoing frames.
     *
     * @param type the first byte of the frame, which encodes the frame type.
     */
    QuicFrame(int type) {
        frameType = type;
    }

    /**
     * Decode a QUIC frame from the supplied buffer.
     *
     * <p>Decoded STREAM payload ranges borrow the supplied backing storage. The caller must not modify a borrowed range while
     * the returned frame is in use. A receive path that retains the frame beyond a synchronous call must first canonicalize its
     * payload ownership.
     *
     * @param buffer source buffer positioned at the frame type field
     * @return decoded frame
     * @throws QuicTransportException if the frame type or payload is malformed
     */
    public static QuicFrame decode(ByteBuffer buffer) throws QuicTransportException {
        return decode(buffer, false, Integer.MAX_VALUE);
    }

    /**
     * Decode a QUIC frame from the supplied buffer with an ACK-range resource limit.
     *
     * @param buffer source buffer positioned at the frame type field
     * @param maxAckRangesPerFrame maximum number of packet-number ranges accepted in one ACK frame
     * @return decoded frame
     * @throws IllegalArgumentException if {@code maxAckRangesPerFrame} is not positive
     * @throws QuicPacketDiscardException if a structurally valid ACK frame exceeds the configured limit
     * @throws QuicTransportException if the frame type or payload is malformed
     */
    @Api.Internal
    public static QuicFrame decodeWithAckRangeLimit(ByteBuffer buffer,
                                                    int maxAckRangesPerFrame) throws QuicTransportException {
        return decode(buffer, false, checkMaxAckRangesPerFrame(maxAckRangesPerFrame));
    }

    /**
     * Decode a QUIC frame from transport-owned packet storage.
     *
     * <p>On successful return, the decoded frame may retain read-only payload slices backed by the supplied buffer. The caller
     * must not overwrite or reuse that backing storage while the returned frame or any derived payload view remains reachable.
     *
     * @param buffer transport-owned source buffer positioned at the frame type field
     * @return decoded frame
     * @throws QuicTransportException if the frame type or payload is malformed
     */
    @Api.Internal
    public static QuicFrame decodeOwned(ByteBuffer buffer) throws QuicTransportException {
        return decode(buffer, true, Integer.MAX_VALUE);
    }

    /**
     * Decode a QUIC frame from transport-owned packet storage with an ACK-range resource limit.
     *
     * @param buffer transport-owned source buffer positioned at the frame type field
     * @param maxAckRangesPerFrame maximum number of packet-number ranges accepted in one ACK frame
     * @return decoded frame
     * @throws IllegalArgumentException if {@code maxAckRangesPerFrame} is not positive
     * @throws QuicPacketDiscardException if a structurally valid ACK frame exceeds the configured limit
     * @throws QuicTransportException if the frame type or payload is malformed
     */
    @Api.Internal
    public static QuicFrame decodeOwnedWithAckRangeLimit(ByteBuffer buffer,
                                                         int maxAckRangesPerFrame) throws QuicTransportException {
        return decode(buffer, true, checkMaxAckRangesPerFrame(maxAckRangesPerFrame));
    }

    /**
     * Validate and consume the next frame after an ACK-range policy violation has made the packet discardable.
     *
     * <p>ACK frames are structurally validated without retaining their ranges. Other frame types are decoded but not retained.
     * Packet-type validation remains the caller's responsibility.
     *
     * @param buffer source buffer positioned at the frame type field
     * @return decoded frame type field
     * @throws QuicTransportException if the frame type or payload is malformed
     */
    @Api.Internal
    public static long validateAfterAckPolicyDiscard(ByteBuffer buffer) throws QuicTransportException {
        int frameType = decodeFrameType(buffer);
        if (maskType(frameType) == ACK) {
            AckFrame.validateStructure(buffer, frameType);
        } else {
            decodeFramePayload(buffer, false, Integer.MAX_VALUE, frameType);
        }
        return frameType;
    }

    /**
     * Resolve the implementation class for a QUIC frame type.
     *
     * @param frameType frame type value
     * @return frame implementation class
     */
    public static Class<? extends QuicFrame> frameClassOf(int frameType) {
        return switch (maskType(frameType)) {
            case ACK -> AckFrame.class;
            case STREAM -> StreamFrame.class;
            case RESET_STREAM -> ResetStreamFrame.class;
            case PADDING -> PaddingFrame.class;
            case PING -> PingFrame.class;
            case STOP_SENDING -> StopSendingFrame.class;
            case CRYPTO -> CryptoFrame.class;
            case NEW_TOKEN -> NewTokenFrame.class;
            case DATA_BLOCKED -> DataBlockedFrame.class;
            case MAX_DATA -> MaxDataFrame.class;
            case MAX_STREAMS -> MaxStreamsFrame.class;
            case MAX_STREAM_DATA -> MaxStreamDataFrame.class;
            case STREAM_DATA_BLOCKED -> StreamDataBlockedFrame.class;
            case STREAMS_BLOCKED -> StreamsBlockedFrame.class;
            case NEW_CONNECTION_ID -> NewConnectionIDFrame.class;
            case RETIRE_CONNECTION_ID -> RetireConnectionIDFrame.class;
            case PATH_CHALLENGE -> PathChallengeFrame.class;
            case PATH_RESPONSE -> PathResponseFrame.class;
            case CONNECTION_CLOSE -> ConnectionCloseFrame.class;
            case HANDSHAKE_DONE -> HandshakeDoneFrame.class;
            default -> throw new IllegalArgumentException("Unrecognised frame");
        };
    }

    /**
     * Resolve the canonical frame type for a frame implementation class.
     *
     * @param frameClass frame implementation class
     * @return canonical frame type value
     */
    public static int frameTypeOf(Class<? extends QuicFrame> frameClass) {
        // we don't have class pattern matching yet - so switch
        // on the class name instead
        return switch (frameClass.getSimpleName()) {
            case "AckFrame" -> ACK;
            case "StreamFrame" -> STREAM;
            case "ResetStreamFrame" -> RESET_STREAM;
            case "PaddingFrame" -> PADDING;
            case "PingFrame" -> PING;
            case "StopSendingFrame" -> STOP_SENDING;
            case "CryptoFrame" -> CRYPTO;
            case "NewTokenFrame" -> NEW_TOKEN;
            case "DataBlockedFrame" -> DATA_BLOCKED;
            case "MaxDataFrame" -> MAX_DATA;
            case "MaxStreamsFrame" -> MAX_STREAMS;
            case "MaxStreamDataFrame" -> MAX_STREAM_DATA;
            case "StreamDataBlockedFrame" -> STREAM_DATA_BLOCKED;
            case "StreamsBlockedFrame" -> STREAMS_BLOCKED;
            case "NewConnectionIDFrame" -> NEW_CONNECTION_ID;
            case "RetireConnectionIDFrame" -> RETIRE_CONNECTION_ID;
            case "PathChallengeFrame" -> PATH_CHALLENGE;
            case "PathResponseFrame" -> PATH_RESPONSE;
            case "ConnectionCloseFrame" -> CONNECTION_CLOSE;
            case "HandshakeDoneFrame" -> HANDSHAKE_DONE;
            default -> throw new IllegalArgumentException("Unrecognised frame");
        };
    }

    /**
     * Writes {@code src} to {@code dest}, preserving position in {@code src}.
     *
     * @param dest destination buffer
     * @param src  source buffer
     */
    protected static void putByteBuffer(ByteBuffer dest, ByteBuffer src) {
        dest.put(src.asReadOnlyBuffer());
    }

    /**
     * Throws a {@code QuicTransportException} if the given buffer does not have enough bytes
     * to finish decoding the frame.
     *
     * @param buffer   source buffer
     * @param expected minimum number of bytes required
     * @param type     frame type to include in exception
     * @throws QuicTransportException if the buffer is shorter than {@code expected}
     */
    protected static void validateRemainingLength(ByteBuffer buffer, int expected, long type)
            throws QuicTransportException {
        if (buffer.remaining() < expected) {
            throw new QuicTransportException("Error decoding frame",
                                             type, QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
    }

    /**
     * Validate that an integer can be encoded as a QUIC variable-length integer.
     *
     * @param val     value to validate
     * @param message field name used in the exception message
     * @return the validated value
     */
    protected static int requireVLRange(int val, String message) {
        if (val < 0) {
            throw new IllegalArgumentException(message + " " + val + " not in range");
        }
        return val;
    }

    /**
     * Validate that a long can be encoded as a QUIC variable-length integer.
     *
     * @param val       value to validate
     * @param fieldName field name used in the exception message
     * @return the validated value
     */
    protected static long requireVLRange(long val, String fieldName) {
        if (val < 0 || val > MAX_VL_INTEGER) {
            throw new IllegalArgumentException(
                    String.format("%s not in VL range: %s", fieldName, val));
        }
        return val;
    }

    /**
     * Encode a QUIC variable-length integer field.
     *
     * @param buffer destination buffer
     * @param val    value to encode
     * @param name   field name used in error messages
     */
    protected static void encodeVLField(ByteBuffer buffer, long val, String name) {
        try {
            VariableLengthEncoder.encode(buffer, val);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Error encoding " + name, e);
        }
    }

    /**
     * Compute the encoded length of a QUIC variable-length integer.
     *
     * @param val value to encode
     * @return encoded size in bytes
     */
    protected static int variableLengthFieldLength(long val) {
        return VariableLengthEncoder.encodedSize(val);
    }

    /**
     * Tells whether a decoded frame type is valid in the given packet type.
     *
     * @param typeField decoded frame type field
     * @param packetType packet type
     * @return true if the frame type can be embedded in the packet type
     */
    @Api.Internal
    public static boolean isValidIn(long typeField, QuicPacket.PacketType packetType) {
        if (typeField < 0 || typeField > Integer.MAX_VALUE) {
            return false;
        }
        return isValidIn(maskType((int) typeField), typeField, packetType);
    }

    static long decodeVLField(ByteBuffer buffer, String name, long typeField) throws QuicTransportException {
        long v = VariableLengthEncoder.decode(buffer);
        if (v < 0) {
            throw new QuicTransportException("Error decoding field: " + name,
                                             typeField, QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        return v;
    }

    static int decodeVLFieldAsInt(ByteBuffer buffer, String name, long typeField) throws QuicTransportException {
        long l = decodeVLField(buffer, name, typeField);
        int intval = (int) l;
        if (((long) intval) != l) {
            throw new QuicTransportException(name + ":field too long",
                                             typeField, QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        return intval;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

    /**
     * Returns true if this frame is <em>ACK-eliciting</em>.
     * A frame is <em>ACK-eliciting</em> if it is anything
     * other than {@link QuicFrame#ACK},
     * {@link QuicFrame#PADDING} or
     * {@link QuicFrame#CONNECTION_CLOSE}
     * (or its variant).
     *
     * @return true if this frame is <em>ACK-eliciting</em>
     */
    public boolean isAckEliciting() {
        return true;
    }

    /**
     * Returns the minimum number of bytes needed to encode this frame.
     *
     * @return the minimum number of bytes needed to encode this frame
     */
    public abstract int size();

    /**
     * Returns the type of this frame. This is one of the values above, which means it
     * excludes the additional information that is encoded into the first field
     * of some QUIC frames. That additional info has to be maintained by the sub
     * class and used by its encode() method to generate the first field for outgoing frames.
     *
     * @return canonical frame type
     */
    public int frameType() {
        return frameType;
    }

    /**
     * Encode this QUIC frame into the supplied buffer.
     *
     * @param buffer destination buffer
     */
    public abstract void encode(ByteBuffer buffer);

    /**
     * Returns the type field that was encoded or should be encoded.
     *
     * @return the type field that was encoded or should be encoded
     * This is the {@linkplain #frameType() frame type} with
     * possibly some additional bits set, depending on the
     * frame.
     *
     * @implSpec The default implementation of this method is to return
     *        {@link #frameType()}.
     */
    public long typeField() {
        return frameType();
    }

    /**
     * Tells whether this particular frame is valid in the given
     * packet type.
     *
     * <p>From <a href="https://www.rfc-editor.org/rfc/rfc9000#section-12.5">
     * RFC 9000, section 12.5. Frames and Number Spaces:</a>
     * <blockquote>
     * Some frames are prohibited in different packet number space
     * The rules here generalize those of TLS, in that frames associated
     * with establishing the connection can usually appear in packets
     * in any packet number space, whereas those associated with transferring
     * data can only appear in the application data packet number space:
     *
     * <ul>
     *  <li> PADDING, PING, and CRYPTO frames MAY appear in any packet number
     *       space.</li>
     *  <li> CONNECTION_CLOSE frames signaling errors at the QUIC layer (type 0x1c)
     *       MAY appear in any packet number space.</li>
     *  <li> CONNECTION_CLOSE frames signaling application errors (type 0x1d)
     *       MUST only appear in the application data packet number space.</li>
     *  <li> ACK frames MAY appear in any packet number space but can only
     *       acknowledge packets that appeared in that packet number space.
     *       However, as noted below, 0-RTT packets cannot contain ACK frames.</li>
     *  <li> All other frame types MUST only be sent in the application data
     *       packet number space.</li>
     * </ul>
     *
     * Note that it is not possible to send the following frames in 0-RTT
     * packets for various reasons: ACK, CRYPTO, HANDSHAKE_DONE, NEW_TOKEN,
     * PATH_RESPONSE, and RETIRE_CONNECTION_ID. A server MAY treat receipt
     * of these frames in 0-RTT packets as a connection error of
     * type PROTOCOL_VIOLATION.
     * </blockquote>
     *
     * @param packetType the packet type
     * @return true if the frame can be embedded in a packet of that type
     */
    public boolean isValidIn(QuicPacket.PacketType packetType) {
        return isValidIn(frameType, typeField(), packetType);
    }

    /**
     * Decode a QUIC variable-length integer field from the buffer.
     *
     * @param buffer source buffer
     * @param name   field name used in error messages
     * @return decoded value
     * @throws QuicTransportException if the field cannot be decoded
     */
    protected final long decodeVLField(ByteBuffer buffer, String name) throws QuicTransportException {
        return decodeVLField(buffer, name, typeField());
    }

    /**
     * Decode a QUIC variable-length integer field and narrow it to {@code int}.
     *
     * @param buffer source buffer
     * @param name   field name used in error messages
     * @return decoded integer value
     * @throws QuicTransportException if the field cannot be decoded or does not fit in {@code int}
     */
    protected final int decodeVLFieldAsInt(ByteBuffer buffer, String name) throws QuicTransportException {
        return decodeVLFieldAsInt(buffer, name, typeField());
    }

    private static QuicFrame decode(ByteBuffer buffer,
                                    boolean payloadOwned,
                                    int maxAckRangesPerFrame) throws QuicTransportException {
        int frameType = decodeFrameType(buffer);
        return decodeFramePayload(buffer, payloadOwned, maxAckRangesPerFrame, frameType);
    }

    private static int decodeFrameType(ByteBuffer buffer) throws QuicTransportException {
        long frameTypeLong = VariableLengthEncoder.decode(buffer);
        if (frameTypeLong < 0) {
            throw new QuicTransportException("Error decoding frame type",
                                             0, QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        if (frameTypeLong > Integer.MAX_VALUE) {
            throw new QuicTransportException("Unrecognized frame",
                                             frameTypeLong, QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        return (int) frameTypeLong;
    }

    private static QuicFrame decodeFramePayload(ByteBuffer buffer,
                                                boolean payloadOwned,
                                                int maxAckRangesPerFrame,
                                                int frameType) throws QuicTransportException {
        return switch (maskType(frameType)) {
            case ACK -> new AckFrame(buffer, frameType, maxAckRangesPerFrame);
            case STREAM -> payloadOwned
                    ? StreamFrame.decodeOwned(buffer, frameType)
                    : new StreamFrame(buffer, frameType);
            case RESET_STREAM -> new ResetStreamFrame(buffer, frameType);
            case PADDING -> new PaddingFrame(buffer, frameType);
            case PING -> new PingFrame(buffer, frameType);
            case STOP_SENDING -> new StopSendingFrame(buffer, frameType);
            case CRYPTO -> new CryptoFrame(buffer, frameType);
            case NEW_TOKEN -> new NewTokenFrame(buffer, frameType);
            case DATA_BLOCKED -> new DataBlockedFrame(buffer, frameType);
            case MAX_DATA -> new MaxDataFrame(buffer, frameType);
            case MAX_STREAMS -> new MaxStreamsFrame(buffer, frameType);
            case MAX_STREAM_DATA -> new MaxStreamDataFrame(buffer, frameType);
            case STREAM_DATA_BLOCKED -> new StreamDataBlockedFrame(buffer, frameType);
            case STREAMS_BLOCKED -> new StreamsBlockedFrame(buffer, frameType);
            case NEW_CONNECTION_ID -> new NewConnectionIDFrame(buffer, frameType);
            case RETIRE_CONNECTION_ID -> new RetireConnectionIDFrame(buffer, frameType);
            case PATH_CHALLENGE -> new PathChallengeFrame(buffer, frameType);
            case PATH_RESPONSE -> new PathResponseFrame(buffer, frameType);
            case CONNECTION_CLOSE -> new ConnectionCloseFrame(buffer, frameType);
            case HANDSHAKE_DONE -> new HandshakeDoneFrame(buffer, frameType);
            default -> throw new QuicTransportException("Unrecognized frame",
                                                        frameType, QuicTransportErrors.FRAME_ENCODING_ERROR);
        };
    }

    private static int checkMaxAckRangesPerFrame(int maxAckRangesPerFrame) {
        if (maxAckRangesPerFrame < 1) {
            throw new IllegalArgumentException("maxAckRangesPerFrame must be positive: " + maxAckRangesPerFrame);
        }
        return maxAckRangesPerFrame;
    }

    private static boolean isValidIn(int frameType, long typeField, QuicPacket.PacketType packetType) {
        Objects.requireNonNull(packetType, "packetType");
        return switch (frameType) {
            case PADDING, PING -> true;
            case ACK, CRYPTO -> switch (packetType) {
                case VERSIONS, ZERORTT -> false;
                default -> true;
            };
            case CONNECTION_CLOSE -> {
                if ((typeField & 0x1D) == 0x1C) {
                    yield true;
                }
                yield QuicPacket.PacketNumberSpace.of(packetType) == QuicPacket.PacketNumberSpace.APPLICATION;
            }
            case HANDSHAKE_DONE, NEW_TOKEN, PATH_RESPONSE,
                 RETIRE_CONNECTION_ID -> switch (packetType) {
                case ZERORTT -> false;
                default -> QuicPacket.PacketNumberSpace.of(packetType) == QuicPacket.PacketNumberSpace.APPLICATION;
            };
            default -> QuicPacket.PacketNumberSpace.of(packetType) == QuicPacket.PacketNumberSpace.APPLICATION;
        };
    }

    /**
     * Masks a frame type value to the canonical frame type. Depending on the
     * frame type, additional bits can be encoded in {@link #frameType()}.
     *
     * @param type frame type value
     * @return canonical frame type value
     */
    private static int maskType(int type) {
        if (type >= ACK && type < RESET_STREAM) {
            return ACK;
        }
        if (type >= STREAM && type < MAX_DATA) {
            return STREAM;
        }
        if (type >= MAX_STREAMS && type < DATA_BLOCKED) {
            return MAX_STREAMS;
        }
        if (type >= STREAMS_BLOCKED && type < NEW_CONNECTION_ID) {
            return STREAMS_BLOCKED;
        }
        if (type >= CONNECTION_CLOSE && type < HANDSHAKE_DONE) {
            return CONNECTION_CLOSE;
        }
        // all others are unique
        return type;
    }
}
