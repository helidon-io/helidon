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
import java.util.List;

import io.helidon.quic.CodingContext;
import io.helidon.quic.QuicConnectionId;
import io.helidon.quic.QuicConnectionIdFactory;
import io.helidon.quic.QuicTLSEngine.KeySpace;
import io.helidon.quic.QuicVersion;
import io.helidon.quic.packet.QuicPacket.PacketType;
import io.helidon.quic.spi.QuicPacketTLSEngine;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuicPacketEncoderTest {
    private static final QuicConnectionId DESTINATION_ID = QuicConnectionIdFactory.client().newConnectionId();

    @ParameterizedTest
    @CsvSource({
            "24, 16, 0, 11",
            "16, 16, 5, 4",
            "16, 16, 12, 11"
    })
    void shouldPadOneRttPacketForHeaderProtection(int sampleSize,
                                                  int tagSize,
                                                  int minimumPayloadSize,
                                                  int expectedFramePayloadSize) {
        QuicPacketTLSEngine tlsEngine = mock(QuicPacketTLSEngine.class);
        when(tlsEngine.authTagSize()).thenReturn(tagSize);
        when(tlsEngine.headerProtectionSampleSize(KeySpace.ONE_RTT)).thenReturn(sampleSize);
        CodingContext context = mock(CodingContext.class);
        when(context.tlsEngine()).thenReturn(tlsEngine);
        when(context.minShortPacketPayloadSize(DESTINATION_ID.length())).thenReturn(minimumPayloadSize);

        OneRttPacket packet = QuicPacketEncoder.of(QuicVersion.QUIC_V1)
                .newOneRttPacket(DESTINATION_ID, 0, -1, List.of(), context);

        assertThat(packet.payloadSize(), is(expectedFramePayloadSize));
    }

    @ParameterizedTest
    @CsvSource({
            "false, false",
            "true, true"
    })
    void shouldPreservePayloadBufferShape(boolean direct, boolean expectedReadOnly) throws Exception {
        QuicPacketTLSEngine tlsEngine = mock(QuicPacketTLSEngine.class);
        CodingContext context = mock(CodingContext.class);
        when(context.tlsEngine()).thenReturn(tlsEngine);
        ByteBuffer buffer = direct ? ByteBuffer.allocateDirect(48) : ByteBuffer.allocate(48);
        int initialPosition = 3;
        int initialLimit = 45;
        byte[] plaintext = new byte[] {1, 2, 3, 4, 5, 6};
        buffer.position(initialPosition);
        buffer.limit(initialLimit);
        QuicPacketEncoder.PacketWriter writer =
                new QuicPacketEncoder.PacketWriter(buffer, context, PacketType.INITIAL);
        writer.writeHeaders((byte) 0xc0);
        int payloadStart = writer.position();
        buffer.put(plaintext);

        doAnswer(invocation -> {
            ByteBuffer packetPayload = invocation.getArgument(3);
            ByteBuffer output = invocation.getArgument(4);
            assertThat(packetPayload.isReadOnly(), is(expectedReadOnly));
            assertThat(packetPayload.position(), is(0));
            assertThat(packetPayload.limit(), is(plaintext.length));
            assertThat(output, sameInstance(buffer));
            assertThat(output.position(), is(payloadStart));
            assertThat(output.limit(), is(initialLimit));

            byte original = packetPayload.get(0);
            byte replacement = (byte) (original ^ 0x7f);
            output.put(payloadStart, replacement);
            assertThat(packetPayload.get(0), is(replacement));
            output.put(payloadStart, original);

            byte[] source = new byte[packetPayload.remaining()];
            packetPayload.get(source);
            assertThat(packetPayload.position(), is(packetPayload.limit()));
            assertThat(packetPayload.limit(), is(plaintext.length));
            for (byte value : source) {
                output.put((byte) (value ^ 1));
            }
            output.put(new byte[16]);
            return null;
        }).when(tlsEngine).encryptPacketBuffer(any(), anyLong(), any(), any(), any());

        writer.encryptPayload(7, payloadStart);

        assertThat(buffer.position(), is(payloadStart + plaintext.length + 16));
        assertThat(buffer.limit(), is(initialLimit));
        assertThat(writer.offset(), is(initialPosition));
    }

    @ParameterizedTest
    @CsvSource({"1", "2", "3", "4"})
    void masksEveryPacketNumberLengthFromPackedMask(int packetNumberLength) throws Exception {
        long headerProtectionMask = 0x0d11_2233_44L;
        QuicPacketTLSEngine tlsEngine = mock(QuicPacketTLSEngine.class);
        when(tlsEngine.headerProtectionSampleSize(KeySpace.INITIAL)).thenReturn(16);
        when(tlsEngine.computeHeaderProtectionMaskBits(eq(KeySpace.INITIAL),
                                                       eq(false),
                                                       any(ByteBuffer.class)))
                .thenReturn(headerProtectionMask);
        CodingContext context = mock(CodingContext.class);
        when(context.tlsEngine()).thenReturn(tlsEngine);
        int initialPosition = 3;
        ByteBuffer buffer = ByteBuffer.allocate(64);
        buffer.position(initialPosition);
        QuicPacketEncoder.PacketWriter writer =
                new QuicPacketEncoder.PacketWriter(buffer, context, PacketType.INITIAL);
        byte headers = (byte) (0xc0 | packetNumberLength - 1);
        writer.writeHeaders(headers);
        int packetNumberStart = writer.position();
        byte[] packetNumber = new byte[packetNumberLength];
        for (int i = 0; i < packetNumber.length; i++) {
            packetNumber[i] = (byte) (0x50 + i);
        }
        writer.writeEncodedPacketNumber(packetNumber);
        buffer.put(new byte[20]);
        int packetEnd = buffer.position();

        writer.protectHeaderLong(packetNumberStart, packetNumberLength);

        assertThat(buffer.get(initialPosition), is((byte) (headers ^ 0x0d)));
        for (int i = 0; i < packetNumberLength; i++) {
            int shift = 24 - i * Byte.SIZE;
            assertThat(buffer.get(packetNumberStart + i),
                       is((byte) (packetNumber[i] ^ (headerProtectionMask >>> shift))));
        }
        assertThat(buffer.position(), is(packetEnd));
    }
}
