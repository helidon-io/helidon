/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The DataChunk represents a part of the HTTP body content.
 * <p>
 * The DataChunk and the content it carries stay immutable as long as method
 * {@link #release()} is not called. After that, the given instance and the associated
 * data structure instances (e.g., the {@link ByteBuffer} obtained by {@link #data()})
 * should not be used. The idea behind this class is to be able to
 * minimize data copying; ideally, in order to achieve the best performance,
 * to not copy them at all. However, the implementations may choose otherwise.
 * <p>
 * The instances of this class are expected to be accessed by a single thread. Calling
 * the methods of this class (such as {@link #data()}, {@link #release()} from different
 * threads may result in a race condition unless an external synchronization is used.
 */
@FunctionalInterface
public interface DataChunk {
    /**
     * Creates a simple {@link ByteBuffer} backed data chunk. The resulting
     * instance doesn't have any kind of a lifecycle and as such, it doesn't need
     * to be released.
     *
     * @param byteBuffer a byte buffer to create the request chunk from
     * @return a request chunk
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
     * @return a request chunk
     */
    static DataChunk create(byte[] bytes) {
        return create(false, ByteBuffer.wrap(bytes));
    }

    /**
     * Creates a reusable data chunk.
     *
     * @param flush a signal that chunk should be written and flushed from any cache if possible
     * @param data  a data chunk. Should not be reused until {@code releaseCallback} is used
     * @return a reusable data chunk with no release callback
     */
    static DataChunk create(boolean flush, ByteBuffer data) {
        return create(flush, data, Utils.EMPTY_RUNNABLE, false);
    }

    /**
     * Creates a reusable data chunk.
     *
     * @param flush a signal that chunk should be written and flushed from any cache if possible
     * @param data  a data chunk. Should not be reused until {@code releaseCallback} is used
     * @param readOnly indicates underlying buffer is not reused
     * @return a reusable data chunk with no release callback
     */
    static DataChunk create(boolean flush, ByteBuffer data, boolean readOnly) {
        return create(flush, data, Utils.EMPTY_RUNNABLE, readOnly);
    }

    /**
     * Creates a reusable data chunk.
     *
     * @param flush           a signal that chunk should be written and flushed from any cache if possible
     * @param data            a data chunk. Should not be reused until {@code releaseCallback} is used
     * @param releaseCallback a callback which is called when this chunk is completely processed and instance is free for reuse
     * @return a reusable data chunk with a release callback
     */
    static DataChunk create(boolean flush, ByteBuffer data, Runnable releaseCallback) {
        return create(flush, data, releaseCallback, false);
    }

    /**
     * Creates a reusable data chunk.
     *
     * @param flush           a signal that chunk should be written and flushed from any cache if possible
     * @param data            a data chunk. Should not be reused until {@code releaseCallback} is used
     * @param releaseCallback a callback which is called when this chunk is completely processed and instance is free for reuse
     * @param readOnly       indicates underlying buffer is not reused
     * @return a reusable data chunk with a release callback
     */
    static DataChunk create(boolean flush, ByteBuffer data, Runnable releaseCallback, boolean readOnly) {
        return new DataChunk() {
            private boolean isReleased = false;
            private CompletableFuture<DataChunk> writeFuture;

            @Override
            public ByteBuffer data() {
                return data;
            }

            @Override
            public boolean flush() {
                return flush;
            }

            @Override
            public void release() {
                releaseCallback.run();
                isReleased = true;
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
            public void writeFuture(CompletableFuture<DataChunk> writeFuture) {
                this.writeFuture = writeFuture;
            }

            @Override
            public Optional<CompletableFuture<DataChunk>> writeFuture() {
                return Optional.ofNullable(writeFuture);
            }
        };
    }

    /**
     * Returns a representation of this chunk as a ByteBuffer. Multiple calls
     * of this method always return the same ByteBuffer instance. As such, when
     * the buffer is read, the subsequent call of the {@link #data()} returns
     * a buffer that is also already read.
     * <p>
     * It is expected the returned ByteBuffer holds a reference to data that
     * will become stale upon calling method {@link #release()}. (For instance,
     * the memory segment is pooled by the underlying TCP server and is reused
     * for a subsequent request chunk.) The idea behind this class is to be able to
     * minimize data copying; ideally, in order to achieve the best performance,
     * to not copy them at all. However, the implementations may choose otherwise.
     * <p>
     * Note that the methods of this instance are expected to be called by a single
     * thread; if not, external synchronization must be used.
     *
     * @return a ByteBuffer representation of this chunk that is guarantied to stay
     * immutable as long as method {@link #release()} is not called
     */
    ByteBuffer data();

    /**
     * The tracing ID of this chunk.
     *
     * @return the tracing ID of this chunk
     */
    default long id() {
        return System.identityHashCode(this);
    }

    /**
     * Gets the content of the underlying {@link ByteBuffer} as an array of bytes.
     * If the the ByteBuffer was read, the returned array contains only the part of
     * data that wasn't read yet. On the other hand, calling this method doesn't cause
     * the underlying {@link ByteBuffer} to be read.
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
        return Utils.toByteArray(data().asReadOnlyBuffer());
    }

    /**
     * Whether this chunk is released and the associated data structures returned
     * by methods (such as {@link #data()} or {@link #bytes()}) should not be used.
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
     * methods {@link #bytes()} and {@link #data()} may become stale and should not be used
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
        byte[] bytes = new byte[data().limit()];
        data().get(bytes);
        DataChunk dup = DataChunk.create(bytes);
        dup.data().position(0);
        return dup;
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
     * flush. This method determines if this chunk is used for that purpose.
     *
     * @return Outcome of test.
     */
    default boolean isFlushChunk() {
        return flush() && data().limit() == 0;
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
