/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * The RequestChunk represents a part of the HTTP request body content.
 * <p>
 * The ReqeustChunk and the content it carries stay immutable as long as method
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
public interface RequestChunk {

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
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            Utils.write(data().asReadOnlyBuffer(), stream);
            return stream.toByteArray();
        } catch (IOException e) {
            // never happens with ByteArrayOutputStream
            throw new AssertionError("ByteArrayOutputStream is not expected to throw an IO Exception.", e);
        }
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
     * Whether this chunk is released and the associated data structures returned
     * by methods (such as {@link #data()} or {@link #bytes()}) should not be used.
     * The implementations may choose to not implement this optimization and to never mutate
     * the underlying memory; in such case this method does no-op.
     * <p>
     * Note that the methods of this instance are expected to be called by a single
     * thread; if not, external synchronization must be used.
     *
     * @return whether this chunk has been released
     */
    boolean isReleased();

    /**
     * Releases this chunk. The underlying data as well as the data structure instances returned by
     * methods {@link #bytes()} and {@link #data()} may become stale and should not be used
     * anymore. The implementations may choose to not implement this optimization and to never mutate
     * the underlying memory; in such case this method does no-op.
     * <p>
     * Note that the methods of this instance are expected to be called by a single
     * thread; if not, external synchronization must be used.
     */
    void release();

    /**
     * The tracing ID of this chunk.
     *
     * @return the tracing ID of this chunk
     */
    default long id() {
        return System.identityHashCode(this);
    }

    /**
     * Creates a simple {@link ByteBuffer} backed request chunk. The resulting
     * instance doesn't have any kind of a lifecycle and as such, it doesn't need
     * to be released.
     *
     * @param byteBuffer a byte buffer to create the request chunk from
     * @return a request chunk
     */
    static RequestChunk from(ByteBuffer byteBuffer) {
        return new ByteBufferRequestChunk(byteBuffer);
    }

    /**
     * Creates a simple byte array backed request chunk. The resulting
     * instance doesn't have any kind of a lifecycle and as such, it doesn't need
     * to be released.
     *
     * @param bytes a byte array to create the request chunk from
     * @return a request chunk
     */
    static RequestChunk from(byte[] bytes) {
        return new ByteBufferRequestChunk(ByteBuffer.wrap(bytes));
    }
}
