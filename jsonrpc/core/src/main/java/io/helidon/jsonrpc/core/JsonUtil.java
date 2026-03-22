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
import io.helidon.json.JsonParser;
import io.helidon.json.JsonValue;
import io.helidon.json.binding.JsonBinding;

/**
 * Provides conversions between JSON values and Java objects.
 */
public class JsonUtil {

    static final LazyValue<JsonBinding> JSON = LazyValue.create(JsonBinding::create);

    private JsonUtil() {
    }

    /**
     * Convert a Java object into a JSON value.
     *
     * @param object the object
     * @return the JSON value
     */
    public static JsonValue objectToJson(Object object) {
        Objects.requireNonNull(object, "json object is null");
        String serialized = JSON.get().serialize(object);
        return JsonParser.create(serialized).readJsonValue();
    }

    /**
     * Convert a JSON value into a Java object.
     *
     * @param value the JSON value
     * @param type  the target type
     * @param <T>    the type of the instance
     * @return the Java instance
     */
    public static <T> T jsonToObject(JsonValue value, Class<T> type) {
        Objects.requireNonNull(value, "json value is null");
        Objects.requireNonNull(type, "type is null");
        return JSON.get().deserialize(value, type);
    }
}
