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
package io.helidon.webserver.jsonrpc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.helidon.common.LazyValue;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

/**
 * Provides JSONP <-> JSONB conversions. Not efficient, but simple and portable
 * for now.
 */
class JsonUtils {

    private static final LazyValue<Jsonb> JSONB = LazyValue.create(JsonbBuilder::create);

    private JsonUtils() {
    }

    static JsonValue jsonbToJsonp(Object object) throws Exception {
        String serialized = JSONB.get().toJson(object);
        InputStream stream = new ByteArrayInputStream(serialized.getBytes(StandardCharsets.UTF_8));
        try (JsonReader reader = Json.createReader(stream)) {
            return reader.readValue();
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T jsonpToJsonb(JsonValue value, Class<T> type) throws Exception {
        if (type == JsonObject.class) {
            return (T) value.asJsonObject();
        } else if (type == JsonArray.class) {
            return (T) value.asJsonArray();
        } else if (type == JsonStructure.class) {
            return (T) value;
        } else {
            return JSONB.get().fromJson(value.toString(), type);
        }
    }
}
