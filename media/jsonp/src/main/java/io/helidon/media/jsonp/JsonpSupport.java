/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
package io.helidon.media.jsonp;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonReaderFactory;
import javax.json.JsonStructure;
import javax.json.JsonWriterFactory;

import io.helidon.common.LazyValue;
import io.helidon.media.common.MediaSupport;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.media.common.MessageBodyStreamWriter;
import io.helidon.media.common.MessageBodyWriter;

/**
 * Support for JSON Processing integration.
 *
 * For usage examples navigate to {@link MediaSupport}.
 */
public final class JsonpSupport implements MediaSupport {
    private static final LazyValue<JsonpSupport> DEFAULT =
            LazyValue.create(() -> new JsonpSupport(Builder.readerFactory(null),
                                                    Builder.writerFactory(null)));

    private final JsonpBodyReader reader;
    private final JsonpBodyWriter writer;
    private final JsonpBodyStreamWriter streamWriter;
    private final JsonpEsBodyStreamWriter esStreamWriter;
    private final JsonpNdBodyStreamWriter ndStreamWriter;

    private JsonpSupport(JsonReaderFactory readerFactory, JsonWriterFactory writerFactory) {
        reader = new JsonpBodyReader(readerFactory);
        writer = new JsonpBodyWriter(writerFactory);
        streamWriter = new JsonpBodyStreamWriter(writerFactory);
        esStreamWriter = new JsonpEsBodyStreamWriter(writerFactory);
        ndStreamWriter = new JsonpNdBodyStreamWriter(writerFactory);
    }

    /**
     * Provides a default instance for JSON-P readers and writers.
     *
     * @return json processing with default configuration
     */
    public static JsonpSupport create() {
        return DEFAULT.get();
    }

