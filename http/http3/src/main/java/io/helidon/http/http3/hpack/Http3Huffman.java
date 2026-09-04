/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.http.http3.hpack;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.HuffmanCodec;

/**
 * HTTP/3 wrapper over the shared HPACK/QPACK Huffman codec.
 */
@Api.Internal
public final class Http3Huffman {
    private Http3Huffman() {
    }

    /**
     * Decode all remaining bytes from the supplied source.
     *
     * @param source encoded bytes
     * @param destination decoded character destination
     * @throws IllegalArgumentException if the input is malformed
     * @throws java.io.UncheckedIOException if appending fails
     */
    public static void decode(BufferData source, Appendable destination) {
        HuffmanCodec.decode(source, source.available(), destination);
    }
}
