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

package io.helidon.quic.packet;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;
import io.helidon.quic.CodingContext;
import io.helidon.quic.PeerConnectionId;
import io.helidon.quic.QuicConnectionId;
import io.helidon.quic.QuicKeyUnavailableException;
import io.helidon.quic.QuicPacketAuthenticationException;
import io.helidon.quic.QuicTLSEngine;
import io.helidon.quic.QuicTransportErrors;
import io.helidon.quic.QuicTransportException;
import io.helidon.quic.QuicVersion;
import io.helidon.quic.Utils;
import io.helidon.quic.VariableLengthEncoder;
import io.helidon.quic.frame.QuicFrame;
import io.helidon.quic.packet.QuicPacket.PacketNumberSpace;
import io.helidon.quic.packet.QuicPacket.PacketType;
import io.helidon.quic.spi.QuicPacketTLSEngine;

/**
 * A {@code QuicPacketDecoder} encapsulates the logic to decode a
 * quic packet. A {@code QuicPacketDecoder} is typically tied to
 * a particular version of the QUIC protocol.
 *
 * @see <a href="https://www.rfc-editor.org/info/rfc9000">
 *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport</a>
 * @see <a href="https://www.rfc-editor.org/info/rfc9001">
 *        RFC 9001: Using TLS to Secure QUIC</a>
 * @see <a href="https://www.rfc-editor.org/info/rfc9369">
 *        RFC 9369: QUIC Version 2</a>
 */
@Api.Internal
public class QuicPacketDecoder {

    private static final System.Logger LOGGER = System.getLogger(QuicPacketDecoder.class.getName());
    private static final LongHeaderResult EMPTY_LONG_HEADER = new EmptyLongHeaderResult();

    private final QuicVersion quicVersion;

    private QuicPacketDecoder(QuicVersion quicVersion) {
        this.quicVersion = quicVersion;
    }

    /**
     * Peeks at the headers type in the given byte buffer.
     * Does not advance the cursor.
     *
     * @param buffer the byte buffer containing a packet.
     * @param offset the offset at which the packet starts.
     * @return the header's type of the packet contained in this
     *        byte buffer. NONE if the header's type cannot be determined.
     * <p>Note: This method starts reading at the offset but respects
     *        the buffer limit.The provided offset must be less than the buffer
     *        limit in order for this method to read the header
     *        bytes.
     */
    public static QuicPacket.HeadersType peekHeaderType(ByteBuffer buffer, int offset) {
        if (offset < 0 || offset >= buffer.limit()) {
            return QuicPacket.HeadersType.NONE;
        }
        return headersType(buffer.get(offset));
    }

