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

package io.helidon.common.buffers;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FixedBufferDataTest {
    static Stream<Arguments> sources() {
        return Stream.<Function<byte[], BufferData>>of(BufferData::create,
                                                       bytes -> {
                                                           BufferData source = BufferData.growing(bytes.length);
                                                           source.write(bytes);
                                                           return source;
                                                       },
                                                       bytes -> {
                                                           byte[] padded = new byte[bytes.length + 2];
                                                           System.arraycopy(bytes, 0, padded, 1, bytes.length);
                                                           return BufferData.createReadOnly(padded, 1, bytes.length);
                                                       })
                .flatMap(factory -> Stream.of(false, true).map(explicit -> Arguments.of(factory, explicit)));
    }

    @ParameterizedTest
    @MethodSource("sources")
    void writeRespectsSourceAndDestinationPositions(Function<byte[], BufferData> factory, boolean explicit) {
        BufferData source = factory.apply(new byte[] {0, 1, 2, 3, 4});
        source.skip(1);
        BufferData target = BufferData.create(4);
        target.write(9);

        write(target, source, 3, explicit);

        assertThat(target.capacity(), is(0));
        assertThat(target.readBytes(), is(new byte[] {9, 1, 2, 3}));
        assertThat(source.readBytes(), is(new byte[] {4}));
    }

    @ParameterizedTest
    @MethodSource("sources")
    void writeAdvancesByActualReadCount(Function<byte[], BufferData> factory, boolean explicit) {
        BufferData source = factory.apply(new byte[] {1, 2});
        BufferData target = BufferData.create(6);
        target.write(9);

        write(target, source, 10, explicit);
        target.write(8);

        assertThat(target.available(), is(4));
        assertThat(target.capacity(), is(2));
        assertThat(target.readBytes(), is(new byte[] {9, 1, 2, 8}));
        assertThat(source.available(), is(0));
    }

    @ParameterizedTest
    @MethodSource("sources")
    void writeEmptySource(Function<byte[], BufferData> factory, boolean explicit) {
        BufferData target = BufferData.create(4);
        target.write(9);

        write(target, factory.apply(new byte[0]), 3, explicit);
        target.write(8);

        assertThat(target.readBytes(), is(new byte[] {9, 8}));
        assertThat(target.capacity(), is(2));
    }

    @ParameterizedTest
    @MethodSource("sources")
    void writeZeroBytesToFullDestination(Function<byte[], BufferData> factory, boolean explicit) {
        BufferData source = factory.apply(new byte[] {1, 2});
        BufferData target = BufferData.create(new byte[] {9});

        write(target, source, 0, explicit);

        assertThat(target.readBytes(), is(new byte[] {9}));
        assertThat(source.readBytes(), is(new byte[] {1, 2}));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void writeOverlappingSource(boolean explicit) {
        byte[] bytes = {1, 2, 3, 4, 0, 0};
        BufferData source = BufferData.create(bytes, 0, 4);
        BufferData target = BufferData.create(bytes).clear();
        target.write(new byte[] {1, 2});

        write(target, source, 4, explicit);

        assertThat(target.readBytes(), is(new byte[] {1, 2, 1, 2, 3, 4}));
        assertThat(source.available(), is(0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void writeSelf(boolean explicit) {
        BufferData target = BufferData.create(6);
        target.write(new byte[] {1, 2, 3});

        write(target, target, 3, explicit);

        assertThat(target.readBytes(), is(new byte[] {1, 2, 3}));
        assertThat(target.capacity(), is(0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void writeCompositeSnapshotsOverlappingChildren(boolean explicit) {
        byte[] bytes = {0, 0, 3, 4, 0, 0};
        BufferData source = BufferData.create(BufferData.create(new byte[] {1, 2, 9, 9}),
                                              BufferData.create(bytes, 2, 2));
        BufferData target = BufferData.create(bytes).clear();

        write(target, source, 6, explicit);

        assertThat(target.readBytes(), is(new byte[] {1, 2, 9, 9, 3, 4}));
        assertThat(source.available(), is(0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void writeDoesNotExposeBackingArray(boolean explicit) {
        AtomicReference<byte[]> retained = new AtomicReference<>();
        BufferData source = new TestBufferData(BufferData.create(new byte[] {1})) {
            @Override
            public int read(byte[] bytes, int position, int length) {
                retained.set(bytes);
                return super.read(bytes, position, length);
            }
        };
        BufferData target = BufferData.create(4);
        target.write(9);

        write(target, source, 3, explicit);
        assertThat(retained.get(), notNullValue());
        retained.get()[0] = 2;

        assertThat(target.readBytes(), is(new byte[] {9, 1}));
        assertThat(target.capacity(), is(2));
    }

    @Test
    void writeNegativeLengthDoesNotConsumeSource() {
        BufferData target = BufferData.create(4);
        BufferData source = BufferData.create(new byte[] {1});

        assertThrows(NegativeArraySizeException.class, () -> target.write(source, -1));

        assertThat(target.available(), is(0));
        assertThat(source.available(), is(1));
    }

    private static void write(BufferData target, BufferData source, int length, boolean explicit) {
        if (explicit) {
            target.write(source, length);
        } else {
            target.write(source);
        }
    }
}
