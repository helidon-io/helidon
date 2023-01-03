/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.http.media;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import io.helidon.common.GenericType;
import io.helidon.common.buffers.BufferData;

/**
 * Base for readable entities.
 */
public abstract class ReadableEntityBase implements ReadableEntity {
    private static final ReadableEntity EMPTY = new EmptyReadableEntity();

    private final AtomicBoolean consumed = new AtomicBoolean();
    private final Function<InputStream, InputStream> decoder;
    private final Function<Integer, BufferData> readEntityFunction;
    private final Runnable entityProcessedRunnable;

    private final AtomicBoolean entityProcessed = new AtomicBoolean();
    private InputStream inputStream;

    /**
     * Create a new base.
     *
     * @param decoder                 content decoder when using content encoding
     * @param readEntityFunction      accepts estimate of needed bytes, returns buffer data (the length of buffer data may differ
     *                                from the estimate)
     * @param entityProcessedRunnable runnable to run when entity is fully read
     */
    protected ReadableEntityBase(Function<InputStream, InputStream> decoder,
                                 Function<Integer, BufferData> readEntityFunction,
                                 Runnable entityProcessedRunnable) {
        this.decoder = decoder;
        this.readEntityFunction = readEntityFunction;
        this.entityProcessedRunnable = new EntityProcessedRunnable(entityProcessedRunnable, entityProcessed);
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
        if (consumed.compareAndSet(false, true)) {
            this.inputStream = decoder.apply(new RequestingInputStream(readEntityFunction, entityProcessedRunnable));
            return inputStream;
        } else {
            throw new IllegalStateException("Entity has already been requested. Entity cannot be requested multiple times");
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
    public boolean consumed() {
        return entityProcessed.get();
    }

    @Override
    public void consume() {
        if (consumed()) {
            return;
        }
        InputStream theStream;
        if (inputStream == null) {
            theStream = inputStream();
        } else {
            theStream = inputStream;
        }
        try (theStream) {
            byte[] buffer = new byte[2048];
            while (theStream.read(buffer) > 0) {
                // ignore
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean hasEntity() {
        return true;
    }

    protected abstract <T> T entityAs(GenericType<T> type);

    /**
     * Read all bytes of the entity into memory.
     *
     * @return all bytes of the entity
     */
    protected byte[] readAllBytes() {
        try {
            return inputStream().readAllBytes();
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
     * Content decoder.
     *
     * @return content decoder
     */
    protected Function<InputStream, InputStream> contentDecoder() {
        return decoder;
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
            if (currentBuffer == null) {
                return -1;
            }
            return currentBuffer.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (finished) {
                return -1;
            }
            ensureBuffer(len);
            if (currentBuffer == null) {
                return -1;
            }
            return currentBuffer.read(b, off, len);
        }

        @Override
        public void close() throws IOException {
            if (!finished) {
                // TODO we need to close the connection!
            }
            super.close();
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
}
