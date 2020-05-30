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
import javax.json.JsonWriterFactory;

import io.helidon.common.HelidonFeatures;
import io.helidon.common.HelidonFlavor;
import io.helidon.media.common.MediaSupport;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.media.common.MessageBodyStreamWriter;
import io.helidon.media.common.MessageBodyWriter;

/**
 * Support for JSON Processing integration.
 *
 * For usage examples navigate to {@link MediaSupport}
 */
public final class JsonpSupport implements MediaSupport {

    static {
        HelidonFeatures.register(HelidonFlavor.SE, "Media", "JSON-P");
    }

    private final JsonReaderFactory jsonReaderFactory;
    private final JsonWriterFactory jsonWriterFactory;

    private JsonpSupport(JsonReaderFactory readerFactory, JsonWriterFactory writerFactory) {
        this.jsonReaderFactory = readerFactory;
        this.jsonWriterFactory = writerFactory;
    }

    /**
     * Create a new JSON-P entity reader.
     *
     * @return JsonEntityReader
     */
    public JsonpBodyReader newReader() {
        return new JsonpBodyReader(jsonReaderFactory);
    }

    /**
     * Create a new JSON-P entity writer.
     *
     * @return JsonEntityWriter
     */
    public JsonpBodyWriter newWriter() {
        return new JsonpBodyWriter(jsonWriterFactory);
    }

    /**
     * Create a new JSON-P stream writer.
     * <p>
     * This stream writer supports {@link java.util.concurrent.Flow.Publisher publishers}
     * of {@link javax.json.JsonStructure} (such as {@link javax.json.JsonObject})
     * , writing them as an array of JSONs.
     *
     * @return JSON processing stream writer.
     */
    public JsonpBodyStreamWriter newStreamWriter() {
        return new JsonpBodyStreamWriter(jsonWriterFactory);
    }

    @Override
    public Collection<MessageBodyReader<?>> readers() {
        return List.of(newReader());
    }

    @Override
    public Collection<MessageBodyWriter<?>> writers() {
        return List.of(newWriter());
    }

    @Override
    public Collection<MessageBodyStreamWriter<?>> streamWriters() {
        return List.of(newStreamWriter());
    }

    /**
     * Provides a default instance for JSON-P readers and writers.
     * @return json processing with default configuration
     */
    public static JsonpSupport create() {
        return Builder.DEFAULT_INSTANCE;
    }

    /**
     * Create an instance with the provided JSON-P configuration.
     * @param jsonPConfig configuration of the processing library
     * @return a configured JSON-P instance
     */
    public static JsonpSupport create(Map<String, ?> jsonPConfig) {
        return builder().jsonProcessingConfig(jsonPConfig).build();
    }

    /**
     * Create a new JSON-P entity reader.
     *
     * @return JsonEntityReader
     */
    public static JsonpBodyReader reader() {
        return create().newReader();
    }

    /**
     * Create a new JSON-P entity writer.
     *
     * @return JsonEntityReader
     */
    public static JsonpBodyWriter writer() {
        return create().newWriter();
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
     * Fluent-API builder for {@link JsonpSupport}.
     */
    public static class Builder implements io.helidon.common.Builder<JsonpSupport> {
        private static final JsonpSupport DEFAULT_INSTANCE = new JsonpSupport(readerFactory(null), writerFactory(null));

        private JsonWriterFactory jsonWriterFactory;
        private JsonReaderFactory jsonReaderFactory;
        private Map<String, ?> jsonPConfig;

        @Override
        public JsonpSupport build() {
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
