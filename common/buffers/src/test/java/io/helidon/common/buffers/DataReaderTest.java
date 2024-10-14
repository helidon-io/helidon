/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class DataReaderTest {

    @Test
    void testFindNewLineWithLoneCR() {
        // reading N bytes at a time until a new line is found
        // with data containing a lone CR

        byte[] data = "00\r0\r\n".getBytes(StandardCharsets.US_ASCII);
        AtomicReference<byte[]> ref = new AtomicReference<>(data);
        DataReader dataReader = new DataReader(() -> ref.getAndSet(null), true);

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
        DataReader dataReader = new DataReader(() -> ref.getAndSet(null), true);

        int n = 5;
        assertThat(dataReader.findNewLine(n), is(4));
    }

    @Test
    void testFindNewLineWithMultipleLoneWithinMax() {
        // if the stream index is not updated for each lone CR
        // the computed search range is too big and a value greater than max is returned

        byte[] data = "00\r00\r\n00".getBytes(StandardCharsets.US_ASCII);
        AtomicReference<byte[]> ref = new AtomicReference<>(data);
        DataReader dataReader = new DataReader(() -> ref.getAndSet(null), true);

        int n = 4;
        assertThat(dataReader.findNewLine(n), is(n));
        dataReader.skip(n);
        assertThat(dataReader.findNewLine(n), is(1));
    }
}
