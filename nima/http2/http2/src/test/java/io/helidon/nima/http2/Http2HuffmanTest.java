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

package io.helidon.nima.http2;

import io.helidon.common.buffers.BufferData;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class Http2HuffmanTest {
    @Test
    void testEncodeDecode() {
        Http2HuffmanEncoder enc = new Http2HuffmanEncoder();
        Http2HuffmanDecoder dec = new Http2HuffmanDecoder();

        String value = "my very nice long value";
        BufferData result = BufferData.growing(32);
        enc.encode(result, value);

        result.read(); // 1 byte - length
        String decoded = dec.decodeString(result, result.available());

        assertThat(decoded, is(value));
    }
}