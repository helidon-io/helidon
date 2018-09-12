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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Reader;
import io.helidon.common.http.Utils;
import io.helidon.common.reactive.Flow;

/**
 * The ContentReaders.
 */
public final class ContentReaders {

    private static final class StringContentReadersHolder {

        private static final Map<String, StringContentReader> MAP = new HashMap<>();

        static {
            addReader(StandardCharsets.UTF_8);
            addReader(StandardCharsets.UTF_16);
            addReader(StandardCharsets.ISO_8859_1);
            addReader(StandardCharsets.US_ASCII);

            // try to register another common charset readers
            addReader("cp1252");
            addReader("cp1250");
            addReader("ISO-8859-2");
        }

        private static void addReader(Charset charset) {
            StringContentReader reader = new StringContentReader(charset);
            MAP.put(charset.name(), reader);
        }

        private static void addReader(String charset) {
            try {
                addReader(Charset.forName(charset));
            } catch (Exception e) {
                // ignored
            }
        }

    }

    private ContentReaders() {
    }

    /**
     * For basic charsets, returns a cached {@link StringContentReader} instance
     * or create a new instance otherwise.
     *
     * @param charset the charset to use with the returned string content reader
     * @return a string content reader
     */
    public static StringContentReader stringReader(Charset charset) {
        StringContentReader reader = StringContentReadersHolder.MAP.get(charset.name());
        return reader != null ? reader : new StringContentReader(charset);
    }

    /**
     * Obtain a lazily initialized cached String content reader or {@code null}.
     *
     * @param charset the charset to obtain the reader for
     * @return a cached String content reader or {@code null}
     */
    static StringContentReader cachedStringReader(String charset) {
        return StringContentReadersHolder.MAP.get(charset);
    }

    /**
     * The returned reader provides means to convert a {@link ByteBuffer} publisher to
     * an array of bytes.
     * <p>
     * Returns a lazily initialized classloader-scoped singleton.
     * <p>
     * Note that the singleton aspect is not guarantied by all JVMs. Although, Oracle JVM
     * does create a singleton because the lambda does not capture values.
     *
     * @return the byte array content reader singleton that transforms a publisher of
     * byte buffers to a completion stage that might end exceptionally with
     * {@link IllegalArgumentException} if it wasn't possible to convert the byte buffer
     * to an array of bytes
     */
    public static Reader<byte[]> byteArrayReader() {
        return (publisher, clazz) -> {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            CompletableFuture<byte[]> future = new CompletableFuture<>();
            publisher.subscribe(new Flow.Subscriber<DataChunk>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(DataChunk item) {
                    try {
                        synchronized (bytes) {
                            Utils.write(item.data(), bytes);
                        }
                    } catch (IOException e) {
                        onError(new IllegalArgumentException("Cannot convert byte buffer to a byte array!", e));
                    } finally {
                        item.release();
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    future.completeExceptionally(throwable);
                }

                @Override
                public void onComplete() {
                    future.complete(bytes.toByteArray());
                }
            });

            return future;
        };
    }

    /**
     * Creates a reader that bridges Flow API IO to a blocking Java {@link InputStream}.
     * The resulting {@link java.util.concurrent.CompletionStage} is already completed;
     * however, the referenced {@link InputStream} in it may not already have all the data
     * available; in such case, the read method (e.g., {@link InputStream#read()}) block.
     *
     * @return a input stream content reader
     */
    public static Reader<InputStream> inputStreamReader() {
        return (publisher, clazz) -> CompletableFuture.completedFuture(new PublisherInputStream(publisher));
    }
}
