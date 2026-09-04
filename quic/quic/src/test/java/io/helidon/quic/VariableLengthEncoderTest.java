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

package io.helidon.quic;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import io.helidon.common.buffers.BufferData;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VariableLengthEncoderTest {
    @ParameterizedTest
    @MethodSource("encodedValues")
    void shouldRoundTripEncodedValues(long value, int expectedSize) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        BufferData bufferData = BufferData.create(expectedSize);

        int written = VariableLengthEncoder.encode(buffer, value);
        int bufferDataWritten = VariableLengthEncoder.encode(bufferData, value);
        int positionAfterEncode = buffer.position();
        byte[] expectedBytes = Arrays.copyOf(buffer.array(), positionAfterEncode);

        assertThat(written, is(expectedSize));
        assertThat(bufferDataWritten, is(expectedSize));
        assertThat(VariableLengthEncoder.encodedSize(value), is(expectedSize));
        assertThat(VariableLengthEncoder.peekEncodedValueSize(buffer.flip(), 0), is(expectedSize));
        assertThat(VariableLengthEncoder.peekEncodedValue(buffer, 0), is(value));
        assertThat(VariableLengthEncoder.decode(buffer), is(value));
        assertThat(bufferData.readBytes(), is(expectedBytes));
        assertThat(VariableLengthEncoder.decode(BufferData.create(expectedBytes)), is(value));
        assertThat(buffer.position(), is(positionAfterEncode));
    }

    @ParameterizedTest
    @MethodSource("encodedValues")
    void shouldDecodeFromNonZeroPositionAcrossBufferKinds(long value, int expectedSize) {
        ByteBuffer encoded = ByteBuffer.allocate(8);
        VariableLengthEncoder.encode(encoded, value);
        byte[] bytes = new byte[expectedSize + 1];
        Arrays.fill(bytes, (byte) 0xFF);
        encoded.flip().get(bytes, 1, expectedSize);

        ByteBuffer heap = ByteBuffer.wrap(bytes.clone());
        ByteBuffer direct = ByteBuffer.allocateDirect(bytes.length).put(bytes).flip();
        ByteBuffer readOnlyLittleEndian = ByteBuffer.wrap(bytes).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        for (ByteBuffer buffer : List.of(heap, direct, readOnlyLittleEndian)) {
            buffer.position(1);
            int originalLimit = buffer.limit();

            assertThat(VariableLengthEncoder.decode(buffer), is(value));
            assertThat(buffer.position(), is(1 + expectedSize));
            assertThat(buffer.limit(), is(originalLimit));
        }
    }

    @ParameterizedTest
    @MethodSource("incompleteValues")
    void shouldNotAdvancePositionOnIncompleteDecode(byte[] incomplete) {
        ByteBuffer buffer = ByteBuffer.allocate(incomplete.length + 1);
        buffer.put((byte) 0xFF);
        buffer.put(incomplete);
        buffer.flip();
        buffer.position(1);
        int originalLimit = buffer.limit();

        long decoded = VariableLengthEncoder.decode(buffer);

        assertThat(decoded, is(-1L));
        assertThat(buffer.position(), is(1));
        assertThat(buffer.limit(), is(originalLimit));
    }

    @ParameterizedTest
    @MethodSource("nonMinimalValues")
    void shouldAcceptNonMinimalEncoding(byte[] encoded) {
        ByteBuffer buffer = ByteBuffer.wrap(encoded);

        assertThat(VariableLengthEncoder.decode(buffer), is(0L));
        assertThat(buffer.position(), is(encoded.length));
    }

    @Test
    void shouldRejectNullDecodeBuffer() {
        assertThrows(NullPointerException.class, () -> VariableLengthEncoder.decode((ByteBuffer) null));
    }

    @Test
    void shouldRejectEncodingValuesOutsideRange() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                   () -> VariableLengthEncoder.encode(ByteBuffer.allocate(8), -1));

        assertThat(ex.getMessage(), is("value supplied falls outside of acceptable bounds"));
    }

    @Test
    void shouldRejectEncodingIntoTooSmallBuffer() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                   () -> VariableLengthEncoder.encode(ByteBuffer.allocate(1), 64));

        assertThat(ex.getMessage(), is("buffer does not contain enough bytes to store length"));
    }

    @Test
    void shouldRejectEncodingIntoTooSmallBufferData() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                                   () -> VariableLengthEncoder.encode(BufferData.create(1), 64));

        assertThat(ex.getMessage(), is("buffer does not contain enough bytes to store length"));
    }

    private static Stream<Arguments> encodedValues() {
        return Stream.of(
                Arguments.of(0L, 1),
                Arguments.of(63L, 1),
                Arguments.of(64L, 2),
                Arguments.of(16383L, 2),
                Arguments.of(16384L, 4),
                Arguments.of((1L << 30) - 1, 4),
                Arguments.of(1L << 30, 8),
                Arguments.of(VariableLengthEncoder.MAX_ENCODED_INTEGER, 8)
        );
    }

    private static Stream<Arguments> incompleteValues() {
        return Stream.of(
                Arguments.of((Object) new byte[0]),
                Arguments.of((Object) new byte[] {(byte) 0x40}),
                Arguments.of((Object) new byte[] {(byte) 0x80, 0, 0}),
                Arguments.of((Object) new byte[] {(byte) 0xC0, 0, 0, 0, 0, 0, 0})
        );
    }

    private static Stream<Arguments> nonMinimalValues() {
        return Stream.of(
                Arguments.of((Object) new byte[] {(byte) 0x40, 0}),
                Arguments.of((Object) new byte[] {(byte) 0x80, 0, 0, 0}),
                Arguments.of((Object) new byte[] {(byte) 0xC0, 0, 0, 0, 0, 0, 0, 0})
        );
    }
}
