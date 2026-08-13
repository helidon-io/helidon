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

package io.helidon.common.buffers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HuffmanCodecTest {
    private static final HexFormat HEX = HexFormat.of();

    @ParameterizedTest
    @CsvSource({
            "www.example.com, f1e3c2e5f23a6ba0ab90f4ff",
            "no-cache, a8eb10649cbf",
            "custom-key, 25a849e95ba97d7f",
            "custom-value, 25a849e95bb8e8b4bf"
    })
    void rfc7541Examples(String value, String encodedHex) {
        byte[] expected = HEX.parseHex(encodedHex);

        assertThat(HuffmanCodec.encodedLength(value), is(expected.length));

        byte[] encoded = new byte[expected.length];
        assertThat(HuffmanCodec.encode(value, encoded), is(expected.length));
        assertArrayEquals(expected, encoded);

        byte[] decoded = new byte[expected.length * 8 / 5];
        int decodedLength = HuffmanCodec.decode(BufferData.create(expected), expected.length, decoded);
        assertArrayEquals(value.getBytes(StandardCharsets.ISO_8859_1), Arrays.copyOf(decoded, decodedLength));

        StringBuilder decodedText = new StringBuilder();
        HuffmanCodec.decode(BufferData.create(expected), expected.length, decodedText);
        assertThat(decodedText.toString(), is(value));
    }

    @Test
    void roundTripsEveryOctet() {
        StringBuilder value = new StringBuilder(256);
        for (int i = 0; i < 256; i++) {
            value.append((char) i);
        }

        byte[] encoded = new byte[HuffmanCodec.encodedLength(value)];
        int encodedLength = HuffmanCodec.encode(value, encoded);
        assertThat(encodedLength, is(encoded.length));

        byte[] decoded = new byte[encodedLength * 8 / 5];
        int decodedLength = HuffmanCodec.decode(BufferData.create(encoded), encodedLength, decoded);
        assertArrayEquals(value.toString().getBytes(StandardCharsets.ISO_8859_1),
                          Arrays.copyOf(decoded, decodedLength));

        StringBuilder decodedText = new StringBuilder();
        HuffmanCodec.decode(BufferData.create(encoded), encodedLength, decodedText);
        assertThat(decodedText.toString(), is(value.toString()));
    }

    @Test
    void acceptsEmptyInput() {
        assertThat(HuffmanCodec.encodedLength(""), is(0));
        assertThat(HuffmanCodec.encode("", new byte[0]), is(0));

        BufferData byteSource = BufferData.create(new byte[0]);
        assertThat(HuffmanCodec.decode(byteSource, 0, new byte[0]), is(0));
        assertThat(byteSource.available(), is(0));

        BufferData textSource = BufferData.create(new byte[0]);
        StringBuilder decodedText = new StringBuilder("unchanged");
        HuffmanCodec.decode(textSource, 0, decodedText);
        assertThat(decodedText.toString(), is("unchanged"));
        assertThat(textSource.available(), is(0));
    }

    @Test
    void reportsShortEncodeDestination() {
        String value = "www.example.com";
        int encodedLength = HuffmanCodec.encodedLength(value);

        byte[] exact = new byte[encodedLength];
        assertThat(HuffmanCodec.encode(value, exact), is(encodedLength));

        byte[] withSentinel = new byte[encodedLength + 1];
        withSentinel[encodedLength] = 0x5A;
        assertThat(HuffmanCodec.encode(value, withSentinel), is(encodedLength));
        assertThat(withSentinel[encodedLength], is((byte) 0x5A));

        assertThat(HuffmanCodec.encode(value, new byte[encodedLength - 1]), is(-1));
    }

    @Test
    void preflightsDecodeDestinationCapacity() {
        byte[] encoded = HEX.parseHex("f1e3c2e5f23a6ba0ab90f4ff");
        int maximumDecodedLength = encoded.length * 8 / 5;

        BufferData exactSource = BufferData.create(encoded);
        byte[] exact = new byte[maximumDecodedLength];
        int decodedLength = HuffmanCodec.decode(exactSource, encoded.length, exact);
        assertThat(decodedLength, is("www.example.com".length()));
        assertThat(exactSource.available(), is(0));

        BufferData shortSource = BufferData.create(encoded);
        int available = shortSource.available();
        assertThrows(IndexOutOfBoundsException.class,
                     () -> HuffmanCodec.decode(shortSource, encoded.length, new byte[maximumDecodedLength - 1]));
        assertThat(shortSource.available(), is(available));
    }

    @Test
    void decodesOnlyDeclaredSourceRange() {
        byte[] encoded = HEX.parseHex("a8eb10649cbf");
        byte[] withTrailingByte = Arrays.copyOf(encoded, encoded.length + 1);
        withTrailingByte[encoded.length] = 0x5A;

        BufferData byteSource = BufferData.create(withTrailingByte);
        byte[] decoded = new byte[encoded.length * 8 / 5];
        int decodedLength = HuffmanCodec.decode(byteSource, encoded.length, decoded);
        assertThat(new String(decoded, 0, decodedLength, StandardCharsets.ISO_8859_1), is("no-cache"));
        assertThat(byteSource.available(), is(1));
        assertThat(byteSource.read(), is(0x5A));

        BufferData textSource = BufferData.create(withTrailingByte);
        StringBuilder decodedText = new StringBuilder();
        HuffmanCodec.decode(textSource, encoded.length, decodedText);
        assertThat(decodedText.toString(), is("no-cache"));
        assertThat(textSource.available(), is(1));
        assertThat(textSource.read(), is(0x5A));
    }

    @Test
    void rejectsInvalidSourceLengthBeforeReading() {
        BufferData negativeByteSource = BufferData.create(new byte[] {0});
        assertThrows(IndexOutOfBoundsException.class,
                     () -> HuffmanCodec.decode(negativeByteSource, -1, new byte[1]));
        assertThat(negativeByteSource.available(), is(1));

        BufferData longByteSource = BufferData.create(new byte[] {0});
        assertThrows(IndexOutOfBoundsException.class,
                     () -> HuffmanCodec.decode(longByteSource, 2, new byte[4]));
        assertThat(longByteSource.available(), is(1));

        BufferData negativeTextSource = BufferData.create(new byte[] {0});
        assertThrows(IndexOutOfBoundsException.class,
                     () -> HuffmanCodec.decode(negativeTextSource, -1, new StringBuilder()));
        assertThat(negativeTextSource.available(), is(1));

        BufferData longTextSource = BufferData.create(new byte[] {0});
        assertThrows(IndexOutOfBoundsException.class,
                     () -> HuffmanCodec.decode(longTextSource, 2, new StringBuilder()));
        assertThat(longTextSource.available(), is(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ff", "1e", "1c", "ffffffff"})
    void rejectsMalformedInput(String encodedHex) {
        byte[] encoded = HEX.parseHex(encodedHex);

        assertThrows(IllegalArgumentException.class,
                     () -> HuffmanCodec.decode(BufferData.create(encoded),
                                               encoded.length,
                                               new byte[encoded.length * 8 / 5]));
        assertThrows(IllegalArgumentException.class,
                     () -> HuffmanCodec.decode(BufferData.create(encoded), encoded.length, new StringBuilder()));
    }

    @Test
    void wrapsAppendFailure() {
        byte[] encoded = HEX.parseHex("a8eb10649cbf");
        Appendable failing = new Appendable() {
            @Override
            public Appendable append(CharSequence value) throws IOException {
                throw new IOException("expected");
            }

            @Override
            public Appendable append(CharSequence value, int start, int end) throws IOException {
                throw new IOException("expected");
            }

            @Override
            public Appendable append(char value) throws IOException {
                throw new IOException("expected");
            }
        };

        UncheckedIOException failure = assertThrows(UncheckedIOException.class,
                                                    () -> HuffmanCodec.decode(BufferData.create(encoded),
                                                                              encoded.length,
                                                                              failing));
        assertThat(failure.getCause(), instanceOf(IOException.class));
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> HuffmanCodec.encodedLength(null));
        assertThrows(NullPointerException.class, () -> HuffmanCodec.encode(null, new byte[0]));
        assertThrows(NullPointerException.class, () -> HuffmanCodec.encode("", null));

        assertThrows(NullPointerException.class, () -> HuffmanCodec.decode(null, 0, new byte[0]));
        assertThrows(NullPointerException.class,
                     () -> HuffmanCodec.decode(BufferData.create(new byte[0]), 0, (byte[]) null));
        assertThrows(NullPointerException.class,
                     () -> HuffmanCodec.decode(null, 0, new StringBuilder()));
        assertThrows(NullPointerException.class,
                     () -> HuffmanCodec.decode(BufferData.create(new byte[0]), 0, (Appendable) null));
    }

    @Test
    void rejectsCharactersOutsideOctetRange() {
        assertThrows(IllegalArgumentException.class, () -> HuffmanCodec.encodedLength("\u0100"));
        assertThrows(IllegalArgumentException.class, () -> HuffmanCodec.encode("\u0100", new byte[4]));
    }
}
