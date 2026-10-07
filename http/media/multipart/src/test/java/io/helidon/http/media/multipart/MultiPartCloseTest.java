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
import java.util.NoSuchElementException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.ReadableEntity;
import io.helidon.http.media.ReadableEntityBase;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MultiPartCloseTest {
    private static final String BOUNDARY = "close-boundary";
    private static final String FIRST_PART = "A".repeat(2048);
    private static final String SECOND_PART = "B".repeat(3072);
    private static final String ENTITY = "--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=first\r\n\r\n" + FIRST_PART + "\r\n"
            + "--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=second\r\n\r\n" + SECOND_PART + "\r\n"
            + "--" + BOUNDARY + "--\r\n";
    private static final Headers HEADERS = WritableHeaders.create()
            .set(HeaderNames.CONTENT_TYPE, "multipart/form-data; boundary=" + BOUNDARY);
    private static final Headers REQUEST_HEADERS = WritableHeaders.create();
    private static final MediaContext MEDIA_CONTEXT = MediaContext.builder()
            .mediaSupportsDiscoverServices(false)
            .addMediaSupport(MultiPartSupport.create(Config.empty()))
            .build();

    @Test
    void closeBeforeIterationClosesOnceWithoutReading() {
        var source = new TrackingStream(ENTITY.getBytes(StandardCharsets.US_ASCII));
        MultiPart parts = new MultiPartImpl(MEDIA_CONTEXT, BOUNDARY, source);
        parts.close();
        parts.close();

        assertThat(source.closeCount, is(1));
        assertThat("Closing must not drain an unread entity", source.readCount, is(0));
        assertThat(parts.hasNext(), is(false));
        assertThrows(NoSuchElementException.class, parts::next);
    }

    @Test
    void closingPartStreamRetainsMultipartUntilOwnerCloses() throws IOException {
        var source = new TrackingStream(ENTITY.getBytes(StandardCharsets.US_ASCII));
        MultiPart parts = new MultiPartImpl(MEDIA_CONTEXT, BOUNDARY, source);
        try (parts) {
            try (InputStream first = parts.next().inputStream()) {
                assertThat(first.read(), is((int) 'A'));
            }
            assertThat("A part close permits continued multipart iteration", source.closeCount, is(0));
            assertThat(parts.hasNext(), is(true));
            try (InputStream second = parts.next().inputStream()) {
                assertThat(second.read(), is((int) 'B'));
            }
            assertThat("Closing the last part alone does not close its owner", source.closeCount, is(0));
        }
        assertThat(source.closeCount, is(1));
        assertThat(parts.hasNext(), is(false));
    }

    @Test
    void stoppingCompressedIterationClosesDecoderAndSource() throws IOException {
        for (boolean gzip : new boolean[] {true, false}) {
            for (boolean clientResponse : new boolean[] {false, true}) {
                String variant = (gzip ? "gzip" : "deflate") + (clientResponse ? " response" : " request");
                var bytes = new ByteArrayOutputStream();
                try (OutputStream encoder = gzip ? new GZIPOutputStream(bytes) : new DeflaterOutputStream(bytes)) {
                    encoder.write(ENTITY.getBytes(StandardCharsets.US_ASCII));
                }
                var source = new TrackingStream(bytes.toByteArray());
                try (InputStream decoder = gzip ? new GZIPInputStream(source) : new InflaterInputStream(source)) {
                    MultiPart parts = clientResponse
                            ? MEDIA_CONTEXT.reader(MultiPart.GENERIC_TYPE, REQUEST_HEADERS, HEADERS)
                                    .read(MultiPart.GENERIC_TYPE, decoder, REQUEST_HEADERS, HEADERS)
                            : MEDIA_CONTEXT.reader(MultiPart.GENERIC_TYPE, HEADERS)
                                    .read(MultiPart.GENERIC_TYPE, decoder, HEADERS);
                    try (parts) {
                        InputStream first = parts.next().inputStream();
                        assertThat(variant + " first byte", first.read(), is((int) 'A'));
                        assertThat(variant + " retained source before owner close", source.closeCount, is(0));
                        int reads = source.readCount;
                        parts.close();
                        assertThat(variant + " owner close does not drain the source", source.readCount, is(reads));
                        assertThrows(IOException.class, decoder::read, variant + " decoder closed");
                        assertThat(variant + " exhausted multipart after close", parts.hasNext(), is(false));
                    }
                    assertThat(variant + " source closed exactly once", source.closeCount, is(1));
                }
            }
        }
    }

    @Test
    void parserFailureRemainsPrimaryWhenResourceCleanupFails() {
        String malformed = "--" + BOUNDARY + "\r\nInvalid-Header\r\n\r\n";
        var source = new TrackingStream(malformed.getBytes(StandardCharsets.US_ASCII));
        IOException closeFailure = new IOException("source close failed");
        source.closeFailure = closeFailure;
        MultiPart parts = new MultiPartImpl(MEDIA_CONTEXT, BOUNDARY, source);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            try (parts) {
                parts.hasNext();
            }
        });
        assertThat(thrown.getMessage(), containsString("Invalid header"));
        assertThat(thrown.getSuppressed().length, is(1));
        assertThat(thrown.getSuppressed()[0], sameInstance(closeFailure));
        parts.close();
        assertThat("Cleanup after parsing failure is idempotent", source.closeCount, is(1));
    }

    @Test
    void applicationFailureClosesOwnerAndSuppressesCloseFailure() {
        var source = new TrackingStream(ENTITY.getBytes(StandardCharsets.US_ASCII));
        IOException closeFailure = new IOException("source close failed");
        source.closeFailure = closeFailure;
        RuntimeException applicationFailure = new RuntimeException("application stopped consuming parts");
        MultiPart parts = new MultiPartImpl(MEDIA_CONTEXT, BOUNDARY, source);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            try (parts) {
                assertThat(parts.next().inputStream().read(), is((int) 'A'));
                throw applicationFailure;
            }
        });
        assertThat(thrown, sameInstance(applicationFailure));
        assertThat(thrown.getSuppressed().length, is(1));
        assertThat(thrown.getSuppressed()[0], instanceOf(UncheckedIOException.class));
        assertThat(thrown.getSuppressed()[0].getCause(), sameInstance(closeFailure));
        parts.close();
        assertThat(source.closeCount, is(1));
        assertThat(parts.hasNext(), is(false));
    }

    @Test
    void partMaterializationFailureClosesMultipartOwner() {
        var source = new TrackingStream(ENTITY.getBytes(StandardCharsets.US_ASCII));
        IllegalStateException limitFailure = new IllegalStateException("materialized part exceeds eight bytes");
        var entity = new LimitedEntity(source, limitFailure);
        MultiPart parts = entity.as(MultiPart.class);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> {
            try (parts) {
                parts.next().as(String.class);
            }
        });
        assertThat(thrown, sameInstance(limitFailure));
        assertThat(source.closeCount, is(1));
        assertThat(parts.hasNext(), is(false));
    }

    private static final class LimitedEntity extends ReadableEntityBase {
        private final InputStream source;
        private final RuntimeException failure;

        private LimitedEntity(InputStream source, RuntimeException failure) {
            super(_ -> BufferData.empty(), () -> { }, 8);
            this.source = source;
            this.failure = failure;
        }

        @Override
        public InputStream inputStream() {
            return source;
        }

        @Override
        public ReadableEntity copy(Runnable entityProcessedRunnable) {
            throw new UnsupportedOperationException("Test fixture does not copy");
        }

        @Override
        protected <T> T entityAs(GenericType<T> type) {
            return readEntity(MEDIA_CONTEXT.reader(type, HEADERS), type, HEADERS);
        }

        @Override
        protected RuntimeException materializedEntityLimitExceeded() {
            return failure;
        }
    }

    private static final class TrackingStream extends InputStream {
        private final ByteArrayInputStream delegate;
        private IOException closeFailure;
        private int readCount;
        private int closeCount;

        private TrackingStream(byte[] bytes) {
            delegate = new ByteArrayInputStream(bytes);
        }

        @Override
        public int read() {
            readCount++;
            return delegate.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            readCount++;
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
