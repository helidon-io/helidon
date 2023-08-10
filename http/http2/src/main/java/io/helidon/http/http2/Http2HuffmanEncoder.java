/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

/*
 * This class is mostly copied from Netty.
 * Original Copyright:
 *
 * Copyright 2014 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.http.http2;

import io.helidon.common.buffers.BufferData;

/**
 * Implementation of HPack Huffman encoding.
 */
public class Http2HuffmanEncoder {
    private static final int HUFFMAN_ENCODED = 1 << 7;

    /**
     * Huffman encoder.
     */
    private Http2HuffmanEncoder() {
    }

    /**
     * Creates a new HPack Huffman encoder.
     *
     * @return a new Huffman encoder
     */
    public static Http2HuffmanEncoder create() {
        return new Http2HuffmanEncoder();
    }

    void encode(BufferData buffer, String string) {
        int index = 0;

        long current = 0;
        int n = 0;
        byte[] bytes = new byte[string.length()];

        for (int i = 0; i < string.length(); i++) {
            int b = string.charAt(i) & 0xFF;
            int code = Http2HuffmanConstants.HUFFMAN_CODES[b];
            int nbits = Http2HuffmanConstants.HUFFMAN_CODE_LENGTHS[b];

            current <<= nbits;
            current |= code;
            n += nbits;

            while (n >= 8) {
                n -= 8;
                bytes[index] = ((byte) (current >> n));
                index++;
            }
        }

        if (n > 0) {
            current <<= 8 - n;
            current |= 0xFF >>> n; // this should be EOS symbol
            bytes[index] = ((byte) current);
            index++;
        }

        buffer.writeHpackInt(index, HUFFMAN_ENCODED, 7);
        buffer.write(bytes, 0, index);

    }
}
