/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.http.DataChunk;
import io.netty.buffer.ByteBuf;

public class ByteBufDataChunk implements DataChunk {

    private final ByteBuf[] byteBufs;
    private final boolean flush;
    private final boolean readOnly;
    private final Runnable releaseCallback;
    private boolean isReleased = false;
    private CompletableFuture<DataChunk> writeFuture;

    public ByteBufDataChunk(boolean flush, boolean readOnly, ByteBuf... byteBuffers) {
        this.flush = flush;
        this.readOnly = readOnly;
        this.releaseCallback = null;
        this.byteBufs = Objects.requireNonNull(byteBuffers, "byteBuffers is null");
    }

    public ByteBufDataChunk(boolean flush, boolean readOnly, Runnable releaseCallback, ByteBuf... byteBuffers) {
        this.flush = flush;
        this.readOnly = readOnly;
        this.releaseCallback = Objects.requireNonNull(releaseCallback, "release callback is null");
        this.byteBufs = Objects.requireNonNull(byteBuffers, "byteBuffers is null");
    }

    @Override
    public <T> boolean isBackedBy(Class<T> clazz) {
        return ByteBuf.class.isAssignableFrom(clazz);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] data(Class<T> clazz) {
        return (T[]) byteBufs;
    }

    @Override
    public boolean isReleased() {
        return isReleased;
    }

    @Override
    public boolean flush() {
        return flush;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public boolean isFlushChunk() {
        if (!flush()) {
            return false;
        }
        for (ByteBuf byteBuf : data(ByteBuf.class)) {
            if (byteBuf.capacity() > 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void release() {
        if (releaseCallback != null) {
            releaseCallback.run();
        }
        isReleased = true;
    }

    @Override
    public void writeFuture(CompletableFuture<DataChunk> writeFuture) {
        this.writeFuture = writeFuture;
    }

    @Override
    public Optional<CompletableFuture<DataChunk>> writeFuture() {
        return Optional.ofNullable(writeFuture);
    }

    // -- Unsupported methods

    @Override
    public DataChunk duplicate() {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public int remaining() {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public byte[] bytes() {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public ByteBuffer[] data() {
        throw new UnsupportedOperationException("Unsupported");
    }

    @Override
    public Iterator<ByteBuffer> iterator() {
        throw new UnsupportedOperationException("Unsupported");
    }
}
