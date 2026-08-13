/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.HexFormat;

import io.helidon.common.buffers.BufferData;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http2HuffmanProtocolTest {
    private static final HexFormat HEX = HexFormat.of();

    @Test
    void encodesValueWhoseHuffmanRepresentationExpands() {
        assertArrayEquals(HEX.parseHex("82ffc7"), encode("\0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ff", "1e", "1c", "ffffffff"})
    void mapsMalformedInputToCompressionError(String encodedHex) {
        byte[] encoded = HEX.parseHex(encodedHex);

        Http2Exception failure = assertThrows(Http2Exception.class,
                                              () -> Http2HuffmanDecoder.create()
                                                      .decodeString(BufferData.create(encoded), encoded.length));
        assertThat(failure.code(), is(Http2ErrorCode.COMPRESSION));
        assertThat(failure.getCause(), instanceOf(IllegalArgumentException.class));
    }

    @Test
    void preservesLowByteEncodingCompatibility() {
        assertArrayEquals(encode("abca"), encode("abc\u0161"));
    }

    private static byte[] encode(String value) {
        BufferData encoded = BufferData.growing(value.length() + 1);
        Http2HuffmanEncoder.create().encode(encoded, value);
        return encoded.readBytes();
    }
}
