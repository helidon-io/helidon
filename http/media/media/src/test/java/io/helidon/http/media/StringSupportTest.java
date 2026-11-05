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
package io.helidon.http.media;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.common.GenericType;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StringSupportTest {
    private static final MediaSupport SUPPORT = StringSupport.create();

    @Test
    void readsCompleteFragmentedBodies() {
        for (int size : new int[] {0, 1, 255, 256, 257, 1024, 16384, 1048576}) {
            String expected = "x".repeat(size);
            var stream = new FragmentedInputStream(expected.getBytes(StandardCharsets.UTF_8), 113);

            assertThat("body size " + size, read(stream, WritableHeaders.create()), is(expected));
            assertThat("closed body size " + size, stream.closed, is(true));
        }
    }

    @Test
    void decodesUtf8AcrossReadBoundaries() {
        String expected = "a".repeat(255) + "řžýčň😀".repeat(100);
        var stream = new FragmentedInputStream(expected.getBytes(StandardCharsets.UTF_8), 1);

        assertThat(read(stream, WritableHeaders.create()), is(expected));
    }

    @Test
    void preservesMalformedUtf8Replacement() {
        byte[] bytes = {(byte) 0xe2, (byte) 0x82};

        assertThat(read(new FragmentedInputStream(bytes, 1), WritableHeaders.create()),
                   is(new String(bytes, StandardCharsets.UTF_8)));
    }

    @Test
    void honorsRequestCharset() {
        var headers = WritableHeaders.create().set(HeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-16LE");
        String expected = "řžýčň😀".repeat(100);

        assertThat(read(new FragmentedInputStream(expected.getBytes(StandardCharsets.UTF_16LE), 3), headers),
                   is(expected));
    }

    @Test
    void honorsResponseCharsetInsteadOfRequestCharset() {
        var requestHeaders = WritableHeaders.create().set(HeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        var responseHeaders = WritableHeaders.create().set(HeaderNames.CONTENT_TYPE, "text/plain; charset=ISO-8859-1");
        String expected = "café";
        var stream = new FragmentedInputStream(expected.getBytes(StandardCharsets.ISO_8859_1), 1);

        String actual = SUPPORT.reader(GenericType.STRING, requestHeaders, responseHeaders)
                .supplier().get().read(GenericType.STRING, stream, requestHeaders, responseHeaders);

        assertThat(actual, is(expected));
        assertThat(stream.closed, is(true));
    }

    @Test
    void readsDecodedBodyBeyondWireContentLength() throws IOException {
        String expected = "decoded text".repeat(10000);
        var compressed = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(compressed)) {
            gzip.write(expected.getBytes(StandardCharsets.UTF_8));
        }
        var headers = WritableHeaders.create()
                .set(HeaderNames.CONTENT_ENCODING, "gzip")
                .set(HeaderNames.CONTENT_LENGTH, Integer.toString(compressed.size()));
        var source = new FragmentedInputStream(compressed.toByteArray(), 7);

        assertThat(read(new GZIPInputStream(source), headers), is(expected));
        assertThat(source.closed, is(true));
    }

    @Test
    void propagatesReadFailuresAndClosesStream() {
        for (int failAfter : new int[] {0, 17, 1000}) {
            var failure = new IOException("read failed");
            var stream = new FragmentedInputStream(new byte[2000], 1) {
                @Override
                public int read(byte[] bytes, int offset, int length) throws IOException {
                    if (position >= failAfter) {
                        throw failure;
                    }
                    return super.read(bytes, offset, length);
                }
            };

            var actual = assertThrows(UncheckedIOException.class, () -> read(stream, WritableHeaders.create()));

            assertThat("failure after " + failAfter, actual.getCause(), sameInstance(failure));
            assertThat("closed after " + failAfter, stream.closed, is(true));
        }
    }

    @Test
    void propagatesCloseFailure() {
        var failure = new IOException("close failed");
        var stream = new ByteArrayInputStream(new byte[] {'x'}) {
            @Override
            public void close() throws IOException {
                throw failure;
            }
        };

        var actual = assertThrows(UncheckedIOException.class, () -> read(stream, WritableHeaders.create()));

        assertThat(actual.getCause(), sameInstance(failure));
    }

    private static String read(InputStream stream, Headers headers) {
        return SUPPORT.reader(GenericType.STRING, headers).supplier().get().read(GenericType.STRING, stream, headers);
    }

    private static class FragmentedInputStream extends InputStream {
        private final byte[] bytes;
        private final int fragmentSize;
        int position;
        boolean closed;

        private FragmentedInputStream(byte[] bytes, int fragmentSize) {
            this.bytes = bytes;
            this.fragmentSize = fragmentSize;
        }

        @Override
        public int read() {
            return position == bytes.length ? -1 : bytes[position++] & 0xff;
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            if (position == bytes.length) {
                return -1;
            }
            int count = Math.min(Math.min(length, fragmentSize), bytes.length - position);
            System.arraycopy(bytes, position, target, offset, count);
            position += count;
            return count;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
