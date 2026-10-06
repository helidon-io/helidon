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

package io.helidon.webserver.http2;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

import io.helidon.common.buffers.BufferData;
import io.helidon.http.HttpException;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.http.encoding.ContentDecoder;
import io.helidon.http.media.MediaContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http2ServerRequestEntityCloseTest {
    private static final byte[] BODY = ("--boundary\r\n"
            + "Content-Disposition: form-data; name=\"value\"\r\n"
            + "\r\n"
            + "value\r\n"
            + "--boundary--\r\n").getBytes(StandardCharsets.US_ASCII);
    private static final MediaContext MEDIA_CONTEXT = MediaContext.builder()
            .mediaSupportsDiscoverServices(false)
            .build();

    @Test
    void closingDecodedStreamDoesNotFetchOrCompleteTheRawBody() throws IOException {
        for (int bytesRead : new int[] {0, 1, BODY.length}) {
            var decoderCloses = new AtomicInteger();
            var fixture = new Fixture(BODY, stream -> new FilterInputStream(stream) {
                @Override
                public void close() throws IOException {
                    decoderCloses.incrementAndGet();
                    super.close();
                }
            });

            InputStream stream = fixture.entity.inputStream();
            assertThat(stream.readNBytes(bytesRead), is(Arrays.copyOf(BODY, bytesRead)));
            int expectedRequests = bytesRead == 0 ? 0 : 1;
            stream.close();
            assertThat("Closing releases the decoder", decoderCloses.get(), is(1));
            assertThat("Closing must not request another body buffer", fixture.requests.get(), is(expectedRequests));
            assertThat("Closing must not claim transport EOF", fixture.completions.get(), is(0));
            assertThat(fixture.entity.consumed(), is(false));

            fixture.entity.consume();
            assertThat("The transport can explicitly consume to EOF", fixture.requests.get(), is(2));
            assertThat(fixture.completions.get(), is(1));
            assertThat(fixture.entity.consumed(), is(true));
            fixture.entity.consume();
            assertThat("Repeated transport consumption does not fetch again", fixture.requests.get(), is(2));
            assertThat("Completion is signalled once", fixture.completions.get(), is(1));
        }
    }

    @Test
    void closingGzipDecoderLeavesRawCompletionToTheTransport() throws IOException {
        var compressed = new ByteArrayOutputStream();
        try (var encoder = new GZIPOutputStream(compressed)) {
            encoder.write(BODY);
        }
        var fixture = new Fixture(compressed.toByteArray(), stream -> {
            try {
                return new GZIPInputStream(stream);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        InputStream decoded = fixture.entity.inputStream();
        assertThat(decoded.read(), is((int) BODY[0]));
        assertThat(fixture.requests.get(), is(1));
        decoded.close();
        assertThrows(IOException.class, decoded::read);
        assertThat("Decoder closure must not fetch another raw body buffer", fixture.requests.get(), is(1));
        assertThat("Decoder closure does not signal raw EOF", fixture.completions.get(), is(0));
        fixture.entity.consume();
        assertThat(fixture.requests.get(), is(2));
        assertThat(fixture.completions.get(), is(1));
    }

    @ParameterizedTest
    @CsvSource({"identity, true", "identity, false", "gzip, true", "gzip, false", "deflate, true", "deflate, false"})
    void consumingUntouchedBodyCompletesTransportAndReleasesDecoder(String encoding, boolean empty) throws IOException {
        var fixture = encodedFixture(encoding, empty ? new byte[0] : BODY, -1);
        String variant = encoding + (empty ? " empty body" : " nonempty body");

        fixture.entity.consume();

        assertThat(variant + " transport reached EOF", fixture.entity.consumed(), is(true));
        assertThat(variant + " completion callback", fixture.completions.get(), is(1));
        assertThat(variant + " decoder released", fixture.decoderCloses.get(), is(1));
        if (!encoding.equals("identity")) {
            assertThrows(IOException.class, fixture.decodedStream::read, variant + " native decoder is closed");
        }
        int requests = fixture.requests.get();
        fixture.entity.consume();
        assertThat(variant + " repeated consume does not fetch", fixture.requests.get(), is(requests));
        assertThat(variant + " completion remains exactly once", fixture.completions.get(), is(1));
        assertThat(variant + " repeated consume does not close decoder again", fixture.decoderCloses.get(), is(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"identity", "gzip", "deflate"})
    void consumingUntouchedBodyStillEnforcesDecodedPayloadLimit(String encoding) throws IOException {
        var fixture = encodedFixture(encoding, BODY, 8);

        HttpException failure = assertThrows(HttpException.class, fixture.entity::consume,
                                            encoding + " discard must enforce max-payload-size");
        assertThat(failure.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
        assertThat(encoding + " decoder closed after payload failure", fixture.decoderCloses.get(), is(1));
        assertThat(encoding + " raw completion never repeats", fixture.completions.get(), lessThanOrEqualTo(1));
    }

    private static Fixture encodedFixture(String encoding, byte[] decoded, long maxPayloadSize) throws IOException {
        byte[] payload = decoded;
        if (!encoding.equals("identity")) {
            var bytes = new ByteArrayOutputStream();
            try (OutputStream encoder = encoding.equals("gzip")
                    ? new GZIPOutputStream(bytes) : new DeflaterOutputStream(bytes)) {
                encoder.write(decoded);
            }
            payload = bytes.toByteArray();
        }
        ContentDecoder decoder = stream -> {
            try {
                return switch (encoding) {
                    case "identity" -> stream;
                    case "gzip" -> new GZIPInputStream(stream);
                    case "deflate" -> new InflaterInputStream(stream);
                    default -> throw new IllegalArgumentException("Unexpected encoding: " + encoding);
                };
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        return new Fixture(payload, decoder, maxPayloadSize, 1);
    }

    private static final class Fixture {
        private final AtomicInteger requests = new AtomicInteger();
        private final AtomicInteger completions = new AtomicInteger();
        private final AtomicInteger decoderCloses = new AtomicInteger();
        private final Http2ServerRequestEntity entity;

        private InputStream decodedStream;

        private Fixture(byte[] payload, ContentDecoder decoder) {
            this(payload, decoder, -1, 1024);
        }

        private Fixture(byte[] payload, ContentDecoder decoder, long maxPayloadSize, long maxBufferedEntitySize) {
            ContentDecoder trackedDecoder = stream -> {
                decodedStream = new FilterInputStream(decoder.apply(stream)) {
                    @Override
                    public void close() throws IOException {
                        decoderCloses.incrementAndGet();
                        super.close();
                    }
                };
                return decodedStream;
            };
            entity = Http2ServerRequestEntity.create(UnaryOperator.identity(),
                    trackedDecoder,
                    _ -> requests.incrementAndGet() == 1 && payload.length != 0
                            ? BufferData.create(payload) : BufferData.empty(),
                    completions::incrementAndGet,
                    ServerRequestHeaders.create(),
                    MEDIA_CONTEXT,
                    maxPayloadSize,
                    maxBufferedEntitySize);
        }
    }
}
