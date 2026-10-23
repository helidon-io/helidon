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

package io.helidon.webclient.http2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

import io.helidon.http.ClientRequestHeaders;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.Status;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.MediaContext;
import io.helidon.webclient.api.ClientUri;
import io.helidon.webclient.api.HttpClientConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Http2ClientResponseImplTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void closesDecoderAfterByteArrayConversion(boolean gzip) throws IOException {
        InputStream decoder = decoder(gzip, false);
        var completed = new CompletableFuture<Void>();
        var closeCount = new AtomicInteger();
        var response = response(decoder, completed, closeCount);

        assertThat(response.entity().as(byte[].class), is("data".getBytes(StandardCharsets.UTF_8)));

        assertThat(assertThrows(IOException.class, decoder::read).getMessage(), is("Stream closed"));
        assertThat(completed.isDone(), is(true));
        assertThat(closeCount.get(), is(1));
        response.close();
        assertThat(closeCount.get(), is(1));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void closesDecoderAfterByteArrayConversionFails(boolean gzip) throws IOException {
        InputStream decoder = decoder(gzip, true);
        var completed = new CompletableFuture<Void>();
        var closeCount = new AtomicInteger();
        var response = response(decoder, completed, closeCount);

        assertThrows(UncheckedIOException.class, () -> response.entity().as(byte[].class));

        assertThat(assertThrows(IOException.class, decoder::read).getMessage(), is("Stream closed"));
        assertThat(completed.isDone(), is(true));
        assertThat(closeCount.get(), is(1));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void keepsLazyDecoderOpenUntilResponseCloses(boolean gzip) throws IOException {
        InputStream decoder = decoder(gzip, false);
        var completed = new CompletableFuture<Void>();
        var closeCount = new AtomicInteger();
        var response = response(decoder, completed, closeCount);

        InputStream entityStream = response.entity().inputStream();
        assertThat(entityStream.read(), is((int) 'd'));
        assertThat(entityStream.read(), is((int) 'a'));
        assertThat(completed.isDone(), is(false));
        assertThat(closeCount.get(), is(0));
        response.close();

        assertThat(assertThrows(IOException.class, decoder::read).getMessage(), is("Stream closed"));
        assertThat(closeCount.get(), is(1));
    }

    @Test
    void closesResponseEvenWhenDecoderCloseFailsWithoutDraining() {
        var reads = new AtomicInteger();
        var completed = new CompletableFuture<Void>();
        var closeCount = new AtomicInteger();
        var response = response(new InputStream() {
            @Override
            public int read() {
                reads.incrementAndGet();
                return 'a';
            }

            @Override
            public void close() throws IOException {
                throw new IOException("close failed");
            }
        }, completed, closeCount);

        assertThrows(RuntimeException.class, response::close);

        assertThat(reads.get(), is(0));
        assertThat(completed.isDone(), is(true));
        assertThat(closeCount.get(), is(1));
    }

    @Test
    void preservesReadFailureAndClosesResponseWhenDecoderCloseFails() {
        var readFailure = new IOException("read failed");
        var closeFailure = new IOException("close failed");
        var reads = new AtomicInteger();
        var completed = new CompletableFuture<Void>();
        var closeCount = new AtomicInteger();
        var response = response(new InputStream() {
            @Override
            public int read() throws IOException {
                reads.incrementAndGet();
                throw readFailure;
            }

            @Override
            public void close() throws IOException {
                throw closeFailure;
            }
        }, completed, closeCount);

        var failure = assertThrows(UncheckedIOException.class, () -> response.entity().as(byte[].class));

        assertThat(failure.getCause(), sameInstance(readFailure));
        assertThat(failure.getSuppressed().length, is(2));
        assertThat(failure.getSuppressed()[0].getCause(), sameInstance(closeFailure));
        assertThat(failure.getSuppressed()[1].getMessage(), is("Response is closed"));
        assertThat(reads.get(), is(1));
        assertThat(completed.isDone(), is(true));
        assertThat(closeCount.get(), is(1));
    }

    @Test
    void propagatesReadErrorWithoutAttemptingFailureCleanup() {
        var readFailure = new OutOfMemoryError("sentinel read failure");
        var decoderCloses = new AtomicInteger();
        var completed = new CompletableFuture<Void>();
        var closeCount = new AtomicInteger();
        var response = response(new InputStream() {
            @Override
            public int read() {
                throw readFailure;
            }

            @Override
            public void close() {
                decoderCloses.incrementAndGet();
            }
        }, completed, closeCount);

        var failure = assertThrows(OutOfMemoryError.class, () -> response.entity().inputStream().read());

        assertThat(failure, sameInstance(readFailure));
        assertThat(decoderCloses.get(), is(0));
        assertThat(completed.isDone(), is(false));
        assertThat(closeCount.get(), is(0));
    }

    @Test
    void propagatesDecoderCloseErrorAfterReadFailure() {
        var readFailure = new IOException("read failed");
        var closeFailure = new OutOfMemoryError("sentinel decoder close failure");
        var decoderCloses = new AtomicInteger();
        var completed = new CompletableFuture<Void>();
        var closeCount = new AtomicInteger();
        var response = response(new InputStream() {
            @Override
            public int read() throws IOException {
                throw readFailure;
            }

            @Override
            public void close() {
                decoderCloses.incrementAndGet();
                throw closeFailure;
            }
        }, completed, closeCount);

        var failure = assertThrows(OutOfMemoryError.class, () -> response.entity().inputStream().read());

        assertThat(failure, sameInstance(closeFailure));
        assertThat(failure.getSuppressed().length, is(0));
        assertThat(decoderCloses.get(), is(1));
        assertThat(completed.isDone(), is(true));
        assertThat(closeCount.get(), is(1));
    }

    private static InputStream decoder(boolean gzip, boolean truncated) throws IOException {
        var encoded = new ByteArrayOutputStream();
        try (var encoder = gzip ? new GZIPOutputStream(encoded) : new DeflaterOutputStream(encoded)) {
            encoder.write("data".getBytes(StandardCharsets.UTF_8));
        }
        byte[] bytes = encoded.toByteArray();
        var source = new ByteArrayInputStream(bytes, 0, truncated ? bytes.length - 3 : bytes.length);
        return gzip ? new GZIPInputStream(source) : new InflaterInputStream(source);
    }

    private static Http2ClientResponseImpl response(InputStream input,
                                                     CompletableFuture<Void> completed,
                                                     AtomicInteger closeCount) {
        return new Http2ClientResponseImpl(HttpClientConfig.builder().build(),
                                           "h2",
                                           Status.OK_200,
                                           ClientRequestHeaders.create(WritableHeaders.create()),
                                           ClientResponseHeaders.create(WritableHeaders.create()),
                                           CompletableFuture.completedFuture(ClientResponseTrailers.create()),
                                           input,
                                           MediaContext.create(),
                                           ClientUri.create(URI.create("http://localhost/test")),
                                           null,
                                           completed,
                                           closeCount::incrementAndGet,
                                           1024,
                                           false,
                                           null);
    }
}
