/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.media.common;

import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.Flow;
import io.helidon.common.reactive.ReactiveStreamsAdapter;
import io.helidon.common.reactive.RetrySchema;

import reactor.core.publisher.Mono;

/**
 * A utility class for various handy response content writers.
 * <p>
 * Some of these writers are by default registered on the response.
 */
public final class ContentWriters {

    private static final ByteArrayWriter COPY_BYTE_ARRAY_WRITER = new ByteArrayWriter(true);
    private static final ByteArrayWriter BYTE_ARRAY_WRITER = new ByteArrayWriter(false);

    private static final Map<Charset, CharSequenceWriter> CHAR_SEQUENCE_WRITERS = new HashMap<>();
    private static final Map<Charset, CharBufferWriter> CHAR_BUFFER_WRITERS = new HashMap<>();

    static {
        addWriters(StandardCharsets.UTF_8);
        addWriters(StandardCharsets.UTF_16);
        addWriters(StandardCharsets.ISO_8859_1);
        addWriters(StandardCharsets.US_ASCII);

        // try to register another common charset readers
        addWriters("cp1252");
        addWriters("cp1250");
        addWriters("ISO-8859-2");
    }

    private static void addWriters(final Charset charset) {
        CHAR_SEQUENCE_WRITERS.put(charset, new CharSequenceWriter(charset));
        CHAR_BUFFER_WRITERS.put(charset, new CharBufferWriter(charset));
    }

    private static void addWriters(final String charset) {
        try {
            addWriters(Charset.forName(charset));
        } catch (Exception ignored) {
            // ignored
        }
    }

    /**
     * A utility class constructor.
     */
    private ContentWriters() {
    }

    /**
     * Returns a writer function for {@code byte[]}.
     * <p>
     * The {@code copy} variant is by default registered in {@code ServerResponse}.
     *
     * @param copy a signal if byte array should be copied - set it {@code true} if {@code byte[]} will be immediately reused.
     * @return a {@code byte[]} writer
     */
    public static Function<byte[], Flow.Publisher<DataChunk>> byteArrayWriter(boolean copy) {
        return copy ? COPY_BYTE_ARRAY_WRITER : BYTE_ARRAY_WRITER;
    }

    /**
     * Returns a writer function for {@link CharSequence} using provided standard {@code charset}.
     * <p>
     * An instance is by default registered in {@code ServerResponse} for all standard charsets.
     *
     * @param charset a standard charset to use
     * @return a {@link String} writer
     * @throws NullPointerException if parameter {@code charset} is {@code null}
     */
    public static Function<CharSequence, Flow.Publisher<DataChunk>> charSequenceWriter(Charset charset) {
        return CHAR_SEQUENCE_WRITERS.computeIfAbsent(charset, key -> new CharSequenceWriter(charset));
    }

    /**
     * Returns a writer function for {@link CharBuffer} using provided standard {@code charset}.
     * <p>
     * An instance is by default registered in {@code ServerResponse} for all standard charsets.
     *
     * @param charset a standard charset to use
     * @return a {@link String} writer
     * @throws NullPointerException if parameter {@code charset} is {@code null}
     */
    public static Function<CharBuffer, Flow.Publisher<DataChunk>> charBufferWriter(Charset charset) {
        return CHAR_BUFFER_WRITERS.computeIfAbsent(charset, key -> new CharBufferWriter(charset));
    }

    /**
     * Returns a writer function for {@link ReadableByteChannel}. Created publisher use provided {@link RetrySchema} to define
     * delay between unsuccessful read attempts.
     *
     * @param retrySchema a retry schema to use in case when {@code read} operation reads {@code 0 bytes}
     * @return a {@link ReadableByteChannel} writer
     */
    public static Function<ReadableByteChannel, Flow.Publisher<DataChunk>> byteChannelWriter(RetrySchema retrySchema) {
        final RetrySchema schema = retrySchema == null ? RetrySchema.linear(0, 10, 250) : retrySchema;
        return channel -> new ReadableByteChannelPublisher(channel, schema);
    }

    /**
     * Returns a writer function for {@link ReadableByteChannel}.
     *
     * @return a {@link ReadableByteChannel} writer
     */
    public static Function<ReadableByteChannel, Flow.Publisher<DataChunk>> byteChannelWriter() {
        return byteChannelWriter(null);
    }

    private static class ByteArrayWriter implements Function<byte[], Flow.Publisher<DataChunk>> {

        private final boolean copy;

        ByteArrayWriter(boolean copy) {
            this.copy = copy;
        }

        @Override
        public Flow.Publisher<DataChunk> apply(byte[] bytes) {
            if ((bytes == null) || (bytes.length == 0)) {
                return ReactiveStreamsAdapter.publisherToFlow(Mono.empty());
            }

            byte[] bs;
            if (copy) {
                bs = new byte[bytes.length];
                System.arraycopy(bytes, 0, bs, 0, bytes.length);
            } else {
                bs = bytes;
            }
            DataChunk chunk = DataChunk.create(false, ByteBuffer.wrap(bs));
            return ReactiveStreamsAdapter.publisherToFlow(Mono.just(chunk));
        }
    }

    private static class CharSequenceWriter implements Function<CharSequence, Flow.Publisher<DataChunk>> {

        private final Charset charset;

        /**
         * Creates new instance.
         *
         * @param charset a charset to use
         * @throws NullPointerException if parameter {@code charset} is {@code null}
         */
        CharSequenceWriter(Charset charset) {
            Objects.requireNonNull(charset, "Parameter 'charset' is null!");
            this.charset = charset;
        }

        /**
         * Creates new instance.
         *
         * @param charset a name of the charset to use
         * @throws IllegalCharsetNameException if the given charset name is illegal
         * @throws IllegalArgumentException    if the given {@code charsetName} is null
         * @throws UnsupportedCharsetException if no support for the named charset is available in this instance of the JVM
         */
        CharSequenceWriter(String charset) {
            this(Charset.forName(charset));
        }

        @Override
        public Flow.Publisher<DataChunk> apply(CharSequence s) {
            if (s == null || s.length() == 0) {
                return ReactiveStreamsAdapter.publisherToFlow(Mono.empty());
            }
            DataChunk chunk = DataChunk.create(false, charset.encode(s.toString()));
            return ReactiveStreamsAdapter.publisherToFlow(Mono.just(chunk));
        }
    }

    private static class CharBufferWriter implements Function<CharBuffer, Flow.Publisher<DataChunk>> {

        private final Charset charset;

        /**
         * Creates new instance.
         *
         * @param charset a charset to use
         * @throws NullPointerException if parameter {@code charset} is {@code null}
         */
        CharBufferWriter(Charset charset) {
            Objects.requireNonNull(charset, "Parameter 'charset' is null!");
            this.charset = charset;
        }

        @Override
        public Flow.Publisher<DataChunk> apply(CharBuffer buffer) {
            if (buffer == null || buffer.size() == 0) {
                return ReactiveStreamsAdapter.publisherToFlow(Mono.empty());
            }
            final DataChunk chunk = DataChunk.create(false, buffer.encode(charset));
            return ReactiveStreamsAdapter.publisherToFlow(Mono.just(chunk));
        }
    }
}
