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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicPacketNumbersTest {
    @ParameterizedTest
    @MethodSource("packetNumbers")
    void shouldRoundTripEncodedPacketNumbers(long fullPacketNumber, long largestAcked) {
        byte[] encoded = QuicPacketNumbers.encodePacketNumber(fullPacketNumber, largestAcked);
        long decoded = QuicPacketNumbers.decodePacketNumber(largestAcked, ByteBuffer.wrap(encoded), encoded.length);

        assertThat(decoded, is(fullPacketNumber));
        assertThat(encoded.length, is(QuicPacketNumbers.computePacketNumberLength(fullPacketNumber, largestAcked)));
    }

    @Test
    void shouldRejectOversizedPacketNumberEncoding() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                   () -> QuicPacketNumbers.computePacketNumberLength(1L << 40, -1));

        assertThat(ex.getMessage(), is("Encoded packet number needs 6 bytes for pn=1099511627776, ack=-1"));
    }

    @Test
    void shouldRejectInvalidTruncationLength() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                   () -> QuicPacketNumbers.truncatePacketNumber(10, 5));

        assertThat(ex.getMessage(), is("Invalid packet number length: 5"));
    }

    private static Stream<Arguments> packetNumbers() {
        return Stream.of(
                Arguments.of(0L, -1L),
                Arguments.of(1L, -1L),
                Arguments.of(10L, 5L),
                Arguments.of(0x1234L, 0x1200L),
                Arguments.of(0x12_3456L, 0x12_3400L),
                Arguments.of(0x1234_5678L, 0x1234_5600L)
        );
    }
}
