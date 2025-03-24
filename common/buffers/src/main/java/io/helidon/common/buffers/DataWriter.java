/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.common.buffers;

/**
 * Write data to the underlying transport (most likely a socket).
 * Do not combine {@link #write(io.helidon.common.buffers.BufferData)} and {@link #writeNow(io.helidon.common.buffers.BufferData)}
 * to a single underlying transport, unless you can guarantee there will not be a race between these two methods.
 */
public interface DataWriter extends AutoCloseable {
    /**
     * Write buffers, may delay writing and may write on a different thread.
     * This method also may combine multiple calls into a single write to the underlying transport.
     * @param buffers buffers to write
     */
    void write(BufferData... buffers);

    /**
     * Write buffer, may delay writing and may write on a different thread.
     * This method also may combine multiple calls into a single write to the underlying transport.
     * @param buffer buffer to write
     */
    void write(BufferData buffer);

    /**
     * Write buffers to underlying transport blocking until the buffers are written.
     *
     * @param buffers buffers to write
     */
    void writeNow(BufferData... buffers);

    /**
     * Write buffer to underlying transport blocking until the buffer is written.
     *
     * @param buffer buffer to write
     */
    void writeNow(BufferData buffer);

    /**
     * Flushes to the underlying transport any pending data that has been written using
     * either {@link #write(BufferData)} or {@link #write(BufferData...)}.
     */
    default void flush() {
    }

    /**
     * Closes this writer and frees any associated resources. Defaults to just a call
     * to {@link #flush()}.
     */
    default void close() {
        flush();
    }
}
