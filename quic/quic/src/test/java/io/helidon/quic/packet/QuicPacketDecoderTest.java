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

package io.helidon.quic.packet;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.crypto.AEADBadTagException;

import io.helidon.quic.CodingContext;
import io.helidon.quic.QuicPacketAuthenticationException;
import io.helidon.quic.QuicTLSEngine;
import io.helidon.quic.QuicTransportErrors;
import io.helidon.quic.QuicTransportException;
import io.helidon.quic.QuicVersion;
import io.helidon.quic.spi.QuicPacketTLSEngine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuicPacketDecoderTest {
    @Test
    void headerPeekingUsesExplicitAbsenceAndRejectsNull() {
        ByteBuffer longHeader = ByteBuffer.allocate(7)
                .put((byte) 0x80)
                .putInt(QuicVersion.QUIC_V1.versionNumber())
                .put((byte) 0)
                .put((byte) 0)
                .flip();
        ByteBuffer shortHeader = ByteBuffer.wrap(new byte[] {0x40, 1});

        var presentLongHeader = QuicPacketDecoder.peekLongHeader(longHeader);
        var emptyLongHeader = QuicPacketDecoder.peekLongHeader(ByteBuffer.allocate(6));
        assertThat(presentLongHeader.isPresent(), is(true));
        assertThat(presentLongHeader.orElseThrow().version(), is(QuicVersion.QUIC_V1.versionNumber()));
        assertThat(emptyLongHeader.isEmpty(), is(true));
        assertThrows(NoSuchElementException.class, emptyLongHeader::orElseThrow);
        assertThat(QuicPacketDecoder.peekShortConnectionId(shortHeader, 1).isPresent(), is(true));
        assertThat(QuicPacketDecoder.peekShortConnectionId(ByteBuffer.allocate(1), 1), is(Optional.empty()));
        assertThrows(NullPointerException.class, () -> QuicPacketDecoder.peekLongHeader(null));
        assertThrows(NullPointerException.class, () -> QuicPacketDecoder.peekLongHeader(null, 0));
        assertThrows(NullPointerException.class, () -> QuicPacketDecoder.peekShortConnectionId(null, 1));
    }

    @ParameterizedTest(name = "{0}: offset={1}")
    @MethodSource("invalidLongHeaderOffsets")
    void rejectsLongHeaderOffsetsOutsideLimit(BufferKind bufferKind, int offset) {
        ByteBuffer buffer = bufferKind.wrap(longHeader(new byte[0], new byte[0]), 0);
        int originalPosition = buffer.position();
        int originalLimit = buffer.limit();

        var result = QuicPacketDecoder.peekLongHeader(buffer, offset);

        assertThat(result.isEmpty(), is(true));
        assertThat(buffer.position(), is(originalPosition));
        assertThat(buffer.limit(), is(originalLimit));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("malformedLongHeaders")
    void rejectsMalformedLongHeaderWithoutChangingBuffer(BufferKind bufferKind, String description, byte[] packet) {
        int offset = 3;
        ByteBuffer buffer = bufferKind.wrap(packet, offset);
        int originalPosition = buffer.position();
        int originalLimit = buffer.limit();

        var result = QuicPacketDecoder.peekLongHeader(buffer, offset);

        assertThat(description, result.isEmpty(), is(true));
        assertThat(buffer.position(), is(originalPosition));
        assertThat(buffer.limit(), is(originalLimit));
    }

    @ParameterizedTest(name = "{0}: destination={1}, source={2}, position={3}")
    @MethodSource("validLongHeaders")
    void peeksLongHeaderAtExplicitOffsetWithoutChangingBuffer(BufferKind bufferKind,
                                                            int destinationLength,
                                                            int sourceLength,
                                                            int position) {
        int offset = 3;
        byte[] destinationId = connectionIdBytes(destinationLength, 0x30);
        byte[] sourceId = connectionIdBytes(sourceLength, 0x90);
        ByteBuffer buffer = bufferKind.wrap(longHeader(destinationId, sourceId), offset);
        buffer.position(position);
        int originalLimit = buffer.limit();

        var result = QuicPacketDecoder.peekLongHeader(buffer, offset);

        assertThat(result.isPresent(), is(true));
        LongHeader header = result.orElseThrow();
        assertThat(header.version(), is(QuicVersion.QUIC_V1.versionNumber()));
        assertThat(header.headerLength(), is(7 + destinationLength + sourceLength));
        assertThat(header.destinationId().bytes(), is(destinationId));
        assertThat(header.sourceId().bytes(), is(sourceId));
        assertThat(buffer.position(), is(position));
        assertThat(buffer.limit(), is(originalLimit));
    }

    @Test
    void rejectsTruncatedCoalescedLongHeaderAfterSkippingInitial() {
        byte[] destinationId = {1, 2, 3, 4, 5, 6, 7, 8};
        ByteBuffer datagram = ByteBuffer.allocate(1200);
        datagram.put((byte) 0xc0).putInt(QuicVersion.QUIC_V1.versionNumber());
        datagram.put((byte) destinationId.length).put(destinationId);
        datagram.put((byte) 0); // Empty source connection ID.
        datagram.put((byte) 0); // Empty Initial token.
        datagram.putShort((short) (0x4000 | 1175)); // Packet-number plus ciphertext length.
        datagram.position(1193); // Leave unauthenticated, zero-filled ciphertext.
        datagram.put((byte) 0xc0).putInt(QuicVersion.QUIC_V1.versionNumber()).put((byte) 20).put((byte) 0);
        datagram.flip();
        QuicPacketDecoder decoder = QuicPacketDecoder.of(QuicVersion.QUIC_V1);

        assertThat(datagram.limit(), is(1200));
        assertThat(decoder.peekPacketType(datagram), is(QuicPacket.PacketType.INITIAL));
        assertThat(QuicPacketDecoder.peekLongHeader(datagram).orElseThrow().destinationId().bytes(), is(destinationId));
        assertThat(datagram.position(), is(0));

        // A connection without Initial keys skips the first packet before examining the coalesced tail.
        decoder.skipPacket(datagram, datagram.position());
        assertThat(datagram.position(), is(1193));
        assertThat(datagram.remaining(), is(7));
        assertThat(QuicPacketDecoder.peekHeaderType(datagram, datagram.position()), is(QuicPacket.HeadersType.LONG));

        // The second header declares 20 destination-ID bytes but contains only one.
        assertThat(QuicPacketDecoder.peekLongHeader(datagram).isEmpty(), is(true));
        assertThat(datagram.position(), is(1193));
        assertThat(datagram.limit(), is(1200));
    }

    @Test
    void rejectsUnsupportedPacketAsDiscardableDecodeFailure() {
        ByteBuffer packet = ByteBuffer.wrap(new byte[] {(byte) 0x80, 0, 0, 0, 2});

        assertThrows(QuicPacketDecodeException.class,
                     () -> QuicPacketDecoder.of(QuicVersion.QUIC_V1)
                             .decode(packet, mock(CodingContext.class)));
    }

    @Test
    void mapsAuthenticationFailureToDiscardableDecodeFailure() {
        QuicPacketTLSEngine engine = mock(QuicPacketTLSEngine.class);
        when(engine.keysAvailable(QuicTLSEngine.KeySpace.INITIAL)).thenReturn(true);
        when(engine.headerProtectionSampleSize(QuicTLSEngine.KeySpace.INITIAL)).thenReturn(16);
        when(engine.computeHeaderProtectionMaskBits(eq(QuicTLSEngine.KeySpace.INITIAL),
                                                    eq(true),
                                                    any(ByteBuffer.class)))
                .thenReturn(0L);
        QuicPacketAuthenticationException authenticationFailure =
                new QuicPacketAuthenticationException("Packet authentication failed", new AEADBadTagException());
        doThrow(authenticationFailure).when(engine)
                .decryptPacketBuffer(eq(QuicTLSEngine.KeySpace.INITIAL),
                                     anyLong(),
                                     eq(-1),
                                     any(ByteBuffer.class),
                                     anyInt(),
                                     any(ByteBuffer.class));
        CodingContext context = mock(CodingContext.class);
        when(context.tlsEngine()).thenReturn(engine);
        when(context.verifyToken(any(), any(byte[].class))).thenReturn(true);
        QuicPacketDecodeException failure =
                assertThrows(QuicPacketDecodeException.class,
                             () -> QuicPacketDecoder.of(QuicVersion.QUIC_V1).decode(initialPacket(), context));

        assertThat(failure.getCause(), sameInstance(authenticationFailure));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void unmasksEveryPacketNumberLengthFromPackedMask(int packetNumberLength) throws Exception {
        long headerProtectionMask = 0x0d11_2233_44L;
        QuicPacketTLSEngine engine = mock(QuicPacketTLSEngine.class);
        when(engine.keysAvailable(QuicTLSEngine.KeySpace.INITIAL)).thenReturn(true);
        when(engine.headerProtectionSampleSize(QuicTLSEngine.KeySpace.INITIAL)).thenReturn(16);
        when(engine.computeHeaderProtectionMaskBits(eq(QuicTLSEngine.KeySpace.INITIAL),
                                                    eq(true),
                                                    any(ByteBuffer.class)))
                .thenReturn(headerProtectionMask);
        QuicPacketAuthenticationException authenticationFailure =
                new QuicPacketAuthenticationException("Packet authentication failed", new AEADBadTagException());
        doThrow(authenticationFailure).when(engine)
                .decryptPacketBuffer(eq(QuicTLSEngine.KeySpace.INITIAL),
                                     anyLong(),
                                     eq(-1),
                                     any(ByteBuffer.class),
                                     anyInt(),
                                     any(ByteBuffer.class));
        CodingContext context = mock(CodingContext.class);
        when(context.tlsEngine()).thenReturn(engine);
        when(context.verifyToken(any(), any(byte[].class))).thenReturn(true);
        ByteBuffer packet = initialPacket(packetNumberLength, headerProtectionMask);

        assertThrows(QuicPacketDecodeException.class,
                     () -> QuicPacketDecoder.of(QuicVersion.QUIC_V1).decode(packet, context));

        assertThat(packet.get(0), is((byte) (0xc0 | packetNumberLength - 1)));
        for (int i = 0; i < packetNumberLength; i++) {
            assertThat(packet.get(9 + i), is((byte) (0x50 + i)));
        }
    }

    @Test
    void rejectsInvalidInitialTokenAsEmpty() throws Exception {
        QuicPacketTLSEngine engine = mock(QuicPacketTLSEngine.class);
        when(engine.keysAvailable(QuicTLSEngine.KeySpace.INITIAL)).thenReturn(true);
        CodingContext context = mock(CodingContext.class);
        when(context.tlsEngine()).thenReturn(engine);
        when(context.verifyToken(any(), any(byte[].class))).thenReturn(false);

        assertThat(QuicPacketDecoder.of(QuicVersion.QUIC_V1).decode(initialPacket(), context), is(Optional.empty()));
    }

    @Test
    void preservesTransportFailureFromFrameDecoder() {
        CodingContext context = mock(CodingContext.class);
        when(context.maxAckRangesPerFrame()).thenReturn(1024);
        QuicPacketDecoder decoder = QuicPacketDecoder.of(QuicVersion.QUIC_V1);
        QuicPacketDecoder.PacketReader reader = decoder.new PacketReader(
                ByteBuffer.allocate(0),
                context,
                QuicPacket.PacketType.ONERTT);

        QuicTransportException failure = assertThrows(
                QuicTransportException.class,
                () -> reader.parsePayloadSlice(ByteBuffer.wrap(new byte[] {0x1f})));

        assertThat(failure.keySpace(), is(Optional.empty()));
        assertThat(failure.sourceStreamId(), is(OptionalLong.empty()));
        assertThat(failure.frameType(), is(0x1fL));
        assertThat(failure.errorCode(), is(QuicTransportErrors.FRAME_ENCODING_ERROR.code()));
    }

    @Test
    void appliesConfiguredAckRangeLimit() {
        CodingContext context = mock(CodingContext.class);
        when(context.maxAckRangesPerFrame()).thenReturn(2);
        QuicPacketDecoder decoder = QuicPacketDecoder.of(QuicVersion.QUIC_V1);
        QuicPacketDecoder.PacketReader reader = decoder.new PacketReader(
                ByteBuffer.allocate(0),
                context,
                QuicPacket.PacketType.ONERTT);

        QuicPacketDiscardException failure = assertThrows(
                QuicPacketDiscardException.class,
                () -> reader.parsePayloadSlice(ByteBuffer.wrap(new byte[] {0x02, 6, 0, 2, 0, 0, 0, 0, 0})));

        assertThat(failure.getMessage(), containsString("3 > 2"));
    }

    @Test
    void preservesLaterTransportFailureAfterAckPolicyExcess() {
        CodingContext context = mock(CodingContext.class);
        when(context.maxAckRangesPerFrame()).thenReturn(2);
        QuicPacketDecoder decoder = QuicPacketDecoder.of(QuicVersion.QUIC_V1);
        QuicPacketDecoder.PacketReader reader = decoder.new PacketReader(
                ByteBuffer.allocate(0),
                context,
                QuicPacket.PacketType.ONERTT);
        byte[] payload = {0x02, 6, 0, 2, 0, 0, 0, 0, 0, 0x1f};

        QuicTransportException failure = assertThrows(
                QuicTransportException.class,
                () -> reader.parsePayloadSlice(ByteBuffer.wrap(payload)));

        assertThat(failure.frameType(), is(0x1fL));
        assertThat(failure.errorCode(), is(QuicTransportErrors.FRAME_ENCODING_ERROR.code()));
    }

    @Test
    void retainsFirstDiscardAfterRepeatedAckPolicyExcess() {
        CodingContext context = mock(CodingContext.class);
        when(context.maxAckRangesPerFrame()).thenReturn(2);
        QuicPacketDecoder decoder = QuicPacketDecoder.of(QuicVersion.QUIC_V1);
        QuicPacketDecoder.PacketReader reader = decoder.new PacketReader(
                ByteBuffer.allocate(0),
                context,
                QuicPacket.PacketType.ONERTT,
                "",
                true);
        ByteBuffer payload = ByteBuffer.wrap(new byte[] {
                0x02, 6, 0, 2, 0, 0, 0, 0, 0,
                0x02, 8, 0, 3, 0, 0, 0, 0, 0, 0, 0
        });

        QuicPacketDiscardException failure = assertThrows(
                QuicPacketDiscardException.class,
                () -> reader.parsePayloadSlice(payload));

        assertThat(failure.getMessage(), containsString("3 > 2"));
        assertThat(failure.frameType(), is(0x02L));
        assertThat(payload.position(), is(payload.limit()));
    }

    @Test
    void consumesAckEcnCountersAfterAckPolicyExcess() {
        CodingContext context = mock(CodingContext.class);
        when(context.maxAckRangesPerFrame()).thenReturn(2);
        QuicPacketDecoder decoder = QuicPacketDecoder.of(QuicVersion.QUIC_V1);
        QuicPacketDecoder.PacketReader reader = decoder.new PacketReader(
                ByteBuffer.allocate(0),
                context,
                QuicPacket.PacketType.ONERTT);
        ByteBuffer payload = ByteBuffer.wrap(new byte[] {
                0x02, 6, 0, 2, 0, 0, 0, 0, 0,
                0x03, 6, 0, 2, 0, 0, 0, 0, 0, 0x1f, 0x1f, 0x1f
        });

        QuicPacketDiscardException failure = assertThrows(
                QuicPacketDiscardException.class,
                () -> reader.parsePayloadSlice(payload));

        assertThat(failure.getMessage(), containsString("3 > 2"));
        assertThat(failure.frameType(), is(0x02L));
        assertThat(payload.position(), is(payload.limit()));
    }

    @Test
    void preservesMalformedAckAfterRepeatedAckPolicyExcess() {
        CodingContext context = mock(CodingContext.class);
        when(context.maxAckRangesPerFrame()).thenReturn(2);
        QuicPacketDecoder decoder = QuicPacketDecoder.of(QuicVersion.QUIC_V1);
        QuicPacketDecoder.PacketReader reader = decoder.new PacketReader(
                ByteBuffer.allocate(0),
                context,
                QuicPacket.PacketType.ONERTT);
        byte[] payload = {
                0x02, 6, 0, 2, 0, 0, 0, 0, 0,
                0x02, 1, 0, 2, 0, 0, 0, 0, 0
        };

        QuicTransportException failure = assertThrows(
                QuicTransportException.class,
                () -> reader.parsePayloadSlice(ByteBuffer.wrap(payload)));

        assertThat(failure.getMessage(), containsString("Negative PN acknowledged"));
        assertThat(failure.frameType(), is(0x02L));
        assertThat(failure.errorCode(), is(QuicTransportErrors.FRAME_ENCODING_ERROR.code()));
    }

    @Test
    void preservesPacketTypeViolationAfterRepeatedAckPolicyExcess() {
        assertInitialPacketTypeViolation(new byte[] {
                0x02, 6, 0, 2, 0, 0, 0, 0, 0,
                0x02, 8, 0, 3, 0, 0, 0, 0, 0, 0, 0,
                0x0a, 0, 0
        });
    }

    @Test
    void preservesInitialPacketTypeViolationBeforeAckPolicyExcess() {
        assertInitialPacketTypeViolation(new byte[] {
                0x0a, 0, 0,
                0x02, 6, 0, 2, 0, 0, 0, 0, 0
        });
    }

    @Test
    void preservesInitialPacketTypeViolationAfterAckPolicyExcess() {
        assertInitialPacketTypeViolation(new byte[] {
                0x02, 6, 0, 2, 0, 0, 0, 0, 0,
                0x0a, 0, 0
        });
    }

    @Test
    void rejectsOverLimitAckInZeroRtt() {
        CodingContext context = mock(CodingContext.class);
        when(context.maxAckRangesPerFrame()).thenReturn(2);
        QuicPacketDecoder decoder = QuicPacketDecoder.of(QuicVersion.QUIC_V1);
        QuicPacketDecoder.PacketReader reader = decoder.new PacketReader(
                ByteBuffer.allocate(0),
                context,
                QuicPacket.PacketType.ZERORTT);

        QuicTransportException failure = assertThrows(
                QuicTransportException.class,
                () -> reader.parsePayloadSlice(
                        ByteBuffer.wrap(new byte[] {0x02, 6, 0, 2, 0, 0, 0, 0, 0})));

        assertThat(failure.keySpace().orElseThrow(), is(QuicTLSEngine.KeySpace.ZERO_RTT));
        assertThat(failure.frameType(), is(0x02L));
        assertThat(failure.errorCode(), is(QuicTransportErrors.PROTOCOL_VIOLATION.code()));
    }

    private static Stream<Arguments> invalidLongHeaderOffsets() {
        return Stream.of(BufferKind.values())
                .flatMap(bufferKind -> IntStream.of(-1, 7, 8, Integer.MIN_VALUE, Integer.MAX_VALUE)
                        .mapToObj(offset -> Arguments.of(bufferKind, offset)));
    }

    private static Stream<Arguments> malformedLongHeaders() {
        byte[] emptyIds = longHeader(new byte[0], new byte[0]);
        byte[] maximumDestinationId = longHeader(connectionIdBytes(20, 0x30), new byte[0]);
        byte[] maximumSourceId = longHeader(new byte[0], connectionIdBytes(20, 0x90));
        byte[] shortHeader = emptyIds.clone();
        shortHeader[0] = 0x40;
        return Stream.of(BufferKind.values())
                .flatMap(bufferKind -> Stream.of(
                        Arguments.of(bufferKind, "truncated fixed header", Arrays.copyOf(emptyIds, 5)),
                        Arguments.of(bufferKind, "missing destination ID", Arrays.copyOf(maximumDestinationId, 6)),
                        Arguments.of(bufferKind, "truncated destination ID", Arrays.copyOf(maximumDestinationId, 7)),
                        Arguments.of(bufferKind, "missing source ID length", Arrays.copyOf(maximumDestinationId, 26)),
                        Arguments.of(bufferKind, "truncated source ID", Arrays.copyOf(maximumSourceId, 26)),
                        Arguments.of(bufferKind, "21-byte destination ID", longHeader(new byte[21], new byte[0])),
                        Arguments.of(bufferKind, "255-byte destination ID", longHeader(new byte[255], new byte[0])),
                        Arguments.of(bufferKind, "21-byte source ID", longHeader(new byte[0], new byte[21])),
                        Arguments.of(bufferKind, "255-byte source ID", longHeader(new byte[0], new byte[255])),
                        Arguments.of(bufferKind, "short header", shortHeader)));
    }

    private static Stream<Arguments> validLongHeaders() {
        return Stream.of(BufferKind.values())
                .flatMap(bufferKind -> Stream.of(
                        Arguments.of(bufferKind, 0, 0, 0),
                        Arguments.of(bufferKind, 20, 0, 3),
                        Arguments.of(bufferKind, 0, 20, 6),
                        Arguments.of(bufferKind, 20, 20, 50)));
    }

    private static byte[] longHeader(byte[] destinationId, byte[] sourceId) {
        return ByteBuffer.allocate(7 + destinationId.length + sourceId.length)
                .put((byte) 0xc0)
                .putInt(QuicVersion.QUIC_V1.versionNumber())
                .put((byte) destinationId.length)
                .put(destinationId)
                .put((byte) sourceId.length)
                .put(sourceId)
                .array();
    }

    private static byte[] connectionIdBytes(int length, int firstByte) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (firstByte + i);
        }
        return bytes;
    }

    private static void assertInitialPacketTypeViolation(byte[] payload) {
        CodingContext context = mock(CodingContext.class);
        when(context.maxAckRangesPerFrame()).thenReturn(2);
        QuicPacketDecoder decoder = QuicPacketDecoder.of(QuicVersion.QUIC_V1);
        QuicPacketDecoder.PacketReader reader = decoder.new PacketReader(
                ByteBuffer.allocate(0),
                context,
                QuicPacket.PacketType.INITIAL);

        QuicTransportException failure = assertThrows(
                QuicTransportException.class,
                () -> reader.parsePayloadSlice(ByteBuffer.wrap(payload)));

        assertThat(failure.keySpace().orElseThrow(), is(QuicTLSEngine.KeySpace.INITIAL));
        assertThat(failure.frameType(), is(0x0aL));
        assertThat(failure.errorCode(), is(QuicTransportErrors.PROTOCOL_VIOLATION.code()));
    }

    private static ByteBuffer initialPacket() {
        return initialPacket(1, 0);
    }

    private static ByteBuffer initialPacket(int packetNumberLength, long headerProtectionMask) {
        ByteBuffer packet = ByteBuffer.allocate(29 + packetNumberLength);
        byte headers = (byte) (0xc0 | packetNumberLength - 1);
        packet.put((byte) (headers ^ (headerProtectionMask >>> 32)));
        packet.putInt(QuicVersion.QUIC_V1.versionNumber());
        packet.put((byte) 0);
        packet.put((byte) 0);
        packet.put((byte) 0);
        packet.put((byte) (20 + packetNumberLength));
        for (int i = 0; i < packetNumberLength; i++) {
            int shift = 24 - i * Byte.SIZE;
            packet.put((byte) (0x50 + i ^ (headerProtectionMask >>> shift)));
        }
        packet.put(new byte[20]);
        return packet.flip();
    }

    private enum BufferKind {
        HEAP,
        DIRECT,
        READ_ONLY,
        SLICED;

        ByteBuffer wrap(byte[] packet, int offset) {
            int capacity = offset + packet.length + 8;
            ByteBuffer buffer = switch (this) {
                case HEAP, READ_ONLY -> ByteBuffer.allocate(capacity);
                case DIRECT -> ByteBuffer.allocateDirect(capacity);
                case SLICED -> ByteBuffer.allocate(capacity + 8).slice(8, capacity);
            };
            buffer.position(offset).put(packet).flip();
            return this == READ_ONLY ? buffer.asReadOnlyBuffer() : buffer;
        }
    }
}
