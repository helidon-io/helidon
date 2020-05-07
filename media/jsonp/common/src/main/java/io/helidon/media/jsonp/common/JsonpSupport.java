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
package io.helidon.media.jsonp.common;

import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonReaderFactory;
import javax.json.JsonWriterFactory;

import io.helidon.media.common.MediaSupport;
import io.helidon.media.common.MessageBodyReaderContext;
import io.helidon.media.common.MessageBodyWriterContext;

/**
 * Support for JSON Processing integration.
 *
 * For usage examples navigate to {@link MediaSupport}
 */
public final class JsonpSupport implements MediaSupport {

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

    @Override
    public void register(MessageBodyReaderContext readerContext, MessageBodyWriterContext writerContext) {
        readerContext.registerReader(newReader());
        writerContext.registerWriter(newWriter());
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
