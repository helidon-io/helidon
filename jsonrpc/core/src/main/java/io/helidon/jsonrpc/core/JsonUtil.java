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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.helidon.common.LazyValue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

/**
 * Provides JSONP to/from JSONB conversions. Not efficient, but simple and portable
 * for now. A more efficient implementation should avoid serialization.
 */
public class JsonUtil {

    private static final LazyValue<Jsonb> JSONB = LazyValue.create(JsonbBuilder::create);

    private JsonUtil() {
    }

    /**
     * Convert a JSONB object into a {@link JsonValue}.
     *
     * @param object the object
     * @return the JSON value
     */
    public static JsonObject jsonbToJsonp(Object object) {
        String serialized = JSONB.get().toJson(object);
        InputStream stream = new ByteArrayInputStream(serialized.getBytes(StandardCharsets.UTF_8));
        try (JsonReader reader = Json.createReader(stream)) {
            return reader.readValue().asJsonObject();
        }
    }

    /**
     * Convert a JSON object into a JSONB object.
     *
     * @param object the JSON object
     * @param type   the JSONB type
     * @param <T>    the type of the instance
     * @return the JSONB instance
     */
    public static <T> T jsonpToJsonb(JsonObject object, Class<T> type) {
        String serialized = object.toString();
        return JSONB.get().fromJson(serialized, type);
    }
}