    /**
     * Peeks at the header in the long header packet bytes.
     * This method doesn't advance the cursor.
     * The buffer position must be at the start of the long header packet.
     *
     * @param buffer the buffer containing a long header packet.
     * @return result containing long header data, or an empty result if the packet is malformed
     */
    public static LongHeaderResult peekLongHeader(ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "buffer");
        return peekLongHeader(buffer, buffer.position());
    }

    /**
     * Peeks at the header in the long header packet bytes.
     * This method doesn't advance the cursor.
     *
     * @param buffer the buffer containing a long header packet.
     * @param offset the position of the start of the packet
     * @return result containing long header data, or an empty result if the packet is malformed
     */
    public static LongHeaderResult peekLongHeader(ByteBuffer buffer, int offset) {
        Objects.requireNonNull(buffer, "buffer");
        // the destination connection id length starts at index 5
        // (1 byte for headers, 4 bytes for version)
        // Therefore the packet needs at least 6 bytes to contain
        // a DCID length (coded on 1 byte)
        var limit = buffer.limit();
        if (offset < 0 || offset > limit - 7) {
            return EMPTY_LONG_HEADER;
        }
        var remaining = limit - offset;
        if ((buffer.get(offset) & 0x80) == 0) {
            // short header
            return EMPTY_LONG_HEADER;
        }
        int version = buffer.getInt(offset + 1);

        // read the DCID length (coded on 1 byte)
        int length = connectionIdLength(buffer.get(offset + 5));
        if (length < 0 || length > QuicConnectionId.MAX_CONNECTION_ID_LENGTH) {
            return EMPTY_LONG_HEADER;
        }
        // We need at least 6 + length + 1 byte to have
        // a chance to read the SCID length (coded on 1 byte)
        if (length > remaining - 7) {
            return EMPTY_LONG_HEADER;
        }
        int srcPos = offset + 6 + length;

        // read the SCID length
        int srclength = connectionIdLength(buffer.get(srcPos));
        if (srclength < 0 || srclength > QuicConnectionId.MAX_CONNECTION_ID_LENGTH) {
            return EMPTY_LONG_HEADER;
        }
        // we need at least pos + srclength + 1 byte in the
        // packet to peek at the SCID
        if (srclength > limit - srcPos - 1) {
            return EMPTY_LONG_HEADER;
        }
        QuicConnectionId destinationId = PeerConnectionId.create(buffer.slice(offset + 6, length));
        QuicConnectionId sourceId = PeerConnectionId.create(buffer.slice(srcPos + 1, srclength));
        int headerLength = 7 + length + srclength;

        // Return the SCID as a buffer slice.
        // The SCID begins at pos + 1 and has srclength bytes.
        return LongHeader.create(version, destinationId, sourceId, headerLength);
    }

    /**
     * Peeks at the connection id in the short header packet bytes.
     * This method doesn't advance the cursor.
     * The buffer position must be at the start of the short header packet.
     *
     * @param buffer the buffer containing a short headers packet.
     * @param length the connection id length.
     * @return a buffer slice containing the connection ID bytes, or an empty optional if the packet is malformed
     */
    public static Optional<ByteBuffer> peekShortConnectionId(ByteBuffer buffer, int length) {
        Objects.requireNonNull(buffer, "buffer");
        int pos = buffer.position();
        int limit = buffer.limit();
        if (limit - pos < length + 1) {
            return Optional.empty();
        }
        return Optional.of(buffer.slice(pos + 1, length));
    }

    /**
     * Returns the version of the first packet in the buffer.
     * This method doesn't advance the cursor.
     * Returns 0 if the version is 0 (version negotiation packet),
     * or if the version cannot be determined.
     * The packet is expected to start at the buffer's current position.
     *
     * @param buffer the buffer containing the packet.
     * @return the version of the packet in the buffer, or 0.
     * @implNote This is equivalent to calling:
     *        {@code peekVersion(buffer, buffer.position())}.
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8999">
     *        RFC 8999: Version-Independent Properties of QUIC</a>
     */
    public static int peekVersion(ByteBuffer buffer) {
        return peekVersion(buffer, buffer.position());
    }

    /**
     * Returns the version of the first packet in the buffer.
     * This method doesn't advance the cursor.
     * Returns 0 if the version is 0 (version negotiation packet),
     * or if the version cannot be determined.
     *
     * @param buffer the buffer containing the packet.
     * @param offset the offset at which the packet starts.
     * @return the version of the packet in the buffer, or 0.
     * <p>Note: This method starts reading at the offset but respects
     *        the buffer limit. The buffer limit must allow for reading
     *        the header byte and version number starting at the offset.
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8999">
     *        RFC 8999: Version-Independent Properties of QUIC</a>
     */
    public static int peekVersion(ByteBuffer buffer, int offset) {
        int limit = buffer.limit();
        if (limit - offset < 5) {
            return 0;
        }
        QuicPacket.HeadersType headersType = peekHeaderType(buffer, offset);
        if (headersType == QuicPacket.HeadersType.LONG) {
            return buffer.getInt(offset + 1);
        }
        return 0;
    }

    /**
     * Returns a decoder for the given Quic version.
     *
     * @param quicVersion the Quic protocol version number
     * @return a decoder for the given Quic version
     */
    public static QuicPacketDecoder of(QuicVersion quicVersion) {
        return switch (quicVersion) {
            case QUIC_V1 -> Decoders.QUIC_V1_DECODER;
            case QUIC_V2 -> Decoders.QUIC_V2_DECODER;
            default -> throw new IllegalArgumentException("No packet decoder for Quic version " + quicVersion.text());
        };
    }

    /**
     * Returns a {@code QuicPacketDecoder} to decode the packet
     * starting at the specified offset in the buffer.
     * This method will attempt to read the quic version in the
     * packet in order to return the proper decoder.
     * If the version is 0, then the decoder for Quic Version 1
     * is returned.
     *
     * @param buffer A buffer containing a Quic packet
     * @param offset The offset at which the packet starts
     * @return a {@code QuicPacketDecoder} instance to decode the
     *        packet starting at the given offset, or {@link Optional#empty()}
     *        when the encoded version is unsupported
     */
    public static Optional<QuicPacketDecoder> of(ByteBuffer buffer, int offset) {
        var version = peekVersion(buffer, offset);
        if (version == 0) {
            return Optional.of(of(QuicVersion.QUIC_V1));
        }
        return QuicVersion.of(version).map(QuicPacketDecoder::of);
    }

    /**
     * Decode the contents of the given {@code ByteBuffer} and, depending on the
     * {@link PacketType}, return a {@link QuicPacket} with the corresponding type.
     * This method removes packet protection and decrypt the packet encoded into
     * the provided byte buffer as appropriate.
     *
     * <p> If successful, an {@code IncomingQuicPacket} instance is returned.
     * The position of the buffer is moved to the first byte following the last
     * decoded byte. The buffer limit is unchanged.
     * Decoded STREAM payloads borrow their ranges in the supplied backing storage; the caller must not modify those ranges
     * while the returned packet or any of its frames are in use. A receive path that retains a frame must canonicalize its
     * payload ownership first.
     *
     * <p> Otherwise, an exception is thrown. The position of the buffer is unspecified,
     * but is usually set at the place where the error occurred.
     *
     * @param buffer  the buffer with the bytes to be decoded
     * @param context the decoding context
     * @return decoded incoming packet, or {@link Optional#empty()} when the packet is discarded or cannot be processed in
     *         the current state
     * @throws QuicPacketDecodeException   if an unauthenticated or discardable packet cannot be decoded
     * @throws BufferUnderflowException    if buffer does not have enough bytes
     * @throws QuicKeyUnavailableException if the packet cannot be decoded because decryption keys are unavailable
     * @throws QuicTransportException      if packet is correctly signed but malformed
     * <p>Note: If successful, and the limit was not reached, this method should be
     *        called again to decode the next packet contained in the buffer. Otherwise, if
     *        an exception occurs, the remaining bytes in the buffer should be dropped, since
     *        the position of the next packet in the buffer cannot be determined with
     *        certainty.
     * @see <a href="https://www.rfc-editor.org/info/rfc9000">
     *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport</a>
     * @see <a href="https://www.rfc-editor.org/info/rfc9001">
     *        RFC 9001: Using TLS to Secure QUIC</a>
     * @see <a href="https://www.rfc-editor.org/info/rfc9369">
     *        RFC 9369: QUIC Version 2</a>
     */
    public Optional<IncomingQuicPacket> decode(ByteBuffer buffer, CodingContext context)
            throws QuicKeyUnavailableException, QuicTransportException {
        return decode(buffer, context, "");
    }

    /**
     * Decode the contents of the given {@code ByteBuffer} and, depending on the
     * {@link PacketType}, return a {@link QuicPacket} with the corresponding type.
     * This method removes packet protection and decrypt the packet encoded into
     * the provided byte buffer as appropriate.
     * Decoded STREAM payloads borrow their ranges in the supplied backing storage; the caller must not modify those ranges
     * while the returned packet or any of its frames are in use. A receive path that retains a frame must canonicalize its
     * payload ownership first.
     *
     * @param buffer  the buffer with the bytes to be decoded
     * @param context the decoding context
     * @param logTag  log tag to prepend to decoder diagnostics
     * @return decoded incoming packet, or {@link Optional#empty()} when the packet is discarded or cannot be processed in
     *         the current state
     * @throws QuicPacketDecodeException   if an unauthenticated or discardable packet cannot be decoded
     * @throws BufferUnderflowException    if buffer does not have enough bytes
     * @throws QuicKeyUnavailableException if the packet cannot be decoded because decryption keys are unavailable
     * @throws QuicTransportException      if packet is correctly signed but malformed
     */
    public Optional<IncomingQuicPacket> decode(ByteBuffer buffer, CodingContext context, String logTag)
            throws QuicKeyUnavailableException, QuicTransportException {
        return decode(buffer, context, logTag, false);
    }

    /**
     * Decode a packet from transport-owned storage.
     *
     * <p>A successfully decoded packet may retain read-only frame payload slices backed by the supplied buffer. The caller may
     * continue decoding non-overlapping packet ranges, but must not overwrite or reuse the backing storage while the returned
     * packet or any of its frames remain reachable.
     *
     * @param buffer  transport-owned buffer with the bytes to be decoded
     * @param context decoding context
     * @param logTag  log tag to prepend to decoder diagnostics
     * @return decoded incoming packet, or {@link Optional#empty()} when the packet is discarded or cannot be processed in
     *         the current state
     * @throws QuicPacketDecodeException   if an unauthenticated or discardable packet cannot be decoded
     * @throws BufferUnderflowException    if the buffer does not have enough bytes
     * @throws QuicKeyUnavailableException if packet decryption keys are unavailable
     * @throws QuicTransportException      if the packet is authenticated but malformed
     */
    @Api.Internal
    public Optional<IncomingQuicPacket> decodeOwned(ByteBuffer buffer, CodingContext context, String logTag)
            throws QuicKeyUnavailableException, QuicTransportException {
        return decode(buffer, context, logTag, true);
    }

    /**
     * Peek at the size of the first packet present in the buffer.
     * The position of the buffer must be at the first byte of the
     * first packet. This method doesn't advance the buffer position.
     *
     * @param buffer A byte buffer containing quic packets
     * @return the size of the first packet present in the buffer.
     */
    public int peekPacketSize(ByteBuffer buffer) {
        int pos = buffer.position();
        int limit = buffer.limit();
        int available = limit - pos;
        if (available <= 0) {
            return available;
        }
        PacketType type = peekPacketType(buffer);
        return switch (type) {
            case HANDSHAKE, INITIAL, ZERORTT -> {
                int end = peekPacketEnd(type, buffer);
                yield end - pos;
            }
            // ONERTT, RETRY, VERSIONS, NONE:
            default -> available;
        };
    }

    /**
     * Determine the packet type at the buffer's current position.
     *
     * @param buffer packet bytes to inspect
     * @return detected packet type, or {@link PacketType#NONE} if it cannot be determined
     */
    public PacketType peekPacketType(ByteBuffer buffer) {
        int offset = buffer.position();
        return peekPacketType(buffer, offset);
    }

    /**
     * Determine the packet type at the specified offset without advancing the buffer position.
     *
     * @param buffer packet bytes to inspect
     * @param offset offset of the packet start
     * @return detected packet type, or {@link PacketType#NONE} if it cannot be determined
     */
    public PacketType peekPacketType(ByteBuffer buffer, int offset) {
        if (offset < 0 || offset >= buffer.limit()) {
            return PacketType.NONE;
        }
        var headers = buffer.get(offset);
        var headersType = headersType(headers);
        if (headersType == QuicPacket.HeadersType.LONG) {
            if (isVersionNegotiation(buffer, offset)) {
                return PacketType.VERSIONS;
            }
            var version = peekVersion(buffer, offset);
            if (version != quicVersion.versionNumber()) {
                return PacketType.NONE;
            }
        }
        return packetType(headers);
    }

    /**
     * Find the length of the next packet in the buffer, and return
     * the next packet bytes as a slice of the original packet.
     * Advances the original buffer position to after the returned
     * packet.
     *
     * @param buffer a buffer containing coalesced packets
     * @param offset the offset at which the next packet starts
     * @return the next packet.
     */
    public ByteBuffer nextPacketSlice(ByteBuffer buffer, int offset) {
        return nextPacketSlice(buffer, offset, "");
    }

    /**
     * Find the length of the next packet in the buffer, and return
     * the next packet bytes as a slice of the original packet.
     * Advances the original buffer position to after the returned
     * packet.
     *
     * @param buffer a buffer containing coalesced packets
     * @param offset the offset at which the next packet starts
     * @param logTag log tag to prepend to decoder diagnostics
     * @return the next packet
     */
    public ByteBuffer nextPacketSlice(ByteBuffer buffer, int offset, String logTag) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(logTag, "logTag");
        int pos = buffer.position();
        int limit = buffer.limit();
        buffer.position(offset);
        ByteBuffer next = null;
        try {
            int size = peekPacketSize(buffer);
            debug(logTag, "next packet bytes from %d (%d/%d)",
                  offset, size, buffer.remaining());
            next = buffer.slice(offset, size);
            buffer.position(offset + size);
        } catch (Throwable tt) {
            debug(logTag, tt, "failed to peek packet size: %s", tt);
            debug(logTag, "dropping all remaining bytes (%d)", limit - pos);
            buffer.position(limit);
            next = buffer;
        }
        return next;
    }

    /**
     * Advance the bytebuffer position to the end of the packet.
     *
     * @param buffer A byte buffer containing quic packets
     * @param offset The offset at which the packet starts
     */
    public void skipPacket(ByteBuffer buffer, int offset) {
        skipPacket(buffer, offset, "");
    }

    /**
     * Advance the bytebuffer position to the end of the packet.
     *
     * @param buffer a byte buffer containing QUIC packets
     * @param offset the offset at which the packet starts
     * @param logTag log tag to prepend to decoder diagnostics
     */
    public void skipPacket(ByteBuffer buffer, int offset, String logTag) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(logTag, "logTag");
        int pos = buffer.position();
        int limit = buffer.limit();
        buffer.position(offset);
        try {
            int size = peekPacketSize(buffer);
            debug(logTag, "dropping packet bytes from %d (%d/%d)",
                  offset, size, buffer.remaining());
            buffer.position(offset + size);
        } catch (Throwable tt) {
            debug(logTag, tt, "failed to peek packet size: %s", tt);
            debug(logTag, "dropping all remaining bytes (%d)", limit - pos);
            buffer.position(limit);
        }
    }

    private static boolean isLoggable(System.Logger.Level level) {
        return LOGGER.isLoggable(level);
    }

    private static void debug(PacketReader reader, String format, Object... params) {
        log(reader.logTag(), System.Logger.Level.DEBUG, null, format, params);
    }

    private static void debug(String logTag, String format, Object... params) {
        log(logTag, System.Logger.Level.DEBUG, null, format, params);
    }

    private static void debug(String logTag, Throwable thrown, String format, Object... params) {
        log(logTag, System.Logger.Level.DEBUG, thrown, format, params);
    }

    private static void log(String logTag, System.Logger.Level level, Throwable thrown, String format, Object... params) {
        if (!isLoggable(level)) {
            return;
        }
        String message = decorate(logTag, formatMessage(format, params));
        if (thrown == null) {
            LOGGER.log(level, message);
        } else {
            LOGGER.log(level, message, thrown);
        }
    }

    private static String decorate(String logTag, String message) {
        if (logTag == null || logTag.isBlank()) {
            return String.valueOf(message);
        }
        return "[" + logTag + "] " + message;
    }

    private static String formatMessage(String format, Object... params) {
        if (params == null || params.length == 0) {
            return String.valueOf(format);
        }
        try {
            return String.format(Locale.ROOT, format, params);
        } catch (IllegalFormatException _) {
            return format + " " + Arrays.toString(params);
        }
    }

    /**
     * Reads the headers type from the given byte.
     *
     * @param first the first byte of a quic packet
     * @return the headers type encoded in the given byte.
     */
    private static QuicPacket.HeadersType headersType(byte first) {
        int type = first & 0x80;
        return type == 0 ? QuicPacket.HeadersType.SHORT : QuicPacket.HeadersType.LONG;
    }

    /**
     * Reads a connection ID length from the connection ID length
     * byte.
     *
     * @param length the connection ID length byte.
     * @return the connection ID length
     */
    private static int connectionIdLength(byte length) {
        // length is represented by an unsigned byte.
        return length & 0xFF;
    }

    /**
     * Returns true if the first packet in the buffer is a version
     * negotiation packet.
     * This method doesn't advance the cursor.
     *
     * @param buffer the buffer containing the packet.
     * @param offset the offset at which the packet starts.
     * @return true if the first packet in the buffer is a version
     *        negotiation packet.
     * <p>Note: This method starts reading at the offset but respects
     *        the buffer limit. If the packet is a long header packet,
     *        the buffer limit must allow for reading
     *        the header byte and version number starting at the offset.
     * @see <a href="https://www.rfc-editor.org/rfc/rfc8999">
     *        RFC 8999: Version-Independent Properties of QUIC</a>
     */
    private static boolean isVersionNegotiation(ByteBuffer buffer, int offset) {
        int limit = buffer.limit();
        if (limit - offset < 5) {
            return false;
        }
        QuicPacket.HeadersType headersType = peekHeaderType(buffer, offset);
        if (headersType == QuicPacket.HeadersType.LONG) {
            return buffer.getInt(offset + 1) == 0;
        }
        return false;
    }

    private static QuicConnectionId decodeConnectionID(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            throw new BufferUnderflowException();
        }

        int len = buffer.get() & 0xFF;
        if (len > buffer.remaining()) {
            throw new BufferUnderflowException();
        }
        byte[] destinationConnectionID = new byte[len];

        buffer.get(destinationConnectionID);

        return PeerConnectionId.create(destinationConnectionID);
    }

    private Optional<IncomingQuicPacket> decode(ByteBuffer buffer,
                                                CodingContext context,
                                                String logTag,
                                                boolean payloadOwned)
            throws QuicKeyUnavailableException, QuicTransportException {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(logTag, "logTag");

        PacketType type = peekPacketType(buffer);
        PacketReader packetReader = new PacketReader(buffer, context, type, logTag, payloadOwned);

        if (type == PacketType.ZERORTT && context.tlsEngine().clientMode()) {
            debug(packetReader, "QuicPacketDecoder.decode(%s): client mode ignores incoming 0-RTT packets", packetReader);
            return Optional.empty();
        }

        QuicTLSEngine.KeySpace keySpace = type.keySpace().orElse(null);
        if (keySpace != null && !context.tlsEngine().keysAvailable(keySpace)) {
            debug(packetReader, "QuicPacketDecoder.decode(%s): no keys, skipping", packetReader);
            return Optional.empty();
        }

        return Optional.ofNullable(switch (type) {
            case RETRY -> IncomingRetryPacket.decode(packetReader, context);
            case ONERTT -> IncomingOneRttPacket.decode(packetReader, context);
            case ZERORTT -> IncomingZeroRttPacket.decode(packetReader, context);
            case HANDSHAKE -> IncomingHandshakePacket.decode(packetReader, context);
            case INITIAL -> IncomingInitialPacket.decode(packetReader, context);
            case VERSIONS -> IncomingVersionNegotiationPacket.decode(packetReader, context);
            case NONE -> throw new QuicPacketDecodeException("Unknown type: " + type.text());
            default -> throw new QuicPacketDecodeException("Not implemented: " + type.text());
        });
    }

    /**
     * Reads the Quic V1 packet type from the given byte.
     *
     * @param headerByte the first byte of a quic packet
     * @return the packet type encoded in the given byte.
     */
    private PacketType packetType(byte headerByte) {
        int htype = headerByte & 0xC0;
        int ptype = headerByte & 0xF0;
        return switch (htype) {
            case 0xC0 -> switch (quicVersion) {
                case QUIC_V1 -> switch (ptype) {
                    case 0xC0 -> PacketType.INITIAL;
                    case 0xD0 -> PacketType.ZERORTT;
                    case 0xE0 -> PacketType.HANDSHAKE;
                    case 0xF0 -> PacketType.RETRY;
                    default -> PacketType.NONE;
                };
                case QUIC_V2 -> switch (ptype) {
                    case 0xD0 -> PacketType.INITIAL;
                    case 0xE0 -> PacketType.ZERORTT;
                    case 0xF0 -> PacketType.HANDSHAKE;
                    case 0xC0 -> PacketType.RETRY;
                    default -> PacketType.NONE;
                };
            };
            case 0x40 -> PacketType.ONERTT; // may be a stateless reset too
            default -> PacketType.NONE;
        };
    }

    /**
     * Returns the position just after the first packet present in the buffer.
     *
     * @param type   the first packet type. Must be INITIAL, HANDSHAKE, or ZERORTT.
     * @param buffer the byte buffer containing the packet
     * @return the position just after the first packet present in the buffer.
     */
    private int peekPacketEnd(PacketType type, ByteBuffer buffer) {
        // Store initial position to calculate size of packet decoded
        int initialPosition = buffer.position();
        int limit = buffer.limit();
        // This case should have been handled by the caller

        int pos = initialPosition;     // header bits
        pos = pos + 4;                 // version
        pos = pos + 1;                 // dcid length
        if (pos <= 0 || pos >= limit) {
            return limit;
        }
        int dcidlen = buffer.get(pos) & 0xFF;
        pos = pos + dcidlen + 1;       // scid length
        if (pos <= 0 || pos >= limit) {
            return limit;
        }
        int scidlen = buffer.get(pos) & 0xFF;
        pos = pos + scidlen + 1;       // token length or packet length
        if (pos <= 0 || pos >= limit) {
            return limit;
        }

        if (type == PacketType.INITIAL) {
            int tksize = VariableLengthEncoder.peekEncodedValueSize(buffer, pos);
            if (tksize <= 0 || tksize > 8) {
                return limit;
            }
            if (limit - tksize < pos) {
                return limit;
            }
            long tklen = VariableLengthEncoder.peekEncodedValue(buffer, pos);
            if (tklen < 0 || tklen > limit - pos) {
                return limit;
            }
            pos = pos + tksize + (int) tklen; // packet length
            if (pos <= 0 || pos >= limit) {
                return limit;
            }
        }

        int lensize = VariableLengthEncoder.peekEncodedValueSize(buffer, pos);
        if (lensize <= 0 || lensize > 8) {
            return limit;
        }
        long len = VariableLengthEncoder.peekEncodedValue(buffer, pos);
        if (len < 0 || len > limit - pos) {
            return limit;
        }
        pos = pos + lensize + (int) len; // end of packet
        if (pos <= 0 || pos >= limit) {
            return limit;
        }
        return pos;
    }

    /**
     * Allocation-free result of peeking at a long packet header.
     */
    public sealed interface LongHeaderResult permits LongHeader, EmptyLongHeaderResult {
        /**
         * Whether a complete long header is available.
         *
         * @return {@code true} if a complete long header is available
         */
        boolean isPresent();

        /**
         * Whether a complete long header is unavailable.
         *
         * @return {@code true} if a complete long header is unavailable
         */
        default boolean isEmpty() {
            return !isPresent();
        }

        /**
         * Returns the complete long header.
         *
         * @return complete long header
         * @throws NoSuchElementException if no complete long header is available
         */
        LongHeader orElseThrow();
    }

    /**
     * Base type for packets decoded from an inbound QUIC datagram.
     */
    public abstract static class IncomingQuicPacket implements QuicPacket {
        private final QuicConnectionId destinationId;

        /**
         * Creates an incoming packet with a decoded destination connection ID.
         *
         * @param destinationId destination connection ID
         */
        protected IncomingQuicPacket(QuicConnectionId destinationId) {
            this.destinationId = destinationId;
        }

        @Override
        public final QuicConnectionId destinationId() {
            return destinationId;
        }
    }

    private static final class EmptyLongHeaderResult implements LongHeaderResult {
        @Override
        public boolean isPresent() {
            return false;
        }

        @Override
        public LongHeader orElseThrow() {
            throw new NoSuchElementException("No complete long header is available");
        }
    }

    private abstract static class IncomingLongHeaderPacket
            extends IncomingQuicPacket implements LongHeaderPacket {

        private final QuicConnectionId sourceId;
        private final int version;

        IncomingLongHeaderPacket(QuicConnectionId sourceId,
                                 QuicConnectionId destinationId,
                                 int version) {
            super(destinationId);
            this.sourceId = sourceId;
            this.version = version;
        }

        @Override
        public final QuicConnectionId sourceId() {
            return sourceId;
        }

        @Override
        public final int version() {
            return version;
        }
    }

    private abstract static class IncomingShortHeaderPacket
            extends IncomingQuicPacket implements ShortHeaderPacket {

        IncomingShortHeaderPacket(QuicConnectionId destinationId) {
            super(destinationId);
        }
    }

    private static final class IncomingRetryPacket
            extends IncomingLongHeaderPacket implements RetryPacket {
        private final int size;
        private final byte[] retryToken;

        private IncomingRetryPacket(QuicConnectionId sourceId, QuicConnectionId destinationId,
                                    int version, int size, byte[] retryToken) {
            super(sourceId, destinationId, version);
            this.size = size;
            this.retryToken = retryToken;
        }

        /**
         * Decode a valid {@code ByteBuffer} into an {@link IncomingRetryPacket}.
         *
         * @param reader  A {@code PacketReader} to decode the {@code ByteBuffer} that contains
         *               the bytes of this packet
         * @param context the decoding context
         * @return an {@code IncomingRetryPacket} with its contents set
         *        according to the packets fields
         * @throws QuicPacketDecodeException if retry integrity validation fails
         * @throws BufferUnderflowException if buffer does not have enough bytes
         */
        static IncomingRetryPacket decode(PacketReader reader, CodingContext context)
                throws QuicTransportException {
            try {
                reader.verifyRetry();
            } catch (QuicPacketAuthenticationException e) {
                throw new QuicPacketDecodeException("Bad integrity tag", e);
            }

            int size = reader.remaining();
            debug(reader, "IncomingRetryPacket.decode(%s)", reader);

            byte headers = reader.readHeaders(); // read headers
            int version = reader.readVersion();  // read version
            debug(reader, "IncomingRetryPacket.decode(headers(%x), version(%d), %s)",
                  headers, version, reader);

            // Retrieve the destination and source connections IDs
            var destinationID = reader.readLongConnectionId();
            debug(reader, "IncomingRetryPacket.decode(dcid(%d), %s)",
                  destinationID.length(), reader);
            var sourceID = reader.readLongConnectionId();
            debug(reader, "IncomingRetryPacket.decode(scid(%d), %s)",
                  sourceID.length(), reader);

            // Retry Token
            byte[] retryToken = reader.readRetryToken();
            debug(reader, "IncomingRetryPacket.decode(retryToken(%d), %s)",
                  retryToken.length, reader);

            // Retry Integrity Tag
            byte[] retryIntegrityTag = reader.readRetryIntegrityTag();
            debug(reader, "IncomingRetryPacket.decode(retryIntegrityTag(%d), %s)",
                  retryIntegrityTag.length, reader);

            return new IncomingRetryPacket(sourceID, destinationID, version,
                                           size, retryToken);
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public byte[] retryToken() {
            return retryToken;
        }
    }

    private static final class IncomingHandshakePacket
            extends IncomingLongHeaderPacket implements HandshakePacket {

        private final int size;
        private final int length;
        private final long packetNumber;
        private final List<QuicFrame> frames;

        IncomingHandshakePacket(QuicConnectionId sourceId, QuicConnectionId destinationId,
                                int version, int length, long packetNumber, List<QuicFrame> frames, int size) {
            super(sourceId, destinationId, version);
            this.size = size;
            this.length = length;
            this.packetNumber = packetNumber;
            this.frames = List.copyOf(frames);
        }

        /**
         * Decode a valid {@code ByteBuffer} into an {@link IncomingHandshakePacket}.
         * This method removes packet protection and decrypt the packet encoded into
         * the provided byte buffer, then creates an {@code IncomingHandshakePacket}
         * with the decoded data.
         *
         * @param reader  A {@code PacketReader} to decode the {@code ByteBuffer} that contains
         *               the bytes of this packet
         * @param context the decoding context
         * @return an {@code IncomingHandshakePacket} with its contents set
         *        according to the packets fields
         * @throws QuicPacketDecodeException if packet authentication fails
         * @throws BufferUnderflowException if buffer does not have enough bytes
         * @throws QuicTransportException   if packet is correctly signed but malformed
         */
        static IncomingHandshakePacket decode(PacketReader reader, CodingContext context)
                throws QuicKeyUnavailableException, QuicTransportException {
            debug(reader, "IncomingHandshakePacket.decode(%s)", reader);

            byte headers = reader.readHeaders(); // read headers
            int version = reader.readVersion();  // read version
            debug(reader, "IncomingHandshakePacket.decode(headers(%x), version(%d), %s)",
                  headers, version, reader);

            // Retrieve the destination and source connections IDs
            var destinationID = reader.readLongConnectionId();
            debug(reader, "IncomingHandshakePacket.decode(dcid(%d), %s)",
                  destinationID.length(), reader);
            var sourceID = reader.readLongConnectionId();
            debug(reader, "IncomingHandshakePacket.decode(scid(%d), %s)",
                  sourceID.length(), reader);

            // Get length of packet number and payload
            var packetLength = reader.readPacketLength();
            debug(reader, "IncomingHandshakePacket.decode(length(%d), %s)",
                  packetLength, reader);

            // Remove protection before reading packet number
            reader.unprotectLong(packetLength);

            // re-read headers, now that protection is removed
            headers = reader.headers();
            debug(reader, "IncomingHandshakePacket.decode([unprotected]headers(%x), %s)",
                  headers, reader);

            // Packet Number
            var packetNumberLength = reader.packetNumberLength();
            var packetNumber = reader.readPacketNumber(packetNumberLength);
            debug(reader, "IncomingHandshakePacket.decode(packetNumberLength(%d), packetNumber(%d), %s)",
                  packetNumberLength, packetNumber, reader);

            // Calculate payload length and retrieve payload
            int payloadLen = (int) (packetLength - packetNumberLength);
            debug(reader, "IncomingHandshakePacket.decode(payloadLen(%d), %s)",
                  payloadLen, reader);
            ByteBuffer payload;
            try {
                payload = reader.decryptPayload(packetNumber, payloadLen, -1 /* key phase */);
            } catch (QuicPacketAuthenticationException e) {
                throw new QuicPacketDecodeException("Bad AEAD tag", e);
            }
            // check reserved bits after checking integrity, see RFC 9000, section 17.2
            if ((headers & 0xc) != 0) {
                throw new QuicTransportException("Nonzero reserved bits in packet header",
                                                 QuicTLSEngine.KeySpace.HANDSHAKE, 0, QuicTransportErrors.PROTOCOL_VIOLATION);
            }

            List<QuicFrame> frames = reader.parsePayloadSlice(payload);

            // Finally, get the size (in bytes) of new packet
            var size = reader.bytesRead();

            return new IncomingHandshakePacket(sourceID, destinationID,
                                               version, (int) packetLength, packetNumber, frames, size);
        }

        @Override
        public int length() {
            return length;
        }

        @Override
        public long packetNumber() {
            return packetNumber;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public List<QuicFrame> frames() {
            return frames;
        }
    }

    private static final class IncomingZeroRttPacket
            extends IncomingLongHeaderPacket implements ZeroRttPacket {

        private final int size;
        private final int length;
        private final long packetNumber;
        private final List<QuicFrame> frames;

        IncomingZeroRttPacket(QuicConnectionId sourceId, QuicConnectionId destinationId,
                              int version, int length, long packetNumber, List<QuicFrame> frames, int size) {
            super(sourceId, destinationId, version);
            this.size = size;
            this.length = length;
            this.packetNumber = packetNumber;
            this.frames = List.copyOf(frames);
        }

        /**
         * Decode a valid {@code ByteBuffer} into an {@link IncomingZeroRttPacket}.
         * This method removes packet protection and decrypt the packet encoded into
         * the provided byte buffer, then creates an {@code IncomingZeroRttPacket}
         * with the decoded data.
         *
         * @param reader  A {@code PacketReader} to decode the {@code ByteBuffer} that contains
         *               the bytes of this packet
         * @param context the decoding context
         * @return an {@code IncomingZeroRttPacket} with its contents set
         *        according to the packets fields
         * @throws QuicPacketDecodeException if packet authentication fails
         * @throws BufferUnderflowException if buffer does not have enough bytes
         * @throws QuicTransportException   if packet is correctly signed but malformed
         */
        static IncomingZeroRttPacket decode(PacketReader reader, CodingContext context)
                throws QuicKeyUnavailableException, QuicTransportException {

            debug(reader, "IncomingZeroRttPacket.decode(%s)", reader);

            byte headers = reader.readHeaders(); // read headers
            int version = reader.readVersion();  // read version
            debug(reader, "IncomingZeroRttPacket.decode(headers(%x), version(%d), %s)",
                  headers, version, reader);

            // Retrieve the destination and source connections IDs
            var destinationID = reader.readLongConnectionId();
            debug(reader, "IncomingZeroRttPacket.decode(dcid(%d), %s)",
                  destinationID.length(), reader);
            var sourceID = reader.readLongConnectionId();
            debug(reader, "IncomingZeroRttPacket.decode(scid(%d), %s)",
                  sourceID.length(), reader);

            // Get length of packet number and payload
            var length = reader.readPacketLength();
            debug(reader, "IncomingZeroRttPacket.decode(length(%d), %s)",
                  length, reader);

            // Remove protection before reading packet number
            reader.unprotectLong(length);

            // re-read headers, now that protection is removed
            headers = reader.headers();
            debug(reader, "IncomingZeroRttPacket.decode([unprotected]headers(%x), %s)",
                  headers, reader);

            // Packet Number
            var packetNumberLength = reader.packetNumberLength();
            var packetNumber = reader.readPacketNumber(packetNumberLength);
            debug(reader, "IncomingZeroRttPacket.decode(packetNumberLength(%d), packetNumber(%d), %s)",
                  packetNumberLength, packetNumber, reader);

            // Calculate payload length and retrieve payload
            int payloadLen = (int) (length - packetNumberLength);
            debug(reader, "IncomingZeroRttPacket.decode(payloadLen(%d), %s)",
                  payloadLen, reader);
            ByteBuffer payload = null;
            try {
                payload = reader.decryptPayload(packetNumber, payloadLen, -1 /* key phase */);
            } catch (QuicPacketAuthenticationException e) {
                throw new QuicPacketDecodeException("Bad AEAD tag", e);
            }
            // check reserved bits after checking integrity, see RFC 9000, section 17.2
            if ((headers & 0xc) != 0) {
                throw new QuicTransportException("Nonzero reserved bits in packet header",
                                                 QuicTLSEngine.KeySpace.ZERO_RTT, 0, QuicTransportErrors.PROTOCOL_VIOLATION);
            }
            List<QuicFrame> frames = reader.parsePayloadSlice(payload);

            // Finally, get the size (in bytes) of new packet
            var size = reader.bytesRead();

            return new IncomingZeroRttPacket(sourceID, destinationID,
                                             version, (int) length, packetNumber, frames, size);
        }

        @Override
        public int length() {
            return length;
        }

        @Override
        public long packetNumber() {
            return packetNumber;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public List<QuicFrame> frames() {
            return frames;
        }
    }

    private static final class IncomingOneRttPacket
            extends IncomingShortHeaderPacket implements OneRttPacket {

        private final int size;
        private final long packetNumber;
        private final List<QuicFrame> frames;
        private final int keyPhase;
        private final int spin;

        IncomingOneRttPacket(QuicConnectionId destinationId,
                             long packetNumber, List<QuicFrame> frames,
                             int spin, int keyPhase, int size) {
            super(destinationId);
            this.keyPhase = keyPhase;
            this.spin = spin;
            this.size = size;
            this.packetNumber = packetNumber;
            this.frames = frames;
        }

        /**
         * Decode a valid {@code ByteBuffer} into an {@link IncomingOneRttPacket}.
         * This method removes packet protection and decrypt the packet encoded into
         * the provided byte buffer, then creates an {@code IncomingOneRttPacket}
         * with the decoded data.
         *
         * @param reader  A {@code PacketReader} to decode the {@code ByteBuffer} that contains
         *               the bytes of this packet
         * @param context the decoding context
         * @return an {@code IncomingOneRttPacket} with its contents set
         *        according to the packets fields
         * @throws QuicPacketDecodeException if packet authentication fails
         * @throws BufferUnderflowException if buffer does not have enough bytes
         * @throws QuicTransportException   if packet is correctly signed but malformed
         */
        static IncomingOneRttPacket decode(PacketReader reader, CodingContext context)
                throws QuicKeyUnavailableException, QuicTransportException {

            debug(reader, "IncomingOneRttPacket.decode(%s)", reader);

            byte headers = reader.readHeaders(); // read headers
            debug(reader, "IncomingOneRttPacket.decode(headers(%x), %s)",
                  headers, reader);

            // Retrieve the destination and source connections IDs
            var destinationID = reader.readShortConnectionId();
            debug(reader, "IncomingOneRttPacket.decode(dcid(%d), %s)",
                  destinationID.length(), reader);

            // Remove protection before reading packet number
            reader.unprotectShort();

            // re-read headers, now that protection is removed
            headers = reader.headers();
            debug(reader, "IncomingOneRttPacket.decode([unprotected]headers(%x), %s)",
                  headers, reader);
            // Packet Number
            var packetNumberLength = reader.packetNumberLength();
            var packetNumber = reader.readPacketNumber(packetNumberLength);
            debug(reader, "IncomingOneRttPacket.decode(packetNumberLength(%d), packetNumber(%d), %s)",
                  packetNumberLength, packetNumber, reader);

            // Calculate payload length and retrieve payload
            int payloadLen = reader.remaining();
            debug(reader, "IncomingOneRttPacket.decode(payloadLen(%d), %s)",
                  payloadLen, reader);
            int keyPhase = (headers & 0x04) >> 2;
            // keyphase is a 1 bit structure, so only 0 or 1 are valid values
            int spin = (headers & 0x20) >> 5;

            ByteBuffer payload = null;
            try {
                payload = reader.decryptPayload(packetNumber, payloadLen, keyPhase);
            } catch (QuicPacketAuthenticationException e) {
                throw new QuicPacketDecodeException("Bad AEAD tag", e);
            }
            // check reserved bits after checking integrity, see RFC 9000, section 17.3.1
            if ((headers & 0x18) != 0) {
                throw new QuicTransportException("Nonzero reserved bits in packet header",
                                                 QuicTLSEngine.KeySpace.ONE_RTT, 0, QuicTransportErrors.PROTOCOL_VIOLATION);
            }
            List<QuicFrame> frames = reader.parsePayloadSlice(payload);

            // Finally, get the size (in bytes) of new packet
            var size = reader.bytesRead();

            return new IncomingOneRttPacket(destinationID, packetNumber, frames, spin, keyPhase, size);
        }

        public long packetNumber() {
            return packetNumber;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public int keyPhase() {
            return keyPhase;
        }

        @Override
        public int spin() {
            return spin;
        }

        @Override
        public List<QuicFrame> frames() {
            return frames;
        }
    }

    private static final class IncomingInitialPacket
            extends IncomingLongHeaderPacket implements InitialPacket {

        private final int size;
        private final int length;
        private final int tokenLength;
        private final long packetNumber;
        private final byte[] token;
        private final List<QuicFrame> frames;

        IncomingInitialPacket(QuicConnectionId sourceId,
                              QuicConnectionId destinationId, int version,
                              int tokenLength, byte[] token, int length,
                              long packetNumber, List<QuicFrame> frames, int size) {
            super(sourceId, destinationId, version);
            this.size = size;
            this.length = length;
            this.tokenLength = tokenLength;
            this.token = Objects.requireNonNull(token, "token");
            this.packetNumber = packetNumber;
            this.frames = List.copyOf(frames);
        }

        /**
         * Decode a valid {@code ByteBuffer} into an {@link IncomingInitialPacket}.
         * This method removes packet protection and decrypt the packet encoded into
         * the provided byte buffer, then creates an {@code IncomingInitialPacket}
         * with the decoded data.
         *
         * @param reader  A {@code PacketReader} to decode the {@code ByteBuffer} that contains
         *               the bytes of this packet
         * @param context the decoding context
         * @return an {@code IncomingInitialPacket} with its contents set according to the packet fields, or {@code null}
         *         when the decoding context rejects the token and the packet must be discarded
         * @throws QuicPacketDecodeException if packet authentication fails
         * @throws BufferUnderflowException if buffer does not have enough bytes
         * @throws QuicTransportException   if packet is correctly signed but malformed
         */
        static IncomingInitialPacket decode(PacketReader reader, CodingContext context)
                throws QuicKeyUnavailableException, QuicTransportException {

            debug(reader, "IncomingInitialPacket.decode(%s)", reader);

            byte headers = reader.readHeaders(); // read headers
            int version = reader.readVersion();  // read version
            debug(reader, "IncomingInitialPacket.decode([protected]headers(%x), version(%d), %s)",
                  headers, version, reader);

            // Retrieve the destination and source connections IDs
            var destinationID = reader.readLongConnectionId();
            debug(reader, "IncomingInitialPacket.decode(dcid(%d), %s)",
                  destinationID.length(), reader);
            var sourceID = reader.readLongConnectionId();
            debug(reader, "IncomingInitialPacket.decode(scid(%d), %s)",
                  sourceID.length(), reader);

            // Get number of bytes needed to store the length of the token
            var tokenLength = (int) reader.readTokenLength();
            debug(reader, "IncomingInitialPacket.decode(token-length(%d), %s)",
                  tokenLength, reader);
            var token = reader.readToken(tokenLength);
            debug(reader, "IncomingInitialPacket.decode(token(%d), %s)",
                  token.length, reader);

            // Get length of packet number and payload
            var packetLength = reader.readPacketLength();
            debug(reader, "IncomingInitialPacket.decode(packetLength(%d), %s)",
                  packetLength, reader);
            if (packetLength > reader.remaining()) {
                debug(reader, "IncomingInitialPacket rejected, invalid length(%d/%d), %s)",
                      packetLength, reader.remaining(), reader);
                throw new BufferUnderflowException();
            }

            // get the size (in bytes) of new packet
            int size = reader.bytesRead() + (int) packetLength;

            if (!context.verifyToken(destinationID, token)) {
                debug(reader,
                      "IncomingInitialPacket rejected, invalid token (%d bytes), %s",
                      token.length,
                      reader);
                if (context.unsafeRawData() && isLoggable(System.Logger.Level.TRACE)) {
                    log(reader.logTag(),
                        System.Logger.Level.TRACE,
                        null,
                        "UNSAFE raw rejected Initial packet token: %s",
                        HexFormat.of().formatHex(token));
                }
                return null;
            }

            // Remove protection before reading packet number
            reader.unprotectLong(packetLength);

            // re-read headers, now that protection is removed
            headers = reader.headers();
            debug(reader, "IncomingInitialPacket.decode([unprotected]headers(%x), %s)",
                  headers, reader);

            // Packet Number
            int packetNumberLength = reader.packetNumberLength();
            var packetNumber = reader.readPacketNumber(packetNumberLength);
            debug(reader, "IncomingInitialPacket.decode(packetNumberLength(%d), packetNumber(%d), %s)",
                  packetNumberLength, packetNumber, reader);

            // Calculate payload length and retrieve payload
            int payloadLen = (int) (packetLength - packetNumberLength);
            debug(reader, "IncomingInitialPacket.decode(payloadLen(%d), %s)",
                  payloadLen, reader);
            ByteBuffer payload = null;
            try {
                payload = reader.decryptPayload(packetNumber, payloadLen, -1 /* key phase */);
            } catch (QuicPacketAuthenticationException e) {
                throw new QuicPacketDecodeException("Bad AEAD tag", e);
            }
            // check reserved bits after checking integrity, see RFC 9000, section 17.2
            if ((headers & 0xc) != 0) {
                throw new QuicTransportException("Nonzero reserved bits in packet header",
                                                 QuicTLSEngine.KeySpace.INITIAL, 0, QuicTransportErrors.PROTOCOL_VIOLATION);
            }
            List<QuicFrame> frames = reader.parsePayloadSlice(payload);


            return new IncomingInitialPacket(sourceID, destinationID,
                                             version, tokenLength, token, (int) packetLength, packetNumber, frames, size);
        }

        @Override
        public int tokenLength() {
            return tokenLength;
        }

        @Override
        public byte[] token() {
            return token;
        }

        @Override
        public int length() {
            return length;
        }

        @Override
        public long packetNumber() {
            return packetNumber;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public List<QuicFrame> frames() {
            return frames;
        }

    }

    private static final class IncomingVersionNegotiationPacket
            extends IncomingLongHeaderPacket
            implements VersionNegotiationPacket {

        private final int size;
        private final int[] versions;

        IncomingVersionNegotiationPacket(QuicConnectionId sourceId,
                                         QuicConnectionId destinationId,
                                         int version, int[] versions,
                                         int size) {
            super(sourceId, destinationId, version);
            this.size = size;
            this.versions = Objects.requireNonNull(versions);
        }

        /**
         * Decode a valid {@code ByteBuffer} into an {@link IncomingVersionNegotiationPacket}.
         *
         * @param reader  A {@code PacketReader} to decode the {@code ByteBuffer} that contains
         *               the bytes of this packet
         * @param context the decoding context
         * @return an {@code IncomingVersionNegotiationPacket} with its contents set
         *        according to the packets fields
         * @throws QuicPacketDecodeException if version-negotiation framing is invalid
         * @throws BufferUnderflowException if buffer does not have enough bytes
         */
        static IncomingVersionNegotiationPacket decode(PacketReader reader, CodingContext context) {

            debug(reader, "IncomingVersionNegotiationPacket.decode(%s)", reader);

            byte headers = reader.readHeaders(); // read headers
            int version = reader.readVersion();  // read version
            debug(reader, "IncomingVersionNegotiationPacket.decode(headers(%x), version(%d), %s)",
                  headers, version, reader);
            // The long header bit should be set. We should ignore the other 7 bits

            // Retrieve the destination and source connections IDs
            var destinationID = reader.readLongConnectionId();
            debug(reader, "IncomingVersionNegotiationPacket.decode(dcid(%d), %s)",
                  destinationID.length(), reader);
            var sourceID = reader.readLongConnectionId();
            debug(reader, "IncomingVersionNegotiationPacket.decode(scid(%d), %s)",
                  sourceID.length(), reader);

            // Calculate payload length and retrieve payload
            int payloadLen = reader.remaining();
            int versionsCount = payloadLen >> 2;
            debug(reader, "IncomingVersionNegotiationPacket.decode(payloadLen(%d), %s)",
                  payloadLen, reader);
            int[] versions = reader.readSupportedVersions();

            // Finally, get the size (in bytes) of new packet
            var size = reader.bytesRead();

            // sanity checks:
            var msg = "Bad version negotiation packet";
            if (payloadLen != versionsCount << 2) {
                throw new QuicPacketDecodeException("%s: %s bytes after %s versions"
                                                            .formatted(msg, payloadLen % 4, versionsCount));
            }
            if (versionsCount == 0) {
                throw new QuicPacketDecodeException("%s: no supported versions in packet".formatted(msg));
            }

            return new IncomingVersionNegotiationPacket(sourceID, destinationID,
                                                        version, versions, size);
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public List<QuicFrame> frames() {
            return List.of();
        }

        @Override
        public int payloadSize() {
            return versions.length << 2;
        }

        @Override
        public int[] supportedVersions() {
            return versions;
        }
    }

    private static final class Decoders {
        static final QuicPacketDecoder QUIC_V1_DECODER = new QuicPacketDecoder(QuicVersion.QUIC_V1);
        static final QuicPacketDecoder QUIC_V2_DECODER = new QuicPacketDecoder(QuicVersion.QUIC_V2);
    }

    /**
     * A {@code PacketReader} to read a Quic packet.
     * A {@code PacketReader} may have version specific code, and therefore
     * has an implicit pointer to a {@code QuicPacketDecoder} instance.
     * <p>
     * A {@code PacketReader} offers high level helper methods to read
     * data (such as Connection IDs or Packet Numbers) from a Quic packet.
     * It has however no or little knowledge of the actual packet structure.
     * It is driven by the {@code decode} method of the appropriate
     * {@code IncomingQuicPacket} type.
     * <p>
     * A {@code PacketReader} is stateful: it encapsulates a {@code ByteBuffer}
     * (or possibly a list of byte buffers - as a future enhancement) and
     * advances the position on the buffer it is reading.
     *
     */
    class PacketReader {
        private static final int PACKET_NUMBER_MASK = 0x03;
        private final ByteBuffer buffer;
        private final int offset;
        private final int initialLimit;
        private final CodingContext context;
        private final PacketType packetType;
        private final String logTag;
        private final boolean payloadOwned;

        PacketReader(ByteBuffer buffer, CodingContext context) {
            this(buffer, context, peekPacketType(buffer), "");
        }

        PacketReader(ByteBuffer buffer, CodingContext context, PacketType packetType) {
            this(buffer, context, packetType, "");
        }

        PacketReader(ByteBuffer buffer, CodingContext context, PacketType packetType, String logTag) {
            this(buffer, context, packetType, logTag, false);
        }

        PacketReader(ByteBuffer buffer,
                     CodingContext context,
                     PacketType packetType,
                     String logTag,
                     boolean payloadOwned) {
            int pos = buffer.position();
            int limit = buffer.limit();
            this.buffer = buffer;
            this.offset = pos;
            this.initialLimit = limit;
            this.context = context;
            this.packetType = packetType;
            this.logTag = Objects.requireNonNull(logTag, "logTag");
            this.payloadOwned = payloadOwned;
        }

        public int offset() {
            return offset;
        }

        public int position() {
            return buffer.position();
        }

        public int remaining() {
            return buffer.remaining();
        }

        public boolean hasRemaining() {
            return buffer.hasRemaining();
        }

        public int bytesRead() {
            return position() - offset;
        }

        public String logTag() {
            return logTag;
        }

        public void reset() {
            buffer.position(offset);
            buffer.limit(initialLimit);
        }

        public byte headers() {
            return buffer.get(offset);
        }

        public void headers(byte headers) {
            buffer.put(offset, headers);
        }

        public PacketType packetType() {
            return packetType;
        }

        public int packetNumberLength() {
            return (headers() & PACKET_NUMBER_MASK) + 1;
        }

        public byte readHeaders() {
            return buffer.get();
        }

        public int readVersion() {
            return buffer.getInt();
        }

        public int[] readSupportedVersions() {
            // Calculate payload length and retrieve payload
            int payloadLen = buffer.remaining();
            int versionsCount = payloadLen >> 2;

            int[] versions = new int[versionsCount];
            for (int i = 0; i < versionsCount; i++) {
                versions[i] = buffer.getInt();
            }
            return versions;
        }

        public long readPacketLength() {
            var packetLength = readVariableLength();
            if (packetLength > remaining()) {
                throw new BufferUnderflowException();
            }
            return packetLength;
        }

        public long readTokenLength() {
            return readVariableLength();
        }

        public byte[] readToken(int tokenLength) {
            // Check to ensure that tokenLength is within valid range
            if (tokenLength < 0 || tokenLength > buffer.remaining()) {
                throw new BufferUnderflowException();
            }
            byte[] token = tokenLength > 0 ? new byte[tokenLength] : BufferData.EMPTY_BYTES;
            if (tokenLength > 0) {
                buffer.get(token);
            }
            return token;
        }

        public long readVariableLength() {
            return VariableLengthEncoder.decode(buffer);
        }

        public long readPacketNumber(int packetNumberLength) {
            var packetNumberSpace = PacketNumberSpace.of(packetType);
            var largestProcessedPN = context.largestProcessedPN(packetNumberSpace);
            return QuicPacketNumbers.decodePacketNumber(largestProcessedPN, buffer, packetNumberLength);
        }

        public long readPacketNumber() {
            return readPacketNumber(packetNumberLength());
        }

        public List<QuicFrame> parsePayloadSlice(ByteBuffer payload)
                throws QuicTransportException {
            if (!payload.hasRemaining()) {
                throw new QuicTransportException("Packet with no frames",
                                                 packetType().keySpace().get(), 0, QuicTransportErrors.PROTOCOL_VIOLATION);
            }
            try {
                List<QuicFrame> frames = new ArrayList<>();
                int maxAckRangesPerFrame = context.maxAckRangesPerFrame();
                while (payload.hasRemaining()) {
                    try {
                        QuicFrame frame = payloadOwned
                                ? QuicFrame.decodeOwnedWithAckRangeLimit(payload, maxAckRangesPerFrame)
                                : QuicFrame.decodeWithAckRangeLimit(payload, maxAckRangesPerFrame);
                        validateFrameType(frame.typeField());
                        frames.add(frame);
                    } catch (QuicPacketDiscardException firstFailure) {
                        validateFrameType(firstFailure.frameType());
                        frames.clear();
                        // Complete structural and mandatory packet-type validation before discard. Connection-state ACK
                        // validation runs only when retained frames are applied, so it is intentionally skipped here.
                        while (payload.hasRemaining()) {
                            long frameType = QuicFrame.validateAfterAckPolicyDiscard(payload);
                            validateFrameType(frameType);
                        }
                        throw firstFailure;
                    }
                }
                return frames;
            } catch (QuicTransportException | QuicPacketDecodeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new QuicTransportException(Utils.throwableText(e),
                                                 packetType().keySpace().get(),
                                                 0,
                                                 QuicTransportErrors.INTERNAL_ERROR.code(),
                                                 e);
            }
        }

        public void verifyRetry() throws QuicPacketAuthenticationException, QuicTransportException {
            // assume the buffer position and limit are set to packet boundaries
            QuicTLSEngine tlsEngine = context.tlsEngine();
            QuicPacketTLSEngine.internal(tlsEngine)
                    .verifyRetryPacketBuffer(quicVersion,
                                             context.originalServerConnId().asReadOnlyBuffer(),
                                             buffer.asReadOnlyBuffer());
        }

        public QuicConnectionId readLongConnectionId() {
            return decodeConnectionID(buffer);
        }

        public QuicConnectionId readShortConnectionId() {
            if (!buffer.hasRemaining()) {
                throw new BufferUnderflowException();
            }

            // Retrieve connection ID length from endpoint via context
            int len = context.connectionIdLength();
            if (len > buffer.remaining()) {
                throw new BufferUnderflowException();
            }
            byte[] destinationConnectionID = new byte[len];

            buffer.get(destinationConnectionID);

            return PeerConnectionId.create(destinationConnectionID);
        }

        @Override
        public String toString() {
            return "PacketReader(offset=%s, pos=%s, remaining=%s)"
                    .formatted(offset, position(), remaining());
        }

        public void unprotectLong(long packetLength)
                throws QuicKeyUnavailableException, QuicTransportException {
            unprotect(packetLength, (byte) 0x0f);
        }

        public void unprotectShort()
                throws QuicKeyUnavailableException, QuicTransportException {
            unprotect(buffer.remaining(), (byte) 0x1f);
        }

        byte[] readRetryToken() {
            var tokenLength = buffer.limit() - buffer.position() - 16;
            byte[] retryToken = new byte[tokenLength];
            buffer.get(retryToken);
            return retryToken;
        }

        byte[] readRetryIntegrityTag() {
            // The 16 last bytes in the datagram payload
            byte[] retryIntegrityTag = new byte[16];
            buffer.get(retryIntegrityTag);
            return retryIntegrityTag;
        }

        private void validateFrameType(long frameType) throws QuicTransportException {
            if (!QuicFrame.isValidIn(frameType, packetType)) {
                throw new QuicTransportException("Invalid frame in %s packet".formatted(packetType.text()),
                                                 packetType.keySpace().orElseThrow(),
                                                 frameType,
                                                 QuicTransportErrors.PROTOCOL_VIOLATION);
            }
        }

        private ByteBuffer peekPayloadSlice(int relativeOffset, int length) {
            int payloadStart = buffer.position() + relativeOffset;
            return buffer.slice(payloadStart, length);
        }

        private ByteBuffer decryptPayload(long packetNumber, int payloadLen, int keyPhase)
                throws QuicPacketAuthenticationException, QuicKeyUnavailableException, QuicTransportException {
            // Calculate payload length and retrieve payload
            ByteBuffer output = buffer.slice();
            // output's position is on the first byte of encrypted data
            output.mark();
            int payloadStart = buffer.position();
            buffer.position(offset);
            buffer.limit(payloadStart + payloadLen);
            // buffer's position and limit are set to the boundaries of the encrypted packet
            try {
                QuicPacketTLSEngine.internal(context.tlsEngine())
                        .decryptPacketBuffer(packetType.keySpace().get(),
                                             packetNumber,
                                             keyPhase,
                                             buffer,
                                             payloadStart - offset,
                                             output);
            } catch (BufferOverflowException e) {
                throw new QuicTransportException("Decrypted packet output buffer is too small",
                                                 packetType.keySpace().get(),
                                                 0,
                                                 QuicTransportErrors.INTERNAL_ERROR.code(),
                                                 e);
            }
            // buffer's position and limit are both at end of the packet
            output.limit(output.position());
            output.reset();
            // output's position and limit are set to the boundaries of decrypted frame data
            buffer.limit(initialLimit);
            return output;
        }

        private void unprotect(long packetLength, byte headerMask)
                throws QuicKeyUnavailableException, QuicTransportException {
            QuicTLSEngine tlsEngine = context.tlsEngine();
            int sampleSize = tlsEngine.headerProtectionSampleSize(packetType.keySpace().get());
            if (packetLength > buffer.remaining() || packetLength < sampleSize + 4) {
                throw new BufferUnderflowException();
            }
            ByteBuffer sample = peekPayloadSlice(4, sampleSize);
            long headerProtectionMask = QuicPacketTLSEngine.internal(tlsEngine)
                    .computeHeaderProtectionMaskBits(packetType.keySpace().get(), true, sample);
            byte headers = headers();
            headers ^= (byte) ((headerProtectionMask >>> 32) & headerMask);
            headers(headers);
            int packetNumberLength = packetNumberLength();
            int packetNumberStart = buffer.position();
            for (int i = 0; i < packetNumberLength; i++) {
                int shift = 24 - i * Byte.SIZE;
                buffer.put(packetNumberStart + i,
                           (byte) (buffer.get(packetNumberStart + i) ^ (headerProtectionMask >>> shift)));
            }
        }
    }
}
