/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.http.encoding.brotli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BitWriterTest {

    @Test
    void testWriteBits() throws BrotliException {
        int[] array = {1, 0, 0};
        int position = 1;
        long data = 1;
        position = BitWriter.writeBit(1, data, position, array);

        assertEquals(3, array[0]);
        assertEquals(2, position);
    }

    @Test
    void testWriteBits1() throws BrotliException {
        int[] array = {0, 1, 0};
        int position = 9;
        long data = 1;
        position = BitWriter.writeBit(1, data, position, array);

        assertEquals(3, array[1]);
        assertEquals(10, position);
    }

    @Test
    void testWriteBits2() throws BrotliException {
        int[] array = {0, 0, 0, 0, 0, 0, 0, 0};
        int position = 0;
        long data = Long.MAX_VALUE;
        position = BitWriter.writeBit(64, data, position, array);

        assertArrayEquals(new int[] {255, 255, 255, 255, 255, 255, 255, 127}, array);
        assertEquals(64, position);
    }
}
