/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.http.media.jsonp;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.helidon.common.GenericType;
import io.helidon.common.config.Config;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Headers;
import io.helidon.http.HttpMediaType;
import io.helidon.http.WritableHeaders;
import io.helidon.http.media.EntityReader;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.MediaSupport;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonStructure;
import jakarta.json.JsonWriterFactory;

/**
 * Media support implementation for JSON Processing media support.
 */
public class JsonpSupport implements MediaSupport {
    /**
     * Json object generic type.
     */
    public static final GenericType<JsonObject> JSON_OBJECT_TYPE = GenericType.create(JsonObject.class);
    /**
     * Json array generic type.
     */
    public static final GenericType<JsonArray> JSON_ARRAY_TYPE = GenericType.create(JsonArray.class);

    private static final JsonReaderFactory READER_FACTORY = Json.createReaderFactory(Map.of());
    private static final JsonWriterFactory WRITER_FACTORY = Json.createWriterFactory(Map.of());

    private final JsonpReader reader = new JsonpReader(READER_FACTORY);
    private final JsonpWriter writer = new JsonpWriter(WRITER_FACTORY);
    private final String name;

    private JsonpSupport(String name) {
        this.name = name;
    }

    /**
     * Creates a new named {@link JsonpSupport}.
     *
     * @return a new {@link JsonpSupport}
     */
    public static MediaSupport create() {
        return new JsonpSupport("jsonp");
    }

    /**
     * Creates a new {@link JsonpSupport}.
     *
     * @param config must not be {@code null}
     * @return a new {@link JsonpSupport}
     */
    public static MediaSupport create(Config config) {
        return create(config, "jsonp");
    }

    /**
     * Creates a new named {@link JsonpSupport}.
     *
     * @param config must not be {@code null}
     * @param name name of the support
     * @return a new {@link JsonpSupport}
     */
    public static MediaSupport create(Config config, String name) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(name);

        return new JsonpSupport(name);
    }

    /**
     * Server response writer direct access.
     *
     * @param <T> type to write
     * @return a writer to write JSON-P objects
     */
    public static <T extends JsonStructure> EntityWriter<T> serverResponseWriter() {
        return new JsonpWriter<>(WRITER_FACTORY);
    }

    /**
     * Server request reader direct access.
     *
     * @param <T> type to read
     * @return a reader to read JSON-P objects
     */
    public static <T extends JsonStructure> EntityReader<T> serverRequestReader() {
        return new JsonpReader<>(READER_FACTORY);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String type() {
        return "jsonp";
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type, Headers requestHeaders) {
        if (isSupportedType(type)) {
            if (requestHeaders.contentType()
                    .map(this::isMediaTypeSupported)
                    .orElse(true)) {
                return new ReaderResponse<>(SupportLevel.SUPPORTED, this::reader);
            }
        }
        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type,
                                        Headers requestHeaders,
                                        WritableHeaders<?> responseHeaders) {

        if (isSupportedType(type)) {
            return new WriterResponse<>(SupportLevel.SUPPORTED, this::writer);
        }

        return WriterResponse.unsupported();
    }

    @Override
    public <T> ReaderResponse<T> reader(GenericType<T> type,
                                        Headers requestHeaders,
                                        Headers responseHeaders) {
        if (isSupportedType(type)) {
            List<HttpMediaType> acceptedTypes = requestHeaders.acceptedTypes();
            // check if accepted
            if (acceptedTypes.isEmpty()) {
                return new ReaderResponse<>(SupportLevel.SUPPORTED, this::reader);
            }
            for (HttpMediaType acceptedType : acceptedTypes) {
                if (isMediaTypeSupported(acceptedType)) {
                    return new ReaderResponse<>(SupportLevel.SUPPORTED, this::reader);
                }
            }
        }

        return ReaderResponse.unsupported();
    }

    @Override
    public <T> WriterResponse<T> writer(GenericType<T> type, WritableHeaders<?> requestHeaders) {
        // if the type is JsonStructure, we support it and do not care about content type
        // you provide json, you get json...
        if (isSupportedType(type)) {
            return new WriterResponse<>(SupportLevel.SUPPORTED, this::writer);
        }
        return WriterResponse.unsupported();
    }

    boolean isSupportedType(GenericType<?> type) {
        return JsonStructure.class.isAssignableFrom(type.rawType());
    }

    boolean isMediaTypeSupported(HttpMediaType mediaType) {
        return mediaType.test(MediaTypes.APPLICATION_JSON)
                || mediaType.test(MediaTypes.APPLICATION_JSON_PATCH_JSON);
    }

    <T> EntityReader<T> reader() {
        return reader;
    }

    <T> EntityWriter<T> writer() {
        return writer;
    }

}
