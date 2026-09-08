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

package io.helidon.http.http2;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.HuffmanCodec;

/**
 * Implementation of HPACK Huffman encoding.
 */
public class Http2HuffmanEncoder {
    private static final int HUFFMAN_ENCODED = 1 << 7;

    /**
     * Huffman encoder.
     */
    private Http2HuffmanEncoder() {
    }

    /**
     * Creates a new HPACK Huffman encoder.
     *
     * @return a new Huffman encoder
     */
    public static Http2HuffmanEncoder create() {
        return new Http2HuffmanEncoder();
    }

    static byte[] encodeLatin1(String value) {
        byte[] bytes = new byte[value.length()];
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character > 0xff) {
                throw new IllegalArgumentException("Header value contains a character above 0xff");
            }
            bytes[i] = (byte) character;
        }
        return bytes;
    }

    void encode(BufferData buffer, String string) {
        byte[] bytes = new byte[string.length()];
        int encodedLength = HuffmanCodec.encode(string, bytes);
        if (encodedLength == -1) {
            bytes = new byte[HuffmanCodec.encodedLength(string)];
            encodedLength = HuffmanCodec.encode(string, bytes);
        }

        buffer.writeHpackInt(encodedLength, HUFFMAN_ENCODED, 7);
        buffer.write(bytes, 0, encodedLength);
    }
}