    /**
     * Create an instance with the provided JSON-P configuration.
     *
     * @param jsonPConfig configuration of the processing library
     * @return a configured JSON-P instance
     */
    public static JsonpSupport create(Map<String, ?> jsonPConfig) {
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
     * Return a default JSON-P entity reader.
     *
     * @return default JSON-P body reader instance
     */
    public static MessageBodyReader<JsonStructure> reader() {
        return DEFAULT.get().reader;
    }

    /**
     * Create a new JSON-P entity reader based on {@link JsonReaderFactory}.
     *
     * @param readerFactory json reader factory
     * @return new JSON-P body reader instance
     */
    public static MessageBodyReader<JsonStructure> reader(JsonReaderFactory readerFactory) {
        return new JsonpBodyReader(readerFactory);
    }

    /**
     * Return a default JSON-P entity writer.
     *
     * @return default JSON-P body writer instance
     */
    public static MessageBodyWriter<JsonStructure> writer() {
        return DEFAULT.get().writer;
    }

    /**
     * Create a new JSON-P entity writer based on {@link JsonWriterFactory}.
     *
     * @param writerFactory json writer factory
     * @return new JSON-P body writer instance
     */
    public static MessageBodyWriter<JsonStructure> writer(JsonWriterFactory writerFactory) {
        return new JsonpBodyWriter(writerFactory);
    }

    /**
     * Return a default JSON-P entity stream writer.
     *
     * @return default JSON-P body stream writer instance
     */
    public static MessageBodyStreamWriter<JsonStructure> streamWriter() {
        return DEFAULT.get().streamWriter;
    }

    /**
     * Create a new JSON-P entity stream writer based on {@link JsonWriterFactory}.
     *
     * @param writerFactory json writer factory
     * @return new JSON-P stream body writer instance
     */
    public static MessageBodyStreamWriter<JsonStructure> streamWriter(JsonWriterFactory writerFactory) {
        return new JsonpBodyStreamWriter(writerFactory);
    }

    /**
     * Return a default JSON-P entity event stream writer.
     * This writer is for {@link io.helidon.common.http.MediaType#TEXT_EVENT_STREAM} content type.
     *
     * @return new JSON-P body stream writer instance
     */
    public static MessageBodyStreamWriter<JsonStructure> eventStreamWriter() {
        return DEFAULT.get().esStreamWriter;
    }

    /**
     * Create a new JSON-P entity stream writer based on {@link JsonWriterFactory} instance.
     * This writer is for {@link io.helidon.common.http.MediaType#TEXT_EVENT_STREAM} content type.
     *
     * @param writerFactory json writer factory
     * @return new JSON-P body stream writer instance
     */
    public static MessageBodyStreamWriter<JsonStructure> eventStreamWriter(JsonWriterFactory writerFactory) {
        return new JsonpEsBodyStreamWriter(writerFactory);
    }

    /**
     * Return a default JSON-P entity event stream writer.
     * This writer is for {@link io.helidon.common.http.MediaType#APPLICATION_X_NDJSON} content type.
     *
     * @return new JSON-P body stream writer instance
     */
    public static MessageBodyStreamWriter<JsonStructure> ndJsonStreamWriter() {
        return DEFAULT.get().ndStreamWriter;
    }

    /**
     * Create a new JSON-P entity stream writer based on {@link JsonWriterFactory} instance.
     * This writer is for {@link io.helidon.common.http.MediaType#APPLICATION_X_NDJSON} content type.
     *
     * @param writerFactory json writer factory
     * @return new JSON-P body stream writer instance
     */
    public static MessageBodyStreamWriter<JsonStructure> ndJsonStreamWriter(JsonWriterFactory writerFactory) {
        return new JsonpNdBodyStreamWriter(writerFactory);
    }

    /**
     * Return JSON-P reader instance.
     *
     * @return JSON-P reader instance
     */
    public MessageBodyReader<JsonStructure> readerInstance() {
        return reader;
    }

    /**
     * Return JSON-P entity writer.
     *
     * @return JSON-P writer instance
     */
    public MessageBodyWriter<JsonStructure> writerInstance() {
        return writer;
    }

    /**
     * Return JSON-P stream writer.
     * <p>
     * This stream writer supports {@link java.util.concurrent.Flow.Publisher publishers}
     * of {@link javax.json.JsonStructure} (such as {@link javax.json.JsonObject}),
     * writing them as an array of JSONs.
     *
     * @return JSON processing stream writer.
     */
    public MessageBodyStreamWriter<JsonStructure> streamWriterInstance() {
        return streamWriter;
    }

    /**
     * Return JSON-P stream writer.
     * <p>
     * This stream writer supports {@link java.util.concurrent.Flow.Publisher publishers}
     * of {@link javax.json.JsonStructure} (such as {@link javax.json.JsonObject}),
     * writing them as separate entries in the following format:
     * <pre><code>
     * data: {"json":"data"}\n
     * \n
     * data: {"json2":"data2"}\n
     * \n
     * </code></pre>
     *
     * This writer is for {@link io.helidon.common.http.MediaType#TEXT_EVENT_STREAM} content type.
     *
     * @return JSON processing stream writer.
     */
    public MessageBodyStreamWriter<JsonStructure> eventStreamWriterInstance() {
        return esStreamWriter;
    }

    /**
     * Return JSON-P stream writer.
     * <p>
     * This stream writer supports {@link java.util.concurrent.Flow.Publisher publishers}
     * of {@link javax.json.JsonStructure} (such as {@link javax.json.JsonObject}),
     * writing them as separate entries in the following format:
     * <pre><code>
     * {"json":"data"}\n
     * {"json2":"data2"}
     * </code></pre>
     *
     * This writer is for {@link io.helidon.common.http.MediaType#APPLICATION_X_NDJSON} content type.
     *
     * @return JSON processing stream writer.
     */
    public MessageBodyStreamWriter<JsonStructure> ndJsonStreamWriterInstance() {
        return ndStreamWriter;
    }

    @Override
    public Collection<MessageBodyReader<?>> readers() {
        return List.of(reader);
    }

    @Override
    public Collection<MessageBodyWriter<?>> writers() {
        return List.of(writer);
    }

    @Override
    public Collection<MessageBodyStreamWriter<?>> streamWriters() {
        return List.of(streamWriter, ndStreamWriter, esStreamWriter);
    }

    /**
     * Fluent-API builder for {@link JsonpSupport}.
     */
    public static class Builder implements io.helidon.common.Builder<JsonpSupport> {

        private JsonWriterFactory jsonWriterFactory;
        private JsonReaderFactory jsonReaderFactory;
        private Map<String, ?> jsonPConfig;

        @Override
        public JsonpSupport build() {
            if ((null == jsonReaderFactory) && (null == jsonWriterFactory) && (null == jsonPConfig)) {
                return DEFAULT.get();
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

            return new JsonpSupport(jsonReaderFactory, jsonWriterFactory);
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
         *
         * @param factory writer factory
         * @return updated builder instance
         */
        public Builder jsonWriterFactory(JsonWriterFactory factory) {
            this.jsonWriterFactory = factory;
            return this;
        }

        /**
         * Explicit JSON-P Reader factory instance.
         *
         * @param factory reader factory
         * @return updated builder instance
         */
        public Builder jsonReaderFactory(JsonReaderFactory factory) {
            this.jsonReaderFactory = factory;
            return this;
        }
    }
}
