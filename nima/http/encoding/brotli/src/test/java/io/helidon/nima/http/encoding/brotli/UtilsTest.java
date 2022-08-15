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

import static org.junit.jupiter.api.Assertions.assertEquals;

class UtilsTest {

    @Test
    void testGet16Bits() {
        int result = Utils.get16Bits(new int[] {1, 1}, 0);
        assertEquals(257, result);

        result = Utils.get16Bits(new int[] {0, 1, 1}, 1);
        assertEquals(257, result);

        result = Utils.get16Bits(new int[] {0, 1, 1}, 2);
        assertEquals(1, result);

        result = Utils.get16Bits(new int[] {0, 1, 1}, 3);
        assertEquals(0, result);
    }

    @Test
    void testGet32Bits() {
        int result = Utils.get32Bits(new int[] {1, 1}, 0);
        assertEquals(257, result);

        result = Utils.get32Bits(new int[] {0, 1, 1}, 1);
        assertEquals(257, result);

        result = Utils.get32Bits(new int[] {0, 1, 1}, 2);
        assertEquals(1, result);

        result = Utils.get32Bits(new int[] {0, 1, 1}, 3);
        assertEquals(0, result);
    }

    @Test
    void testGet64Bits() {
        long result = Utils.get64Bits(new int[] {1, 1}, 0);
        assertEquals(257, result);

        result = Utils.get64Bits(new int[] {0, 1, 1}, 1);
        assertEquals(257, result);

        result = Utils.get64Bits(new int[] {0, 1, 1}, 2);
        assertEquals(1, result);

        result = Utils.get64Bits(new int[] {0, 1, 1}, 3);
        assertEquals(0, result);
    }

    @Test
    void testLog2FloorNonZero() {
        int n = Utils.log2FloorNonZero(4);
        assertEquals(2, n);
    }
}
