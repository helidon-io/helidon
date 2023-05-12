/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.http.media.jsonb;

import io.helidon.common.GenericType;
import io.helidon.common.config.Config;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.nima.http.media.EntityReader;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http.media.MediaSupport;

import jakarta.json.JsonObject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import static io.helidon.common.http.Http.HeaderValues.CONTENT_TYPE_JSON;

/**
 * {@link java.util.ServiceLoader} provider implementation for JSON Binding media support.
 */
public class JsonbSupport implements MediaSupport {
    private static final GenericType<JsonObject> JSON_OBJECT_TYPE = GenericType.create(JsonObject.class);

    private static final Jsonb JSON_B = JsonbBuilder.create();

    private final JsonbReader reader = new JsonbReader(JSON_B);
    private final JsonbWriter writer = new JsonbWriter(JSON_B);

    private JsonbSupport() {
    }

    /**
     * Creates a new {@link JsonbSupport}.
     *
     * @param config must not be {@code null}
     * @return a new {@link JsonbSupport}
     */
    public static MediaSupport create(Config config) {
        return new JsonbSupport();
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type, Headers requestHeaders) {
        if (requestHeaders.contentType()
                .map(it -> it.test(MediaTypes.APPLICATION_JSON))
                .orElse(true)) {
            if (type.equals(JSON_OBJECT_TYPE)) {
                // leave this to JSON-P
                return ReaderResponse.unsupported();
            }
            return new ReaderResponse<>(SupportLevel.COMPATIBLE, this::reader);
        }

        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type,
                                        Headers requestHeaders,
                                        WritableHeaders<?> responseHeaders) {
        if (JSON_OBJECT_TYPE.equals(type)) {
            return WriterResponse.unsupported();
        }

        // check if accepted
        for (HttpMediaType acceptedType : requestHeaders.acceptedTypes()) {
            if (acceptedType.test(MediaTypes.APPLICATION_JSON)) {
                return new WriterResponse<>(SupportLevel.COMPATIBLE, this::writer);
            }
        }

        if (requestHeaders.acceptedTypes().isEmpty()) {
            return new WriterResponse<>(SupportLevel.COMPATIBLE, this::writer);
        }

        return WriterResponse.unsupported();
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type,
                                        Headers requestHeaders,
                                        Headers responseHeaders) {
        if (JSON_OBJECT_TYPE.equals(type)) {
            return ReaderResponse.unsupported();
        }

        // check if accepted
        for (HttpMediaType acceptedType : requestHeaders.acceptedTypes()) {
            if (acceptedType.test(MediaTypes.APPLICATION_JSON) || acceptedType.mediaType().isWildcardType()) {
                return new ReaderResponse<>(SupportLevel.COMPATIBLE, this::reader);
            }
        }

        if (requestHeaders.acceptedTypes().isEmpty()) {
            return new ReaderResponse<>(SupportLevel.COMPATIBLE, this::reader);
        }

        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
        if (type.equals(JSON_OBJECT_TYPE)) {
            return WriterResponse.unsupported();
        }
        if (requestHeaders.contains(Http.Header.CONTENT_TYPE)) {
            if (requestHeaders.contains(CONTENT_TYPE_JSON)) {
                return new WriterResponse<>(SupportLevel.COMPATIBLE, this::writer);
            }
        } else {
            return new WriterResponse<>(SupportLevel.SUPPORTED, this::writer);
        }
        return WriterResponse.unsupported();
    }

    <T> EntityReader<T> reader() {
        return reader;
    }

    <T> EntityWriter<T> writer() {
        return writer;
    }
}
