/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The DataChunk represents a part of the HTTP body content.
 * <p>
 * The DataChunk and the content it carries stay immutable as long as method
 * {@link #release()} is not called. After that, the given instance and the associated
 * data structure instances (e.g., the {@link ByteBuffer} array obtained by {@link #data()})
 * should not be used. The idea behind this class is to be able to
 * minimize data copying; ideally, in order to achieve the best performance,
 * to not copy them at all. However, the implementations may choose otherwise.
 * <p>
 * The instances of this class are expected to be accessed by a single thread. Calling
 * the methods of this class (such as {@link #data()}, {@link #release()} from different
 * threads may result in a race condition unless an external synchronization is used.
 */
@FunctionalInterface
public interface DataChunk extends Iterable<ByteBuffer> {

    /**
     * Creates a simple {@link ByteBuffer} backed data chunk. The resulting
     * instance doesn't have any kind of a lifecycle and as such, it doesn't need
     * to be released.
     *
     * @param byteBuffer a byte buffer to create the request chunk from
     * @return a data chunk
     */
    static DataChunk create(ByteBuffer byteBuffer) {
        return create(false, byteBuffer);
    }

    /**
     * Creates a simple byte array backed data chunk. The resulting
     * instance doesn't have any kind of a lifecycle and as such, it doesn't need
     * to be released.
     *
     * @param bytes a byte array to create the request chunk from
     * @return a data chunk
     */
    static DataChunk create(byte[] bytes) {
        return create(false, false, ByteBuffer.wrap(bytes));
    }

    /**
     * Creates a data chunk backed by one or more ByteBuffer. The resulting
     * instance doesn't have any kind of a lifecycle and as such, it doesn't need
     * to be released.
     *
     * @param byteBuffers the data for the chunk
     * @return a data chunk
     */
    static DataChunk create(ByteBuffer... byteBuffers) {
        return new ByteBufferDataChunk(false, false, byteBuffers);
    }

    /**
     * Creates a reusable data chunk.
     *
     * @param flush       a signal that this chunk should be written and flushed from any cache if possible
     * @param byteBuffers the data for this chunk. Should not be reused until {@code releaseCallback} is used
     * @return a reusable data chunk with no release callback
     */
    static DataChunk create(boolean flush, ByteBuffer... byteBuffers) {
        return new ByteBufferDataChunk(flush, false, byteBuffers);
    }

    /**
     * Creates a reusable data chunk.
     *
     * @param flush       a signal that this chunk should be written and flushed from any cache if possible
     * @param readOnly    indicates underlying buffers are not reused
     * @param byteBuffers the data for this chunk. Should not be reused until {@code releaseCallback} is used
     * @return a reusable data chunk with no release callback
     */
    static DataChunk create(boolean flush, boolean readOnly, ByteBuffer... byteBuffers) {
        return new ByteBufferDataChunk(flush, readOnly, byteBuffers);
    }

    /**
     * Creates a reusable byteBuffers chunk.
     *
     * @param flush           a signal that this chunk should be written and flushed from any cache if possible
     * @param releaseCallback a callback which is called when this chunk is completely processed and instance is free for reuse
     * @param byteBuffers     the data for this chunk. Should not be reused until {@code releaseCallback} is used
     * @return a reusable data chunk with a release callback
     */
    static DataChunk create(boolean flush, Runnable releaseCallback, ByteBuffer... byteBuffers) {
        return new ByteBufferDataChunk(flush, false, releaseCallback, byteBuffers);
    }

    /**
     * Creates a reusable byteBuffers chunk.
     *
     * @param flush           a signal that this chunk should be written and flushed from any cache if possible
     * @param readOnly        indicates underlying buffers are not reused
     * @param byteBuffers     the data for this chunk. Should not be reused until {@code releaseCallback} is used
     * @param releaseCallback a callback which is called when this chunk is completely processed and instance is free for reuse
     * @return a reusable data chunk with a release callback
     */
    static DataChunk create(boolean flush, boolean readOnly, Runnable releaseCallback, ByteBuffer... byteBuffers) {
        return new ByteBufferDataChunk(flush, readOnly, releaseCallback, byteBuffers);
    }

    /**
     * Returns a representation of this chunk as an array of ByteBuffer.
     * <p>
     * It is expected the returned byte buffers hold references to data that
     * will become stale upon calling method {@link #release()}. (For instance,
     * the memory segment is pooled by the underlying TCP server and is reused
     * for a subsequent request chunk.) The idea behind this class is to be able to
     * minimize data copying; ideally, in order to achieve the best performance,
     * to not copy them at all. However, the implementations may choose otherwise.
     * <p>
     * Note that the methods of this instance are expected to be called by a single
     * thread; if not, external synchronization must be used.
     *
     * @return an array of ByteBuffer representing the data of this chunk that are guarantied to stay
     * immutable as long as method {@link #release()} is not called
     */
    ByteBuffer[] data();

    /**
     * Returns a representation of this chunk as an array of T's.
     *
     * @param clazz class of return type
     * @return an array of T's
     */
    @SuppressWarnings("unchecked")
    default <T> T[] data(Class<T> clazz) {
        if (ByteBuffer.class.isAssignableFrom(clazz)) {
            return (T[]) data();
        }
        throw new UnsupportedOperationException("Unsupported operation for class " + clazz);
    }

    /**
     * Checks if this instance is backed by buffers of a certain kind.
     *
     * @param clazz a buffer class instance
     * @param <T> the buffer type
     * @return outcome of test
     */
    default <T> boolean isBackedBy(Class<T> clazz) {
        return ByteBuffer.class.isAssignableFrom(clazz);
    }

    /**
     * Returns the sum of elements between the current position and the limit of each of the underlying ByteBuffer.
     *
     * @return The number of elements remaining in all underlying buffers
     */
    default int remaining() {
        int remaining = 0;
        for (ByteBuffer byteBuffer : data()) {
            remaining += byteBuffer.remaining();
        }
        return remaining;
    }

    @Override
    default Iterator<ByteBuffer> iterator() {
        final ByteBuffer[] byteBuffers = data();
        return new Iterator<ByteBuffer>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < byteBuffers.length;
            }

            @Override
            public ByteBuffer next() {
                if (index < byteBuffers.length) {
                    return byteBuffers[index++];
                }
                throw new NoSuchElementException();
            }
        };
    }

    /**
     * The tracing ID of this chunk.
     *
     * @return the tracing ID of this chunk
     */
    default long id() {
        return System.identityHashCode(this);
    }

    /**
     * Gets the content of the underlying byte buffers as an array of bytes.
     * The returned array contains only the part of data that wasn't read yet.
     * Calling this method doesn't cause the underlying byte buffers to be read.
     * <p>
     * It is expected the returned byte array holds a reference to data that
     * will become stale upon calling method {@link #release()}. (For instance,
     * the memory segment is pooled by the underlying TCP server and is reused
     * for a subsequent request chunk.) The idea behind this class is to be able to
     * minimize data copying; ideally, in order to achieve the best performance,
     * to not copy them at all. However, the implementations may choose otherwise.
     * <p>
     * Note that the methods of this instance are expected to be called by a single
     * thread; if not, external synchronization must be used.
     *
     * @return an array of bytes that is guarantied to stay immutable as long as
     * method {@link #release()} is not called
     */
    default byte[] bytes() {
        byte[] bytes = null;
        for (ByteBuffer byteBuffer : data()) {
            if (bytes == null) {
                bytes = Utils.toByteArray(byteBuffer.asReadOnlyBuffer());
            } else {
                byte[] newBytes = new byte[bytes.length + byteBuffer.remaining()];
                System.arraycopy(bytes, 0, newBytes, 0, bytes.length);
                Utils.toByteArray(byteBuffer.asReadOnlyBuffer(), newBytes, bytes.length);
                bytes = newBytes;
            }
        }
        return bytes == null ? new byte[0] : bytes;
    }

    /**
     * Whether this chunk is released and the associated data structures returned
     * by methods (such as {@link #iterator()} or {@link #bytes()}) should not be used.
     * The implementations may choose to not implement this optimization and to never mutate
     * the underlying memory; in such case this method does no-op.
     * <p>
     * Note that the methods of this instance are expected to be called by a single
     * thread; if not, external synchronization must be used.
     *
     * @return whether this chunk has been released, defaults to false
     */
    default boolean isReleased() {
        return false;
    }

    /**
     * Releases this chunk. The underlying data as well as the data structure instances returned by
     * methods {@link #bytes()} and {@link #iterator()} may become stale and should not be used
     * anymore. The implementations may choose to not implement this optimization and to never mutate
     * the underlying memory; in such case this method does no-op.
     * <p>
     * Note that the methods of this instance are expected to be called by a single
     * thread; if not, external synchronization must be used.
     */
    default void release() {
    }

    /**
     * Returns {@code true} if all caches are requested to flush when this chunk is written.
     * This method is only meaningful when handing data over to
     * Helidon APIs (e.g. for server response and client requests).
     *
     * @return {@code true} if it is requested to flush all caches after this chunk is written, defaults to {@code false}.
     */
    default boolean flush() {
        return false;
    }

    /**
     * Makes a copy of this data chunk including its underlying {@link ByteBuffer}. This
     * may be necessary for caching in case {@link ByteBuffer#rewind()} is called to
     * reuse a byte buffer. Note that only the actual bytes used in the data chunk are
     * copied, the resulting data chunk's capacity may be less than the original.
     *
     * @return A copy of this data chunk.
     */
    default DataChunk duplicate() {
        ByteBuffer[] byteBuffers = data();
        ByteBuffer[] byteBuffersCopy = new ByteBuffer[byteBuffers.length];
        for (int i = 0; i < byteBuffers.length; i++) {
            byte[] bytes = new byte[byteBuffers[i].limit()];
            byteBuffers[i].get(bytes);
            byteBuffers[i].position(0);
            byteBuffersCopy[i] = ByteBuffer.wrap(bytes);
        }
        return DataChunk.create(byteBuffersCopy);
    }

    /**
     * Returns {@code true} if the underlying byte buffer of this chunk is read
     * only or {@code false} otherwise.
     *
     * @return Immutability outcome.
     */
    default boolean isReadOnly() {
        return false;
    }

    /**
     * An empty data chunk with a flush flag can be used to force a connection
     * flush without actually writing any bytes. This method determines if
     * this chunk is used for that purpose.
     *
     * @return Outcome of test.
     */
    default boolean isFlushChunk() {
        return flush() && remaining() == 0;
    }

    /**
     * Set a write future that will complete when data chunk has been
     * written to a connection.
     *
     * @param writeFuture Write future.
     */
    default void writeFuture(CompletableFuture<DataChunk> writeFuture) {
    }

    /**
     * Returns a write future associated with this chunk.
     *
     * @return Write future if one has ben set.
     */
    default Optional<CompletableFuture<DataChunk>> writeFuture() {
        return Optional.empty();
    }
}
