/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.media.jsonb.common;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.function.Function;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbException;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Reader;
import io.helidon.media.common.CharBuffer;
import io.helidon.media.common.ContentReaders;
import io.helidon.media.common.ContentWriters;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Contains utility methods for working with JSON-B.
 *
 * @see Jsonb
 */
public final class JsonBinding {

    private JsonBinding() {
        super();
    }

    /**
     * Returns a new {@link Reader} that converts a {@link Flow.Publisher Publisher} of {@link java.nio.ByteBuffer}s to
     * a Java object.
     *
     * <p>This method is intended for the derivation of other, more specific readers.</p>
     *
     * @param jsonb the {@link Jsonb} to use; must not be {@code null}
     * @return the byte array content reader that transforms a publisher of byte buffers to a completion stage that
     * might end exceptionally with a {@link RuntimeException} in case of I/O error
     * @exception NullPointerException if {@code objectMapper} is {@code null}
     */
    public static Reader<Object> reader(final Jsonb jsonb) {
        Objects.requireNonNull(jsonb);
        return (publisher, cls) -> ContentReaders.byteArrayReader()
            .apply(publisher)
            .thenApply(bytes -> {
                    try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
                        return jsonb.fromJson(inputStream, cls);
                    } catch (final IOException ioException) {
                        throw new JsonbException(ioException.getMessage(), ioException);
                    }
                });
    }

    /**
     * Returns a function (writer) converting {@link Object}s to {@link Flow.Publisher Publisher}s
     * of {@link DataChunk}s by using the supplied {@link Jsonb}.
     *
     * @param jsonb the {@link Jsonb} to use; must not be {@code null}
     * @param charset the charset to use; may be null
     * @return created function
     * @exception NullPointerException if {@code jsonb} is {@code null}
     */
    public static Function<Object, Flow.Publisher<DataChunk>> writer(final Jsonb jsonb, final Charset charset) {
        Objects.requireNonNull(jsonb);
        return payload -> {
            CharBuffer buffer = new CharBuffer();
            jsonb.toJson(payload, buffer);
            return ContentWriters.charBufferWriter(charset == null ? UTF_8 : charset).apply(buffer);
        };
    }
}
