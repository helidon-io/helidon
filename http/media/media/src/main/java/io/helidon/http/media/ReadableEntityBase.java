/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;
import io.helidon.http.Headers;

/**
 * Base for readable entities.
 */
public abstract class ReadableEntityBase implements ReadableEntity {
    private static final int BUFFER_SIZE = 8 * 1024;
    private static final ReadableEntity EMPTY = new EmptyReadableEntity();

    private final AtomicBoolean consumed = new AtomicBoolean();
    private final Function<Integer, BufferData> readEntityFunction;
    private final Runnable entityProcessedRunnable;

    private final AtomicBoolean entityProcessed = new AtomicBoolean();
    private final AtomicBoolean entityRequested = new AtomicBoolean();
    private final Consumer<Boolean> entityRequestedCallback;
    private InputStream inputStream;
    private EntityReaderContext readerContext;

    private byte[] bufferedEntity;
    private final long maxBufferedEntitySize;

    /**
     * Create a new base.
     *
     * @param readEntityFunction      accepts estimate of needed bytes, returns buffer data (the length of buffer data may differ
     *                                from the estimate)
     * @param entityProcessedRunnable runnable to run when entity is fully read
     * @param maxBufferedEntitySize   maximum size of a buffered entity
     */
    protected ReadableEntityBase(Function<Integer, BufferData> readEntityFunction,
                                 Runnable entityProcessedRunnable,
                                 long maxBufferedEntitySize) {
        this.entityRequestedCallback = d -> {
        };
        this.readEntityFunction = readEntityFunction;
        this.entityProcessedRunnable = new EntityProcessedRunnable(entityProcessedRunnable, entityProcessed);
        this.maxBufferedEntitySize = maxBufferedEntitySize;
    }

    /**
     * Create a new base.
     *
     * @param entityRequestedCallback callback invoked when entity is requested
     * @param readEntityFunction      accepts estimate of needed bytes, returns buffer data (the length of buffer data may differ
     *                                from the estimate)
     * @param entityProcessedRunnable runnable to run when entity is fully read
     * @param maxBufferedEntitySize maximum length of a buffered entity
     */
    protected ReadableEntityBase(Consumer<Boolean> entityRequestedCallback,
                                 Function<Integer, BufferData> readEntityFunction,
                                 Runnable entityProcessedRunnable,
                                 long maxBufferedEntitySize) {
        this.entityRequestedCallback = entityRequestedCallback;
        this.readEntityFunction = readEntityFunction;
        this.entityProcessedRunnable = new EntityProcessedRunnable(entityProcessedRunnable, entityProcessed);
        this.maxBufferedEntitySize = maxBufferedEntitySize;
    }

    /**
     * Create a new empty readable entity (when there is no entity).
     *
     * @return empty readable entity
     */
    public static ReadableEntity empty() {
        return EMPTY;
    }

    @Override
    public InputStream inputStream() {
        if (bufferedEntity != null) {
            return new ByteArrayInputStream(bufferedEntity);
        }
        return createInputStream();
    }

