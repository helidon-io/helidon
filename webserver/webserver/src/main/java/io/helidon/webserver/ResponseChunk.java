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

import java.nio.ByteBuffer;

/**
 * Represents a single chunk of response data.
 * <p>
 * Data are represented as a readable {@link ByteBuffer}. The {@code ByteBuffer} can be reused when {@link #release()} method
 * is called.
 * <p>
 * Each chunk can request to be {@link #flush() flushed} or not.
 */
// todo we need to provide pool and static methods for various simple use
// todo we should consider to modify it to the interface
public final class ResponseChunk {

    private final boolean flush;
    private final Runnable releaseCallback;
    private final ByteBuffer data;

    /**
     * Creates new instance.
     *
     * @param flush           a signal that chunk should be written and flushed from any cache if possible
     * @param data            a data chunk. Should not be reused until {@code releaseCallback} is used
     * @param releaseCallback a callback which is called when this chunk is completely processed and instance is free for reuse
     */
    public ResponseChunk(boolean flush, ByteBuffer data, Runnable releaseCallback) {
        this.flush = flush;
        this.releaseCallback = releaseCallback;
        this.data = data != null ? data : ByteBuffer.allocate(0);
    }

    /**
     * Creates new instance.
     *
     * @param flush           a signal that chunk should be written and flushed from any cache if possible
     * @param data            a data chunk. Should not be reused until {@code releaseCallback} is used
     */
    public ResponseChunk(boolean flush, ByteBuffer data) {
        this(flush, data, null);
    }

    /**
     * Call when data were consumed and data buffer can be reused.
     */
    public void release() {
        if (releaseCallback != null) {
            releaseCallback.run();
        }
    }

    /**
     * Returns a data.
     *
     * @return a data.
     */
    public ByteBuffer data() {
        return this.data;
    }

    /**
     * Returns {@code true} if all caches are requested to flush when this chunk is written.
     *
     * @return {@code true} if it is requested to flush all caches after this chunk
     */
    public boolean flush() {
        return flush;
    }
}
