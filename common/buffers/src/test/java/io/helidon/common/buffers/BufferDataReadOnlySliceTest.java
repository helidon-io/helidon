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

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BufferDataReadOnlySliceTest {

    @Test
    void readOnlySliceHasIndependentCursorAndStrictLogicalBounds() {
        BufferData source = BufferData.createReadOnly("guardHEADbodyTAILguard".getBytes(StandardCharsets.UTF_8), 5, 12);
        source.skip(4);

        BufferData body = BufferData.readOnlySlice(source, 4);

        assertThrows(IndexOutOfBoundsException.class, () -> body.get(4));
        assertThat(body.readString(4), is("body"));
        assertThat(source.readString(4), is("TAIL"));
        body.rewind();
        assertThrows(IndexOutOfBoundsException.class, () -> body.get(4));
        assertThat(body.readString(4), is("body"));
        assertThrows(IndexOutOfBoundsException.class, () -> body.get(0));
        assertThrows(UnsupportedOperationException.class, () -> body.write(1));
    }

    @Test
    void slicesAdvanceOnlyTheSourceRangeTheyConsume() {
        BufferData source = BufferData.createReadOnly(new byte[] {1, 2, 3, 4, 5}, 0, 5);

        BufferData first = BufferData.readOnlySlice(source, 2);
        BufferData second = BufferData.readOnlySlice(source, 2);

        assertThat(first.readBytes(), is(new byte[] {1, 2}));
        assertThat(second.readBytes(), is(new byte[] {3, 4}));
        assertThat(source.readBytes(), is(new byte[] {5}));
        first.rewind();
        assertThat(first.readBytes(), is(new byte[] {1, 2}));
    }

    @Test
    void readOnlyArraySliceSharesBackingArray() {
        byte[] bytes = {1, 2, 3, 4};
        BufferData source = BufferData.createReadOnly(bytes, 0, bytes.length);
        source.skip(1);

        BufferData slice = BufferData.readOnlySlice(source, 3);
        BufferData resliced = BufferData.readOnlySlice(slice, 2);
        bytes[1] = 9;

        assertThat(resliced.readBytes(), is(new byte[] {9, 3}));
    }

    @Test
    void unknownMutableSourceUsesIndependentCopy() {
        byte[] bytes = {1, 2, 3, 4};
        BufferData source = BufferData.create(bytes);

        BufferData slice = BufferData.readOnlySlice(source, 3);
        BufferData resliced = BufferData.readOnlySlice(slice, 2);
        bytes[0] = 9;

        assertThat(resliced.readBytes(), is(new byte[] {1, 2}));
        assertThat(source.readBytes(), is(new byte[] {4}));
        assertThrows(UnsupportedOperationException.class, () -> resliced.write(1));
    }

    @Test
    void dataReaderSourcesUseIndependentCopyRegardlessOfFragmentation() {
        byte[] contiguousBytes = {0, 1, 2, 3};
        DataReader contiguousReader = DataReader.create(() -> contiguousBytes);
        contiguousReader.skip(1);
        BufferData contiguousSlice = BufferData.readOnlySlice(contiguousReader.getBuffer(3), 3);
        BufferData contiguousReslice = BufferData.readOnlySlice(contiguousSlice, 2);

        byte[] first = {0, 4, 5};
        byte[] second = {6};
        Iterator<byte[]> chunks = List.of(first, second).iterator();
        DataReader fragmentedReader = DataReader.create(chunks::next);
        fragmentedReader.skip(1);
        BufferData fragmentedSlice = BufferData.readOnlySlice(fragmentedReader.getBuffer(3), 3);
        BufferData fragmentedReslice = BufferData.readOnlySlice(fragmentedSlice, 2);

        contiguousBytes[1] = 9;
        first[1] = 9;
        second[0] = 9;

        assertThat(contiguousReslice.readBytes(), is(new byte[] {1, 2}));
        assertThat(fragmentedReslice.readBytes(), is(new byte[] {4, 5}));
    }

    @Test
    void compositeSourceUsesIndependentCopy() {
        byte[] first = {1, 2};
        byte[] second = {3, 4};
        BufferData source = BufferData.create(BufferData.createReadOnly(first, 0, first.length),
                                              BufferData.createReadOnly(second, 0, second.length));

        BufferData slice = BufferData.readOnlySlice(source, 3);
        first[0] = 9;
        second[0] = 9;

        assertThat(slice.readBytes(), is(new byte[] {1, 2, 3}));
        assertThat(source.readBytes(), is(new byte[] {4}));
    }

    @Test
    void emptyAndInvalidSlicesDoNotAdvanceSource() {
        BufferData source = BufferData.createReadOnly(new byte[] {1, 2}, 0, 2);

        BufferData empty = BufferData.readOnlySlice(source, 0);
        assertThat(empty.available(), is(0));
        assertThrows(UnsupportedOperationException.class, () -> empty.write(1));
        assertThat(source.available(), is(2));
        assertThrows(IndexOutOfBoundsException.class, () -> BufferData.readOnlySlice(source, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> BufferData.readOnlySlice(source, 3));
        assertThat(source.readBytes(), is(new byte[] {1, 2}));
    }
}
