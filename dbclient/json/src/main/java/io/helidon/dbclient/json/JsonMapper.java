/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.dbclient.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.helidon.dbclient.DbMapper;
import io.helidon.dbclient.DbRow;
import io.helidon.json.JsonBoolean;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonNumber;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;
import io.helidon.json.JsonValue;

/**
 * Helidon JSON mapper.
 */
public final class JsonMapper implements DbMapper<JsonObject> {

    private JsonMapper() {
    }

    /**
     * Create a new mapper that can map {@link JsonObject} to DB parameters and {@link DbRow} to a {@link JsonObject}.
     *
     * @return a new mapper
     */
    public static JsonMapper create() {
        return new JsonMapper();
    }

    @Override
    public JsonObject read(DbRow row) {
        JsonObject.Builder builder = JsonObject.builder();
        row.forEach(column -> builder.set(column.name(), toJsonValue(column.asOptional().orElse(null))));
        return builder.build();
    }

    @Override
    public Map<String, Object> toNamedParameters(JsonObject value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.keysAsStrings().forEach(name -> result.put(name, toObject(value.value(name, JsonNull.instance()))));
        return result;
    }

    @Override
    public List<Object> toIndexedParameters(JsonObject value) {
        List<Object> result = new ArrayList<>();
        value.keysAsStrings().forEach(name -> result.add(toObject(value.value(name, JsonNull.instance()))));
        return result;
    }

    private static JsonValue toJsonValue(Object value) {
        if (value == null) {
            return JsonNull.instance();
        }
        if (value instanceof JsonValue jsonValue) {
            return jsonValue;
        }
        if (value instanceof String string) {
            return JsonString.create(string);
        }
        if (value instanceof Boolean bool) {
            return JsonBoolean.create(bool);
        }
        if (value instanceof BigDecimal bigDecimal) {
            return JsonNumber.create(bigDecimal);
        }
        if (value instanceof BigInteger bigInteger) {
            return JsonNumber.create(new BigDecimal(bigInteger));
        }
        if (value instanceof Float || value instanceof Double) {
            return JsonNumber.create(((Number) value).doubleValue());
        }
        if (value instanceof Number number) {
            return JsonNumber.create(number.longValue());
        }
        return JsonString.create(String.valueOf(value));
    }

    private static Object toObject(JsonValue json) {
        return switch (json.type()) {
        case STRING -> json.asString().value();
        case NUMBER -> json.asNumber().bigDecimalValue();
        case BOOLEAN -> json.asBoolean().value();
        case NULL -> null;
        case OBJECT -> json.asObject();
        case ARRAY -> json.asArray();
        default -> throw new IllegalStateException("Unknown JSON value type: " + json.type());
        };
    }
}
