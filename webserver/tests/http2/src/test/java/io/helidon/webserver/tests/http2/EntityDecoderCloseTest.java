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

package io.helidon.webserver.tests.http2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.HttpException;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.encoding.ContentEncoding;
import io.helidon.http.encoding.deflate.DeflateEncoding;
import io.helidon.http.encoding.gzip.GzipEncoding;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.ReadableEntity;
import io.helidon.webserver.http.ServerRequestEntity;
import io.helidon.webserver.http2.Http2ServerRequestEntity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityDecoderCloseTest {
    private static final byte[] PAYLOAD = new byte[32768];

    static {
        new Random(1).nextBytes(PAYLOAD);
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("decoders")
    void byteArrayClosesDecoder(boolean http2, String encodingName, ContentEncoding encoding) throws IOException {
        var fixture = new EntityFixture(http2, encoding, -1, false);
        try {
            assertThat(fixture.entity.as(byte[].class), is(PAYLOAD));
            fixture.assertDecoderClosed();
            assertThat(fixture.processed.get(), is(1));
            fixture.entity.consume();
            assertThat(fixture.processed.get(), is(1));
        } finally {
            fixture.closeDecoder();
        }
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("decoders")
    void ioFailureClosesDecoder(boolean http2, String encodingName, ContentEncoding encoding) throws IOException {
        var fixture = new EntityFixture(http2, encoding, -1, true);
        try {
            assertThrows(UncheckedIOException.class, () -> fixture.entity.as(byte[].class));
            fixture.assertDecoderClosed();
        } finally {
            fixture.closeDecoder();
        }
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("decoders")
    void decodedLimitClosesDecoderWithoutDraining(boolean http2, String encodingName, ContentEncoding encoding)
            throws IOException {
        var fixture = new EntityFixture(http2, encoding, 16, false);
        try {
            var failure = assertThrows(HttpException.class, () -> fixture.entity.as(byte[].class));
            assertThat(failure.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
            fixture.assertDecoderClosed();
            assertThat("cleanup must not drain the compressed entity", fixture.reads.get(), lessThanOrEqualTo(3));
            assertThat(fixture.position, lessThan(fixture.encoded.length));
            assertThat("transport EOF must remain owned by the transport", fixture.processed.get(), is(0));
            assertThat(fixture.entity.consumed(), is(false));
        } finally {
            fixture.closeDecoder();
        }
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("decoders")
    void inputStreamRemainsLazy(boolean http2, String encodingName, ContentEncoding encoding) throws IOException {
        var fixture = new EntityFixture(http2, encoding, -1, false);
        try (var stream = fixture.entity.as(InputStream.class)) {
            assertThat(stream.read(), is(Byte.toUnsignedInt(PAYLOAD[0])));
            assertThat(fixture.decoder.read(), is(Byte.toUnsignedInt(PAYLOAD[1])));
            assertThat(fixture.position, lessThan(fixture.encoded.length));
            assertThat(fixture.processed.get(), is(0));
        }
        fixture.assertDecoderClosed();
    }

    private static Stream<Arguments> decoders() {
        return Stream.of(false, true)
                .flatMap(http2 -> Stream.of(GzipEncoding.create(), DeflateEncoding.create())
                        .map(encoding -> Arguments.of(http2, encoding.name(), encoding)));
    }

    private static class EntityFixture {
        private final AtomicInteger processed = new AtomicInteger();
        private final AtomicInteger reads = new AtomicInteger();
        private final byte[] encoded;
        private final ReadableEntity entity;
        private int position;
        private InputStream decoder;

        EntityFixture(boolean http2, ContentEncoding encoding, long maxPayloadSize, boolean truncate) throws IOException {
            var output = new ByteArrayOutputStream();
            try (var encoder = encoding.encoder().apply(output)) {
                encoder.write(PAYLOAD);
            }
            byte[] bytes = output.toByteArray();
            encoded = truncate ? Arrays.copyOf(bytes, bytes.length - 8) : bytes;
            ContentDecoder decoding = input -> {
                decoder = encoding.decoder().apply(input);
                return decoder;
            };
            Function<Integer, BufferData> reading = _ -> {
                reads.incrementAndGet();
                if (position == encoded.length) {
                    return BufferData.empty();
                }
                int end = Math.min(position + 64, encoded.length);
                var buffer = BufferData.create(Arrays.copyOfRange(encoded, position, end));
                position = end;
                return buffer;
            };
            entity = http2
                    ? Http2ServerRequestEntity.create(input -> input,
                                                      decoding,
                                                      reading,
                                                      processed::incrementAndGet,
                                                      ServerRequestHeaders.create(),
                                                      MediaContext.create(),
                                                      maxPayloadSize,
                                                      1)
                    : ServerRequestEntity.create(_ -> { },
                                                 input -> input,
                                                 decoding,
                                                 reading,
                                                 processed::incrementAndGet,
                                                 ServerRequestHeaders.create(),
                                                 MediaContext.create(),
                                                 maxPayloadSize,
                                                 1);
        }

        void assertDecoderClosed() {
            var failure = assertThrows(IOException.class, decoder::read);
            assertThat(failure.getMessage(), is("Stream closed"));
        }

        void closeDecoder() throws IOException {
            if (decoder != null) {
                decoder.close();
            }
        }
    }
}