    @Override
    public void buffer() {
        if (bufferedEntity == null) {
            try (InputStream is = inputStream()) {
                try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    long entityLength = 0;
                    while ((read = is.read(buffer)) != -1) {
                        entityLength += read;
                        if (entityLength > maxBufferedEntitySize) {
                            throw bufferedEntityLimitExceeded();
                        }
                        bos.write(buffer, 0, read);
                    }
                    bufferedEntity = bos.toByteArray();
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T as(Class<T> type) {
        if (InputStream.class.equals(type)) {
            return (T) inputStream();
        }

        if (byte[].class.equals(type)) {
            return (T) readAllBytes();
        }

        if (String.class.equals(type)) {
            return (T) as(GenericType.STRING);
        }

        return as(GenericType.create(type));
    }

    @Override
    public final <T> T as(GenericType<T> type) {
        return entityAs(type);
    }

    @Override
    public <T> Optional<T> asOptional(GenericType<T> type) {
        if (hasEntity()) {
            return Optional.of(entityAs(type));
        }
        return Optional.empty();
    }

    @Override
    public boolean consumed() {
        return entityProcessed.get();
    }

    @Override
    public void consume() {
        if (consumed()) {
            return;
        }
        if (!entityRequested.getAndSet(true)) {
            entityRequestedCallback.accept(true);
        }
        if (inputStream == null) {
            InputStream stream = inputStream();
            consumeStream(stream);
            if (inputStream == null || stream == inputStream || consumed()) {
                return;
            }
            // A decoder can finish before the transport, which must still reach EOF.
        }
        consumeStream(inputStream);
    }

    @Override
    public boolean hasEntity() {
        return true;
    }

    /**
     * Read entity as a specific generic type.
     *
     * @param type type the entity should be coerced into
     * @return entity value
     * @param <T> type of the entity
     */
    protected abstract <T> T entityAs(GenericType<T> type);

    /**
     * Read all bytes of the entity into memory.
     *
     * @return all bytes of the entity
     */
    protected byte[] readAllBytes() {
        try {
            return materializedInputStream().readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Function to request entity bytes.
     *
     * @return function that accepts an integer with a suggestion of how many bytes are requested, and returns a buffer
     */
    protected Function<Integer, BufferData> readEntityFunction() {
        return readEntityFunction;
    }

    /**
     * Runnable to run once the entity is fully consumed.
     *
     * @return runnable to run on consumed
     */
    protected Runnable entityProcessedRunnable() {
        return entityProcessedRunnable;
    }

    /**
     * Maximum entity length for materializing reads and explicit buffering.
     *
     * @return maximum number of input bytes to materialize
     */
    protected long maxBufferedEntitySize() {
        return maxBufferedEntitySize;
    }

    /**
     * Whether an entity has been buffered and can be read again. An entity
     * implementation that decodes its input stream must not decode this buffer again.
     *
     * @return whether a buffered entity is available
     */
    protected boolean bufferedEntityAvailable() {
        return bufferedEntity != null;
    }

    /**
     * Exception to throw when explicit buffering exceeds its configured size.
     *
     * @return exception for a buffered entity that exceeds the limit
     */
    protected RuntimeException bufferedEntityLimitExceeded() {
        return new IllegalStateException("Maximum buffered entity length exceeded");
    }

    /**
     * Exception to throw when a materializing reader exceeds its configured size limit.
     * Server request implementations override this to produce an HTTP error response.
     *
     * @return exception for a materialized entity that exceeds the limit
     */
    protected RuntimeException materializedEntityLimitExceeded() {
        return new IllegalStateException("Maximum materialized entity size exceeded");
    }

    /**
     * Read a server request with the selected media reader, enforcing the materialization limit.
     * If a reader wraps or suppresses a materialization-limit failure, the original limit failure is thrown.
     * Streaming readers retain responsibility for stream ownership as described by {@link EntityReader#streaming()}.
     *
     * @param reader the selected media reader
     * @param type type of the entity
     * @param headers request headers
     * @param <T> type of the entity
     * @return entity value
     */
    protected <T> T readEntity(EntityReader<T> reader, GenericType<T> type, Headers headers) {
        return readerContext().read(reader, type, inputStream(), headers);
    }

    /**
     * Read a client response with the selected media reader, enforcing the materialization limit.
     * If a reader wraps or suppresses a materialization-limit failure, the original limit failure is thrown.
     * Streaming readers retain responsibility for stream ownership as described by {@link EntityReader#streaming()}.
     *
     * @param reader the selected media reader
     * @param type type of the entity
     * @param requestHeaders request headers
     * @param responseHeaders response headers
     * @param <T> type of the entity
     * @return entity value
     */
    protected <T> T readEntity(EntityReader<T> reader,
                             GenericType<T> type,
                             Headers requestHeaders,
                             Headers responseHeaders) {
        return readerContext().read(reader, type, inputStream(), requestHeaders, responseHeaders);
    }

    private static void consumeStream(InputStream stream) {
        try (stream) {
            if (stream.read() == -1) {
                return;
            }
            byte[] buffer = new byte[2048];
            while (stream.read(buffer) > 0) {
                // ignore
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private <T> T readEntity(EntityReader<T> reader,
                            GenericType<T> type,
                            InputStream stream,
                            Headers requestHeaders,
                            Headers responseHeaders,
                            boolean clientResponse) {
        Objects.requireNonNull(reader);
        Objects.requireNonNull(type);
        Objects.requireNonNull(stream);
        Objects.requireNonNull(requestHeaders);
        Objects.requireNonNull(responseHeaders);
        MaterializedInputStream limited = reader.streaming() ? null
                : new MaterializedInputStream(this, stream, maxBufferedEntitySize);
        InputStream readerStream = limited == null ? stream : limited;
        T result;
        try {
            result = clientResponse ? reader.read(type, readerStream, requestHeaders, responseHeaders, readerContext())
                    : reader.read(type, readerStream, requestHeaders, readerContext());
        } catch (RuntimeException e) {
            if (limited != null && limited.limitExceeded != null) {
                throw limited.limitExceeded;
            }
            throw e;
        }
        if (limited != null && limited.limitExceeded != null) {
            throw limited.limitExceeded;
        }
        return result;
    }

    private EntityReaderContext readerContext() {
        if (readerContext == null) {
            readerContext = new ReaderContext();
        }
        return readerContext;
    }

    private InputStream materializedInputStream() {
        return new MaterializedInputStream(this, inputStream(), maxBufferedEntitySize);
    }

    private InputStream createInputStream() {
        if (consumed.compareAndSet(false, true)) {
            if (!entityRequested.getAndSet(true)) {
                entityRequestedCallback.accept(false);
            }

            this.inputStream = new RequestingInputStream(readEntityFunction, entityProcessedRunnable);
            return inputStream;
        } else {
            throw new IllegalStateException("Entity has already been requested. Entity cannot be requested multiple times");
        }
    }

    private static final class MaterializedInputStream extends InputStream {
        private final ReadableEntityBase entity;
        private final InputStream delegate;
        private final long maxSize;
        private long read;
        private RuntimeException limitExceeded;

        private MaterializedInputStream(ReadableEntityBase entity, InputStream delegate, long maxSize) {
            this.entity = entity;
            this.delegate = delegate;
            this.maxSize = maxSize;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = delegate.read(bytes, offset, length);
            if (count > 0) {
                count(count);
            }
            return count;
        }

        @Override
        public long skip(long length) throws IOException {
            long skipped = delegate.skip(length);
            if (skipped > 0) {
                count(skipped);
            }
            return skipped;
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private void count(long bytes) {
            if (limitExceeded != null) {
                throw limitExceeded;
            }
            if (bytes > maxSize - read) {
                limitExceeded = entity.materializedEntityLimitExceeded();
                throw limitExceeded;
            }
            read += bytes;
        }
    }

    private static class EntityProcessedRunnable implements Runnable {
        private final Runnable original;
        private final AtomicBoolean finishedReading;

        EntityProcessedRunnable(Runnable original, AtomicBoolean finishedReading) {
            this.original = original;
            this.finishedReading = finishedReading;
        }

        @Override
        public void run() {
            finishedReading.set(true);
            original.run();
        }
    }

    private static class RequestingInputStream extends InputStream {
        private final Function<Integer, BufferData> bufferFunction;
        private final Runnable entityProcessedRunnable;

        private BufferData currentBuffer;
        private boolean finished;

        private RequestingInputStream(Function<Integer, BufferData> bufferFunction, Runnable entityProcessedRunnable) {
            this.bufferFunction = bufferFunction;
            this.entityProcessedRunnable = entityProcessedRunnable;
        }

        @Override
        public int read() throws IOException {
            if (finished) {
                return -1;
            }
            ensureBuffer(512);
            if (finished || currentBuffer == null) {
                return -1;
            }
            return currentBuffer.read();
        }

        @Override
        public void close() throws IOException {
            if (!finished) {
                ensureBuffer(1);
            }
        }

        @Override
        public int read(byte[] b, int off, int len) {
            Objects.checkFromIndexSize(off, len, b.length);
            if (len == 0) {
                return 0;
            }
            if (finished) {
                return -1;
            }
            ensureBuffer(len);
            if (finished || currentBuffer == null) {
                return -1;
            }
            return currentBuffer.read(b, off, len);
        }

        private void ensureBuffer(int estimate) {
            if (currentBuffer != null && currentBuffer.consumed()) {
                currentBuffer = null;
            }
            if (currentBuffer == null) {
                currentBuffer = bufferFunction.apply(estimate);
                if (currentBuffer == null || currentBuffer == BufferData.empty()) {
                    entityProcessedRunnable.run();
                    finished = true;
                }
            }
        }
    }

    private static final class EmptyReadableEntity implements ReadableEntity {
        @Override
        public InputStream inputStream() {
            return new ByteArrayInputStream(BufferData.EMPTY_BYTES);
        }

        @Override
        public <T> T as(Class<T> type) {
            throw new IllegalStateException("No entity");
        }

        @Override
        public <T> T as(GenericType<T> type) {
            throw new IllegalStateException("No entity");
        }

        @Override
        public <T> Optional<T> asOptional(GenericType<T> type) {
            return Optional.empty();
        }

        @Override
        public boolean consumed() {
            return true;
        }

        @Override
        public ReadableEntity copy(Runnable entityProcessedRunnable) {
            entityProcessedRunnable.run();
            return this;
        }

        @Override
        public boolean hasEntity() {
            return false;
        }
    }

    private final class ReaderContext implements EntityReaderContext {
        @Override
        public <T> T read(EntityReader<T> reader, GenericType<T> type, InputStream stream, Headers headers) {
            return readEntity(reader, type, stream, headers, headers, false);
        }

        @Override
        public <T> T read(EntityReader<T> reader,
                          GenericType<T> type,
                          InputStream stream,
                          Headers requestHeaders,
                          Headers responseHeaders) {
            return readEntity(reader, type, stream, requestHeaders, responseHeaders, true);
        }
    }
}
