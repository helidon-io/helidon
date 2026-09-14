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

import java.nio.charset.StandardCharsets;

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
        int index = 0;

        long current = 0;
        int n = 0;
        byte[] bytes = new byte[encodedLength(string)];

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

    private static int encodedLength(String string) {
        long bitLength = 0;
        for (int i = 0; i < string.length(); i++) {
            bitLength += Http2HuffmanConstants.HUFFMAN_CODE_LENGTHS[string.charAt(i)];
        }
        return Math.toIntExact((bitLength + 7) / 8);
    }
}
