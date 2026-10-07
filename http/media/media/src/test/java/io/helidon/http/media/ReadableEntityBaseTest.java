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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.Headers;
import io.helidon.http.WritableHeaders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadableEntityBaseTest {

    private static final byte[] BYTES = new byte[1024];
    private static final Headers HEADERS = WritableHeaders.create();

    static {
        Arrays.fill(BYTES, (byte) 'A');
    }

    @Test
    void testInputStream() throws IOException {
        ReadableEntityBase entityBase = new ReadableEntityImpl(new Readable(), 1024);
        try (InputStream is = entityBase.inputStream()) {
            assertThat(is, is(notNullValue()));
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                is.transferTo(bos);
                byte[] bytes = bos.toByteArray();
                assertThat(bytes.length, is(1024));
                for (int i = 0; i < bytes.length; i++) {
                    assertThat(bytes[i], is(BYTES[i]));
                }
            }
        }
    }

    @Test
    void testOptionalInputStream() throws IOException {
        ReadableEntityBase entityBase = new ReadableEntityImpl(new Readable(), 1024);
        try (InputStream is = entityBase.asOptional(InputStream.class).orElseThrow()) {
            assertThat(is.readAllBytes(), is(BYTES));
        }
    }

    @Test
    void testOptionalByteArray() {
        ReadableEntityBase entityBase = new ReadableEntityImpl(new Readable(), 1024);
        assertThat(entityBase.asOptional(byte[].class).orElseThrow(), is(BYTES));
    }

    @Test
    void testMaterializedByteArrayLimit() {
        assertThat(new ReadableEntityImpl(new Readable(), 1024).as(byte[].class), is(BYTES));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                       () -> new ReadableEntityImpl(new Readable(), 1023).as(byte[].class));
        assertThat(exception.getMessage(), is("Maximum materialized entity size exceeded"));
    }

    @Test
    void testInputStreamIsNotSubjectToMaterializedLimit() throws IOException {
        try (InputStream stream = new ReadableEntityImpl(new Readable(), 1).as(InputStream.class)) {
            assertThat(stream.readAllBytes(), is(BYTES));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testWrappedMaterializationFailurePreservesOriginalException(boolean clientResponse) {
        RuntimeException limitFailure = new IllegalStateException("Configured materialization limit");
        var entity = new LimitFailureEntity(limitFailure);
        var reader = new CapHandlingReader(ReadMode.WRAP, clientResponse);

        RuntimeException failure = assertThrows(RuntimeException.class,
                                                () -> read(entity, reader, clientResponse));
        assertThat(failure, sameInstance(limitFailure));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testSwallowedMaterializationFailureStillRejectsEntity(boolean clientResponse) {
        RuntimeException limitFailure = new IllegalStateException("Configured materialization limit");
        var entity = new LimitFailureEntity(limitFailure);
        var reader = new CapHandlingReader(ReadMode.SWALLOW, clientResponse);

        RuntimeException failure = assertThrows(RuntimeException.class,
                                                () -> read(entity, reader, clientResponse));
        assertThat(failure, sameInstance(limitFailure));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testUnrelatedProviderExceptionRemainsIntact(boolean clientResponse) {
        var entity = new ReadableEntityImpl(new Readable(), 1);
        var reader = new CapHandlingReader(ReadMode.FAIL, clientResponse);

        RuntimeException failure = assertThrows(RuntimeException.class,
                                                () -> read(entity, reader, clientResponse));
        assertThat(failure, sameInstance(reader.providerFailure));
        assertThat(failure.getCause(), sameInstance(reader.providerFailure.getCause()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testStreamingReaderCanConsumeBeyondLimitThroughReaderBoundary(boolean clientResponse) {
        var entity = new ReadableEntityImpl(new Readable(), 1);
        var reader = new CapHandlingReader(ReadMode.STREAM, clientResponse);

        assertThat(read(entity, reader, clientResponse), is(new String(BYTES, StandardCharsets.US_ASCII)));
    }

    @Test
    void testMultipleInputStream() throws IOException {
        ReadableEntityBase entityBase = new ReadableEntityImpl(new Readable(), 1024);
        try (InputStream is = entityBase.inputStream()) {
            assertThat(is, is(notNullValue()));
            assertThrows(IllegalStateException.class, entityBase::inputStream);
        }
    }

    @Test
    void testBuffer() throws IOException {
        ReadableEntityBase entityBase = new ReadableEntityImpl(new Readable(), 1024);
        entityBase.buffer();
        for (int i = 0; i < 3; i++) {
            try (InputStream is = entityBase.inputStream()) {
                assertThat(is, is(notNullValue()));
            }
        }
    }

    @Test
    void testBufferAfterInputStream() throws IOException {
        ReadableEntityBase entityBase = new ReadableEntityImpl(new Readable(), 1024);
        try (InputStream is = entityBase.inputStream()) {
            assertThat(is, is(notNullValue()));
        }
        assertThrows(IllegalStateException.class, entityBase::buffer);
    }

    @Test
    void testMaxBufferedEntityLength() {
        ReadableEntityBase entityBase = new ReadableEntityImpl(new Readable(), 1024 - 1);
        IllegalStateException e1 = assertThrows(IllegalStateException.class, entityBase::buffer);
        assertThat(e1.getMessage(), is("Maximum buffered entity length exceeded"));
        IllegalStateException e2 = assertThrows(IllegalStateException.class, entityBase::inputStream);
        assertThat(e2.getMessage(), is("Entity has already been requested. Entity cannot be requested multiple times"));
    }

    @Test
    void testZeroLengthReadDoesNotRequestMoreData() throws IOException {
        byte[] payload = "ping".getBytes(StandardCharsets.UTF_8);
        AtomicInteger reads = new AtomicInteger();
        ReadableEntityBase entityBase = new ReadableEntityImpl(estimate -> {
            if (reads.getAndIncrement() == 0) {
                return BufferData.create(payload);
            }
            return BufferData.empty();
        }, 1024);

        InputStream inputStream = entityBase.inputStream();
        assertThat(Arrays.equals(inputStream.readNBytes(payload.length), payload), is(true));
        assertThat(inputStream.read(new byte[0]), is(0));
        assertThat(reads.get(), is(1));
        inputStream.close();
    }

    @Test
    void testZeroLengthReadStillValidatesArguments() throws IOException {
        try (InputStream inputStream = new ReadableEntityImpl(new Readable(), 1024).inputStream()) {
            assertThrows(NullPointerException.class, () -> inputStream.read(null, 0, 0));
        }
        try (InputStream inputStream = new ReadableEntityImpl(new Readable(), 1024).inputStream()) {
            assertThrows(IndexOutOfBoundsException.class, () -> inputStream.read(new byte[1], 2, 0));
        }
    }

    @Test
    void testConsumeUntouchedEmptyEntityCompletesOnlyOnce() {
        AtomicInteger sourceRequests = new AtomicInteger();
        AtomicInteger completions = new AtomicInteger();
        var entity = new ReadableEntityImpl(_ -> {
            sourceRequests.incrementAndGet();
            return BufferData.empty();
        }, completions::incrementAndGet, 1);

        assertThat(entity.consumed(), is(false));
        entity.consume();
        assertThat(entity.consumed(), is(true));
        assertThat(completions.get(), is(1));
        assertThat(sourceRequests.get(), is(1));

        entity.consume();
        assertThat("Repeated consume does not request the empty entity again", sourceRequests.get(), is(1));
        assertThat("Repeated consume does not repeat completion", completions.get(), is(1));
    }

    @Test
    void testConsumeCompletesFullyReadEntityWithoutPriorEofProbe() throws IOException {
        byte[] payload = {0, (byte) 0xff, 17};
        AtomicInteger sourceRequests = new AtomicInteger();
        AtomicInteger completions = new AtomicInteger();
        var entity = new ReadableEntityImpl(_ -> sourceRequests.getAndIncrement() == 0
                ? BufferData.create(payload) : BufferData.empty(), completions::incrementAndGet, 1);

        try (InputStream stream = entity.inputStream()) {
            byte[] received = new byte[payload.length];
            assertThat(stream.read(received), is(payload.length));
            assertThat(received, is(payload));
            assertThat("Reading the payload alone has not observed EOF", entity.consumed(), is(false));
            assertThat(completions.get(), is(0));

            entity.consume();
            assertThat(entity.consumed(), is(true));
            assertThat(completions.get(), is(1));
            entity.consume();
            assertThat("Repeated consume does not repeat completion", completions.get(), is(1));
            assertThat("A consumed stream remains at EOF", stream.read(), is(-1));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void testConsumeDrainsUnreadBinaryPayload(int alreadyRead) throws IOException {
        byte[] payload = {42, 0, (byte) 0xff, 17, 0, (byte) 0xff};
        AtomicInteger sourceRequests = new AtomicInteger();
        AtomicInteger completions = new AtomicInteger();
        var entity = new ReadableEntityImpl(_ -> {
            int index = sourceRequests.getAndIncrement();
            return index < payload.length ? BufferData.create(new byte[] {payload[index]}) : BufferData.empty();
        }, completions::incrementAndGet, 1);

        try (InputStream stream = entity.inputStream()) {
            for (int i = 0; i < alreadyRead; i++) {
                assertThat(stream.read(), is(Byte.toUnsignedInt(payload[i])));
            }
            assertThat(entity.consumed(), is(false));
            entity.consume();

            assertThat("Consume drains all remaining bytes, including zero and 0xff", entity.consumed(), is(true));
            assertThat(completions.get(), is(1));
            assertThat(stream.read(), is(-1));
            int requestsAfterConsume = sourceRequests.get();
            entity.consume();
            assertThat("Repeated consume does not fetch more binary content",
                       sourceRequests.get(), is(requestsAfterConsume));
            assertThat("Repeated consume does not repeat completion", completions.get(), is(1));
        }
    }

    @Test
    void testConsumePreservesReadFailureAndClosesStream() {
        IOException readFailure = new IOException("entity read failed");
        IOException closeFailure = new IOException("entity close failed");
        AtomicInteger closes = new AtomicInteger();
        InputStream failingStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw readFailure;
            }

            @Override
            public void close() throws IOException {
                closes.incrementAndGet();
                throw closeFailure;
            }
        };
        var entity = new ReadableEntityImpl(_ -> BufferData.empty(), 1) {
            @Override
            public InputStream inputStream() {
                return failingStream;
            }
        };

        UncheckedIOException thrown = assertThrows(UncheckedIOException.class, entity::consume);
        assertThat(thrown.getCause(), sameInstance(readFailure));
        assertThat(readFailure.getSuppressed().length, is(1));
        assertThat(readFailure.getSuppressed()[0], sameInstance(closeFailure));
        assertThat("Consume closes its stream even when the first read fails", closes.get(), is(1));
    }

    private static String read(ReadableEntityBase entity, EntityReader<String> reader, boolean clientResponse) {
        return clientResponse
                ? entity.readEntity(reader, GenericType.STRING, HEADERS, HEADERS)
                : entity.readEntity(reader, GenericType.STRING, HEADERS);
    }

    private enum ReadMode {
        WRAP,
        SWALLOW,
        FAIL,
        STREAM
    }

    static class Readable implements Function<Integer, BufferData> {
        private boolean done;

        @Override
        public BufferData apply(Integer estimate) {
            if (!done) {
                done = true;
                return BufferData.create(BYTES);
            }
            return BufferData.empty();
        }
    }

    static class ReadableEntityImpl extends ReadableEntityBase {

        protected ReadableEntityImpl(Function<Integer, BufferData> readEntityFunction,
                                     int maxBufferedEntityLength) {
            this(readEntityFunction, () -> { }, maxBufferedEntityLength);
        }

        protected ReadableEntityImpl(Function<Integer, BufferData> readEntityFunction,
                                     Runnable entityProcessed,
                                     int maxBufferedEntityLength) {
            super(readEntityFunction, entityProcessed, maxBufferedEntityLength);
        }

        @Override
        protected <T> T entityAs(GenericType<T> type) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public ReadableEntity copy(Runnable entityProcessedRunnable) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    private static class TestReader implements EntityReader<String> {
        @Override
        public String read(GenericType<String> type, InputStream stream, Headers headers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String read(GenericType<String> type, InputStream stream, Headers requestHeaders, Headers responseHeaders) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class LimitFailureEntity extends ReadableEntityImpl {
        private final RuntimeException failure;

        private LimitFailureEntity(RuntimeException failure) {
            super(new Readable(), 1);
            this.failure = failure;
        }

        @Override
        protected RuntimeException materializedEntityLimitExceeded() {
            return failure;
        }
    }

    private static final class CapHandlingReader extends TestReader {
        private final RuntimeException providerFailure = new RuntimeException("Provider rejected malformed input",
                new IllegalStateException("Maximum materialized entity size exceeded"));
        private final ReadMode mode;
        private final boolean clientResponse;

        private CapHandlingReader(ReadMode mode, boolean clientResponse) {
            this.mode = mode;
            this.clientResponse = clientResponse;
        }

        @Override
        public boolean streaming() {
            return mode == ReadMode.STREAM;
        }

        @Override
        public String read(GenericType<String> type, InputStream stream, Headers headers) {
            assertThat("Server request reader overload", clientResponse, is(false));
            return readStream(stream);
        }

        @Override
        public String read(GenericType<String> type, InputStream stream, Headers requestHeaders, Headers responseHeaders) {
            assertThat("Client response reader overload", clientResponse, is(true));
            return readStream(stream);
        }

        private String readStream(InputStream stream) {
            try (stream) {
                if (mode == ReadMode.FAIL) {
                    throw providerFailure;
                }
                try {
                    return new String(stream.readAllBytes(), StandardCharsets.US_ASCII);
                } catch (RuntimeException e) {
                    return switch (mode) {
                        case WRAP -> throw new RuntimeException("Provider wrapped input failure",
                                                               new RuntimeException(e));
                        case SWALLOW -> "partial entity";
                        default -> throw e;
                    };
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
