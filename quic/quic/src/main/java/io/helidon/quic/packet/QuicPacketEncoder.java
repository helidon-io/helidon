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
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntFunction;

import io.helidon.common.Api;
import io.helidon.quic.CodingContext;
import io.helidon.quic.QuicConnectionId;
import io.helidon.quic.QuicKeyUnavailableException;
import io.helidon.quic.QuicTLSEngine;
import io.helidon.quic.QuicTLSEngine.KeySpace;
import io.helidon.quic.QuicTransportException;
import io.helidon.quic.QuicVersion;
import io.helidon.quic.VariableLengthEncoder;
import io.helidon.quic.frame.PaddingFrame;
import io.helidon.quic.frame.QuicFrame;
import io.helidon.quic.packet.QuicPacket.PacketNumberSpace;
import io.helidon.quic.packet.QuicPacket.PacketType;
import io.helidon.quic.spi.QuicPacketTLSEngine;

import static io.helidon.quic.packet.QuicPacketNumbers.computePacketNumberLength;
import static io.helidon.quic.packet.QuicPacketNumbers.encodePacketNumber;

/**
 * A {@code QuicPacketEncoder} encapsulates the logic to encode a
 * quic packet. A {@code QuicPacketEncoder} is typically tied to
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
public class QuicPacketEncoder {

    private static final System.Logger LOGGER = System.getLogger(QuicPacketEncoder.class.getName());

    private final QuicVersion quicVersion;

    private QuicPacketEncoder(QuicVersion quicVersion) {
        this.quicVersion = quicVersion;
    }

    /**
     * Create a new unencrypted VersionNegotiationPacket to be transmitted over the wire
     * after encryption.
     *
     * @param source      The source connection ID
     * @param destination The destination connection ID
     * @param versions    The supported quic versions
     * @return the new initial packet
     */
    public static OutgoingQuicPacket newVersionNegotiationPacket(QuicConnectionId source,
                                                                 QuicConnectionId destination,
                                                                 int[] versions) {
        return new OutgoingVersionNegotiationPacket(source, destination, versions);
    }

    /**
     * Compute the max size of the usable payload of an initial
     * packet, given the max size of the datagram.
     * <pre>
     * Initial Packet {
     *    Header (1 byte),
     *    Version (4 bytes),
     *    Destination Connection ID Length (1 byte),
     *    Destination Connection ID (0..20 bytes),
     *    Source Connection ID Length (1 byte),
     *    Source Connection ID (0..20 bytes),
     *    Token Length (variable int),
     *    Token (..),
     *    Length (variable int),
     *    Packet Number (1..4 bytes),
     *    Packet Payload (1 to ... bytes),
     * }
     * </pre>
     *
     * @param codingContext   the coding context, used to compute the
     *                       encoded packet number
     * @param pnsize          packet number length
     * @param tokenLength     the length of the token (or {@code 0})
     * @param scidLength      the length of the source connection id
     * @param dstidLength     the length of the destination connection id
     * @param maxDatagramSize the desired total maximum size
     *                       of the packet after encryption
     * @return the maximum size of the payload that can be fit into this
     *        initial packet
     */
    public static int computeMaxInitialPayloadSize(CodingContext codingContext,
                                                   int pnsize,
                                                   int tokenLength,
                                                   int scidLength,
                                                   int dstidLength,
                                                   int maxDatagramSize) {
        // header=1, version=4, len(scidlen)+len(dstidlen)=2
        int overhead = 1 + 4 + 2 + scidLength + dstidLength + tokenLength
                + VariableLengthEncoder.encodedSize(tokenLength);
        // encryption tag, included in the payload, but not usable for frames
        int tagSize = codingContext.tlsEngine().authTagSize();
        int length = maxDatagramSize - overhead - 1; // at least 1 byte for length encoding
        if (length <= 0) {
            return 0;
        }
        int lenbefore = VariableLengthEncoder.encodedSize(length);
        length = length - lenbefore + 1; // discount length encoding
        if (length <= 0) {
            return 0;
        }
        int available = length - pnsize - tagSize;
        if (available < 0) {
            return 0;
        }
        return available;
    }

    /**
     * Compute the max size of the usable payload of a handshake
     * packet, given the max size of the datagram.
     * <pre>
     * Initial Packet {
     *    Header (1 byte),
     *    Version (4 bytes),
     *    Destination Connection ID Length (1 byte),
     *    Destination Connection ID (0..20 bytes),
     *    Source Connection ID Length (1 byte),
     *    Source Connection ID (0..20 bytes),
     *    Length (variable int),
     *    Packet Number (1..4 bytes),
     *    Packet Payload (1 to ... bytes),
     * }
     * </pre>
     *
     * @param codingContext   the coding context, used to compute the
     *                       encoded packet number
     * @param packetNumber    the full packet number
     * @param scidLength      the length of the source connection id
     * @param dstidLength     the length of the destination connection id
     * @param maxDatagramSize the desired total maximum size
     *                       of the packet after encryption
     * @return the maximum size of the payload that can be fit into this
     *        initial packet
     */
    public static int computeMaxHandshakePayloadSize(CodingContext codingContext,
                                                     long packetNumber,
                                                     int scidLength,
                                                     int dstidLength,
                                                     int maxDatagramSize) {
        // header=1, version=4, len(scidlen)+len(dstidlen)=2
        int overhead = 1 + 4 + 2 + scidLength + dstidLength;
        int pnsize = computePacketNumberLength(packetNumber,
                                               codingContext.largestAckedPN(PacketNumberSpace.HANDSHAKE));
        // encryption tag, included in the payload, but not usable for frames
        int tagSize = codingContext.tlsEngine().authTagSize();
        int length = maxDatagramSize - overhead - 1; // at least 1 byte for length encoding
        if (length < 0) {
            return 0;
        }
        int lenbefore = VariableLengthEncoder.encodedSize(length);
        length = length - lenbefore + 1; // discount length encoding
        int available = length - pnsize - tagSize;
        return available;
    }

    /**
     * Computes the maximum usable payload that can be carried on in a
     * {@link OneRttPacket} given the max datagram size before
     * encryption.
     *
     * @param codingContext                   the coding context
     * @param packetNumber                    the packet number
     * @param dstidLength                     the peer connection id length
     * @param maxDatagramSizeBeforeEncryption the maximum size of the datagram
     * @param largestPeerAckedPN              largest peer-acknowledged packet number in application space
     * @return the maximum payload that can be carried on in a
     *        {@link OneRttPacket} given the max datagram size before
     *        encryption
     */
    public static int computeMaxOneRTTPayloadSize(CodingContext codingContext,
                                                  long packetNumber,
                                                  int dstidLength,
                                                  int maxDatagramSizeBeforeEncryption,
                                                  long largestPeerAckedPN) {
        // header=1
        int overhead = 1 + dstidLength;
        // always reserve four bytes for packet number to avoid issues with packet
        // sizes when retransmitting. This is a hack, but it avoids having to
        // repack StreamFrames.
        int pnsize = 4; //computePacketNumberLength(packetNumber, largestPeerAckedPN);
        // encryption tag, included in the payload, but not usable for frames
        int tagSize = codingContext.tlsEngine().authTagSize();
        int available = maxDatagramSizeBeforeEncryption - overhead - pnsize - tagSize;
        if (available < 0) {
            return 0;
        }
        return available;
    }

    /**
     * Returns an encoder for the given Quic version.
     *
     * @param quicVersion the Quic protocol version number
     * @return an encoder for the given Quic version
     */
    public static QuicPacketEncoder of(QuicVersion quicVersion) {
        return switch (quicVersion) {
            case QUIC_V1 -> Encoders.QUIC_V1_ENCODER;
            case QUIC_V2 -> Encoders.QUIC_V2_ENCODER;
            default -> throw new IllegalArgumentException("No packet encoder for Quic version " + quicVersion.text());
        };
    }

    /**
     * Create a new unencrypted InitialPacket to be transmitted over the wire
     * after encryption.
     *
     * @param source            The source connection ID
     * @param destination       The destination connection ID
     * @param token             The token field, or a zero-length array if no token is present
     * @param packetNumber      The packet number
     * @param ackedPacketNumber The largest acknowledged packet number
     * @param frames            The initial packet payload
     * @param codingContext     coding context used for packet number encoding and key metadata
     * @return the new initial packet
     */
    public OutgoingQuicPacket newInitialPacket(QuicConnectionId source,
                                               QuicConnectionId destination,
                                               byte[] token,
                                               long packetNumber,
                                               long ackedPacketNumber,
                                               List<QuicFrame> frames,
                                               CodingContext codingContext) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(codingContext, "codingContext");
        String logTag = Objects.requireNonNull(codingContext.logTag(), "codingContext.logTag()");
        debug(logTag, "newInitialPacket: fullPN=%d ackedPN=%d", packetNumber, ackedPacketNumber);
        byte[] encodedPacketNumber = encodePacketNumber(packetNumber, ackedPacketNumber);
        QuicTLSEngine tlsEngine = codingContext.tlsEngine();
        int tagSize = tlsEngine.authTagSize();
        // https://www.rfc-editor.org/rfc/rfc9000#section-14.1
        // A client MUST expand the payload of all UDP datagrams carrying Initial packets
        // to at least the smallest allowed maximum datagram size of 1200 bytes
        // by adding PADDING frames to the Initial packet or by coalescing the Initial packet

        // first compute the packet size
        int originalPayloadSize = frames.stream()
                .mapToInt(QuicFrame::size)
                .reduce(0, Math::addExact);
        int originalLength = OutgoingInitialPacket.computeLength(originalPayloadSize,
                                                                 encodedPacketNumber.length, tagSize);
        int originalPacketSize = OutgoingInitialPacket.computePacketSize(
                new OutgoingInitialPacket.InitialPacketVariableComponents(originalLength, token,
                                                                          source, destination));
        if (originalPacketSize >= 1200) {
            return new OutgoingInitialPacket(source, destination, this.quicVersion.versionNumber(),
                                             token, packetNumber, encodedPacketNumber, frames, tagSize);
        } else {
            // add padding
            int numPaddingBytesNeeded = 1200 - originalPacketSize;
            if (originalLength < 64 && originalLength + numPaddingBytesNeeded > 64) {
                // if originalLength + numPaddingBytesNeeded == 64, will send
                //  1201 bytes
                numPaddingBytesNeeded--;
            }
            List<QuicFrame> newFrames = new ArrayList<>();
            for (QuicFrame frame : frames) {
                if (frame instanceof PaddingFrame) {
                    // a padding frame already exists, instead of including this and the new padding
                    // frame in the new frames, we just include 1 single padding frame whose
                    // combined size will be the sum of all existing padding frames and the
                    // additional padding bytes needed
                    numPaddingBytesNeeded += frame.size();
                    continue;
                }
                // non-padding frame, include it in the new frames
                newFrames.add(frame);
            }
            // add the padding frame as the first frame
            newFrames.add(0, PaddingFrame.create(numPaddingBytesNeeded));
            return new OutgoingInitialPacket(
                    source, destination, this.quicVersion.versionNumber(),
                    token, packetNumber, encodedPacketNumber, newFrames, tagSize);
        }
    }

    /**
     * Create a new unencrypted RetryPacket to be transmitted over the wire
     * after encryption.
     *
     * @param source      The source connection ID
     * @param destination The destination connection ID
     * @param retryToken  The retry token
     * @return the new retry packet
     */
    public OutgoingQuicPacket newRetryPacket(QuicConnectionId source,
                                             QuicConnectionId destination,
                                             byte[] retryToken) {
        return new OutgoingRetryPacket(
                source, destination, this.quicVersion.versionNumber(), retryToken);
    }

    /**
     * Create a new unencrypted HandshakePacket to be transmitted over the wire
     * after encryption.
     *
     * @param source         The source connection ID
     * @param destination    The destination connection ID
     * @param packetNumber   The packet number
     * @param largestAckedPN The largest packet number acknowledged by the peer
     * @param frames         The handshake packet payload
     * @param codingContext  coding context used for packet number encoding and key metadata
     * @return the new handshake packet
     */
    public OutgoingQuicPacket newHandshakePacket(QuicConnectionId source,
                                                 QuicConnectionId destination,
                                                 long packetNumber,
                                                 long largestAckedPN,
                                                 List<QuicFrame> frames, CodingContext codingContext) {
        return newHandshakePacket(source, destination, packetNumber, largestAckedPN, frames, codingContext, "");
    }

    /**
     * Create a new unencrypted HandshakePacket to be transmitted over the wire
     * after encryption.
     *
     * @param source         The source connection ID
     * @param destination    The destination connection ID
     * @param packetNumber   The packet number
     * @param largestAckedPN The largest packet number acknowledged by the peer
     * @param frames         The handshake packet payload
     * @param codingContext  coding context used for packet number encoding and key metadata
     * @param logTag         log tag to prepend to encoder diagnostics
     * @return the new handshake packet
     */
    public OutgoingQuicPacket newHandshakePacket(QuicConnectionId source,
                                                 QuicConnectionId destination,
                                                 long packetNumber,
                                                 long largestAckedPN,
                                                 List<QuicFrame> frames,
                                                 CodingContext codingContext,
                                                 String logTag) {
        Objects.requireNonNull(logTag, "logTag");
        debug(logTag, "newHandshakePacket: fullPN=%d ackedPN=%d", packetNumber, largestAckedPN);
        byte[] encodedPacketNumber = encodePacketNumber(packetNumber, largestAckedPN);
        QuicTLSEngine tlsEngine = codingContext.tlsEngine();
        int tagSize = tlsEngine.authTagSize();
        int protectionSampleSize = tlsEngine.headerProtectionSampleSize(KeySpace.HANDSHAKE);
        int minLength = 4 + protectionSampleSize - encodedPacketNumber.length - tagSize;

        return new OutgoingHandshakePacket(
                source, destination, this.quicVersion.versionNumber(),
                packetNumber, encodedPacketNumber, padFrames(frames, minLength), tagSize);
    }

    /**
     * Create a new unencrypted OneRttPacket to be transmitted over the wire
     * after encryption.
     *
     * @param destination       The destination connection ID
     * @param packetNumber      The packet number
     * @param ackedPacketNumber The largest acknowledged packet number
     * @param frames            The one RTT packet payload
     * @param codingContext     coding context used for packet number encoding and key metadata
     * @return the new one RTT packet
     */
    public OneRttPacket newOneRttPacket(QuicConnectionId destination,
                                        long packetNumber,
                                        long ackedPacketNumber,
                                        List<? extends QuicFrame> frames,
                                        CodingContext codingContext) {
        return newOneRttPacket(destination, packetNumber, ackedPacketNumber, frames, codingContext, "");
    }

    /**
     * Create a new unencrypted OneRttPacket to be transmitted over the wire
     * after encryption.
     *
     * @param destination       The destination connection ID
     * @param packetNumber      The packet number
     * @param ackedPacketNumber The largest acknowledged packet number
     * @param frames            The one RTT packet payload
     * @param codingContext     coding context used for packet number encoding and key metadata
     * @param logTag            log tag to prepend to encoder diagnostics
     * @return the new one RTT packet
     */
    public OneRttPacket newOneRttPacket(QuicConnectionId destination,
                                        long packetNumber,
                                        long ackedPacketNumber,
                                        List<? extends QuicFrame> frames,
                                        CodingContext codingContext,
                                        String logTag) {
        Objects.requireNonNull(logTag, "logTag");
        debug(logTag, "newOneRttPacket: fullPN=%d ackedPN=%d", packetNumber, ackedPacketNumber);
        byte[] encodedPacketNumber = encodePacketNumber(packetNumber, ackedPacketNumber);
        QuicTLSEngine tlsEngine = codingContext.tlsEngine();
        int tagSize = tlsEngine.authTagSize();
        int protectionSampleSize = tlsEngine.headerProtectionSampleSize(KeySpace.ONE_RTT);
        // packets should be at least 22 bytes longer than the local connection id length.
        // we ensure that by padding the frames to the necessary size
        int minPayloadSize = codingContext.minShortPacketPayloadSize(destination.length());
        int minLength = Math.max(Math.max(5, minPayloadSize),
                                 4 + protectionSampleSize - tagSize)
                - encodedPacketNumber.length;
        return new OutgoingOneRttPacket(
                destination, packetNumber,
                encodedPacketNumber, padFrames(frames, minLength), tagSize);
    }

    /**
     * Creates a packet in the given keyspace for the purpose of sending
     * a CONNECTION_CLOSE, or a generic list of frames.
     * The {@code initialToken} parameter is ignored if the key
     * space is not INITIAL.
     *
     * @param keySpace      the sending key space
     * @param packetSpace   the packet space
     * @param sourceId      the source connection id
     * @param destinationId the destination connection id
     * @param initialToken  the initial token for INITIAL packets
     * @param frames        the list of frames
     * @param codingContext the coding context
     * @return a packet in the given key space
     * @throws IllegalArgumentException if the packet number space is
     *                                 not one of INITIAL, HANDSHAKE, or APPLICATION
     */
    public OutgoingQuicPacket newOutgoingPacket(
            KeySpace keySpace,
            PacketSpace packetSpace,
            QuicConnectionId sourceId,
            QuicConnectionId destinationId,
            byte[] initialToken,
            List<QuicFrame> frames,
            CodingContext codingContext) {
        Objects.requireNonNull(initialToken, "initialToken");
        Objects.requireNonNull(codingContext, "codingContext");
        String logTag = Objects.requireNonNull(codingContext.logTag(), "codingContext.logTag()");
        long largestAckedPN = packetSpace.largestPeerAcknowledgedPacketNumber();
        return switch (packetSpace.packetNumberSpace()) {
            case APPLICATION -> {
                long newPacketNumber = packetSpace.allocateNextPN();
                if (keySpace != KeySpace.ONE_RTT) {
                    throw new IllegalArgumentException("Unsupported application key space: " + keySpace);
                }
                OneRttPacket oneRttPacket = newOneRttPacket(destinationId,
                                                            newPacketNumber,
                                                            largestAckedPN,
                                                            frames,
                                                            codingContext,
                                                            logTag);
                yield (OutgoingQuicPacket) oneRttPacket;
            }
            case HANDSHAKE -> {
                long newPacketNumber = packetSpace.allocateNextPN();
                yield newHandshakePacket(sourceId, destinationId,
                                         newPacketNumber, largestAckedPN,
                                         frames, codingContext, logTag);
            }
            case INITIAL -> {
                long newPacketNumber = packetSpace.allocateNextPN();
                yield newInitialPacket(sourceId, destinationId,
                                       initialToken, newPacketNumber,
                                       largestAckedPN,
                                       frames, codingContext);
            }
            case NONE -> {
                throw new IllegalArgumentException("packetSpace: %s, keySpace: %s"
                                                           .formatted(packetSpace.packetNumberSpace().text(),
                                                                      keySpace == null ? "null" : keySpace.text()));
            }
        };
    }

    /**
     * Encodes the given QuicPacket.
     *
     * @param packet  the packet to encode
     * @param buffer  the byte buffer to write the packet into
     * @param context context for encoding
     * @throws IllegalArgumentException    if the packet is not an OutgoingQuicPacket,
     *                                    or if the packet version does not match the encoder version
     * @throws BufferOverflowException     if the buffer is not large enough
     * @throws QuicKeyUnavailableException if the packet could not be encrypted
     *                                    because the required encryption key is not available
     * @throws QuicTransportException      if encrypting the packet resulted
     *                                    in an error that requires closing the connection
     */
    public void encode(QuicPacket packet, ByteBuffer buffer, CodingContext context)
            throws QuicKeyUnavailableException, QuicTransportException {
        encode(packet, buffer, context, "");
    }

    /**
     * Encodes the given QuicPacket.
     *
     * @param packet  the packet to encode
     * @param buffer  the byte buffer to write the packet into
     * @param context context for encoding
     * @param logTag  log tag to prepend to encoder diagnostics
     * @throws IllegalArgumentException    if the packet is not an OutgoingQuicPacket,
     *                                    or if the packet version does not match the encoder version
     * @throws BufferOverflowException     if the buffer is not large enough
     * @throws QuicKeyUnavailableException if the packet could not be encrypted
     *                                    because the required encryption key is not available
     * @throws QuicTransportException      if encrypting the packet resulted
     *                                    in an error that requires closing the connection
     */
    public void encode(QuicPacket packet, ByteBuffer buffer, CodingContext context, String logTag)
            throws QuicKeyUnavailableException, QuicTransportException {
        Objects.requireNonNull(logTag, "logTag");
        switch (packet) {
        case OutgoingOneRttPacket p -> encodePacket(p, buffer, context, logTag);
        case OutgoingVersionNegotiationPacket p -> encodePacket(p, buffer, logTag);
        case OutgoingHandshakePacket p -> encodePacket(p, buffer, context, logTag);
        case OutgoingInitialPacket p -> encodePacket(p, buffer, context, logTag);
        case OutgoingRetryPacket p -> encodePacket(p, buffer, context, logTag);
        default -> throw new IllegalArgumentException("packet is not an outgoing packet: "
                                                              + packet.getClass());
        }
    }

    private static boolean isLoggable(System.Logger.Level level) {
        return LOGGER.isLoggable(level);
    }

    private static void debug(String logTag, String format, Object... params) {
        log(logTag, System.Logger.Level.DEBUG, format, params);
    }

    private static void tracePacketNumber(CodingContext context,
                                          String logTag,
                                          PacketType packetType,
                                          byte[] encodedPacketNumber) {
        if (context.unsafeRawData() && isLoggable(System.Logger.Level.TRACE)) {
            log(logTag,
                System.Logger.Level.TRACE,
                "UNSAFE raw %s encoded packet number: %s",
                packetType,
                Arrays.toString(encodedPacketNumber));
        }
    }

    private static void log(String logTag, System.Logger.Level level, String format, Object... params) {
        if (!isLoggable(level)) {
            return;
        }
        LOGGER.log(level, decorate(logTag, formatMessage(format, params)));
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
     * Computes the packet's header byte, which also encodes
     * the packetNumber length.
     *
     * @param packetTypeTag quic-dependent packet type encoding
     * @param pnsize        the number of bytes needed to encode the packet number
     * @return the packet's header byte
     */
    private static byte headers(byte packetTypeTag, int pnsize) {
        int pnprefix = pnsize - 1;
        return (byte) (packetTypeTag | pnprefix);
    }

    /**
     * Encode the VersionNegotiationPacket into the provided
     * buffer.
     *
     * @param packet
     * @param buffer A buffer to encode the packet into.
     * @throws BufferOverflowException if the buffer is not large enough
     */
    private static void encodePacket(OutgoingVersionNegotiationPacket packet,
                                     ByteBuffer buffer,
                                     String logTag) {
        QuicConnectionId destination = packet.destinationId();
        QuicConnectionId source = packet.sourceId();

        debug(logTag, "VersionNegotiationPacket::encodePacket(ByteBuffer(%d,%d),"
                      + " src=%s, dst=%s, versions=%s, size=%d",
              buffer.position(), buffer.limit(), source, destination,
              Arrays.toString(packet.versions), packet.size);

        int offset = buffer.position();
        int typeTag = 0x80;
        int rand = Encoders.RANDOM.nextInt() & 0x7F;
        int headers = typeTag | rand;
        debug(logTag, "VersionNegotiationPacket::encodePacket:"
                      + " type: 0x%02x, unused: 0x%02x, headers: 0x%02x",
              typeTag, rand & ~0x80, headers);

        // headers(1 byte), version(4 bytes)
        buffer.put((byte) headers); // 1
        putInt32(buffer, 0); // 4

        // DCID: 1 byte for length, + destination id bytes
        var dcidlen = destination.length();
        buffer.put((byte) dcidlen); // 1
        buffer.put(destination.asReadOnlyBuffer());

        // SCID: 1 byte for length, + source id bytes
        var scidlen = source.length();
        buffer.put((byte) scidlen);
        buffer.put(source.asReadOnlyBuffer());

        // Put payload (= supported versions)
        for (int i = 0; i < packet.versions.length; i++) {
            putInt32(buffer, packet.versions[i]);
        }
        int versionsEnd = buffer.position();
        debug(logTag, "VersionNegotiationPacket::encodePacket: encoded %d bytes", offset - versionsEnd);

    }

    private static ByteBuffer putInt32(ByteBuffer buffer, int value) {
        return buffer.putInt(value);
    }

    /**
     * Adds required padding frames if necessary.
     * Needed to make sure there's enough bytes to apply header protection
     *
     * @param frames    requested list of frames
     * @param minLength requested minimum length
     * @return list of frames that meets the minimum length requirement
     */
    private static List<? extends QuicFrame> padFrames(List<? extends QuicFrame> frames, int minLength) {
        if (frames.size() >= minLength) {
            return frames;
        }
        int size = frames.stream().mapToInt(QuicFrame::size).reduce(0, Math::addExact);
        if (size >= minLength) {
            return frames;
        }
        List<QuicFrame> result = new ArrayList<>(frames.size() + 1);
        // add padding frame in front - some frames extend to end of packet
        result.add(PaddingFrame.create(minLength - size));
        result.addAll(frames);
        return result;
    }

    /**
     * Returns the headers tag for the given packet type.
     * Returns 0 if the packet type is NONE or unknown.
     * <p>
     * For version negotiations packet, this method returns 0x80.
     * The other 7 bits must be ignored by a client.
     * When emitting a version negotiation packet the server should
     * also set the fix bit (0x40) to 1.
     * What distinguishes a version negotiation packet from other
     * long header packet types is not the packet type found in the
     * header's byte, but the fact that a. it is a long header and
     * b. the version number in the packet (the 4 bytes following
     * the header) is 0.
     *
     * @param packetType the packet type
     * @return the headers tag for the given packet type.
     * @see <a href="https://www.rfc-editor.org/info/rfc9000">
     *        RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport</a>
     * @see <a href="https://www.rfc-editor.org/info/rfc9369">
     *        RFC 9369: QUIC Version 2</a>
     */
    private byte packetHeadersTag(PacketType packetType) {
        return (byte) switch (quicVersion) {
            case QUIC_V1 -> switch (packetType) {
                case ONERTT -> 0x40;
                case INITIAL -> 0xC0;
                case ZERORTT -> throw new IllegalArgumentException("0-RTT is not supported");
                case HANDSHAKE -> 0xE0;
                case RETRY -> 0xF0;
                case VERSIONS -> 0x80; // remaining bits are ignored
                case NONE -> 0x00;
            };
            case QUIC_V2 -> switch (packetType) {
                case ONERTT -> 0x40;
                case INITIAL -> 0xD0;
                case ZERORTT -> throw new IllegalArgumentException("0-RTT is not supported");
                case HANDSHAKE -> 0xF0;
                case RETRY -> 0xC0;
                case VERSIONS -> 0x80; // remaining bits are ignored
                case NONE -> 0x00;
            };
        };
    }

    /**
     * Encode the OneRttPacket into the provided buffer.
     * This method encrypts the packet into the provided byte buffer as appropriate,
     * adding packet protection as appropriate.
     *
     * @param packet
     * @param buffer  A buffer to encode the packet into
     * @param context
     * @throws BufferOverflowException if the buffer is not large enough
     */
    private void encodePacket(OutgoingOneRttPacket packet,
                              ByteBuffer buffer,
                              CodingContext context,
                              String logTag)
            throws QuicKeyUnavailableException, QuicTransportException {
        QuicConnectionId destination = packet.destinationId();

        debug(logTag, "OneRttPacket::encodePacket(ByteBuffer(%d,%d),"
                      + " dst=%s, packet=%d, encodedPacket=byte[%d],"
                      + " payload=QuicFrames(frames: %s, bytes: %d),"
                      + " size=%d",
              buffer.position(), buffer.limit(), destination,
              packet.packetNumber, packet.encodedPacketNumber.length,
              packet.frames, packet.payloadSize, packet.size);
        tracePacketNumber(context, logTag, packet.packetType(), packet.encodedPacketNumber);

        byte headers = headers(packetHeadersTag(packet.packetType()),
                               packet.encodedPacketNumber.length);

        PacketWriter writer = new PacketWriter(buffer, context, PacketType.ONERTT);
        writer.writeHeaders(headers);
        writer.writeShortConnectionId(destination);
        int packetNumberStart = writer.position();
        writer.writeEncodedPacketNumber(packet.encodedPacketNumber);
        int payloadStart = writer.position();
        writer.writePayload(packet.frames);
        writer.encryptPayload(packet.packetNumber, payloadStart);
        writer.protectHeaderShort(packetNumberStart, packet.encodedPacketNumber.length);
    }

    /**
     * Encode the HandshakePacket into the provided buffer.
     * This method encrypts the packet into the provided byte buffer as appropriate,
     * adding packet protection as appropriate.
     *
     * @param packet
     * @param buffer  A buffer to encode the packet into.
     * @param context
     * @throws BufferOverflowException if the buffer is not large enough
     */
    private void encodePacket(OutgoingHandshakePacket packet,
                              ByteBuffer buffer,
                              CodingContext context,
                              String logTag)
            throws QuicKeyUnavailableException, QuicTransportException {
        int version = packet.version();
        if (quicVersion.versionNumber() != version) {
            throw new IllegalArgumentException("Encoder version %s does not match packet version %s"
                                                       .formatted(quicVersion.text(), version));
        }
        QuicConnectionId destination = packet.destinationId();
        QuicConnectionId source = packet.sourceId();
        if (packet.size > buffer.remaining()) {
            throw new BufferOverflowException();
        }

        debug(logTag, "HandshakePacket::encodePacket(ByteBuffer(%d,%d),"
                      + " src=%s, dst=%s, version=%d, packet=%d, "
                      + "encodedPacket=byte[%d], payload=QuicFrame(frames: %s, bytes: %d),"
                      + " size=%d",
              buffer.position(), buffer.limit(), source, destination,
              version, packet.packetNumber, packet.encodedPacketNumber.length,
              packet.frames, packet.payloadSize, packet.size);
        tracePacketNumber(context, logTag, packet.packetType(), packet.encodedPacketNumber);

        byte headers = headers(packetHeadersTag(packet.packetType()),
                               packet.encodedPacketNumber.length);

        PacketWriter writer = new PacketWriter(buffer, context, PacketType.HANDSHAKE);
        writer.writeHeaders(headers);
        writer.writeVersion(version);
        writer.writeLongConnectionId(destination);
        writer.writeLongConnectionId(source);
        writer.writePacketLength(packet.length);
        int packetNumberStart = writer.position();
        writer.writeEncodedPacketNumber(packet.encodedPacketNumber);
        int payloadStart = writer.position();
        writer.writePayload(packet.frames);
        writer.encryptPayload(packet.packetNumber, payloadStart);
        writer.protectHeaderLong(packetNumberStart, packet.encodedPacketNumber.length);
    }

    /**
     * Encode the InitialPacket into the provided buffer.
     * This method encrypts the packet into the provided byte buffer as appropriate,
     * adding packet protection as appropriate.
     *
     * @param packet
     * @param buffer  A buffer to encode the packet into.
     * @param context coding context
     * @throws BufferOverflowException if the buffer is not large enough
     */
    private void encodePacket(OutgoingInitialPacket packet,
                              ByteBuffer buffer,
                              CodingContext context,
                              String logTag)
            throws QuicKeyUnavailableException, QuicTransportException {
        int version = packet.version();
        if (quicVersion.versionNumber() != version) {
            throw new IllegalArgumentException("Encoder version %s does not match packet version %s"
                                                       .formatted(quicVersion.text(), version));
        }
        QuicConnectionId destination = packet.destinationId();
        QuicConnectionId source = packet.sourceId();
        if (packet.size > buffer.remaining()) {
            throw new BufferOverflowException();
        }

        debug(logTag, "InitialPacket::encodePacket(ByteBuffer(%d,%d),"
                      + " src=%s, dst=%s, version=%d, packet=%d, "
                      + "encodedPacket=byte[%d], token=%s, "
                      + "payload=QuicFrame(frames: %s, bytes: %d), size=%d",
              buffer.position(), buffer.limit(), source, destination,
              version, packet.packetNumber, packet.encodedPacketNumber.length,
              "byte[%s]".formatted(packet.token.length),
              packet.frames, packet.payloadSize, packet.size);
        tracePacketNumber(context, logTag, packet.packetType(), packet.encodedPacketNumber);

        byte headers = headers(packetHeadersTag(packet.packetType()),
                               packet.encodedPacketNumber.length);

        PacketWriter writer = new PacketWriter(buffer, context, PacketType.INITIAL);
        writer.writeHeaders(headers);
        writer.writeVersion(version);
        writer.writeLongConnectionId(destination);
        writer.writeLongConnectionId(source);
        writer.writeToken(packet.token);
        writer.writePacketLength(packet.length);
        int packetNumberStart = writer.position();
        writer.writeEncodedPacketNumber(packet.encodedPacketNumber);
        int payloadStart = writer.position();
        writer.writePayload(packet.frames);
        writer.encryptPayload(packet.packetNumber, payloadStart);
        writer.protectHeaderLong(packetNumberStart, packet.encodedPacketNumber.length);
    }

    /**
     * Encode the RetryPacket into the provided buffer.
     *
     * @param packet  packet to encode
     * @param buffer  A buffer to encode the packet into.
     * @param context encoding context
     * @throws BufferOverflowException if the buffer is not large enough
     */
    private void encodePacket(OutgoingRetryPacket packet,
                              ByteBuffer buffer,
                              CodingContext context,
                              String logTag) throws QuicTransportException {
        int version = packet.version();
        if (quicVersion.versionNumber() != version) {
            throw new IllegalArgumentException("Encoder version %s does not match packet version %s"
                                                       .formatted(quicVersion.text(), version));
        }
        QuicConnectionId destination = packet.destinationId();
        QuicConnectionId source = packet.sourceId();

        debug(logTag, "RetryPacket::encodePacket(ByteBuffer(%d,%d),"
                      + " src=%s, dst=%s, version=%d, retryToken=%d,"
                      + " size=%d",
              buffer.position(), buffer.limit(), source, destination,
              version, packet.retryToken.length, packet.size);

        PacketWriter writer = new PacketWriter(buffer, context, PacketType.RETRY);

        byte headers = packetHeadersTag(packet.packetType());
        headers |= (byte) Encoders.RANDOM.nextInt(0x10);
        writer.writeHeaders(headers);
        writer.writeVersion(version);
        writer.writeLongConnectionId(destination);
        writer.writeLongConnectionId(source);
        writer.writeRetryToken(packet.retryToken);
        writer.signRetry(version);

    }

    /**
     * Base type for outbound packets to be encoded and sent.
     */
    public abstract static class OutgoingQuicPacket implements QuicPacket {
        private final QuicConnectionId destinationId;

        /**
         * Creates an outbound packet targeting the supplied destination connection ID.
         *
         * @param destinationId destination connection ID
         */
        protected OutgoingQuicPacket(QuicConnectionId destinationId) {
            this.destinationId = destinationId;
        }

        @Override
        public final QuicConnectionId destinationId() {
            return destinationId;
        }

        @Override
        public String toString() {

            return this.getClass().getSimpleName() + "[pn=" + this.packetNumber()
                    + ", frames=" + frames() + "]";
        }
    }

    /**
     * A {@code PacketWriter} to write a Quic packet.
     * <p>
     * A {@code PacketWriter} offers high level helper methods to write
     * data (such as Connection IDs or Packet Numbers) from a Quic packet.
     * It has however no or little knowledge of the actual packet structure.
     * It is driven by the {@code encode} method of the appropriate
     * {@code OutgoingQuicPacket} type.
     * <p>
     * A {@code PacketWriter} is stateful: it encapsulates a {@code ByteBuffer}
     * (or possibly a list of byte buffers - as a future enhancement) and
     * advances the position on the buffer it is writing.
     *
     */
    static class PacketWriter {
        private final ByteBuffer buffer;
        private final int offset;
        private final int initialLimit;
        private final CodingContext context;
        private final PacketType packetType;

        PacketWriter(ByteBuffer buffer, CodingContext context, PacketType packetType) {
            int pos = buffer.position();
            int limit = buffer.limit();
            this.buffer = buffer;
            this.offset = pos;
            this.initialLimit = limit;
            this.context = context;
            this.packetType = packetType;
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

        public int bytesWritten() {
            return position() - offset;
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

        public void writeHeaders(byte headers) {
            buffer.put(headers);
        }

        public void writeVersion(int version) {
            buffer.putInt(version);
        }

        public void writeSupportedVersions(int[] versions) {
            for (int i = 0; i < versions.length; i++) {
                buffer.putInt(versions[i]);
            }
        }

        public void writePacketLength(long packetLength) {
            writeVariableLength(packetLength);
        }

        public void writeToken(byte[] token) {
            Objects.requireNonNull(token, "token");
            writeTokenLength(token.length);
            if (token.length > 0) {
                buffer.put(token);
            }
        }

        public void writeVariableLength(long value) {
            VariableLengthEncoder.encode(buffer, value);
        }

        public void writeEncodedPacketNumber(byte[] packetNumber) {
            buffer.put(packetNumber);
        }

        public void encryptPayload(long packetNumber, int payloadstart)
                throws QuicTransportException, QuicKeyUnavailableException {
            int payloadend = buffer.position();
            buffer.position(payloadstart); // position the output buffer
            int payloadLength = payloadend - payloadstart;
            int headersLength = payloadstart - offset;
            ByteBuffer packetHeader = buffer.slice(offset, headersLength);
            ByteBuffer packetPayload = buffer.slice(payloadstart, payloadLength);
            if (!packetPayload.hasArray()) {
                packetPayload = packetPayload.asReadOnlyBuffer();
            }
            QuicPacketTLSEngine.internal(context.tlsEngine())
                    .encryptPacketBuffer(packetType.keySpace().get(),
                                         packetNumber,
                                         new HeaderGenerator(this.packetType, packetHeader),
                                         packetPayload,
                                         buffer);
        }

        public void writePayload(List<QuicFrame> frames) {
            for (var frame : frames) {
                frame.encode(buffer);
            }
        }

        public void writeLongConnectionId(QuicConnectionId connId) {
            ByteBuffer src = connId.asReadOnlyBuffer();
            buffer.put((byte) src.remaining());
            buffer.put(src);
        }

        public void writeShortConnectionId(QuicConnectionId connId) {
            ByteBuffer src = connId.asReadOnlyBuffer();
            buffer.put(src);
        }

        public void writeRetryToken(byte[] retryToken) {
            buffer.put(retryToken);
        }

        @Override
        public String toString() {
            return "PacketWriter(offset=%s, pos=%s, remaining=%s)"
                    .formatted(offset, position(), remaining());
        }

        public void protectHeaderLong(int packetNumberStart, int packetNumberLength)
                throws QuicKeyUnavailableException, QuicTransportException {
            protectHeader(packetNumberStart, packetNumberLength, (byte) 0x0f);
        }

        public void protectHeaderShort(int packetNumberStart, int packetNumberLength)
                throws QuicKeyUnavailableException, QuicTransportException {
            protectHeader(packetNumberStart, packetNumberLength, (byte) 0x1f);
        }

        private void writeTokenLength(long tokenLength) {
            writeVariableLength(tokenLength);
        }

        private void protectHeader(int packetNumberStart, int packetNumberLength, byte headerMask)
                throws QuicKeyUnavailableException, QuicTransportException {
            // expect position at the end of packet
            QuicTLSEngine tlsEngine = context.tlsEngine();
            int sampleSize = tlsEngine.headerProtectionSampleSize(packetType.keySpace().get());

            ByteBuffer sample = buffer.slice(packetNumberStart + 4, sampleSize);
            long headerProtectionMask = QuicPacketTLSEngine.internal(tlsEngine)
                    .computeHeaderProtectionMaskBits(packetType.keySpace().get(), false, sample);
            byte headers = headers();
            headers ^= (byte) ((headerProtectionMask >>> 32) & headerMask);
            headers(headers);
            for (int i = 0; i < packetNumberLength; i++) {
                int shift = 24 - i * Byte.SIZE;
                buffer.put(packetNumberStart + i,
                           (byte) (buffer.get(packetNumberStart + i) ^ (headerProtectionMask >>> shift)));
            }
        }

        private void signRetry(int version) throws QuicTransportException {
            QuicVersion retryVersion = QuicVersion.of(version)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown Quic version 0x"
                                                                            + Integer.toHexString(version)));
            int payloadend = buffer.position();
            ByteBuffer temp = buffer.asReadOnlyBuffer();
            temp.position(offset);
            temp.limit(payloadend);
            QuicPacketTLSEngine.internal(context.tlsEngine())
                    .signRetryPacketBuffer(retryVersion,
                                           context.originalServerConnId().asReadOnlyBuffer(),
                                           temp,
                                           buffer);
        }

        // generates packet header and is capable of inserting a key phase into the header
        // when appropriate
        private static final class HeaderGenerator implements IntFunction<ByteBuffer> {
            private final PacketType packetType;
            private final ByteBuffer header;

            private HeaderGenerator(PacketType packetType, ByteBuffer header) {
                this.packetType = packetType;
                this.header = header;
            }

            @Override
            public ByteBuffer apply(int keyPhase) {
                // we use key phase only in 1-RTT packet header
                if (packetType != PacketType.ONERTT) {
                    // return the packet header without setting any key phase bit
                    return header;
                }
                // update the key phase bit in the packet header
                applyKeyPhase(keyPhase);
                return header.position(0).asReadOnlyBuffer();
            }

            private void applyKeyPhase(int kp) {
                if (kp != 0 && kp != 1) {
                    throw new IllegalArgumentException("Invalid key phase: " + kp);
                }
                byte headerFirstByte = this.header.get();
                byte updated = (byte) (headerFirstByte | (kp << 2));
                this.header.put(0, updated);
            }
        }
    }

    private abstract static class OutgoingShortHeaderPacket
            extends OutgoingQuicPacket implements ShortHeaderPacket {

        OutgoingShortHeaderPacket(QuicConnectionId destinationId) {
            super(destinationId);
        }
    }

    private abstract static class OutgoingLongHeaderPacket
            extends OutgoingQuicPacket implements LongHeaderPacket {

        private final QuicConnectionId sourceId;
        private final int version;

        OutgoingLongHeaderPacket(QuicConnectionId sourceId,
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

    private static final class OutgoingRetryPacket
            extends OutgoingLongHeaderPacket implements RetryPacket {

        private final int size;
        private final byte[] retryToken;

        OutgoingRetryPacket(QuicConnectionId sourceId,
                            QuicConnectionId destinationId,
                            int version,
                            byte[] retryToken) {
            super(sourceId, destinationId, version);
            this.retryToken = retryToken;
            this.size = computeSize(retryToken.length);
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public byte[] retryToken() {
            return retryToken;
        }

        /**
         * Compute the total packet size, starting at the headers byte and
         * ending at the end of the retry integrity tag. This is used to allocate a
         * ByteBuffer in which to encode the packet.
         *
         * @return the total packet size.
         */
        private int computeSize(int tokenLength) {

            // Fixed size bits:
            //   headers(1 byte), version(4 bytes), DCID(1 byte), SCID(1 byte),
            //   retryTokenIntegrity(128 bits) => 7 + 16 = 23 bytes
            int size = Math.addExact(23, tokenLength);
            size = Math.addExact(size, sourceId().length());
            size = Math.addExact(size, destinationId().length());

            return size;
        }
    }

    private static final class OutgoingHandshakePacket
            extends OutgoingLongHeaderPacket implements HandshakePacket {

        private final long packetNumber;
        private final int length;
        private final int size;
        private final byte[] encodedPacketNumber;
        private final List<QuicFrame> frames;
        private final int payloadSize;

        OutgoingHandshakePacket(QuicConnectionId sourceId,
                                QuicConnectionId destinationId,
                                int version,
                                long packetNumber,
                                byte[] encodedPacketNumber,
                                List<? extends QuicFrame> frames, int tagSize) {
            super(sourceId, destinationId, version);
            this.packetNumber = packetNumber;
            this.encodedPacketNumber = encodedPacketNumber;
            this.frames = List.copyOf(frames);
            this.payloadSize = frames.stream().mapToInt(QuicFrame::size).reduce(0, Math::addExact);
            this.length = computeLength(payloadSize, encodedPacketNumber.length, tagSize);
            this.size = computeSize(length);
        }

        @Override
        public int length() {
            return length;
        }

        @Override
        public long packetNumber() {
            return packetNumber;
        }

        public byte[] encodedPacketNumber() {
            return encodedPacketNumber.clone();
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public int payloadSize() {
            return payloadSize;
        }

        @Override
        public List<QuicFrame> frames() {
            return frames;
        }

        /**
         * Computes the value for the packet length field.
         * This is the number of bytes needed to encode the packetNumber
         * and the payload.
         *
         * @param payloadSize The payload size
         * @param pnsize      The number of bytes needed to encode the packet number
         * @param tagSize     The size of the authentication tag added during encryption
         * @return the value for the packet length field.
         */
        private int computeLength(int payloadSize, int pnsize, int tagSize) {

            return Math.addExact(Math.addExact(pnsize, payloadSize), tagSize);
        }

        /**
         * Compute the total packet size, starting at the headers byte and
         * ending at the last payload byte. This is used to allocate a
         * ByteBuffer in which to encode the packet.
         *
         * @param length The value of the length header
         * @return the total packet size.
         */
        private int computeSize(int length) {

            // how many bytes are needed to encode the packet length
            //   the packet length is the number of bytes needed to encode
            //   the remainder of the packet: packet number + payload bytes
            int lnsize = VariableLengthEncoder.encodedSize(length);

            // Fixed size bits:
            //   headers(1 byte), version(4 bytes), DCID(1 byte), SCID(1 byte), => 7 bytes
            int size = Math.addExact(7, sourceId().length());
            size = Math.addExact(size, destinationId().length());

            size = Math.addExact(size, lnsize);
            size = Math.addExact(size, length);
            return size;
        }

    }

    private static final class OutgoingOneRttPacket
            extends OutgoingShortHeaderPacket implements OneRttPacket {

        private final long packetNumber;
        private final int size;
        private final byte[] encodedPacketNumber;
        private final List<QuicFrame> frames;
        private final int payloadSize;

        OutgoingOneRttPacket(QuicConnectionId destinationId,
                             long packetNumber,
                             byte[] encodedPacketNumber,
                             List<? extends QuicFrame> frames, int tagSize) {
            super(destinationId);
            this.packetNumber = packetNumber;
            this.encodedPacketNumber = encodedPacketNumber;
            this.frames = List.copyOf(frames);
            this.payloadSize = this.frames.stream().mapToInt(QuicFrame::size)
                    .reduce(0, Math::addExact);
            this.size = computeSize(payloadSize, encodedPacketNumber.length, tagSize);
        }

        public long packetNumber() {
            return packetNumber;
        }

        public byte[] encodedPacketNumber() {
            return encodedPacketNumber.clone();
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public List<QuicFrame> frames() {
            return frames;
        }

        @Override
        public int payloadSize() {
            return payloadSize;
        }

        /**
         * Compute the total packet size, starting at the headers byte and
         * ending at the last payload byte. This is used to allocate a
         * ByteBuffer in which to encode the packet.
         *
         * @param payloadSize The size of the packet's payload
         * @param pnsize      The number of bytes needed to encode the packet number
         * @param tagSize     The size of the authentication tag
         * @return the total packet size.
         */
        private int computeSize(int payloadSize, int pnsize, int tagSize) {

            // Fixed size bits:
            //   headers(1 byte)
            int size = Math.addExact(1, destinationId().length());

            size = Math.addExact(size, payloadSize);
            size = Math.addExact(size, pnsize);
            size = Math.addExact(size, tagSize);
            return size;
        }

    }

    private static final class OutgoingInitialPacket
            extends OutgoingLongHeaderPacket implements InitialPacket {

        private final byte[] token;
        private final long packetNumber;
        private final int length;
        private final int size;
        private final byte[] encodedPacketNumber;
        private final List<QuicFrame> frames;
        private final int payloadSize;

        private OutgoingInitialPacket(QuicConnectionId sourceId,
                                      QuicConnectionId destinationId,
                                      int version,
                                      byte[] token,
                                      long packetNumber,
                                      byte[] encodedPacketNumber,
                                      List<QuicFrame> frames, int tagSize) {
            super(sourceId, destinationId, version);
            this.token = Objects.requireNonNull(token, "token");
            this.packetNumber = packetNumber;
            this.encodedPacketNumber = encodedPacketNumber;
            this.frames = List.copyOf(frames);
            this.payloadSize = this.frames.stream()
                    .mapToInt(QuicFrame::size)
                    .reduce(0, Math::addExact);
            this.length = computeLength(payloadSize, encodedPacketNumber.length, tagSize);
            this.size = computePacketSize(new InitialPacketVariableComponents(length, token, sourceId,
                                                                              destinationId));
        }

        @Override
        public int tokenLength() {
            return token.length;
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

        public byte[] encodedPacketNumber() {
            return encodedPacketNumber.clone();
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public List<QuicFrame> frames() {
            return frames;
        }

        @Override
        public int payloadSize() {
            return payloadSize;
        }

        /**
         * Computes the value for the packet length field.
         * This is the number of bytes needed to encode the packetNumber
         * and the payload.
         *
         * @param payloadSize The payload size
         * @param pnsize      The number of bytes needed to encode the packet number
         * @param tagSize     The size of the authentication tag added during encryption
         * @return the value for the packet length field.
         */
        private static int computeLength(int payloadSize, int pnsize, int tagSize) {

            return Math.addExact(Math.addExact(pnsize, payloadSize), tagSize);
        }

        /**
         * Compute the total packet size, starting at the headers byte and
         * ending at the last payload byte. This is used to allocate a
         * ByteBuffer in which to encode the packet.
         *
         * @param variableComponents The variable components of the packet
         * @return the total packet size.
         */
        private static int computePacketSize(InitialPacketVariableComponents variableComponents) {

            // how many bytes are needed to encode the length of the token
            byte[] token = variableComponents.token;
            int tkLenSpecifierSize = token.length == 0
                    ? 1 : VariableLengthEncoder.encodedSize(token.length);

            // how many bytes are needed to encode the packet length
            //   the packet length is the number of bytes needed to encode
            //   the remainder of the packet: packet number + payload bytes
            int lnsize = VariableLengthEncoder.encodedSize(variableComponents.length);

            // Fixed size bits:
            //   headers(1 byte), version(4 bytes), DCID length specifier(1 byte),
            //   SCID length specifier(1 byte), => 7 bytes
            int size = Math.addExact(7, variableComponents.sourceId.length());
            size = Math.addExact(size, variableComponents.destinationId.length());
            size = Math.addExact(size, tkLenSpecifierSize);
            size = Math.addExact(size, token.length);
            size = Math.addExact(size, lnsize);
            size = Math.addExact(size, variableComponents.length);
            return size;
        }

        private record InitialPacketVariableComponents(int length, byte[] token, QuicConnectionId sourceId,
                                                       QuicConnectionId destinationId) {

        }

    }

    private static final class OutgoingVersionNegotiationPacket
            extends OutgoingLongHeaderPacket
            implements VersionNegotiationPacket {

        private final int[] versions;
        private final int size;
        private final int payloadSize;

        private OutgoingVersionNegotiationPacket(QuicConnectionId sourceId,
                                                 QuicConnectionId destinationId,
                                                 int[] versions) {
            super(sourceId, destinationId, 0);
            this.versions = versions.clone();
            this.payloadSize = versions.length << 2;
            this.size = computeSize(payloadSize);
        }

        @Override
        public int[] supportedVersions() {
            return versions.clone();
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public int payloadSize() {
            return payloadSize;
        }

        /**
         * Compute the total packet size, starting at the headers byte and
         * ending at the last payload byte. This is used to allocate a
         * ByteBuffer in which to encode the packet.
         *
         * @param payloadSize The size of the packet's payload
         * @return the total packet size.
         */
        private int computeSize(int payloadSize) {
            // Fixed size bits:
            //   headers(1 byte), version(4 bytes), DCID(1 byte), SCID(1 byte), => 7 bytes
            int size = Math.addExact(7, payloadSize);
            size = Math.addExact(size, sourceId().length());
            size = Math.addExact(size, destinationId().length());
            return size;
        }

    }

    private static final class Encoders {
        static final SecureRandom RANDOM = new SecureRandom();
        static final QuicPacketEncoder QUIC_V1_ENCODER = new QuicPacketEncoder(QuicVersion.QUIC_V1);
        static final QuicPacketEncoder QUIC_V2_ENCODER = new QuicPacketEncoder(QuicVersion.QUIC_V2);
    }
}
