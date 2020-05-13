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
package io.helidon.media.multipart.common;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.http.DataChunk;

/**
 * Data chunk with data referencing slices of a parent chunk.
 */
final class BodyPartChunk implements DataChunk {

    private final Parent parent;
    private final ByteBuffer data;
    private final AtomicBoolean released;

    BodyPartChunk(Parent parent, ByteBuffer data) {
        this.parent = Objects.requireNonNull(parent, "parent cannot be null!");
        parent.refCount.incrementAndGet();
        this.data = data;
        this.released = new AtomicBoolean(false);
    }

    @Override
    public ByteBuffer data() {
        return data;
    }

    @Override
    public void release() {
        if (released.compareAndSet(false, true)) {
            if (parent.refCount.decrementAndGet() <= 0) {
                parent.delegate.release();
            }
        }
    }

    /**
     * Parent chunk holder with a reference count so that it can be released
     * when all the sub-chunks are released since they share the same underlying
     * buffer.
     */
    static final class Parent {

        private final DataChunk delegate;
        private final AtomicInteger refCount;

        Parent(DataChunk delegate) {
            this.delegate = delegate;
            refCount = new AtomicInteger(0);
        }
    }
}
