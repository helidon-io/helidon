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
package io.helidon.media.jsonp.common;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonStructure;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Reader;
import io.helidon.common.reactive.Flow;
import io.helidon.media.common.CharBuffer;
import io.helidon.media.common.ContentReaders;
import io.helidon.media.common.ContentWriters;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Support for json processing integration.
 */
public final class JsonProcessing {

    private final JsonReaderFactory jsonReaderFactory;
    private final JsonWriterFactory jsonWriterFactory;

    private JsonProcessing(JsonReaderFactory readerFactory, JsonWriterFactory writerFactory) {
        this.jsonReaderFactory = readerFactory;
        this.jsonWriterFactory = writerFactory;
    }

    /**
     * Returns a function (reader) converting {@link Flow.Publisher Publisher} of {@link java.nio.ByteBuffer}s to
     * a JSON-P object.
     * <p>
     * It is intended for derivation of others, more specific readers.
     *
     * @return the byte array content reader that transforms a publisher of byte buffers to a completion stage that
     * might end exceptionally with a {@link IllegalArgumentException} in case of I/O error or
     * a {@link javax.json.JsonException}
     */
    public Reader<JsonStructure> reader() {
        return reader(null);
    }

    /**
     * Returns a function (reader) converting {@link Flow.Publisher Publisher} of {@link java.nio.ByteBuffer}s to
     * a JSON-P object.
     * <p>
     * It is intended for derivation of others, more specific readers.
     *
     * @param charset a charset to use charset
     * @return the byte array content reader that transforms a publisher of byte buffers to a completion stage that
     * might end exceptionally with a {@link IllegalArgumentException} in case of I/O error or
     * a {@link javax.json.JsonException}
     */
    public Reader<JsonStructure> reader(Charset charset) {
        return (publisher, clazz) -> ContentReaders.byteArrayReader()
                .apply(publisher)
                .thenApply(bytes -> {
                    InputStream is = new ByteArrayInputStream(bytes);

                    JsonReader reader = (charset == null)
                            ? jsonReaderFactory.createReader(is)
                            : jsonReaderFactory.createReader(is, charset);

                    JsonStructure json = reader.read();
                    if (!clazz.isAssignableFrom(json.getClass())) {
                        throw new JsonException("Unable to convert " + json.getClass() + " to " + clazz);
                    }
                    return json;
                });
    }

    /**
     * Returns a function (writer) converting {@link JsonStructure} to the {@link Flow.Publisher Publisher}
     * of {@link DataChunk}s.
     *
     * @param charset a charset to use or {@code null} for default charset
     * @return created function
     */
    public Function<JsonStructure, Flow.Publisher<DataChunk>> writer(Charset charset) {
        return json -> {
            CharBuffer buffer = new CharBuffer();
            JsonWriter writer = jsonWriterFactory.createWriter(buffer);
            writer.write(json);
            writer.close();
            return ContentWriters.charBufferWriter(charset == null ? UTF_8 : charset).apply(buffer);
        };
    }

    /**
     * Returns a function (writer) converting {@link JsonStructure} to the {@link Flow.Publisher Publisher}
     * of {@link DataChunk}s.
     *
     * @return created function
     */
    public Function<JsonStructure, Flow.Publisher<DataChunk>> writer() {
        return writer(null);
    }

    /**
     * Provides a default instance for JSON-P readers and writers.
     * @return json processing with default configuration
     */
    public static JsonProcessing create() {
        return Builder.DEFAULT_INSTANCE;
    }

    /**
     * Create an instance with the provided JSON-P configuration.
     * @param jsonPConfig configuration of the processing library
     * @return a configured JSON-P instance
     */
    public static JsonProcessing create(Map<String, ?> jsonPConfig) {
        return builder().jsonProcessingConfig(jsonPConfig).build();
    }

    /**
     * Fluent API builder to create instances of JSON-P.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent-API builder for {@link io.helidon.media.jsonp.common.JsonProcessing}.
     */
    public static class Builder implements io.helidon.common.Builder<JsonProcessing> {
        private static final JsonProcessing DEFAULT_INSTANCE = new JsonProcessing(readerFactory(null), writerFactory(null));

        private JsonWriterFactory jsonWriterFactory;
        private JsonReaderFactory jsonReaderFactory;
        private Map<String, ?> jsonPConfig;

        @Override
        public JsonProcessing build() {
            if ((null == jsonReaderFactory) && (null == jsonWriterFactory) && (null == jsonPConfig)) {
                return DEFAULT_INSTANCE;
            }

            if (null == jsonPConfig) {
                jsonPConfig = new HashMap<>();
            }

            if (null == jsonWriterFactory) {
                jsonWriterFactory = writerFactory(jsonPConfig);
            }

            if (null == jsonReaderFactory) {
                jsonReaderFactory = readerFactory(jsonPConfig);
            }

            return new JsonProcessing(jsonReaderFactory, jsonWriterFactory);
        }

        private static JsonReaderFactory readerFactory(Map<String, ?> jsonPConfig) {
            return Json.createReaderFactory(jsonPConfig);
        }

        private static JsonWriterFactory writerFactory(Map<String, ?> jsonPConfig) {
            return Json.createWriterFactory(jsonPConfig);
        }

        /**
         * Configuration to use when creating reader and writer factories.
         *
         * @param config configuration of JSON-P library
         * @return updated builder instance
         */
        public Builder jsonProcessingConfig(Map<String, ?> config) {
            this.jsonPConfig = config;
            this.jsonWriterFactory = null;
            this.jsonReaderFactory = null;
            return this;
        }

        /**
         * Explicit JSON-P Writer factory instance.
         * @param factory writer factory
         * @return updated builder instance
         */
        public Builder jsonWriterFactory(JsonWriterFactory factory) {
            this.jsonWriterFactory = factory;
            return this;
        }

        /**
         * Explicit JSON-P Reader factory instance.
         * @param factory reader factory
         * @return updated builder instance
         */
        public Builder jsonReaderFactory(JsonReaderFactory factory) {
            this.jsonReaderFactory = factory;
            return this;
        }
    }
}
