/*
 * Copyright (c) 2024, 2026 Oracle and/or its affiliates.
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class DataReaderTest {

    @Test
    void directReadDrainsBufferedBytesFirst() {
        var supplied = new AtomicReference<>(new byte[] {1, 2, 3});
        var directCalls = new AtomicInteger();
        var direct = new AtomicReference<>(new byte[] {4, 5, 6, 7});
        DataReader dataReader = DataReader.create(
                () -> supplied.getAndSet(null),
                (bytes, offset, length) -> {
                    directCalls.incrementAndGet();
                    byte[] source = direct.getAndSet(null);
                    if (source == null) {
                        return -1;
                    }
                    int read = Math.min(source.length, length);
                    System.arraycopy(source, 0, bytes, offset, read);
                    return read;
                });
        dataReader.ensureAvailable();
        byte[] target = new byte[7];

        assertThat(dataReader.read(target, 0, target.length), is(3));
        assertThat(directCalls.get(), is(0));
        assertThat(dataReader.read(target, 3, target.length - 3), is(4));
        assertThat(directCalls.get(), is(1));
        assertArrayEquals(new byte[] {1, 2, 3, 4, 5, 6, 7}, target);
    }

    @Test
    void bulkReadFallsBackToBufferedSupplier() {
        var supplied = new AtomicReference<>(new byte[] {1, 2, 3});
        DataReader dataReader = DataReader.create(() -> supplied.getAndSet(null));
        byte[] target = new byte[5];

        assertThat(dataReader.read(target, 1, 3), is(3));
        assertArrayEquals(new byte[] {0, 1, 2, 3, 0}, target);
    }

    @Test
    void bulkReadReturnsEndOfInputForExhaustedSupplier() {
        DataReader dataReader = DataReader.create(() -> null);

        assertThat(dataReader.read(new byte[1], 0, 1), is(-1));
    }

    @Test
    void testFindNewLineWithLoneCR() {
        // reading N bytes at a time until a new line is found
        // with data containing a lone CR

        byte[] data = "00\r0\r\n".getBytes(StandardCharsets.US_ASCII);
        AtomicReference<byte[]> ref = new AtomicReference<>(data);
        DataReader dataReader = DataReader.create(() -> ref.getAndSet(null), true);

        int n = 2;
        assertThat(dataReader.findNewLine(n), is(n));
        dataReader.skip(n);
        assertThat(dataReader.findNewLine(n), is(n));
        dataReader.skip(n);
        assertThat(dataReader.findNewLine(n), is(0));
    }

    @Test
    void testFindNewLineWithMultipleLoneCR() {
        // if the stream index is accumulated with the node index for each lone CR
        // it may exceed max and the new line is ignored

        byte[] data = "00\r\r\r\n".getBytes(StandardCharsets.US_ASCII);
        AtomicReference<byte[]> ref = new AtomicReference<>(data);
        DataReader dataReader = DataReader.create(() -> ref.getAndSet(null), true);

        int n = 5;
        assertThat(dataReader.findNewLine(n), is(4));
    }

    @Test
    void testFindNewLineWithMultipleLoneWithinMax() {
        // if the stream index is not updated for each lone CR
        // the computed search range is too big and a value greater than max is returned

        byte[] data = "00\r00\r\n00".getBytes(StandardCharsets.US_ASCII);
        AtomicReference<byte[]> ref = new AtomicReference<>(data);
        DataReader dataReader = DataReader.create(() -> ref.getAndSet(null), true);

        int n = 4;
        assertThat(dataReader.findNewLine(n), is(n));
        dataReader.skip(n);
        assertThat(dataReader.findNewLine(n), is(1));
    }
}
