/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import java.util.Objects;

import io.helidon.common.LazyValue;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.json.JsonValue;
import io.helidon.json.binding.JsonBinding;

/**
 * Provides JSON binding helpers used by JSON-RPC types.
 */
public class JsonUtil {

    static final LazyValue<JsonBinding> JSON_BINDING = LazyValue.create(JsonBinding::create);

    private JsonUtil() {
    }

    /**
     * Convert a bound Java object into a JSON object.
     *
     * @param object the object
     * @return the JSON object
     */
    public static JsonObject toJsonObject(Object object) {
        Objects.requireNonNull(object, "object is null");
        byte[] serialized = JSON_BINDING.get().serializeToBytes(object);
        return JsonParser.create(serialized).readJsonObject();
    }

    /**
     * Convert a JSON value into a Java object.
     *
     * @param value the JSON value
     * @param type  the target type
     * @param <T>   the type of the instance
     * @return the Java instance
     */
    public static <T> T fromJson(JsonValue value, Class<T> type) {
        Objects.requireNonNull(value, "json value is null");
        Objects.requireNonNull(type, "type is null");
        return JSON_BINDING.get().deserialize(value, type);
    }

    /**
     * Convert a JSON object into a Java object.
     *
     * @param object the JSON object
     * @param type   the target type
     * @param <T>    the type of the instance
     * @return the Java instance
     */
    public static <T> T fromJson(JsonObject object, Class<T> type) {
        return fromJson((JsonValue) object, type);
    }
}
