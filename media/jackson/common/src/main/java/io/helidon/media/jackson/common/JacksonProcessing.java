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
package io.helidon.media.jackson.common;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.function.Function;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Reader;
import io.helidon.media.common.CharBuffer;
import io.helidon.media.common.ContentReaders;
import io.helidon.media.common.ContentWriters;

import com.fasterxml.jackson.databind.ObjectMapper;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Utility methods for Jackson integration.
 */
public final class JacksonProcessing {

    private JacksonProcessing() {
        super();
    }

    /**
     * Returns a {@link Reader} that converts a {@link Flow.Publisher Publisher} of {@link java.nio.ByteBuffer}s to
     * a Java object.
     *
     * <p>This method is intended for the derivation of other, more specific readers.</p>
     *
     * @param objectMapper the {@link ObjectMapper} to use; must not be {@code null}
     * @return the byte array content reader that transforms a publisher of byte buffers to a completion stage that
     * might end exceptionally with a {@link RuntimeException} in case of I/O error
     * @exception NullPointerException if {@code objectMapper} is {@code null}
     */
    public static Reader<Object> reader(final ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper);
        return (publisher, cls) -> ContentReaders.byteArrayReader()
            .apply(publisher)
            .thenApply(bytes -> {
                    try {
                        return objectMapper.readValue(bytes, cls);
                    } catch (final IOException wrapMe) {
                        throw new JacksonRuntimeException(wrapMe.getMessage(), wrapMe);
                    }
                });
    }

    /**
     * Returns a function (writer) converting {@link Object}s to {@link Flow.Publisher Publisher}s
     * of {@link DataChunk}s by using the supplied {@link ObjectMapper}.
     *
     * @param objectMapper the {@link ObjectMapper} to use; must not be {@code null}
     * @param charset the charset to use; may be null
     * @return created function
     * @exception NullPointerException if {@code objectMapper} is {@code null}
     */
    public static Function<Object, Flow.Publisher<DataChunk>> writer(final ObjectMapper objectMapper, final Charset charset) {
        Objects.requireNonNull(objectMapper);
        return payload -> {
            CharBuffer buffer = new CharBuffer();
            try {
                objectMapper.writeValue(buffer, payload);
            } catch (final IOException wrapMe) {
                throw new JacksonRuntimeException(wrapMe.getMessage(), wrapMe);
            }
            return ContentWriters.charBufferWriter(charset == null ? UTF_8 : charset).apply(buffer);
        };
    }
}
