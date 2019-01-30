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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Reader;
import io.helidon.common.reactive.Flow;
import io.helidon.media.common.ContentReaders;
import io.helidon.media.common.ContentWriters;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Media type support for Jackson.
 */
public final class JacksonProcessing {

    private JacksonProcessing() {
        super();
    }

    /**
     * Check whether the type is supported by Jackson.
     *
     * @param type media type to check
     * @return true if the media type checked is supported by Jackson
     */
    public static boolean isSupported(MediaType type) {
        // See https://github.com/FasterXML/jackson-jaxrs-providers/blob/jackson-jaxrs-providers-2.9.4/json/src/main/java/com/fasterxml/jackson/jaxrs/json/JacksonJsonProvider.java#L167-L192
        final boolean returnValue;
        if (type == null) {
            returnValue = true;
        } else {
            final String subtype = type.subtype();
            if (subtype == null) {
                returnValue = false;
            } else {
                returnValue = "json".equalsIgnoreCase(subtype)
                    || subtype.endsWith("+json")
                    || "javascript".equals(subtype)
                    || "x-javascript".equals(subtype)
                    || "x-json".equals(subtype);
            }
        }
        return returnValue;
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
                        throw new RuntimeException(wrapMe.getMessage(), wrapMe);
                    }
                });
    }

    /**
     * Returns a function (writer) converting {@link Object}s to {@link Flow.Publisher Publisher}s
     * of {@link DataChunk}s by using the supplied {@link ObjectMapper}.
     *
     * @param objectMapper the {@link ObjectMapper} to use; must not be {@code null}
     * @return created function
     * @exception NullPointerException if {@code objectMapper} is {@code null}
     */
    public static Function<Object, Flow.Publisher<DataChunk>> writer(final ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper);
        return payload -> {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                objectMapper.writeValue(baos, payload);
            } catch (final IOException wrapMe) {
                throw new RuntimeException(wrapMe.getMessage(), wrapMe);
            }
            return ContentWriters.byteArrayWriter(false)
                .apply(baos.toByteArray());
        };
    }

}
