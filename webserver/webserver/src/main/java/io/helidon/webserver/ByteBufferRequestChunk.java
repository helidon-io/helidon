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
 * The ByteBufferRequestChunk is a simple {@link ByteBuffer} based implementation
 * of {@link RequestChunk} without any lifecycle. As such the {@link #release()}
 * and {@link #isReleased()} are no-op.
 */
class ByteBufferRequestChunk implements RequestChunk {

    private final ByteBuffer byteBuffer;

    ByteBufferRequestChunk(ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    @Override
    public ByteBuffer data() {
        return byteBuffer;
    }

    @Override
    public void release() {
        // noop
    }

    @Override
    public boolean isReleased() {
        return false;
    }
}
