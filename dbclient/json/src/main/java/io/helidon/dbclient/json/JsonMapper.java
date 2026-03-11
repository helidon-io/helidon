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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.dbclient.DbMapper;
import io.helidon.dbclient.DbRow;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonValue;

/**
 * Helidon JSON mapper.
 */
public final class JsonMapper implements DbMapper<JsonObject> {
    private static final Map<Class<?>, DbJsonWriter> JSON_WRITERS = new IdentityHashMap<>();
    private static final DbJsonWriter NUMBER_WRITER = (builder, name, value) ->
            builder.set(name, new BigDecimal(String.valueOf(value)));
    private static final DbJsonWriter OBJECT_WRITER = (builder, name, value) -> builder.set(name, String.valueOf(value));

    static {
        JSON_WRITERS.put(JsonValue.class, (builder, name, value) -> builder.set(name, (JsonValue) value));
        JSON_WRITERS.put(Integer.class, (builder, name, value) -> builder.set(name, (Integer) value));
        JSON_WRITERS.put(Short.class, (builder, name, value) -> builder.set(name, ((Short) value).intValue()));
        JSON_WRITERS.put(Byte.class, (builder, name, value) -> builder.set(name, ((Byte) value).intValue()));
        JSON_WRITERS.put(AtomicInteger.class, (builder, name, value) -> builder.set(name, ((AtomicInteger) value).get()));
        JSON_WRITERS.put(Float.class, (builder, name, value) -> builder.set(name, (Float) value));
        JSON_WRITERS.put(Double.class, (builder, name, value) -> builder.set(name, (Double) value));
        JSON_WRITERS.put(BigInteger.class, (builder, name, value) -> builder.set(name, new BigDecimal((BigInteger) value)));
        JSON_WRITERS.put(BigDecimal.class, (builder, name, value) -> builder.set(name, (BigDecimal) value));
        JSON_WRITERS.put(Long.class, (builder, name, value) -> builder.set(name, (Long) value));
        JSON_WRITERS.put(String.class, (builder, name, value) -> builder.set(name, (String) value));
        JSON_WRITERS.put(Boolean.class, (builder, name, value) -> builder.set(name, (Boolean) value));
        JSON_WRITERS.put(int.class, JSON_WRITERS.get(Integer.class));
        JSON_WRITERS.put(short.class, JSON_WRITERS.get(Short.class));
        JSON_WRITERS.put(byte.class, JSON_WRITERS.get(Byte.class));
        JSON_WRITERS.put(float.class, JSON_WRITERS.get(Float.class));
        JSON_WRITERS.put(double.class, JSON_WRITERS.get(Double.class));
        JSON_WRITERS.put(long.class, JSON_WRITERS.get(Long.class));
        JSON_WRITERS.put(boolean.class, JSON_WRITERS.get(Boolean.class));
    }

    private JsonMapper() {
    }

    /**
     * Create a new mapper that can map {@link JsonObject} to DB parameters and {@link DbRow}
     * to a {@link JsonObject}.
     *
     * @return a new mapper
     */
    public static JsonMapper create() {
        return new JsonMapper();
    }

    @Override
    public JsonObject read(DbRow row) {
        JsonObject.Builder builder = JsonObject.builder();
        row.forEach(dbCol -> toJson(builder, dbCol.name(), dbCol.javaType(), dbCol.get()));
        return builder.build();
    }

    @Override
    public Map<String, Object> toNamedParameters(JsonObject value) {
        Map<String, Object> result = new HashMap<>();
        for (String key : value.keysAsStrings()) {
            result.put(key, toObject(value.value(key, JsonNull.instance())));
        }
        return result;
    }

    @Override
    public List<Object> toIndexedParameters(JsonObject value) {
        List<Object> result = new LinkedList<>();
        for (String key : value.keysAsStrings()) {
            result.add(toObject(value.value(key, JsonNull.instance())));
        }
        return result;
    }

    private void toJson(JsonObject.Builder builder, String name, Class<?> valueClass, Object value) {
        if (value == null) {
            builder.setNull(name);
            return;
        }
        getJsonWriter(valueClass, value).write(builder, name, value);
    }

    private DbJsonWriter getJsonWriter(Class<?> valueClass, Object value) {
        DbJsonWriter writer = JSON_WRITERS.get(valueClass);
        if (writer != null) {
            return writer;
        }
        if (value instanceof JsonValue) {
            return JSON_WRITERS.get(JsonValue.class);
        }
        if (Number.class.isAssignableFrom(valueClass)) {
            return NUMBER_WRITER;
        }
        return OBJECT_WRITER;
    }

    private Object toObject(JsonValue json) {
        return switch (json.type()) {
        case STRING -> json.asString().value();
        case NUMBER -> json.asNumber().bigDecimalValue();
        case BOOLEAN -> json.asBoolean().value();
        case NULL -> null;
        case OBJECT -> json.asObject();
        case ARRAY -> json.asArray();
        case UNKNOWN -> throw new IllegalStateException("Unknown JSON value type: " + json.type());
        };
    }

    @FunctionalInterface
    private interface DbJsonWriter {
        void write(JsonObject.Builder builder, String name, Object value);
    }
}
