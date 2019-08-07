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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Utils;
import io.helidon.common.reactive.Flow.Publisher;
import io.helidon.common.reactive.Mono;
import io.helidon.common.reactive.MonoCollector;
import io.helidon.common.reactive.MonoMapper;
import io.helidon.common.reactive.Multi;

/**
 * Utility class that provides standalone mechanisms for reading message body
 * content.
 */
public final class ContentReaders {

    /**
     * A utility class constructor.
     */
    private ContentReaders() {
    }

    /**
     * Collect the {@link DataChunk} of the given publisher into a single byte
     * array.
     *
     * @param chunks source publisher
     * @return Mono
     */
    public static Mono<byte[]> readBytes(Publisher<DataChunk> chunks) {
        return Multi.from(chunks).collect(new BytesCollector());
    }

    /**
     * Convert the given publisher of {@link DataChunk} into a {@link String}.
     * @param chunks source publisher
     * @param charset charset to use for decoding the bytes
     * @return Mono
     */
    public static Mono<String> readString(Publisher<DataChunk> chunks,
            Charset charset) {

        return readBytes(chunks).map(new BytesToString(charset));
    }

    /**
     * For basic charsets, returns a cached {@link StringBodyReader} instance or
     * create a new instance otherwise.
     *
     * @param charset the charset to use with the returned string content reader
     * @return a string content reader
     * @deprecated use {@link #readString(Publisher, Charset)} instead
     * instead
     */
    public static io.helidon.common.http.Reader<String> stringReader(
            Charset charset) {

        return (chunks, type) -> readString(chunks, charset).toFuture();
    }

    /**
     * Get a reader that converts a {@link DataChunk} publisher to an array of
     * bytes.
     *
     * @return reader that transforms a publisher of byte buffers to a
     * completion stage that might end exceptionally with
     * @deprecated use {@link #readBytes(Publisher)} instead
     */
    public static io.helidon.common.http.Reader<byte[]> byteArrayReader() {
        return (publisher, clazz) -> readBytes(publisher).toFuture();
    }

    /**
     * Get a reader that converts a {@link DataChunk} publisher to a blocking
     * Java {@link InputStream}. The resulting
     * {@link java.util.concurrent.CompletionStage} is already completed;
     * however, the referenced {@link InputStream} in it may not already have
     * all the data available; in such case, the read method (e.g.,
     * {@link InputStream#read()}) block.
     *
     * @return a input stream content reader
     * @deprecated use {@link PublisherInputStream} instead
     */
    public static io.helidon.common.http.Reader<InputStream> inputStreamReader() {
        return (publisher, clazz) -> CompletableFuture
                    .completedFuture(new PublisherInputStream(publisher));
    }

    /**
     * Implementation of {@link MonoMapper} that converts a {@code byte[]} into
     * a {@link String} using a given {@link Charset}.
     */
    private static final class BytesToString
            extends MonoMapper<byte[], String> {

        private final Charset charset;

        BytesToString(Charset charset) {
            this.charset = charset;
        }

        @Override
        public String mapNext(byte[] bytes) {
            return new String(bytes, charset);
        }
    }

    /**
     * Implementation of {@link Collector} that collects chunks into a single
     * {@code byte[]}.
     */
    private static final class BytesCollector
            extends MonoCollector<DataChunk, byte[]> {

        private final ByteArrayOutputStream baos;

        BytesCollector() {
            this.baos = new ByteArrayOutputStream();
        }

        @Override
        public void collect(DataChunk chunk) {
            try {
                Utils.write(chunk.data(), baos);
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        "Cannot convert byte buffer to a byte array!", e);
            } finally {
                chunk.release();
            }
        }

        @Override
        public byte[] value() {
            return baos.toByteArray();
        }
    }
}
