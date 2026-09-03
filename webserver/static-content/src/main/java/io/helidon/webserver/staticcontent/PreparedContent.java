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

package io.helidon.webserver.staticcontent;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.Optional;

/**
 * Request-scoped static content selected for a response. Selection resolves representation metadata and a lazy body
 * source; the response planner decides whether the body must be opened.
 */
record PreparedContent(StaticContentMetadata metadata,
                       byte[] bytes,
                       IoSupplier<Body> bodySource,
                       ResponseRepresentation representation,
                       BodyOpenFailureFallback bodyOpenFailureFallback) {

    PreparedContent(StaticContentMetadata metadata,
                    byte[] bytes,
                    IoSupplier<Body> bodySource) {
        this(metadata,
             bytes,
             bodySource,
             ResponseRepresentation.plain(),
             null);
    }

    static Body channel(SeekableByteChannel channel) {
        return new ChannelBody(channel);
    }

    static Body stream(InputStream inputStream) {
        return new InputStreamBody(inputStream);
    }

    PreparedContent withRepresentation(ResponseRepresentation representation) {
        return new PreparedContent(metadata,
                                   bytes,
                                   bodySource,
                                   representation,
                                   bodyOpenFailureFallback);
    }

    PreparedContent withBodyOpenFailureFallback(BodyOpenFailureFallback fallback) {
        return new PreparedContent(metadata,
                                   bytes,
                                   bodySource,
                                   representation,
                                   fallback);
    }

    interface Body extends AutoCloseable {
        void writeTo(OutputStream outputStream, long offset, long length) throws IOException;

        default boolean rangeSupported() {
            return false;
        }

        @Override
        void close() throws IOException;
    }

    @FunctionalInterface
    interface BodyOpenFailureFallback {
        Optional<PreparedContent> prepare(Exception failure) throws IOException;
    }

    private record ChannelBody(SeekableByteChannel channel) implements Body {
        @Override
        public void writeTo(OutputStream outputStream, long offset, long length) throws IOException {
            channel.position(offset);
            ByteBuffer buffer = ByteBuffer.allocate(length < 0 ? 8192 : (int) Math.min(length, 8192));
            long remaining = length;
            while (remaining != 0) {
                buffer.clear();
                if (remaining > 0 && remaining < buffer.capacity()) {
                    buffer.limit((int) remaining);
                }
                int read = channel.read(buffer);
                if (read < 0) {
                    if (remaining > 0) {
                        throw new EOFException("Static content ended before the selected byte range");
                    }
                    break;
                }
                outputStream.write(buffer.array(), 0, read);
                if (remaining > 0) {
                    remaining -= read;
                }
            }
        }

        @Override
        public boolean rangeSupported() {
            return true;
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }

    private record InputStreamBody(InputStream inputStream) implements Body {
        @Override
        public void writeTo(OutputStream outputStream, long offset, long length) throws IOException {
            if (offset != 0 || length >= 0) {
                throw new IllegalArgumentException("Stream content does not support byte ranges");
            }
            inputStream.transferTo(outputStream);
        }

        @Override
        public void close() throws IOException {
            inputStream.close();
        }
    }
}
