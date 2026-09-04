/*
 * Copyright (c) 2015, 2026 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.function.IntSupplier;

import io.helidon.common.Api;
import io.helidon.common.buffers.BufferData;

/**
 * Minimal ISO/IEC 8859-1 encoder and decoder used by HPACK and QPACK code paths.
 *
 * <p>The implementation avoids {@code CharsetEncoder} and {@code CharsetDecoder} allocations and operates directly
 * on {@link ByteBuffer} and {@link BufferData} instances.</p>
 */
@Api.Internal
public final class Iso88591 {

    private Iso88591() {
    }

    /**
     * Incremental ISO-8859-1 decoder.
     */
    public static final class Reader {

        private final Hpack.BufferUpdateConsumer updater =
                (buf, bufLen) -> {
                    buffer = buf;
                    bufferLen = bufLen;
                };

        private long buffer;
        private int bufferLen;

        /**
         * Create a new decoder.
         *
         * @return new decoder
         */
        public static Reader create() {
            return new Reader();
        }

        private Reader() {
        }

        /**
         * Decode bytes from the supplied source into the destination.
         *
         * @param source encoded bytes
         * @param destination decoded character sink
         * @throws UncheckedIOException if writing to the destination fails
         */
        public void read(ByteBuffer source, Appendable destination) {
            readInternal(() -> Hpack.read(source, buffer, bufferLen, updater),
                         destination);
        }

        /**
         * Decode bytes from the supplied buffer data into the destination.
         *
         * @param source encoded bytes
         * @param destination decoded character sink
         * @throws UncheckedIOException if writing to the destination fails
         */
        public void read(BufferData source, Appendable destination) {
            readInternal(() -> Hpack.read(source, buffer, bufferLen, updater),
                         destination);
        }

        private void readInternal(IntSupplier reader, Appendable destination) {
            while (true) {
                int nBytes = reader.getAsInt();
                if (nBytes == 0) {
                    return;
                }
                while (bufferLen > 0) {
                    char c = (char) (buffer >>> 56);
                    try {
                        destination.append(c);
                    } catch (IOException e) {
                        throw new UncheckedIOException("Error appending to the destination", e);
                    }
                    buffer <<= 8;
                    bufferLen -= 8;
                }
            }
        }

        /**
         * Reset decoder state so the instance can be reused for another value.
         *
         * @return this reader
         */
        public Reader reset() {
            buffer = 0;
            bufferLen = 0;
            return this;
        }
    }

    /**
     * Incremental ISO-8859-1 encoder.
     */
    public static final class Writer {

        private final Hpack.BufferUpdateConsumer updater =
                (buf, bufLen) -> {
                    buffer = buf;
                    bufferLen = bufLen;
                };

        private CharSequence source;
        private int pos;
        private int end;
        private long buffer;
        private int bufferLen;

        /**
         * Create a new encoder.
         *
         * @return new encoder
         */
        public static Writer create() {
            return new Writer();
        }

        private Writer() {
        }

        /**
         * Configure the source character range to encode.
         *
         * @param source source characters
         * @param start start index, inclusive
         * @param end end index, exclusive
         * @return this writer
         */
        public Writer configure(CharSequence source, int start, int end) {
            this.source = source;
            this.pos = start;
            this.end = end;
            return this;
        }

        /**
         * Encode the configured characters into the destination buffer.
         *
         * @param destination buffer that receives the encoded bytes
         * @return {@code true} when the complete input was written, {@code false} when more destination space is needed
         */
        public boolean write(ByteBuffer destination) {
            while (true) {
                while (true) { // stuff codes into long
                    if (pos >= end) {
                        break;
                    }
                    char c = source.charAt(pos);
                    if (c > 255) {
                        throw new IllegalArgumentException(Integer.toString((int) c));
                    }
                    if (bufferLen <= 56) {
                        buffer |= (((long) c) << (56 - bufferLen)); // append
                        bufferLen += 8;
                        pos++;
                    } else {
                        break;
                    }
                }
                if (bufferLen == 0) {
                    return true;
                }
                int nBytes = Hpack.write(buffer, bufferLen, updater, destination);
                if (nBytes == 0) {
                    return false;
                }
            }
        }

        /**
         * Reset encoder state so the instance can be reused for another value.
         *
         * @return this writer
         */
        public Writer reset() {
            source = null;
            pos = -1;
            end = -1;
            buffer = 0;
            bufferLen = 0;
            return this;
        }
    }
}
