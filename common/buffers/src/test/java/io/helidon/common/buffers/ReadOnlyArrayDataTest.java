/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ReadOnlyArrayDataTest {
    @Test
    void writeToOutputStreamAfterPartialRead() throws Exception {
        // Regression test for https://github.com/helidon-io/helidon/issues/11738:
        // writeTo(OutputStream) passed `length` as the byte count instead of
        // `length - position`, causing excess bytes to be written when position > 0.
        byte[] data = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        ReadOnlyArrayData buf = new ReadOnlyArrayData(data, 0, data.length);

        // Advance position by consuming 3 bytes
        buf.skip(3);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        buf.writeTo(out);

        assertThat(out.toByteArray(), is(new byte[] {4, 5, 6, 7, 8, 9, 10}));
    }

    @Test
    void lastIndexOfWithLengthAfterPartialRead() {
        byte[] data = "01abcy".getBytes(StandardCharsets.UTF_8);
        ReadOnlyArrayData buf = new ReadOnlyArrayData(data, 0, data.length);

        buf.skip(2);

        assertThat(buf.lastIndexOf((byte) 'y', 4), is(3));
    }

    @Test
    void lastIndexOfClampsLengthToAvailable() {
        byte[] data = "01abcy".getBytes(StandardCharsets.UTF_8);
        ReadOnlyArrayData buf = new ReadOnlyArrayData(data, 0, data.length);

        buf.skip(2);

        assertThat(buf.lastIndexOf((byte) 'y', 5), is(3));
        assertThat(buf.lastIndexOf((byte) 0, 5), is(-1));
    }

    @Test
    void lastIndexOfIgnoresNegativeLength() {
        byte[] data = "abc".getBytes(StandardCharsets.UTF_8);
        ReadOnlyArrayData buf = new ReadOnlyArrayData(data, 0, data.length);

        assertThat(buf.lastIndexOf((byte) 'a', Integer.MIN_VALUE), is(-1));
    }

    @Test
    void binaryDebugHonorsLogicalRangeAndReadPosition() {
        byte[] data = {90, 91, 1, 2, 3, 4, 92, 93};
        BufferData buffer = BufferData.createReadOnly(data, 2, 4);
        String fullRange = BufferData.create(new byte[] {1, 2, 3, 4}).debugDataBinary();

        assertThat("binary diagnostics must exclude bytes outside the selected range", buffer.debugDataBinary(), is(fullRange));
        assertThat("diagnostics must not consume the buffer", buffer.available(), is(4));

        buffer.skip(1);

        assertThat("binary diagnostics must include all remaining bytes",
                   buffer.debugDataBinary(),
                   is(BufferData.create(new byte[] {2, 3, 4}).debugDataBinary()));
        assertThat("diagnostics must preserve the read position", buffer.available(), is(3));

        buffer.skip(3);

        assertThat("a consumed buffer must not print payload bytes",
                   buffer.debugDataBinary(),
                   is(BufferData.create(new byte[0]).debugDataBinary()));

        buffer.rewind();

        assertThat("rewinding must restore the selected range", buffer.debugDataBinary(), is(fullRange));
        assertThat(buffer.available(), is(4));
    }

    @Test
    void hexDebugHonorsLogicalRangeAndReadPosition() {
        byte[] data = {90, 91, 1, 2, 3, 4, 92, 93};
        BufferData buffer = BufferData.createReadOnly(data, 2, 4);
        String fullRange = BufferData.create(new byte[] {1, 2, 3, 4}).debugDataHex();

        assertThat("hex diagnostics must exclude bytes outside the selected range", buffer.debugDataHex(false), is(fullRange));
        assertThat(buffer.available(), is(4));

        buffer.skip(1);

        assertThat("hex diagnostics must include all remaining bytes",
                   buffer.debugDataHex(false),
                   is(BufferData.create(new byte[] {2, 3, 4}).debugDataHex()));
        assertThat("full-buffer diagnostics must retain the selected range", buffer.debugDataHex(true), is(fullRange));
        assertThat("diagnostics must preserve the read position", buffer.available(), is(3));

        buffer.skip(3);

        assertThat("a consumed buffer must not print unread payload bytes",
                   buffer.debugDataHex(false),
                   is(BufferData.create(new byte[0]).debugDataHex()));
        assertThat("full-buffer diagnostics must include consumed bytes", buffer.debugDataHex(true), is(fullRange));

        buffer.rewind();

        assertThat("rewinding must restore the selected range", buffer.debugDataHex(false), is(fullRange));
        assertThat(buffer.available(), is(4));
    }

    @Test
    void testDebugData() {
        byte[] test = "Hello World!".getBytes(StandardCharsets.UTF_8);
        ReadOnlyArrayData rad = new ReadOnlyArrayData(test, 1, test.length - 1);
        String debugged = rad.debugDataHex();
        List<String> lines = debugged.lines().toList();
        assertThat(lines, hasItems("+--------+-------------------------------------------------+----------------+",
                                   "|   index|  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |            data|",
                                   "+--------+-------------------------------------------------+----------------+",
                                   "|00000000| 65 6c 6c 6f 20 57 6f 72 6c 64 21                |ello World!     |",
                                   "+--------+-------------------------------------------------+----------------+"));
    }
}
