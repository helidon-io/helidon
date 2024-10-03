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

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class DataReaderTest {

    @Test
    void testFindNewLineWithLoneCR() {
        // reading N bytes at a time until a new line is found
        // with data containing a lone CR

        byte[] data = new byte[] {0, 0, (byte) '\r', 0, (byte) '\r', (byte) '\n'};
        AtomicReference<byte[]> ref = new AtomicReference<>(data);
        DataReader dataReader = new DataReader(() -> ref.getAndSet(null), true);

        int n = 2;
        assertThat(dataReader.findNewLine(n), is(n));
        dataReader.skip(n);
        assertThat(dataReader.findNewLine(n), is(n));
        dataReader.skip(n);
        assertThat(dataReader.findNewLine(n), is(0));
    }
}
