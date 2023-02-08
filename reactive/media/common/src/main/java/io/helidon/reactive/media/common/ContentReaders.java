/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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

package io.helidon.reactive.media.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.concurrent.Flow.Publisher;

import io.helidon.common.http.DataChunk;
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
     * @since 2.0.0
     */
    public static Single<byte[]> readBytes(Publisher<DataChunk> chunks) {
        return Multi.create(chunks).collect(new BytesCollector());
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
     * @since 2.0.0
     */
    public static Single<String> readURLEncodedString(Publisher<DataChunk> chunks,
            Charset charset) {
        return readString(chunks, charset).map(new StringToDecodedString(charset));
    }

    /**
     * Implementation of {@link Mapper} that converts a {@code byte[]} into
     * a {@link String} using a given {@link Charset}.
     */
    private record BytesToString(Charset charset) implements Mapper<byte[], String> {

        @Override
        public String map(byte[] bytes) {
            return new String(bytes, charset);
        }
    }

    /**
     * Mapper that applies URL decoding to a {@code String}.
     */
    private record StringToDecodedString(Charset charset) implements Mapper<String, String> {

        @Override
        public String map(String s) {
            return URLDecoder.decode(s, charset);
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
                    write(byteBuffer, baos);
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

    private static void write(ByteBuffer byteBuffer, OutputStream out) throws IOException {
        if (byteBuffer.hasArray()) {
            out.write(byteBuffer.array(), byteBuffer.arrayOffset() + byteBuffer.position(), byteBuffer.remaining());
        } else {
            byte[] buff = new byte[byteBuffer.remaining()];
            byteBuffer.get(buff);
            out.write(buff);
        }
    }
}
