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

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.dbclient.DbMapper;
import io.helidon.dbclient.DbRow;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonBoolean;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonNumber;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonString;
import io.helidon.json.JsonValue;

/**
 * Helidon JSON mapper.
 */
public final class HelidonJsonMapper implements DbMapper<JsonObject> {
    private static final Map<Class<?>, DbJsonWriter> JSON_WRITERS = new IdentityHashMap<>();
    private static final DbJsonWriter JSON_VALUE_WRITER = (builder, name, value) -> builder.set(name, (JsonValue) value);
    private static final DbJsonWriter MAP_WRITER = (builder, name, value) -> builder.set(name, toJsonObject((Map<?, ?>) value));
    private static final DbJsonWriter ITERABLE_WRITER =
            (builder, name, value) -> builder.set(name, toJsonArray((Iterable<?>) value));
    private static final DbJsonWriter ARRAY_WRITER = (builder, name, value) -> builder.set(name, toJsonArray(value));
    private static final DbJsonWriter NUMBER_WRITER =
            (builder, name, value) -> builder.set(name, toJsonNumberValue((Number) value));
    private static final DbJsonWriter OBJECT_WRITER = (builder, name, value) -> builder.set(name, String.valueOf(value));

    static {
        JSON_WRITERS.put(Integer.class, (builder, name, value) -> builder.set(name, (Integer) value));
        JSON_WRITERS.put(Short.class, (builder, name, value) -> builder.set(name, (Short) value));
        JSON_WRITERS.put(Byte.class, (builder, name, value) -> builder.set(name, (Byte) value));
        JSON_WRITERS.put(AtomicInteger.class, (builder, name, value) -> builder.set(name, ((AtomicInteger) value).get()));
        JSON_WRITERS.put(Float.class, (builder, name, value) -> builder.set(name, toJsonNumberValue((Number) value)));
        JSON_WRITERS.put(Double.class, (builder, name, value) -> builder.set(name, toJsonNumberValue((Number) value)));
        JSON_WRITERS.put(BigInteger.class, (builder, name, value) -> builder.set(name, new BigDecimal((BigInteger) value)));
        JSON_WRITERS.put(BigDecimal.class, (builder, name, value) -> builder.set(name, (BigDecimal) value));
        JSON_WRITERS.put(Long.class, (builder, name, value) -> builder.set(name, (Long) value));
        JSON_WRITERS.put(String.class, (builder, name, value) -> builder.set(name, (String) value));
        JSON_WRITERS.put(Boolean.class, (builder, name, value) -> builder.set(name, (Boolean) value));
        // primitives
        JSON_WRITERS.put(int.class, JSON_WRITERS.get(Integer.class));
        JSON_WRITERS.put(short.class, JSON_WRITERS.get(Short.class));
        JSON_WRITERS.put(byte.class, JSON_WRITERS.get(Byte.class));
        JSON_WRITERS.put(float.class, JSON_WRITERS.get(Float.class));
        JSON_WRITERS.put(double.class, JSON_WRITERS.get(Double.class));
        JSON_WRITERS.put(long.class, JSON_WRITERS.get(Long.class));
        JSON_WRITERS.put(boolean.class, JSON_WRITERS.get(Boolean.class));
    }

    private HelidonJsonMapper() {
    }

    /**
     * Create a new mapper that can map {@link io.helidon.json.JsonObject} to DB parameters and {@link DbRow}
     * to a {@link io.helidon.json.JsonObject}.
     *
     * @return a new mapper
     */
    public static HelidonJsonMapper create() {
        return new HelidonJsonMapper();
    }

    /**
     * Get a Helidon JSON representation of this row.
     *
     * @return json object containing column name to column value
     */
    @Override
    public JsonObject read(DbRow row) {
        JsonObject.Builder objectBuilder = JsonObject.builder();
        row.forEach(dbCol -> toJson(objectBuilder, dbCol.name(), dbCol.javaType(), dbCol.get()));
        return objectBuilder.build();
    }

