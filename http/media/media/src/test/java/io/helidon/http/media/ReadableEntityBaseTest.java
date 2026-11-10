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
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadableEntityBaseTest {

    private static final byte[] BYTES = new byte[1024];

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
    void testExactLengthByteArrayAcrossBuffersCompletesEntity() {
        byte[] payload = "first-second-third".getBytes(StandardCharsets.UTF_8);
        var next = new AtomicInteger();
        var completed = new AtomicInteger();
        var entity = new ExactLengthReadableEntity(_ -> {
            int start = next.get();
            if (start == payload.length) {
                return BufferData.empty();
            }
            int end = Math.min(start + 3, payload.length);
            next.set(end);
            return BufferData.create(Arrays.copyOfRange(payload, start, end));
        }, completed::incrementAndGet, payload.length);

        assertThat(entity.as(byte[].class), is(payload));
        assertThat(entity.consumed(), is(true));
        assertThat(completed.get(), is(1));
    }

    @Test
    void testZeroExactLengthObservesCompletion() {
        var completed = new AtomicInteger();
        var entity = new ExactLengthReadableEntity(_ -> BufferData.empty(), completed::incrementAndGet, 0);

        assertThat(entity.as(byte[].class), sameInstance(BufferData.EMPTY_BYTES));
        assertThat(entity.consumed(), is(true));
        assertThat(completed.get(), is(1));
    }

    @Test
    void testExactLengthByteArrayClosesInputStream() {
        var stream = new ClosingInputStream(new ByteArrayInputStream(BYTES));
        var entity = new ExactLengthStreamReadableEntity(stream, BYTES.length);

        assertThat(entity.asOptional(byte[].class).orElseThrow(), is(BYTES));
        assertThat(stream.closeCount, is(1));
    }

    @Test
    void testShortExactLengthEntityPreservesCloseFailure() {
        var closeFailure = new IOException("close failed");
        var stream = new ClosingInputStream(new ByteArrayInputStream(BYTES));
        stream.closeFailure = closeFailure;
        var entity = new ExactLengthStreamReadableEntity(stream, BYTES.length + 1);

        var exception = assertThrows(UncheckedIOException.class, () -> entity.as(byte[].class));

        assertThat(exception.getCause(), instanceOf(EOFException.class));
        assertThat(exception.getCause().getMessage(), containsString("received " + BYTES.length));
        assertThat(exception.getCause().getSuppressed(), arrayContaining(closeFailure));
        assertThat(stream.closeCount, is(1));
    }

    @Test
    void testExactLengthRejectsAdditionalBytes() {
        var stream = new ClosingInputStream(new ByteArrayInputStream(BYTES));
        var entity = new ExactLengthStreamReadableEntity(stream, BYTES.length - 1);

        var exception = assertThrows(UncheckedIOException.class, () -> entity.as(byte[].class));

        assertThat(exception.getCause().getMessage(), containsString("exceeds expected length"));
        assertThat(stream.closeCount, is(1));
    }

    @Test
    void testZeroExactLengthRejectsAdditionalBytes() {
        var entity = new ExactLengthStreamReadableEntity(new ByteArrayInputStream(BYTES), 0);

        var exception = assertThrows(UncheckedIOException.class, () -> entity.as(byte[].class));

        assertThat(exception.getCause().getMessage(), containsString("exceeds expected length of 0 bytes"));
    }

    @Test
    void testExactLengthPreservesIoFailureAndClosesStream() {
        var readFailure = new IOException("read failed");
        var closeFailure = new IOException("close failed");
        var stream = new ClosingInputStream(new InputStream() {
            @Override
            public int read() throws IOException {
                throw readFailure;
            }
        });
        stream.closeFailure = closeFailure;
        var entity = new ExactLengthStreamReadableEntity(stream, 1);

        var exception = assertThrows(UncheckedIOException.class, () -> entity.as(byte[].class));

        assertThat(exception.getCause(), sameInstance(readFailure));
        assertThat(readFailure.getSuppressed(), arrayContaining(closeFailure));
        assertThat(stream.closeCount, is(1));
    }

    @Test
    void testExactLengthFailureDoesNotRequestDataDuringClose() {
        var readFailure = new IllegalStateException("read failed");
        var reads = new AtomicInteger();
        var entity = new ExactLengthReadableEntity(_ -> {
            reads.incrementAndGet();
            throw readFailure;
        }, () -> { }, 1);

        var exception = assertThrows(IllegalStateException.class, () -> entity.as(byte[].class));

        assertThat(exception, sameInstance(readFailure));
        assertThat(reads.get(), is(1));
        assertThat(entity.consumed(), is(false));
    }

    @Test
    void testUnknownAndOversizedLengthsReadActualBody() {
        for (long length : new long[] {-1, 1024 * 1024 + 1, (long) Integer.MAX_VALUE + 1, Long.MAX_VALUE}) {
            var stream = new ClosingInputStream(new ByteArrayInputStream(BYTES));
            var entity = new ExactLengthStreamReadableEntity(stream, length);

            assertThat("Declared length " + length, entity.as(byte[].class), is(BYTES));
            assertThat("Declared length " + length, stream.closeCount, is(1));
        }
    }

    @Test
    void testExactLengthCeilingStillValidatesShortBody() {
        var entity = new ExactLengthStreamReadableEntity(new ByteArrayInputStream(BYTES), 1024 * 1024);

        var exception = assertThrows(UncheckedIOException.class, () -> entity.as(byte[].class));

        assertThat(exception.getCause(), instanceOf(EOFException.class));
    }

    @Test
    void testExactLengthAllocationFollowsReceivedBytes() {
        for (int length : new int[] {8191, 8192, 8193, 16385, 1024 * 1024}) {
            byte[] payload = new byte[length];
            for (int i = 0; i < payload.length; i++) {
                payload[i] = (byte) i;
            }
            var stream = new AllocationCheckingInputStream(payload, null);
            var entity = new ExactLengthStreamReadableEntity(stream, length);

            assertThat("Declared length " + length, entity.as(byte[].class), is(payload));
            assertThat("Declared length " + length, stream.position, is(length));
            assertThat("Declared length " + length, stream.terminalReads, is(1));
            assertThat("Declared length " + length, stream.closeCount, is(1));
        }
    }

    @Test
    void testStalledExactLengthDoesNotUseLargeDestination() {
        for (int received : new int[] {0, 1, 8192, 65537, 512 * 1024 - 1}) {
            var readFailure = new IOException("body stalled after " + received + " bytes");
            var stream = new AllocationCheckingInputStream(new byte[received], readFailure);
            var entity = new ExactLengthStreamReadableEntity(stream, 1024 * 1024);

            var exception = assertThrows(UncheckedIOException.class, () -> entity.as(byte[].class));

            assertThat(exception.getCause(), sameInstance(readFailure));
            assertThat(stream.position, is(received));
            assertThat(stream.maximumDestinationLength, lessThanOrEqualTo(8192));
            assertThat(stream.closeCount, is(1));
        }
    }

    @Test
    void testLargeExactLengthRejectsShortBodyBeforeAndAfterDestinationGrowth() {
        for (int received : new int[] {1, 8192, 16384, 16385, 32768}) {
            var stream = new AllocationCheckingInputStream(new byte[received], null);
            var entity = new ExactLengthStreamReadableEntity(stream, 32769);

            var exception = assertThrows(UncheckedIOException.class, () -> entity.as(byte[].class));

            assertThat(exception.getCause(), instanceOf(EOFException.class));
            assertThat(exception.getCause().getMessage(), containsString("received " + received));
            assertThat(stream.position, is(received));
            assertThat(stream.closeCount, is(1));
        }
    }

    @Test
    void testLargeExactLengthRejectsAdditionalBytesAfterDestinationGrowth() {
        var stream = new AllocationCheckingInputStream(new byte[16386], null);
        var entity = new ExactLengthStreamReadableEntity(stream, 16385);

        var exception = assertThrows(UncheckedIOException.class, () -> entity.as(byte[].class));

        assertThat(exception.getCause().getMessage(), containsString("exceeds expected length of 16385 bytes"));
        assertThat(stream.position, is(16386));
        assertThat(stream.terminalReads, is(1));
        assertThat(stream.closeCount, is(1));
    }

    @Test
    void testByteArrayClosesInputStream() {
        var stream = new ClosingInputStream(new ByteArrayInputStream(BYTES));
        var entity = new StreamReadableEntity(stream);

        assertThat(entity.as(byte[].class), is(BYTES));
        assertThat(stream.closeCount, is(1));
    }

    @Test
    void testOptionalByteArrayClosesInputStream() {
        var stream = new ClosingInputStream(new ByteArrayInputStream(BYTES));
        var entity = new StreamReadableEntity(stream);

        assertThat(entity.asOptional(byte[].class).orElseThrow(), is(BYTES));
        assertThat(stream.closeCount, is(1));
    }

    @Test
    void testByteArrayClosesInputStreamOnIoFailure() {
        var readFailure = new IOException("read failed");
        var closeFailure = new IOException("close failed");
        var stream = new ClosingInputStream(new InputStream() {
            @Override
            public int read() throws IOException {
                throw readFailure;
            }
        });
        stream.closeFailure = closeFailure;
        var entity = new StreamReadableEntity(stream);

        var exception = assertThrows(UncheckedIOException.class, () -> entity.as(byte[].class));

        assertThat(exception.getCause(), sameInstance(readFailure));
        assertThat(readFailure.getSuppressed(), arrayContaining(closeFailure));
        assertThat(stream.closeCount, is(1));
    }

    @Test
    void testByteArrayClosesInputStreamOnRuntimeFailure() {
        var readFailure = new IllegalStateException("read failed");
        var closeFailure = new IOException("close failed");
        var stream = new ClosingInputStream(new InputStream() {
            @Override
            public int read() {
                throw readFailure;
            }
        });
        stream.closeFailure = closeFailure;
        var entity = new StreamReadableEntity(stream);

        var exception = assertThrows(IllegalStateException.class, () -> entity.as(byte[].class));

        assertThat(exception, sameInstance(readFailure));
        assertThat(exception.getSuppressed(), arrayContaining(closeFailure));
        assertThat(stream.closeCount, is(1));
    }

    @Test
    void testInputStreamRemainsOpenUntilCallerCloses() throws IOException {
        var stream = new ClosingInputStream(new ByteArrayInputStream(BYTES));
        var entity = new StreamReadableEntity(stream);

        try (InputStream inputStream = entity.as(InputStream.class)) {
            assertThat(inputStream, sameInstance(stream));
            assertThat(stream.readCount, is(0));
            assertThat(stream.closeCount, is(0));
            assertThat(inputStream.readAllBytes(), is(BYTES));
            assertThat(stream.closeCount, is(0));
        }
        assertThat(stream.closeCount, is(1));
    }

    @Test
    void testLazyStreamCloseRetainsSingleBufferRequest() throws IOException {
        var reads = new AtomicInteger();
        var entity = new ReadableEntityImpl(_ -> {
            reads.incrementAndGet();
            return BufferData.create(BYTES);
        }, 1024);

        InputStream stream = entity.inputStream();
        assertThat(reads.get(), is(0));
        stream.close();

        assertThat(reads.get(), is(1));
        assertThat(entity.consumed(), is(false));
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
                                     Runnable entityProcessedRunnable,
                                     int maxBufferedEntityLength) {
            super(readEntityFunction, entityProcessedRunnable, maxBufferedEntityLength);
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

    private static class ExactLengthReadableEntity extends ReadableEntityImpl {
        private final long exactLength;

        private ExactLengthReadableEntity(Function<Integer, BufferData> readEntityFunction,
                                          Runnable entityProcessedRunnable,
                                          long exactLength) {
            super(readEntityFunction, entityProcessedRunnable, 1024);
            this.exactLength = exactLength;
        }

        @Override
        protected byte[] readAllBytes() {
            return super.readAllBytes(exactLength);
        }
    }

    private static class ExactLengthStreamReadableEntity extends StreamReadableEntity {
        private final long exactLength;

        private ExactLengthStreamReadableEntity(InputStream stream, long exactLength) {
            super(stream);
            this.exactLength = exactLength;
        }

        @Override
        protected byte[] readAllBytes() {
            return super.readAllBytes(exactLength);
        }
    }

    private static class StreamReadableEntity extends ReadableEntityImpl {
        private final InputStream stream;

        private StreamReadableEntity(InputStream stream) {
            super(_ -> BufferData.empty(), 1024);
            this.stream = stream;
        }

        @Override
        public InputStream inputStream() {
            return stream;
        }
    }

    private static class AllocationCheckingInputStream extends InputStream {
        private final byte[] payload;
        private final IOException readFailure;

        private int position;
        private int maximumDestinationLength;
        private int terminalReads;
        private int closeCount;

        private AllocationCheckingInputStream(byte[] payload, IOException readFailure) {
            this.payload = payload;
            this.readFailure = readFailure;
        }

        @Override
        public int read() throws IOException {
            terminalReads++;
            if (position == payload.length) {
                if (readFailure != null) {
                    throw readFailure;
                }
                return -1;
            }
            return payload[position++] & 0xff;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            assertThat("Destination capacity after receiving " + position + " bytes",
                       bytes.length, lessThanOrEqualTo(Math.max(8192, 2 * position)));
            maximumDestinationLength = Math.max(maximumDestinationLength, bytes.length);
            if (position == payload.length) {
                if (readFailure != null) {
                    throw readFailure;
                }
                return -1;
            }
            int count = Math.min(Math.min(length, 97), payload.length - position);
            System.arraycopy(payload, position, bytes, offset, count);
            position += count;
            return count;
        }

        @Override
        public void close() {
            closeCount++;
        }
    }

    private static class ClosingInputStream extends InputStream {
        private final InputStream delegate;
        private int readCount;
        private int closeCount;
        private IOException closeFailure;

        private ClosingInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            readCount++;
            return delegate.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
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
