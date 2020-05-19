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
package io.helidon.media.multipart;

import java.nio.ByteBuffer;

import io.helidon.common.http.DataChunk;

/**
 * Body part data chunk.
 * If the parent is non {@code null}, it will be released upon invocation of {@link #release()}.
 */
final class BodyPartChunk implements DataChunk {

    private final DataChunk parent;
    private final ByteBuffer data;

    BodyPartChunk(ByteBuffer data, DataChunk parent) {
        this.parent = parent;
        this.data = data;
    }

    @Override
    public ByteBuffer data() {
        return data;
    }

    @Override
    public void release() {
        if (parent != null) {
            parent.release();
        }
    }
}
