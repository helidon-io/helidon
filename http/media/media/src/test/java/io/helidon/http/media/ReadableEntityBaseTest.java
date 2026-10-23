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
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.is;
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
            super(readEntityFunction, () -> { }, maxBufferedEntityLength);
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
