/*
 * Copyright (c) 2017, 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow.Publisher;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Reader;
import io.helidon.common.http.Utils;
import io.helidon.common.mapper.Mapper;
import io.helidon.common.reactive.Collector;
import io.helidon.common.reactive.Multi;
import io.helidon.common.reactive.Single;

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
     * @return Single
     */
    public static Single<byte[]> readBytes(Publisher<DataChunk> chunks) {
        return Multi.from(chunks).collect(new BytesCollector());
    }

    /**
     * Convert the given publisher of {@link DataChunk} into a {@link String}.
     * @param chunks source publisher
     * @param charset charset to use for decoding the bytes
     * @return Single
     */
    public static Single<String> readString(Publisher<DataChunk> chunks, Charset charset) {
        return readBytes(chunks).map(new BytesToString(charset));
    }

    /**
     * Convert the publisher of {@link DataChunk} into a {@link String} processed through URL
     * decoding.
     * @param chunks source publisher
     * @param charset charset to use for decoding the input
     * @return Single
     */
    public static Single<String> readURLEncodedString(Publisher<DataChunk> chunks,
            Charset charset) {
        return readString(chunks, charset).map(new StringToDecodedString(charset));
    }

    /**
     * Get a reader that converts a {@link DataChunk} publisher to a
     * {@link String}.
     *
     * @param charset the charset to use with the returned string content reader
     * @return a string content reader
     */
    public static Reader<String> stringReader(Charset charset) {
        return (chunks, type) -> readString(chunks, charset).toStage();
    }

    /**
     * Gets a reader that converts a {@link DataChunk} publisher to a {@link String} processed
     * through URL decoding.
     *
     * @param charset the charset to use with the returned string content reader
     * @return the URL-decoded string content reader
     */
    public static Reader<String> urlEncodedStringReader(Charset charset) {
        return (chunks, type) -> readURLEncodedString(chunks, charset).toStage();
    }

    /**
     * Get a reader that converts a {@link DataChunk} publisher to an array of
     * bytes.
     *
     * @return reader that transforms a publisher of byte buffers to a
     * completion stage that might end exceptionally with
     */
    public static Reader<byte[]> byteArrayReader() {
        return (publisher, clazz) -> readBytes(publisher).toStage();
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
     */
    public static Reader<InputStream> inputStreamReader() {
        return (publisher, clazz) -> CompletableFuture.completedFuture(new DataChunkInputStream(publisher));
    }

    /**
     * Implementation of {@link Mapper} that converts a {@code byte[]} into
     * a {@link String} using a given {@link Charset}.
     */
    private static final class BytesToString implements Mapper<byte[], String> {

        private final Charset charset;

        BytesToString(Charset charset) {
            this.charset = charset;
        }

        @Override
        public String map(byte[] bytes) {
            return new String(bytes, charset);
        }
    }

    /**
     * Mapper that applies URL decoding to a {@code String}.
     */
    private static final class StringToDecodedString implements Mapper<String, String> {

        private final Charset charset;

        StringToDecodedString(Charset charset) {
            this.charset = charset;
        }

        @Override
        public String map(String s) {
            try {
                return URLDecoder.decode(s, charset.name());
            } catch (UnsupportedEncodingException e) {
                /*
                 * Convert the encoding exception into an unchecked one to simplify the mapper's use
                 * in lambdas.
                 */
                throw new RuntimeException(e);
            }
        }
    }
    /**
     * Implementation of {@link Collector} that collects chunks into a single
     * {@code byte[]}.
     */
    private static final class BytesCollector implements Collector<DataChunk, byte[]> {

        private final ByteArrayOutputStream baos;

        BytesCollector() {
            this.baos = new ByteArrayOutputStream();
        }

        @Override
        public void collect(DataChunk chunk) {
            try {
                for (ByteBuffer byteBuffer : chunk.data()) {
                    Utils.write(byteBuffer, baos);
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot convert byte buffer to a byte array!", e);
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
