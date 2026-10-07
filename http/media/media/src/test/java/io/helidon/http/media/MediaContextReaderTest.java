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
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import io.helidon.common.GenericType;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MediaContextReaderTest {
    private static final GenericType<InputStream> STREAM_TYPE = GenericType.create(InputStream.class);
    private static final GenericType<String> STRING_TYPE = GenericType.create(String.class);
    private static final Headers HEADERS = WritableHeaders.create();

    @Test
    void testLazyStreamingReaderRetainsGzipStream() throws IOException {
        byte[] content = "lazy compressed entity".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream stream = new GZIPOutputStream(compressed)) {
            stream.write(content);
        }

        for (ReadSide side : ReadSide.values()) {
            TrackingStream source = new TrackingStream(compressed.toByteArray(), null);
            try (GZIPInputStream decoder = new GZIPInputStream(source)) {
                EntityReader<InputStream> reader = side.reader(new StreamingReader<>(stream -> stream), STREAM_TYPE);
                assertThat(side + " streaming declaration", reader.streaming(), is(true));
                try (InputStream entity = side.read(reader, STREAM_TYPE, decoder)) {
                    assertThat(side + " source retained before consumption", source.closeCount, is(0));
                    assertThat(side + " decoded content", entity.readAllBytes(), is(content));
                    assertThat(side + " source retained until entity close", source.closeCount, is(0));
                }
                assertThat(side + " source closed with entity", source.closeCount, is(1));
                assertThrows(IOException.class, decoder::read);
            }
        }
    }

    @Test
    void testDefaultReaderClosesStreamAfterSuccess() {
        for (ReadSide side : ReadSide.values()) {
            TrackingStream source = new TrackingStream(new byte[] {1}, null);
            EntityReader<String> reader = side.reader(new TestReader<>(stream -> {
                try {
                    return Integer.toString(stream.read());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }), STRING_TYPE);

            assertThat(side + " default declaration", reader.streaming(), is(false));
            assertThat(side + " result", side.read(reader, STRING_TYPE, source), is("1"));
            assertThat(side + " closed after successful read", source.closeCount, is(1));
        }
    }

    @Test
    void testDefaultReaderClosesStreamAfterFailure() {
        for (ReadSide side : ReadSide.values()) {
            IllegalStateException failure = new IllegalStateException("reader failed");
            TrackingStream source = new TrackingStream(new byte[] {1}, null);
            EntityReader<String> reader = side.reader(new TestReader<>(ignored -> {
                throw failure;
            }), STRING_TYPE);

            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                       () -> side.read(reader, STRING_TYPE, source));
            assertThat(side + " original reader failure", thrown, sameInstance(failure));
            assertThat(side + " closed after failed read", source.closeCount, is(1));
        }
    }

    @Test
    void testStreamingReaderFailureClosesStreamAndSuppressesCloseFailure() {
        for (ReadSide side : ReadSide.values()) {
            IllegalStateException failure = new IllegalStateException("streaming reader failed");
            IOException closeFailure = new IOException("close failed");
            TrackingStream source = new TrackingStream(new byte[] {1}, closeFailure);
            EntityReader<String> reader = side.reader(new StreamingReader<>(ignored -> {
                throw failure;
            }), STRING_TYPE);

            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                                                       () -> side.read(reader, STRING_TYPE, source));
            assertThat(side + " original streaming failure", thrown, sameInstance(failure));
            assertThat(side + " closed after failed streaming read", source.closeCount, is(1));
            assertThat(side + " suppressed close failure count", thrown.getSuppressed().length, is(1));
            assertThat(side + " suppressed close failure", thrown.getSuppressed()[0], sameInstance(closeFailure));
        }
    }

    @Test
    void testSynchronousStreamingReaderClosesItsStream() {
        byte[] content = "incrementally copied entity".getBytes(StandardCharsets.UTF_8);
        GenericType<Long> type = GenericType.create(Long.class);
        for (ReadSide side : ReadSide.values()) {
            TrackingStream source = new TrackingStream(content, null);
            EntityReader<Long> reader = side.reader(new StreamingReader<>(stream -> {
                try (stream) {
                    return stream.transferTo(OutputStream.nullOutputStream());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }), type);

            assertThat(side + " bytes copied to sink", side.read(reader, type, source), is((long) content.length));
            assertThat(side + " source closed by synchronous reader", source.closeCount, is(1));
        }
    }

    @Test
    void testContextOverloadRemainsCompatibleWithLegacyReaders() {
        EntityReaderContext context = new UnusedNestedContext();
        for (ReadSide side : ReadSide.values()) {
            TrackingStream source = new TrackingStream(new byte[] {42}, null);
            EntityReader<String> reader = side.reader(new TestReader<String>(stream -> {
                try {
                    return Integer.toString(stream.read());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }) {
                @Override
                public String read(GenericType<String> type, InputStream stream, Headers headers) {
                    return "SERVER:" + super.read(type, stream, headers);
                }

                @Override
                public String read(GenericType<String> type, InputStream stream, Headers requestHeaders,
                                   Headers responseHeaders) {
                    return "CLIENT:" + super.read(type, stream, requestHeaders, responseHeaders);
                }
            }, STRING_TYPE);

            assertThat(side + " legacy reader result", side.read(reader, STRING_TYPE, source, context), is(side + ":42"));
            assertThat(side + " legacy stream closure", source.closeCount, is(1));
        }
    }

    @Test
    void testMediaWrapperForwardsContextToLazyStreamingReader() throws IOException {
        EntityReaderContext context = new UnusedNestedContext();
        byte[] content = "context-aware lazy entity".getBytes(StandardCharsets.US_ASCII);
        for (ReadSide side : ReadSide.values()) {
            TrackingStream source = new TrackingStream(content, null);
            EntityReader<InputStream> reader = side.reader(new ContextStreamingReader(context, side), STREAM_TYPE);

            try (InputStream entity = side.read(reader, STREAM_TYPE, source, context)) {
                assertThat(side + " source retained after context-aware read", source.closeCount, is(0));
                assertThat(side + " lazy content remains readable", entity.readAllBytes(), is(content));
            }
            assertThat(side + " lazy source closed by consumer", source.closeCount, is(1));
        }
    }

    private enum ReadSide {
        SERVER,
        CLIENT;

        <T> EntityReader<T> reader(EntityReader<T> delegate, GenericType<T> type) {
            MediaContext context = MediaContext.builder()
                    .registerDefaults(false)
                    .mediaSupportsDiscoverServices(false)
                    .addMediaSupport(new TestSupport(delegate))
                    .build();
            return switch (this) {
                case SERVER -> context.reader(type, HEADERS);
                case CLIENT -> context.reader(type, HEADERS, HEADERS);
            };
        }

        <T> T read(EntityReader<T> reader, GenericType<T> type, InputStream stream) {
            return switch (this) {
                case SERVER -> reader.read(type, stream, HEADERS);
                case CLIENT -> reader.read(type, stream, HEADERS, HEADERS);
            };
        }

        <T> T read(EntityReader<T> reader, GenericType<T> type, InputStream stream, EntityReaderContext context) {
            return switch (this) {
                case SERVER -> reader.read(type, stream, HEADERS, context);
                case CLIENT -> reader.read(type, stream, HEADERS, HEADERS, context);
            };
        }
    }

    private static class TestReader<T> implements EntityReader<T> {
        private final Function<InputStream, T> action;

        TestReader(Function<InputStream, T> action) {
            this.action = action;
        }

        @Override
        public T read(GenericType<T> type, InputStream stream, Headers headers) {
            return action.apply(stream);
        }

        @Override
        public T read(GenericType<T> type, InputStream stream, Headers requestHeaders, Headers responseHeaders) {
            return action.apply(stream);
        }
    }

    private static final class StreamingReader<T> extends TestReader<T> {
        StreamingReader(Function<InputStream, T> action) {
            super(action);
        }

        @Override
        public boolean streaming() {
            return true;
        }
    }

    private static final class ContextStreamingReader extends TestReader<InputStream> {
        private final EntityReaderContext expectedContext;
        private final ReadSide side;

        private ContextStreamingReader(EntityReaderContext expectedContext, ReadSide side) {
            super(ignored -> {
                throw new UnsupportedOperationException("Context-aware overload required");
            });
            this.expectedContext = expectedContext;
            this.side = side;
        }

        @Override
        public boolean streaming() {
            return true;
        }

        @Override
        public InputStream read(GenericType<InputStream> type, InputStream stream, Headers headers,
                                EntityReaderContext context) {
            assertThat("Server context-aware overload", side, is(ReadSide.SERVER));
            assertThat(side + " inherits the original reading context", context, sameInstance(expectedContext));
            return stream;
        }

        @Override
        public InputStream read(GenericType<InputStream> type, InputStream stream, Headers requestHeaders,
                                Headers responseHeaders, EntityReaderContext context) {
            assertThat("Client context-aware overload", side, is(ReadSide.CLIENT));
            assertThat(side + " inherits the original reading context", context, sameInstance(expectedContext));
            return stream;
        }
    }

    private static final class UnusedNestedContext implements EntityReaderContext {
        @Override
        public <T> T read(EntityReader<T> reader, GenericType<T> type, InputStream stream, Headers headers) {
            throw new UnsupportedOperationException("Legacy or lazy reader does not convert a nested entity");
        }

        @Override
        public <T> T read(EntityReader<T> reader, GenericType<T> type, InputStream stream, Headers requestHeaders,
                          Headers responseHeaders) {
            throw new UnsupportedOperationException("Legacy or lazy reader does not convert a nested entity");
        }
    }

    private static final class TestSupport implements MediaSupport {
        private final EntityReader<?> delegate;

        TestSupport(EntityReader<?> delegate) {
            this.delegate = delegate;
        }

        @Override
        public String name() {
            return "test-reader";
        }

        @Override
        public String type() {
            return "test-reader";
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> ReaderResponse<T> reader(GenericType<T> type, Headers headers) {
            return new ReaderResponse<>(SupportLevel.SUPPORTED, () -> (EntityReader<T>) delegate);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> ReaderResponse<T> reader(GenericType<T> type, Headers requestHeaders, Headers responseHeaders) {
            return new ReaderResponse<>(SupportLevel.SUPPORTED, () -> (EntityReader<T>) delegate);
        }
    }

    private static final class TrackingStream extends FilterInputStream {
        private final IOException closeFailure;
        private int closeCount;

        TrackingStream(byte[] content, IOException closeFailure) {
            super(new ByteArrayInputStream(content));
            this.closeFailure = closeFailure;
        }

        @Override
        public void close() throws IOException {
            closeCount++;
            super.close();
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }
}
