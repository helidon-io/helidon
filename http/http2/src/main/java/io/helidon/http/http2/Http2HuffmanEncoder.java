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

import java.nio.charset.StandardCharsets;

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
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }

    static void validateLatin1(String value) {
        int length = value.length();
        int i = 0;
        for (; i + 3 < length; i += 4) {
            int characters = value.charAt(i)
                    | value.charAt(i + 1)
                    | value.charAt(i + 2)
                    | value.charAt(i + 3);
            if ((characters & 0xff00) != 0) {
                throw new IllegalArgumentException("Header value contains a character above 0xff");
            }
        }
        for (; i < length; i++) {
            if (value.charAt(i) > 0xff) {
                throw new IllegalArgumentException("Header value contains a character above 0xff");
            }
        }
    }

    void encode(BufferData buffer, String string) {
        validateLatin1(string);
        encodeValidated(buffer, string);
    }

    void encodeValidated(BufferData buffer, String string) {
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
