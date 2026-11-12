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

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class BufferDataTransferTest {
    static Stream<Function<byte[], BufferData>> readableBuffers() {
        return Stream.of(BufferData::create,
                         bytes -> {
                             BufferData result = BufferData.growing(0);
                             result.write(bytes);
                             return result;
                         },
                         bytes -> BufferData.createReadOnly(bytes, 0, bytes.length),
                         bytes -> BufferData.create(BufferData.create(Arrays.copyOfRange(bytes, 0, 2)),
                                                    BufferData.create(Arrays.copyOfRange(bytes, 2, bytes.length))),
                         bytes -> {
                             CompositeBufferData result = BufferData.createComposite();
                             result.add(BufferData.create(Arrays.copyOfRange(bytes, 0, 2)));
                             result.add(BufferData.create(Arrays.copyOfRange(bytes, 2, bytes.length)));
                             return result;
                         });
    }

    @ParameterizedTest
    @MethodSource("readableBuffers")
    void readIntoRespectsPositionsAndLength(Function<byte[], BufferData> factory) {
        BufferData source = factory.apply(new byte[] {0, 1, 2, 3});
        source.skip(1);
        byte[] target = {9, 9, 9, 9, 9};

        int read = source.readInto(target, 2, 2);

        assertThat(read, is(2));
        assertThat(target, is(new byte[] {9, 9, 1, 2, 9}));
        assertThat(source.readBytes(), is(new byte[] {3}));
    }

    @ParameterizedTest
    @MethodSource("readableBuffers")
    void readIntoAllowsRequestedLengthLargerThanActualTransfer(Function<byte[], BufferData> factory) {
        BufferData source = factory.apply(new byte[] {1, 2, 3, 4});
        source.skip(2);
        byte[] target = {9, 9, 9, 9};

        int read = source.readInto(target, 1, 10);

        assertThat(read, is(2));
        assertThat(target, is(new byte[] {9, 3, 4, 9}));
        assertThat(source.available(), is(0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void compositeReadIntoSnapshotsOverlappingChildren(boolean mutable) {
        byte[] target = {0, 0, 3, 4, 0, 0};
        BufferData first = BufferData.create(new byte[] {1, 2, 9, 9});
        BufferData second = BufferData.createReadOnly(target, 2, 2);
        BufferData source;
        if (mutable) {
            CompositeBufferData composite = BufferData.createComposite(first);
            composite.add(second);
            source = composite;
        } else {
            source = BufferData.create(first, second);
        }

        int read = source.readInto(target, 0, target.length);

        assertThat(read, is(6));
        assertThat(target, is(new byte[] {1, 2, 9, 9, 3, 4}));
        assertThat(source.available(), is(0));
    }

    @Test
    void defaultReadIntoIsolatesRetainedArrayAndPreservesShortRead() {
        AtomicReference<byte[]> retained = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        BufferData backing = BufferData.create(new byte[] {1, 2, 3});
        BufferData source = new TestBufferData(backing) {
            @Override
            public int read(byte[] bytes, int position, int length) {
                retained.set(bytes);
                calls.incrementAndGet();
                return super.read(bytes, position, Math.min(1, length));
            }
        };
        byte[] target = {9, 9, 9, 9};

        int read = source.readInto(target, 1, 3);
        assertThat(retained.get(), notNullValue());
        retained.get()[0] = 8;

        assertThat(read, is(1));
        assertThat(calls.get(), is(1));
        assertThat(target, is(new byte[] {9, 1, 9, 9}));
        assertThat(backing.readBytes(), is(new byte[] {2, 3}));
    }

    @Test
    void defaultWriteFromIsolatesRetainedSourceRange() {
        AtomicReference<byte[]> retained = new AtomicReference<>();
        BufferData target = new TestBufferData(BufferData.empty()) {
            @Override
            public void write(byte[] bytes, int offset, int length) {
                retained.set(bytes);
                assertThat(offset, is(0));
                assertThat(length, is(2));
            }
        };
        byte[] source = {9, 1, 2, 9};

        target.writeFrom(source, 1, 2);
        source[1] = 8;

        assertThat(retained.get(), is(new byte[] {1, 2}));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void writeFromCopiesRequestedRangeWithoutRetainingSource(boolean growing) {
        BufferData target = growing ? BufferData.growing(0) : BufferData.create(5);
        target.write(7);
        byte[] source = {9, 1, 2, 9};

        target.writeFrom(source, 1, 2);
        source[1] = 8;
        target.write(6);

        assertThat(target.readBytes(), is(new byte[] {7, 1, 2, 6}));
    }

    @Test
    void writeFromSnapshotsOverlappingStorage() {
        byte[] bytes = {1, 2, 3, 4, 0, 0};
        BufferData target = BufferData.create(bytes).clear();
        target.write(1);
        target.write(2);

        target.writeFrom(bytes, 0, 4);

        assertThat(target.readBytes(), is(new byte[] {1, 2, 1, 2, 3, 4}));
    }

    @ParameterizedTest
    @ValueSource(strings = {"fixed", "growing", "read-only"})
    void leafReadIntoDoesNotDispatchToOverriddenLegacyRead(String type) {
        BufferData source = switch (type) {
            case "fixed" -> new FixedBufferData(new byte[] {1, 2}) {
                @Override
                public int read(byte[] bytes, int position, int length) {
                    throw new IllegalStateException("Legacy read must not receive the destination array");
                }
            };
            case "growing" -> {
                BufferData buffer = new GrowingBufferData(0) {
                    @Override
                    public int read(byte[] bytes, int position, int length) {
                        throw new IllegalStateException("Legacy read must not receive the destination array");
                    }
                };
                buffer.write(new byte[] {1, 2});
                yield buffer;
            }
            case "read-only" -> new ReadOnlyArrayData(new byte[] {1, 2}, 0, 2) {
                @Override
                public int read(byte[] bytes, int position, int length) {
                    throw new IllegalStateException("Legacy read must not receive the destination array");
                }
            };
            default -> throw new IllegalArgumentException(type);
        };
        byte[] target = {9, 9, 9};

        assertThat(source.readInto(target, 1, 2), is(2));
        assertThat(target, is(new byte[] {9, 1, 2}));
        assertThat(source.available(), is(0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void leafWriteFromDoesNotDispatchToOverriddenLegacyWrite(boolean growing) {
        BufferData target = growing ? new GrowingBufferData(0) {
            @Override
            public void write(byte[] bytes, int offset, int length) {
                throw new IllegalStateException("Legacy write must not receive the source array");
            }
        } : new FixedBufferData(2) {
            @Override
            public void write(byte[] bytes, int offset, int length) {
                throw new IllegalStateException("Legacy write must not receive the source array");
            }
        };
        byte[] source = {9, 1, 2, 9};

        target.writeFrom(source, 1, 2);
        source[1] = 8;

        assertThat(target.readBytes(), is(new byte[] {1, 2}));
    }

    @Test
    void growingBufferCanWriteItselfWhileGrowing() {
        BufferData buffer = BufferData.growing(0);
        byte[] bytes = new byte[200];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) i;
        }
        buffer.write(bytes);

        buffer.write(buffer, bytes.length);

        assertThat(buffer.available(), is(bytes.length));
        assertThat(buffer.readBytes(), is(bytes));
        buffer.rewind();
        byte[] expected = new byte[400];
        System.arraycopy(bytes, 0, expected, 0, bytes.length);
        System.arraycopy(bytes, 0, expected, bytes.length, bytes.length);
        assertThat(buffer.readBytes(), is(expected));
    }

    @Test
    void writeFromSharedIsolatesArbitraryTarget() {
        AtomicReference<byte[]> retained = new AtomicReference<>();
        BufferData target = new TestBufferData(BufferData.empty()) {
            @Override
            public void write(byte[] bytes, int offset, int length) {
                retained.set(bytes);
            }
        };
        byte[] shared = {1, 2};

        BufferData.writeFromShared(target, shared);
        shared[0] = 8;

        assertThat(retained.get(), is(new byte[] {1, 2}));
    }

    @Test
    void writeFromSharedUsesInheritedStrongLeafWrite() {
        AtomicReference<byte[]> retained = new AtomicReference<>();
        BufferData target = new FixedBufferData(2) {
            @Override
            public void write(byte[] bytes, int offset, int length) {
                retained.set(bytes);
                super.write(bytes, offset, length);
            }
        };

        BufferData.writeFromShared(target, new byte[] {1, 2});

        assertThat(retained.get(), nullValue());
        assertThat(target.readBytes(), is(new byte[] {1, 2}));
    }

}
