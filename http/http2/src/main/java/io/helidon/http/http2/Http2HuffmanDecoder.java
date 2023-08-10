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

import java.nio.charset.StandardCharsets;
import java.util.function.BiFunction;

import io.helidon.common.buffers.BufferData;

/**
 * Implementation of HPack Huffman decoding.
 */
public class Http2HuffmanDecoder {
    private static final String EMPTY_STRING = "";
    private static final Http2Exception BAD_ENCODING = new Http2Exception(Http2ErrorCode.COMPRESSION,
                                                                          "Huffman bad encoding.");

    private byte[] dest;
    private int k;
    private int state;

    /**
     * Huffman decoder.
     */
    private Http2HuffmanDecoder() {
    }

    /**
     * Creates a new HPack Huffman decoder.
     *
     * @return a new Huffman decoder
     */
    public static Http2HuffmanDecoder create() {
        return new Http2HuffmanDecoder();
    }

    /**
     * Decode string.
     *
     * @param data   huffman encoded data
     * @param length length of the data
     * @return decoded string
     */
    public String decodeString(BufferData data, int length) {
        if (length == 0) {
            return EMPTY_STRING;
        }
        return decodeBytes(data, length, (bytes, size) -> new String(bytes, 0, size, StandardCharsets.US_ASCII));
    }

    private boolean process(byte input) {
        return processNibble(input >> 4) && processNibble(input);
    }

    private <T> T decodeBytes(BufferData data, int length, BiFunction<byte[], Integer, T> creator) {
        dest = new byte[length * 8 / 5];

        try {
            data.forEach(length, it -> {
                if (!process(it)) {
                    throw new Http2Exception(Http2ErrorCode.COMPRESSION,
                                             "Cannot decode Huffman encoded string");
                }
                return true;
            });
            if ((state & Http2HuffmanConstants.HUFFMAN_COMPLETE_SHIFT) != Http2HuffmanConstants.HUFFMAN_COMPLETE_SHIFT) {
                throw BAD_ENCODING;
            }
            return creator.apply(dest, k);
        } finally {
            dest = null;
            k = 0;
            state = 0;
        }
    }

    private boolean processNibble(int input) {
        // The high nibble of the flags byte of each row is always zero
        // (low nibble after shifting row by 12), since there are only 3 flag bits
        int index = state >> 12 | (input & 0x0F);
        state = Http2HuffmanConstants.HUFFS[index];
        if ((state & Http2HuffmanConstants.HUFFMAN_FAIL_SHIFT) != 0) {
            return false;
        }
        if ((state & Http2HuffmanConstants.HUFFMAN_EMIT_SYMBOL_SHIFT) != 0) {
            // state is always positive so can cast without mask here
            dest[k++] = (byte) state;
        }
        return true;
    }
}
