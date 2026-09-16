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

package io.helidon.quic.frame;

import java.nio.ByteBuffer;
import java.util.List;

import io.helidon.quic.QuicTransportErrors;
import io.helidon.quic.QuicTransportException;
import io.helidon.quic.VariableLengthEncoder;
import io.helidon.quic.packet.QuicPacketDiscardException;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AckFrameTest {
    @Test
    void extendsSingleRangeWithoutMutatingOriginal() throws Exception {
        AckFrame original = AckFrame.create(10,
                                            7,
                                            List.of(AckFrame.AckRange.of(0, 4)),
                                            11,
                                            12,
                                            13);
        byte[] originalEncoding = encode(original);

        AckFrame extended = original.withNextAcknowledged(11);

        assertThat(extended, not(sameInstance(original)));
        assertThat(original.largestAcknowledged(), is(10L));
        assertThat(original.ackDelay(), is(7L));
        assertThat(original.ackRanges(), is(List.of(AckFrame.AckRange.of(0, 4))));
        assertThat(encode(original), is(originalEncoding));

        assertThat(extended.largestAcknowledged(), is(11L));
        assertThat(extended.ackDelay(), is(7L));
        assertThat(extended.ackRanges(), is(List.of(AckFrame.AckRange.of(0, 5))));
        assertThat(extended.ect0Count(), is(11L));
        assertThat(extended.ect1Count(), is(12L));
        assertThat(extended.ecnCECount(), is(13L));
        assertThat(QuicFrame.decode(ByteBuffer.wrap(encode(extended))), is(extended));
    }

    @Test
    void rejectsUnsupportedSingleRangeExtensions() {
        AckFrame multipleRanges = AckFrame.create(10,
                                                  0,
                                                  List.of(AckFrame.AckRange.INITIAL,
                                                          AckFrame.AckRange.INITIAL));
        AckFrame singleRange = AckFrame.create(10, 0, List.of(AckFrame.AckRange.INITIAL));

        assertThrows(IllegalArgumentException.class, () -> multipleRanges.withNextAcknowledged(11));
        assertThrows(IllegalArgumentException.class, () -> singleRange.withNextAcknowledged(10));
        assertThrows(IllegalArgumentException.class, () -> singleRange.withNextAcknowledged(12));

        AckFrame maximumRange = AckFrame.create(QuicFrame.MAX_VL_INTEGER - 1,
                                                0,
                                                List.of(AckFrame.AckRange.of(0,
                                                                            QuicFrame.MAX_VL_INTEGER - 1)))
                .withNextAcknowledged(QuicFrame.MAX_VL_INTEGER);
        assertThat(maximumRange.ackRanges().getFirst().range(), is(QuicFrame.MAX_VL_INTEGER));
        assertThrows(IllegalArgumentException.class,
                     () -> maximumRange.withNextAcknowledged(QuicFrame.MAX_VL_INTEGER + 1));
    }

    @Test
    void updatesEncodedSizeAcrossVariableLengthBoundaries() throws Exception {
        assertSizeDelta(63, 1, 1);
        assertSizeDelta(16_383, 1, 2);
        assertSizeDelta(100, 63, 1);
        assertSizeDelta(20_000, 16_383, 2);
        assertSizeDelta(63, 63, 2);
        assertSizeDelta(16_383, 16_383, 4);
    }

    @Test
    void rejectsHugeDeclaredRangeCountBeforeAllocation() {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        VariableLengthEncoder.encode(buffer, QuicFrame.ACK);
        VariableLengthEncoder.encode(buffer, 0);
        VariableLengthEncoder.encode(buffer, 0);
        VariableLengthEncoder.encode(buffer, Integer.MAX_VALUE);
        buffer.flip();

        QuicTransportException failure = assertThrows(QuicTransportException.class,
                                                       () -> QuicFrame.decodeWithAckRangeLimit(buffer, 1024));

        assertThat(failure.frameType(), is((long) QuicFrame.ACK));
        assertThat(failure.errorCode(), is(QuicTransportErrors.FRAME_ENCODING_ERROR.code()));
    }

    @Test
    void rejectsRangeCountThatCannotFitInRemainingPayload() {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[] {0x02, 4, 0, 1, 0});

        QuicTransportException failure = assertThrows(QuicTransportException.class,
                                                       () -> QuicFrame.decode(buffer));

        assertThat(failure.frameType(), is((long) QuicFrame.ACK));
        assertThat(failure.errorCode(), is(QuicTransportErrors.FRAME_ENCODING_ERROR.code()));
    }

    @Test
    void rejectsLargeRangeCountThatPassesConfiguredLimitButCannotFit() {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        VariableLengthEncoder.encode(buffer, QuicFrame.ACK);
        VariableLengthEncoder.encode(buffer, 0);
        VariableLengthEncoder.encode(buffer, 0);
        VariableLengthEncoder.encode(buffer, 32_762);
        VariableLengthEncoder.encode(buffer, 0);
        buffer.flip();

        QuicTransportException failure = assertThrows(
                QuicTransportException.class,
                () -> QuicFrame.decodeWithAckRangeLimit(buffer, 32_763));

        assertThat(failure.getMessage(), containsString("remaining payload"));
        assertThat(failure.errorCode(), is(QuicTransportErrors.FRAME_ENCODING_ERROR.code()));
    }

    @Test
    void reservesTrailingEcnCountersWhenCheckingRangeCount() {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[] {0x03, 2, 0, 1, 0, 0, 0, 0, 0});

        QuicTransportException failure = assertThrows(
                QuicTransportException.class,
                () -> QuicFrame.decodeWithAckRangeLimit(buffer, 2));

        assertThat(failure.getMessage(), containsString("remaining payload"));
        assertThat(failure.errorCode(), is(QuicTransportErrors.FRAME_ENCODING_ERROR.code()));
    }

    @Test
    void reportsAckEcnTypeForTruncatedCounter() {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[] {0x03, 0, 0, 0, 0, 0, 0, 0x40});

        QuicTransportException failure = assertThrows(QuicTransportException.class,
                                                       () -> QuicFrame.decode(buffer));

        assertThat(failure.frameType(), is(0x03L));
        assertThat(failure.errorCode(), is(QuicTransportErrors.FRAME_ENCODING_ERROR.code()));
    }

    @Test
    void enforcesConfiguredLimitOnStructurallyValidFrame() throws Exception {
        byte[] encoded = {0x02, 6, 0, 2, 0, 0, 0, 0, 0};

        QuicPacketDiscardException failure = assertThrows(
                QuicPacketDiscardException.class,
                () -> QuicFrame.decodeWithAckRangeLimit(ByteBuffer.wrap(encoded), 2));
        AckFrame accepted = (AckFrame) QuicFrame.decodeWithAckRangeLimit(ByteBuffer.wrap(encoded), 3);

        assertThat(failure.getMessage(), containsString("3 > 2"));
        assertThat(accepted.ackRanges().size(), is(3));
    }

    @Test
    void reportsMalformedOverLimitFrameAsEncodingError() {
        byte[] encoded = {0x02, 1, 0, 2, 0, 0, 0, 0, 0};

        QuicTransportException failure = assertThrows(
                QuicTransportException.class,
                () -> QuicFrame.decodeWithAckRangeLimit(ByteBuffer.wrap(encoded), 1));

        assertThat(failure.getMessage(), containsString("Negative PN acknowledged"));
        assertThat(failure.frameType(), is((long) QuicFrame.ACK));
        assertThat(failure.errorCode(), is(QuicTransportErrors.FRAME_ENCODING_ERROR.code()));
    }

    @Test
    void standaloneDecoderRetainsStructurallyValidLargeAckFrame() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        VariableLengthEncoder.encode(buffer, QuicFrame.ACK);
        VariableLengthEncoder.encode(buffer, 2048);
        VariableLengthEncoder.encode(buffer, 0);
        VariableLengthEncoder.encode(buffer, 1024);
        VariableLengthEncoder.encode(buffer, 0);
        for (int i = 0; i < 1024; i++) {
            VariableLengthEncoder.encode(buffer, 0);
            VariableLengthEncoder.encode(buffer, 0);
        }
        buffer.flip();

        AckFrame accepted = (AckFrame) QuicFrame.decode(buffer.asReadOnlyBuffer());
        QuicPacketDiscardException failure = assertThrows(
                QuicPacketDiscardException.class,
                () -> QuicFrame.decodeWithAckRangeLimit(buffer.asReadOnlyBuffer(), 1024));

        assertThat(accepted.ackRanges().size(), is(1025));
        assertThat(failure.getMessage(), containsString("1025 > 1024"));
    }

    @Test
    void validatesOverLimitShapedAckWithoutDiscardSignal() throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[] {0x02, 6, 0, 2, 0, 0, 0, 0, 0});

        long type = QuicFrame.validateAfterAckPolicyDiscard(buffer);

        assertThat(type, is(0x02L));
        assertThat(buffer.position(), is(buffer.limit()));
    }

    private static void assertSizeDelta(long largestAcknowledged,
                                        long firstAckRange,
                                        int expectedDelta) throws Exception {
        AckFrame frame = AckFrame.create(largestAcknowledged,
                                         3,
                                         List.of(AckFrame.AckRange.of(0, firstAckRange)));
        AckFrame extended = frame.withNextAcknowledged(largestAcknowledged + 1);

        assertThat(extended.size(), is(frame.size() + expectedDelta));
        assertThat(QuicFrame.decode(ByteBuffer.wrap(encode(extended))), is(extended));
    }

    private static byte[] encode(AckFrame frame) {
        ByteBuffer buffer = ByteBuffer.allocate(frame.size());
        frame.encode(buffer);
        assertThat(buffer.position(), is(frame.size()));
        return buffer.array();
    }
}
