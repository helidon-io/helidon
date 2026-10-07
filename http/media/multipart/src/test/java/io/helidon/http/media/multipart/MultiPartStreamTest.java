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

package io.helidon.http.media.multipart;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.MediaContext;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MultiPartStreamTest {
    private static final String BOUNDARY = "stream-boundary";
    private static final String FIRST_PART = "A".repeat(2048);
    private static final String SECOND_PART = "B".repeat(3072);
    private static final String ENTITY = "--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=first\r\n\r\n" + FIRST_PART + "\r\n"
            + "--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=second\r\n\r\n" + SECOND_PART + "\r\n"
            + "--" + BOUNDARY + "--\r\n";
    private static final Headers HEADERS = WritableHeaders.create()
            .set(HeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + BOUNDARY);
    private static final MediaContext CONTEXT = MediaContext.builder()
            .mediaSupportsDiscoverServices(false)
            .addMediaSupport(MultiPartSupport.create(Config.empty()))
            .build();

    @Test
    void compressedRequestRemainsReadableUntilIterationCompletes() throws IOException {
        try (InputStream stream = compressedStream()) {
            MultiPart parts = CONTEXT.reader(MultiPart.GENERIC_TYPE, HEADERS)
                    .read(MultiPart.GENERIC_TYPE, stream, HEADERS);
            assertParts(parts);
            assertThrows(IOException.class, stream::read);
        }
    }

    @Test
    void compressedResponseRemainsReadableUntilIterationCompletes() throws IOException {
        try (InputStream stream = compressedStream()) {
            Headers requestHeaders = WritableHeaders.create();
            MultiPart parts = CONTEXT.reader(MultiPart.GENERIC_TYPE, requestHeaders, HEADERS)
                    .read(MultiPart.GENERIC_TYPE, stream, requestHeaders, HEADERS);
            assertParts(parts);
            assertThrows(IOException.class, stream::read);
        }
    }

    @Test
    void emptyMultipartClosesItsInput() {
        var stream = new TrackingInputStream("--" + BOUNDARY + "--\r\n");
        MultiPart parts = new MultiPartImpl(CONTEXT, BOUNDARY, stream);
        assertThat(parts.hasNext(), is(false));
        assertThat(parts.hasNext(), is(false));
        assertThat(stream.closeCount, is(1));
    }

    @Test
    void partReadFailureClosesItsInputAndPreservesTheFailure() throws IOException {
        var stream = new TrackingInputStream(ENTITY);
        MultiPart parts = new MultiPartImpl(CONTEXT, BOUNDARY, stream);
        InputStream part = parts.next().inputStream();
        IOException failure = new IOException("read failed");
        IOException closeFailure = new IOException("close failed");
        stream.readFailure = failure;
        stream.closeFailure = closeFailure;

        UncheckedIOException thrown = assertThrows(UncheckedIOException.class,
                                                  () -> part.transferTo(OutputStream.nullOutputStream()));
        assertThat(thrown.getCause(), sameInstance(failure));
        assertThat(thrown.getSuppressed().length, is(1));
        assertThat(thrown.getSuppressed()[0], sameInstance(closeFailure));
        assertThat(stream.closeCount, is(1));
        assertThat(parts.hasNext(), is(false));
    }

    @Test
    void terminalCloseFailureIsReportedOnlyOnce() {
        var stream = new TrackingInputStream("--" + BOUNDARY + "--\r\n");
        IOException failure = new IOException("close failed");
        stream.closeFailure = failure;
        MultiPart parts = new MultiPartImpl(CONTEXT, BOUNDARY, stream);

        UncheckedIOException thrown = assertThrows(UncheckedIOException.class, parts::hasNext);
        assertThat(thrown.getCause(), sameInstance(failure));
        assertThat(parts.hasNext(), is(false));
        assertThat(stream.closeCount, is(1));
    }

    private static void assertParts(MultiPart parts) throws IOException {
        assertThat(parts.hasNext(), is(true));
        try (InputStream part = parts.next().inputStream()) {
            assertThat(new String(part.readAllBytes(), StandardCharsets.US_ASCII), is(FIRST_PART));
        }
        assertThat(parts.hasNext(), is(true));
        try (InputStream part = parts.next().inputStream()) {
            assertThat(new String(part.readAllBytes(), StandardCharsets.US_ASCII), is(SECOND_PART));
        }
        assertThat(parts.hasNext(), is(false));
        assertThat(parts.hasNext(), is(false));
    }

    private static InputStream compressedStream() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new GZIPOutputStream(bytes)) {
            output.write(ENTITY.getBytes(StandardCharsets.US_ASCII));
        }
        return new GZIPInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static final class TrackingInputStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private IOException readFailure;
        private IOException closeFailure;
        private int closeCount;

        private TrackingInputStream(String entity) {
            delegate = new ByteArrayInputStream(entity.getBytes(StandardCharsets.US_ASCII));
        }

        @Override
        public int read() throws IOException {
            if (readFailure != null) {
                throw readFailure;
            }
            return delegate.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (readFailure != null) {
                throw readFailure;
            }
            return delegate.read(bytes, offset, length);
        }

        @Override
        public void close() throws IOException {
            closeCount++;
            delegate.close();
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }
}
