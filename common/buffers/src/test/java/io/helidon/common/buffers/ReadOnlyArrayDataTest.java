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

package io.helidon.common.buffers;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;

class ReadOnlyArrayDataTest {
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