    @Override
    public Map<String, Object> toNamedParameters(JsonObject value) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String name : value.keysAsStrings()) {
            result.put(name, toObject(value.value(name).orElse(null)));
        }
        return result;
    }

    @Override
    public List<Object> toIndexedParameters(JsonObject value) {
        // in case the underlying map is linked, we can do this
        // obviously the number of parameters must match the number in statement, so most likely this is
        // going to fail
        List<Object> result = new LinkedList<>();
        for (String name : value.keysAsStrings()) {
            result.add(toObject(value.value(name).orElse(null)));
        }
        return result;
    }

    private Object toObject(JsonValue jsonValue) {
        if (jsonValue == null) {
            return null;
        }
        return switch (jsonValue.type()) {
            case STRING -> jsonValue.asString().value();
            case NUMBER -> jsonValue.asNumber().bigDecimalValue();
            case BOOLEAN -> jsonValue.asBoolean().value();
            case NULL -> null;
            case OBJECT -> toMap(jsonValue.asObject());
            case ARRAY -> toList(jsonValue.asArray());
            default -> throw new IllegalStateException(String.format("Unknown JSON value type: %s", jsonValue.type()));
        };
    }

    private Map<String, Object> toMap(JsonObject jsonObject) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        for (String key : jsonObject.keysAsStrings()) {
            mapped.put(key, toObject(jsonObject.value(key).orElse(null)));
        }
        return mapped;
    }

    private List<Object> toList(JsonArray jsonArray) {
        List<JsonValue> values = jsonArray.values();
        List<Object> mapped = new ArrayList<>(values.size());
        for (JsonValue value : values) {
            mapped.add(toObject(value));
        }
        return mapped;
    }

    private void toJson(JsonObject.Builder objectBuilder, String name, Class<?> valueClass, Object value) {
        if (value == null) {
            objectBuilder.setNull(name);
            return;
        }
        getJsonWriter(valueClass).write(objectBuilder, name, value);
    }

    private DbJsonWriter getJsonWriter(Class<?> valueClass) {
        DbJsonWriter writer = JSON_WRITERS.get(valueClass);
        if (writer != null) {
            return writer;
        }
        if (JsonValue.class.isAssignableFrom(valueClass)) {
            return JSON_VALUE_WRITER;
        }
        if (Map.class.isAssignableFrom(valueClass)) {
            return MAP_WRITER;
        }
        if (Iterable.class.isAssignableFrom(valueClass)) {
            return ITERABLE_WRITER;
        }
        if (valueClass.isArray()) {
            return ARRAY_WRITER;
        }
        if (Number.class.isAssignableFrom(valueClass)) {
            return NUMBER_WRITER;
        }
        return OBJECT_WRITER;
    }

    private static JsonValue toJsonValue(Object value) {
        if (value == null) {
            return JsonNull.instance();
        }
        if (value instanceof JsonValue jsonValue) {
            return jsonValue;
        }
        if (value instanceof Map<?, ?> valueMap) {
            return toJsonObject(valueMap);
        }
        if (value instanceof Iterable<?> iterable) {
            return toJsonArray(iterable);
        }
        if (value.getClass().isArray()) {
            return toJsonArray(value);
        }
        if (value instanceof CharSequence charSequence) {
            return JsonString.create(charSequence.toString());
        }
        if (value instanceof Character character) {
            return JsonString.create(String.valueOf(character));
        }
        if (value instanceof Boolean booleanValue) {
            return JsonBoolean.create(booleanValue);
        }
        if (value instanceof Number numberValue) {
            return toJsonNumberValue(numberValue);
        }
        return JsonString.create(String.valueOf(value));
    }

    private static JsonObject toJsonObject(Map<?, ?> value) {
        JsonObject.Builder builder = JsonObject.builder();
        value.forEach((key, nestedValue) -> builder.set(String.valueOf(key), toJsonValue(nestedValue)));
        return builder.build();
    }

    private static JsonArray toJsonArray(Iterable<?> value) {
        List<JsonValue> jsonValues = new LinkedList<>();
        value.forEach(item -> jsonValues.add(toJsonValue(item)));
        return JsonArray.create(jsonValues);
    }

    private static JsonArray toJsonArray(Object value) {
        int length = Array.getLength(value);
        List<JsonValue> jsonValues = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            jsonValues.add(toJsonValue(Array.get(value, i)));
        }
        return JsonArray.create(jsonValues);
    }

    private static JsonValue toJsonNumberValue(Number value) {
        if (value instanceof BigDecimal bigDecimal) {
            return JsonNumber.create(bigDecimal);
        }
        if (value instanceof BigInteger bigInteger) {
            return JsonNumber.create(new BigDecimal(bigInteger));
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return JsonNumber.create(value.longValue());
        }
        if (value instanceof Float || value instanceof Double) {
            double doubleValue = value.doubleValue();
            if (Double.isFinite(doubleValue)) {
                return JsonNumber.create(doubleValue);
            }
            return JsonString.create(String.valueOf(value));
        }
        try {
            return JsonNumber.create(new BigDecimal(value.toString()));
        } catch (NumberFormatException e) {
            return JsonString.create(String.valueOf(value));
        }
    }

    @FunctionalInterface
    private interface DbJsonWriter {
        void write(JsonObject.Builder objectBuilder, String name, Object value);
    }
}
