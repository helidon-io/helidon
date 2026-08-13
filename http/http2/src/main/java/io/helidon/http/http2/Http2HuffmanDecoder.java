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
 * Implementation of HPACK Huffman decoding.
 */
public class Http2HuffmanDecoder {
    private static final String EMPTY_STRING = "";

    /**
     * Huffman decoder.
     */
    private Http2HuffmanDecoder() {
    }

    /**
     * Creates a new HPACK Huffman decoder.
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
        byte[] destination = new byte[Math.toIntExact((long) length * 8 / 5)];
        try {
            int decodedLength = HuffmanCodec.decode(data, length, destination);
            return new String(destination, 0, decodedLength, StandardCharsets.US_ASCII);
        } catch (IllegalArgumentException e) {
            throw new Http2Exception(Http2ErrorCode.COMPRESSION, "Cannot decode Huffman encoded string", e);
        }
    }
}
