/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.jsonrpc.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import io.helidon.common.LazyValue;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

/**
 * Provides JSONP to/from JSONB conversions. Not efficient, but simple and portable
 * for now. A more efficient implementation should avoid serialization.
 */
public class JsonUtil {

    /**
     * Global JSON builder factory used by JSON-RPC implementation.
     */
    public static final JsonBuilderFactory JSON_BUILDER_FACTORY = Json.createBuilderFactory(Map.of());

    static final LazyValue<Jsonb> JSONB_BUILDER = LazyValue.create(JsonbBuilder::create);

    static final LazyValue<JsonReaderFactory> JSON_READER_FACTORY
            = LazyValue.create(() -> Json.createReaderFactory(Map.of()));

    static final LazyValue<JsonWriterFactory> JSON_WRITER_FACTORY
            = LazyValue.create(() -> Json.createWriterFactory(Map.of()));

    private JsonUtil() {
    }

    /**
     * Convert a JSONB instance into a JSONP instance.
     *
     * @param object the object
     * @return the JSON value
     */
    public static JsonObject jsonbToJsonp(Object object) {
        Objects.requireNonNull(object, "json object is null");
        String serialized = JSONB_BUILDER.get().toJson(object);
        InputStream stream = new ByteArrayInputStream(serialized.getBytes(StandardCharsets.UTF_8));
        try (JsonReader reader = JSON_READER_FACTORY.get().createReader(stream)) {
            return reader.readValue().asJsonObject();
        }
    }

    /**
     * Convert a JSONP instance into a JSONB instance.
     *
     * @param object the JSON object
     * @param type   the JSONB type
     * @param <T>    the type of the instance
     * @return the JSONB instance
     */
    public static <T> T jsonpToJsonb(JsonObject object, Class<T> type) {
        Objects.requireNonNull(object, "json object is null");
        Objects.requireNonNull(type, "type is null");
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (JsonWriter writer = JSON_WRITER_FACTORY.get().createWriter(os, StandardCharsets.UTF_8)) {
            writer.writeObject(object);
        }
        ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
        return JSONB_BUILDER.get().fromJson(is, type);
    }
}
