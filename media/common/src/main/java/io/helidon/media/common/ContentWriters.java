/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.concurrent.Flow.Publisher;
import java.util.function.Function;

import io.helidon.common.http.DataChunk;
import io.helidon.common.reactive.IoMulti;
import io.helidon.common.reactive.RetrySchema;
import io.helidon.common.reactive.Single;

/**
 * Utility class that provides standalone mechanisms for writing message body
 * content.
 */
public final class ContentWriters {

    /**
     * A utility class constructor.
     */
    private ContentWriters() {
    }

    /**
     * Create a {@link DataChunk} with the given byte array and return a
     * {@link Single}.
     *
     * @param bytes the byte array
     * @param copy if {@code true} the byte array is copied
     * @return Single
     * @since 2.0.0
     */
    public static Single<DataChunk> writeBytes(byte[] bytes, boolean copy) {
        byte[] data;
        if (copy) {
            data = new byte[bytes.length];
            System.arraycopy(bytes, 0, data, 0, bytes.length);
        } else {
            data = bytes;
        }
        return Single.just(DataChunk.create(false, ByteBuffer.wrap(data)));
    }

    /**
     * Create a publisher of {@link DataChunk} with the given
     * {@link CharSequence} / {@link Charset} and return a {@link Single}.
     *
     * @param cs the char sequence
     * @param charset the charset to use to encode the char sequence
     * @return Single
     * @since 2.0.0
     */
    public static Single<DataChunk> writeCharSequence(CharSequence cs, Charset charset) {
        return Single.just(DataChunk.create(false, charset.encode(cs.toString())));
    }

    /**
     * Create a a publisher {@link DataChunk} with the given
     * {@link CharBuffer} / {@link Charset} and return a {@link Single}.
     *
     * @param buffer the char buffer
     * @param charset the charset to use to encode the char sequence
     * @return Single
     * @since 2.0.0
     */
    public static Single<DataChunk> writeCharBuffer(CharBuffer buffer, Charset charset) {
        return Single.just(DataChunk.create(false, buffer.encode(charset)));
    }

    /**
     * Create a a publisher {@link DataChunk} with the given
     * {@link Throwable} / {@link Charset} and return a {@link Single}.
     *
     * @param throwable the {@link Throwable}
     * @param charset the charset to use to encode the stack trace
     * @return Single
     * @since 2.0.0
     */
    public static Single<DataChunk> writeStackTrace(Throwable throwable, Charset charset) {
        final StringWriter stringWriter = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(stringWriter);
        String stackTraceString = null;
        try {
            throwable.printStackTrace(printWriter);
            stackTraceString = stringWriter.toString();
        } finally {
            printWriter.close();
        }
        final Single<DataChunk> returnValue;
        if (stackTraceString.isEmpty()) {
            returnValue = Single.<DataChunk>empty();
        } else {
            returnValue = writeCharSequence(stackTraceString, charset);
        }
        return returnValue;
    }

    /**
     * Returns a writer function for {@code byte[]}.
     * <p>
     * The {@code copy} variant is by default registered in
     * {@code ServerResponse}.
     *
     * @param copy a signal if byte array should be copied - set it {@code true}
     * if {@code byte[]} will be immediately reused.
     * @return a {@code byte[]} writer
     *
     * @deprecated since 2.0.0, use {@link #writeBytes(byte[], boolean)} instead
     */
    @Deprecated(since = "2.0.0")
    public static Function<byte[], Publisher<DataChunk>> byteArrayWriter(boolean copy) {
        return (bytes) -> writeBytes(bytes, copy);
    }

    /**
     * Returns a writer function for {@link CharSequence} using provided
     * standard {@code charset}.
     * <p>
     * An instance is by default registered in {@code ServerResponse} for all
     * standard charsets.
     *
     * @param charset a standard charset to use
     * @return a {@link String} writer
     * @throws NullPointerException if parameter {@code charset} is {@code null}
     * @deprecated since 2.0.0, use {@link #writeCharSequence(CharSequence, Charset)}
     *  or {@link DefaultMediaSupport#charSequenceWriter()} instead
     */
    @Deprecated(since = "2.0.0")
    public static Function<CharSequence, Publisher<DataChunk>> charSequenceWriter(Charset charset) {
        return (cs) -> writeCharSequence(cs, charset);
    }

    /**
     * Returns a writer function for {@link CharBuffer} using provided standard
     * {@code charset}.
     * <p>
     * An instance is by default registered in {@code ServerResponse} for all
     * standard charsets.
     *
     * @param charset a standard charset to use
     * @return a {@link String} writer
     * @throws NullPointerException if parameter {@code charset} is {@code null}
     * @deprecated since 2.0.0, use {@link #writeCharBuffer(CharBuffer, Charset)} instead
     */
    @Deprecated(since = "2.0.0")
    public static Function<CharBuffer, Publisher<DataChunk>> charBufferWriter(Charset charset) {
        return (buffer) -> writeCharBuffer(buffer, charset);
    }

    /**
     * Returns a writer function for {@link ReadableByteChannel}. Created
     * publisher use provided {@link RetrySchema} to define delay between
     * unsuccessful read attempts.
     *
     * @param retrySchema a retry schema to use in case when {@code read}
     * operation reads {@code 0 bytes}
     * @return a {@link ReadableByteChannel} writer
     * @deprecated since 2.0.0, use {@link DefaultMediaSupport#byteChannelWriter(RetrySchema)}} instead
     */
    @Deprecated(since = "2.0.0")
    public static Function<ReadableByteChannel, Publisher<DataChunk>> byteChannelWriter(RetrySchema retrySchema) {
        Objects.requireNonNull(retrySchema);

        return channel -> IoMulti.multiFromByteChannelBuilder(channel)
                .retrySchema(retrySchema)
                .build()
                .map(DataChunk::create);
    }

    /**
     * Returns a writer function for {@link ReadableByteChannel}.
     *
     * @return a {@link ReadableByteChannel} writer
     * @deprecated since 2.0.0, use {@link DefaultMediaSupport#byteChannelWriter()}} instead
     */
    @Deprecated(since = "2.0.0")
    public static Function<ReadableByteChannel, Publisher<DataChunk>> byteChannelWriter() {
        return channel -> IoMulti.multiFromByteChannel(channel).map(DataChunk::create);
    }

}
