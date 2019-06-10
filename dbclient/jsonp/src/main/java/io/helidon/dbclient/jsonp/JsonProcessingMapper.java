/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.dbclient.jsonp;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import io.helidon.dbclient.DbMapper;
import io.helidon.dbclient.DbRow;

/**
 * Json processing mapper.
 */
public final class JsonProcessingMapper implements DbMapper<JsonObject> {
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Collections.emptyMap());
    private static final Map<Class<?>, DbJsonWriter> JSON_WRITERS = new IdentityHashMap<>();
    private static final DbJsonWriter NUMBER_WRITER = (builder, name, value) -> builder.add(name, ((Number) value).longValue());
    private static final DbJsonWriter OBJECT_WRITER = (builder, name, value) -> builder.add(name, String.valueOf(value));

    static {
        JSON_WRITERS.put(Integer.class, (builder, name, value) -> builder.add(name, (Integer) value));
        JSON_WRITERS.put(Short.class, (builder, name, value) -> builder.add(name, (Short) value));
        JSON_WRITERS.put(Byte.class, (builder, name, value) -> builder.add(name, (Byte) value));
        JSON_WRITERS.put(AtomicInteger.class, (builder, name, value) -> builder.add(name, ((AtomicInteger) value).get()));
        JSON_WRITERS.put(Float.class, (builder, name, value) -> builder.add(name, (Float) value));
        JSON_WRITERS.put(Double.class, (builder, name, value) -> builder.add(name, (Double) value));
        JSON_WRITERS.put(BigInteger.class, (builder, name, value) -> builder.add(name, (BigInteger) value));
        JSON_WRITERS.put(BigDecimal.class, (builder, name, value) -> builder.add(name, (BigDecimal) value));
        JSON_WRITERS.put(Long.class, (builder, name, value) -> builder.add(name, (Long) value));
        JSON_WRITERS.put(String.class, (builder, name, value) -> builder.add(name, (String) value));
        JSON_WRITERS.put(Boolean.class, (builder, name, value) -> builder.add(name, (Boolean) value));
        // primitives
        JSON_WRITERS.put(int.class, JSON_WRITERS.get(Integer.class));
        JSON_WRITERS.put(short.class, JSON_WRITERS.get(Short.class));
        JSON_WRITERS.put(byte.class, JSON_WRITERS.get(Byte.class));
        JSON_WRITERS.put(float.class, JSON_WRITERS.get(Float.class));
        JSON_WRITERS.put(double.class, JSON_WRITERS.get(Double.class));
        JSON_WRITERS.put(long.class, JSON_WRITERS.get(Long.class));
        JSON_WRITERS.put(boolean.class, JSON_WRITERS.get(Boolean.class));
    }

    private JsonProcessingMapper() {
    }

    /**
     * Create a new mapper that can map {@link javax.json.JsonObject} to DB parameters and {@link io.helidon.dbclient.DbRow}
     * to a {@link javax.json.JsonObject}.
     *
     * @return a new mapper
     */
    public static JsonProcessingMapper create() {
        return new JsonProcessingMapper();
    }

    /**
     * Get a JSON-P representation of this row.
     *
     * @return json object containing column name to column value.
     */
    @Override
    public JsonObject read(DbRow row) {
        JsonObjectBuilder objectBuilder = JSON.createObjectBuilder();
        row.forEach(dbCol -> toJson(objectBuilder, dbCol.name(), dbCol.javaType(), dbCol.value()));
        return objectBuilder.build();
    }

    @Override
    public Map<String, Object> toNamedParameters(JsonObject value) {
        Map<String, Object> result = new HashMap<>();
        value.forEach((name, json) -> result.put(name, toObject(name, json, value)));

        return result;
    }

    @Override
    public List<Object> toIndexedParameters(JsonObject value) {
        // in case the underlying map is linked, we can do this
        // obviously the number of parameters must match the number in statement, so most likely this is
        // going to fail
        List<Object> result = new LinkedList<>();
        value.forEach((name, json) -> result.add(toObject(name, json, value)));
        return result;
    }

    private void toJson(JsonObjectBuilder objectBuilder, String name, Class<?> valueClass, Object value) {
        if (value == null) {
            objectBuilder.addNull(name);
        }
        getJsonWriter(valueClass).write(objectBuilder, name, value);
    }

    private DbJsonWriter getJsonWriter(Class<?> valueClass) {
        DbJsonWriter writer = JSON_WRITERS.get(valueClass);
        if (null != writer) {
            return writer;
        }
        if (Number.class.isAssignableFrom(valueClass)) {
            return NUMBER_WRITER;
        }
        return OBJECT_WRITER;
    }

    private Object toObject(String name, JsonValue json, JsonObject jsonObject) {
        if (json == null) {
            return null;
        }
        switch (json.getValueType()) {
        case STRING:
            return jsonObject.getString(name);
        case NUMBER:
            return jsonObject.getJsonNumber(name).numberValue();
        case TRUE:
            return Boolean.TRUE;
        case FALSE:
            return Boolean.FALSE;
        case NULL:
            return null;
        case OBJECT:
            return jsonObject.getJsonObject(name);
        case ARRAY:
            return jsonObject.getJsonArray(name);
        default:
            throw new IllegalStateException(String.format("Unknown JSON value type: %s", json.getValueType()));
        }
    }

    @FunctionalInterface
    private interface DbJsonWriter {
        void write(JsonObjectBuilder objectBuilder, String name, Object value);
    }
}
