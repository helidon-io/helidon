/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.common.http;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of {@link DataChunk} based on {@code java.nio.ByteBuffer}.
 */
final class ByteBufferDataChunk implements DataChunk {

    private final ByteBuffer[] byteBuffers;
    private final boolean flush;
    private final boolean readOnly;
    private final Runnable releaseCallback;
    private boolean isReleased = false;
    private CompletableFuture<DataChunk> writeFuture;

    /**
     * Create a new data chunk.
     * @param flush           a signal that this chunk should be written and flushed from any cache if possible
     * @param readOnly        indicates underlying buffers are not reused
     * @param byteBuffers     the data for this chunk. Should not be reused until {@code releaseCallback} is used
     */
    ByteBufferDataChunk(boolean flush, boolean readOnly, ByteBuffer... byteBuffers) {
        this.flush = flush;
        this.readOnly = readOnly;
        this.releaseCallback = null;
        this.byteBuffers = Objects.requireNonNull(byteBuffers, "byteBuffers is null");
    }

    /**
     * Create a new data chunk.
     * @param flush           a signal that this chunk should be written and flushed from any cache if possible
     * @param readOnly        indicates underlying buffers are not reused
     * @param releaseCallback a callback which is called when this chunk is completely processed and instance is free for reuse
     * @param byteBuffers     the data for this chunk. Should not be reused until {@code releaseCallback} is used
     */
    ByteBufferDataChunk(boolean flush, boolean readOnly, Runnable releaseCallback, ByteBuffer... byteBuffers) {
        this.flush = flush;
        this.readOnly = readOnly;
        this.releaseCallback = Objects.requireNonNull(releaseCallback, "release callback is null");
        this.byteBuffers = Objects.requireNonNull(byteBuffers, "byteBuffers is null");
    }

    @Override
    public boolean flush() {
        return flush;
    }

    @Override
    public ByteBuffer[] data() {
        return byteBuffers;
    }

    @Override
    public boolean isReleased() {
        return isReleased;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public void release() {
        if (!isReleased) {
            if (releaseCallback != null) {
                releaseCallback.run();
            }
            isReleased = true;
        }
    }

    @Override
    public void writeFuture(CompletableFuture<DataChunk> writeFuture) {
        this.writeFuture = writeFuture;
    }

    @Override
    public Optional<CompletableFuture<DataChunk>> writeFuture() {
        return Optional.ofNullable(writeFuture);
    }
}
