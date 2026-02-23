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

package io.helidon.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Represents a JSON object value containing key-value pairs.
 */
public final class JsonObject extends JsonValue {

    static final JsonObject EMPTY_OBJECT = JsonObject.create(List.of());

    private final List<Pair> pairs;
    private final LinkedHashMap<String, JsonValue> content;
    private final boolean resolved;

    private JsonObject(List<Pair> pairs) {
        this.pairs = pairs;
        this.content = new LinkedHashMap<>();
        this.resolved = false;
    }

    private JsonObject(LinkedHashMap<String, JsonValue> content) {
        this.content = content;
        this.pairs = new ArrayList<>();
        this.resolved = true;
    }

    /**
     * Create a new JsonObject.Builder for fluent construction of JsonObject instances.
     *
     * @return a new JsonObject.Builder
     */
    public static JsonObject.Builder builder() {
        return new Builder();
    }

    /**
     * Create a JsonObject from a map of string keys to JsonValue instances.
     *
     * @param content the map containing the object properties
     * @return a new JsonObject
     */
    public static JsonObject create(Map<String, JsonValue> content) {
        return new JsonObject(new LinkedHashMap<>(content));
    }

    static JsonObject create(List<Pair> pairs) {
        return new JsonObject(pairs);
    }

    @Override
    byte jsonStartChar() {
        return '{';
    }

    /**
     * Checks if this object contains the specified key.
     *
     * @param key the key to check for
     * @return true if the object contains the key, false otherwise
     */
    public boolean containsKey(String key) {
        ensureResolvedKeys();
        return content.containsKey(key);
    }

    /**
     * Return the value associated with the specified key, or the default value if the key is not present.
     *
     * @param key the key to look up
     * @param defaultValue the value to return if the key is not present
     * @return the value associated with the key, or the default value
     */
    public JsonValue value(String key, JsonValue defaultValue) {
        ensureResolvedKeys();
        JsonValue jsonValue = content.get(key);
        if (jsonValue == null) {
            return defaultValue;
        }
        return jsonValue;
    }

    /**
     * Return the boolean value associated with the specified key as an Optional.
     *
     * @param key the key to look up
     * @return an Optional containing the boolean value, or empty if the key is not present
     */
    public Optional<Boolean> booleanValue(String key) {
        ensureResolvedKeys();
        JsonValue jsonValue = content.get(key);
        if (jsonValue == null) {
            return Optional.empty();
        }
        return Optional.of(jsonValue.asBoolean().value());
    }

    /**
     * Return the boolean value associated with the specified key, or the default value if the key is not present.
     *
     * @param key the key to look up
     * @param defaultValue the value to return if the key is not present
     * @return the boolean value associated with the key, or the default value
     */
    public boolean booleanValue(String key, boolean defaultValue) {
        ensureResolvedKeys();
        JsonValue jsonValue = content.get(key);
        if (jsonValue == null) {
            return defaultValue;
        }
        return jsonValue.asBoolean().value();
    }

    /**
     * Return the JsonObject value associated with the specified key as an Optional.
     *
     * @param key the key to look up
     * @return an Optional containing the JsonObject value, or empty if the key is not present
     */
    public Optional<JsonObject> objectValue(String key) {
        ensureResolvedKeys();
        JsonValue jsonValue = content.get(key);
        if (jsonValue == null) {
            return Optional.empty();
        }
        return Optional.of(jsonValue.asObject());
    }

    /**
     * Return the JsonObject value associated with the specified key, or the default value if the key is not present.
     *
     * @param key the key to look up
     * @param defaultValue the value to return if the key is not present
     * @return the JsonObject value associated with the key, or the default value
     */
    public JsonObject objectValue(String key, JsonObject defaultValue) {
        ensureResolvedKeys();
        JsonValue jsonValue = content.get(key);
        if (jsonValue == null) {
            return defaultValue;
        }
        return jsonValue.asObject();
    }

    /**
     * Return the string value associated with the specified key as an Optional.
     *
     * @param key the key to look up
     * @return an Optional containing the string value, or empty if the key is not present
     */
    public Optional<String> stringValue(String key) {
        ensureResolvedKeys();
        JsonValue jsonValue = content.get(key);
        if (jsonValue == null) {
            return Optional.empty();
        }
        return Optional.of(jsonValue.asString().value());
    }

    /**
     * Return the string value associated with the specified key, or the default value if the key is not present.
     *
     * @param key the key to look up
     * @param defaultValue the value to return if the key is not present
     * @return the string value associated with the key, or the default value
     */
    public String stringValue(String key, String defaultValue) {
        ensureResolvedKeys();
        JsonValue jsonValue = content.get(key);
        if (jsonValue == null) {
            return defaultValue;
        }
        return jsonValue.asString().value();
    }

    /**
     * Return the integer value associated with the specified key as an Optional.
     *
     * @param key the key to look up
     * @return an Optional containing the integer value, or empty if the key is not present
     */
    public Optional<Integer> intValue(String key) {
        ensureResolvedKeys();
        JsonValue jsonValue = content.get(key);
        if (jsonValue == null) {
            return Optional.empty();
        }
        return Optional.of(jsonValue.asNumber().intValue());
    }

    /**
     * Return the integer value associated with the specified key, or the default value if the key is not present.
     *
     * @param key the key to look up
     * @param defaultValue the value to return if the key is not present
     * @return the integer value associated with the key, or the default value
     */
    public int intValue(String key, int defaultValue) {
        ensureResolvedKeys();
        JsonValue jsonValue = content.get(key);
        if (jsonValue == null) {
            return defaultValue;
        }
        return jsonValue.asNumber().intValue();
    }

    /**
     * Return the double value associated with the specified key as an Optional.
     *
     * @param key the key to look up
     * @return an Optional containing the double value, or empty if the key is not present
     */
    public Optional<Double> doubleValue(String key) {
        ensureResolvedKeys();
        JsonValue jsonValue = content.get(key);
        if (jsonValue == null) {
            return Optional.empty();
        }
        return Optional.of(jsonValue.asNumber().doubleValue());
    }

    /**
     * Return the double value associated with the specified key, or the default value if the key is not present.
     *
     * @param key the key to look up
     * @param defaultValue the value to return if the key is not present
     * @return the double value associated with the key, or the default value
     */
    public double doubleValue(String key, double defaultValue) {
        ensureResolvedKeys();
        JsonValue jsonValue = content.get(key);
        if (jsonValue == null) {
            return defaultValue;
        }
        return jsonValue.asNumber().doubleValue();
    }

    /**
     * Return the BigDecimal value associated with the specified key as an Optional.
     *
     * @param key the key to look up
     * @return an Optional containing the BigDecimal value, or empty if the key is not present
     */
    public Optional<BigDecimal> numberValue(String key) {
        ensureResolvedKeys();
        JsonValue jsonValue = content.get(key);
        if (jsonValue == null) {
            return Optional.empty();
        }
        return Optional.of(jsonValue.asNumber().bigDecimalValue());
    }

    /**
     * Return the BigDecimal value associated with the specified key, or the default value if the key is not present.
     *
     * @param key the key to look up
     * @param defaultValue the value to return if the key is not present
     * @return the BigDecimal value associated with the key, or the default value
     */
    public BigDecimal numberValue(String key, BigDecimal defaultValue) {
        ensureResolvedKeys();
        JsonValue jsonValue = content.get(key);
        if (jsonValue == null) {
            return defaultValue;
        }
        return jsonValue.asNumber().bigDecimalValue();
    }

    /**
     * Return the JsonArray value associated with the specified key as an Optional.
     *
     * @param key the key to look up
     * @return an Optional containing the JsonArray value, or empty if the key is not present
     */
    public Optional<JsonArray> arrayValue(String key) {
        ensureResolvedKeys();
        JsonValue jsonValue = content.get(key);
        if (jsonValue == null) {
            return Optional.empty();
        }
        return Optional.of(jsonValue.asArray());
    }

    /**
     * Return the JsonArray value associated with the specified key, or the default value if the key is not present.
     *
     * @param key the key to look up
     * @param defaultValue the value to return if the key is not present
     * @return the JsonArray value associated with the key, or the default value
     */
    public JsonArray arrayValue(String key, JsonArray defaultValue) {
        ensureResolvedKeys();
        JsonValue jsonValue = content.get(key);
        if (jsonValue == null) {
            return defaultValue;
        }
        return jsonValue.asArray();
    }

    /**
     * Return a set of all keys in this object as JsonString instances.
     *
     * @return a set of JsonString keys
     */
    public Set<JsonString> keys() {
        if (pairs.isEmpty() && !content.isEmpty()) {
            content.forEach((key, value) -> {
                pairs.add(new Pair(JsonString.create(key), value));
            });
        }
        return pairs.stream().map(Pair::key).collect(Collectors.toSet());
    }

    /**
     * Return a set of all keys in this object as String instances.
     *
     * @return a set of String keys
     */
    public Set<String> keysAsStrings() {
        ensureResolvedKeys();
        return content.keySet();
    }

    /**
     * Return the number of properties in this object.
     *
     * @return the size of this object
     */
    public int size() {
        return pairs.size();
    }

    @Override
    public JsonValueType type() {
        return JsonValueType.OBJECT;
    }

    @Override
    public void toJson(JsonGenerator generator) {
        ensureResolvedKeys();
        generator.writeObjectStart();
        for (var entry : content.entrySet()) {
            generator.write(entry.getKey(), entry.getValue());
        }
        generator.writeObjectEnd();
    }

    @Override
    public int hashCode() {
        ensureResolvedKeys();
        return Objects.hash(content);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof JsonObject that)) {
            return false;
        }
        if (that == this) {
            return true;
        }

        this.ensureResolvedKeys();
        that.ensureResolvedKeys();

        if (!this.content.keySet().containsAll(that.content.keySet())) {
            return false;
        }
        return this.content.values().containsAll(that.content.values());
    }

    private void ensureResolvedKeys() {
        if (!resolved) {
            for (Pair pair : pairs) {
                content.put(pair.key.resolveValue(), pair.value);
            }
        }
    }

    record Pair(JsonString key, JsonValue value) {
    }

    /**
     * Builder for creating JsonObject instances with a fluent API.
     */
    public static final class Builder implements io.helidon.common.Builder<Builder, JsonObject> {
        private final Map<String, JsonValue> values = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Builds the JsonObject from the current builder state.
         *
         * @return a new JsonObject instance
         */
        @Override
        public JsonObject build() {
            return new JsonObject(new LinkedHashMap<>(values));
        }

        /**
         * Removes the property with the specified key from this builder.
         *
         * @param key the key to remove
         * @return this builder for method chaining
         */
        public Builder unset(String key) {
            Objects.requireNonNull(key, "key cannot be null");
            values.remove(key);
            return this;
        }

        /**
         * Sets a null value for the specified key.
         *
         * @param key the key to set
         * @return this builder for method chaining
         */
        public Builder setNull(String key) {
            values.put(key, JsonNull.instance());
            return this;
        }

        /**
         * Sets a JsonValue for the specified key.
         *
         * @param key the key to set
         * @param value the JsonValue to set
         * @return this builder for method chaining
         */
        public Builder set(String key, JsonValue value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, value);
            return this;
        }

        /**
         * Sets a nested JsonObject for the specified key using a consumer.
         *
         * @param key the key to set
         * @param consumer the consumer that configures the nested builder
         * @return this builder for method chaining
         */
        public Builder set(String key, Consumer<JsonObject.Builder> consumer) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(consumer, "consumer cannot be null");

            JsonObject.Builder builder = JsonObject.builder();
            consumer.accept(builder);
            values.put(key, builder.build());
            return this;
        }

        /**
         * Sets a string value for the specified key.
         *
         * @param key the key to set
         * @param value the string value to set
         * @return this builder for method chaining
         */
        public Builder set(String key, String value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, JsonString.create(value));
            return this;
        }

        /**
         * Sets a boolean value for the specified key.
         *
         * @param key the key to set
         * @param value the boolean value to set
         * @return this builder for method chaining
         */
        public Builder set(String key, boolean value) {
            Objects.requireNonNull(key, "key cannot be null");

            values.put(key, JsonBoolean.create(value));
            return this;
        }

        /**
         * Sets a float value for the specified key.
         *
         * @param key the key to set
         * @param value the float value to set
         * @return this builder for method chaining
         */
        public Builder set(String key, float value) {
            Objects.requireNonNull(key, "key cannot be null");

            return set(key, new BigDecimal(String.valueOf(value)));
        }

        /**
         * Sets a double value for the specified key.
         *
         * @param key the key to set
         * @param value the double value to set
         * @return this builder for method chaining
         */
        public Builder set(String key, double value) {
            Objects.requireNonNull(key, "key cannot be null");

            return set(key, new BigDecimal(String.valueOf(value)));
        }

        /**
         * Sets an int value for the specified key.
         *
         * @param key the key to set
         * @param value the int value to set
         * @return this builder for method chaining
         */
        public Builder set(String key, int value) {
            Objects.requireNonNull(key, "key cannot be null");

            values.put(key, JsonNumber.create(new BigDecimal(value)));
            return this;
        }

        /**
         * Sets a long value for the specified key.
         *
         * @param key the key to set
         * @param value the long value to set
         * @return this builder for method chaining
         */
        public Builder set(String key, long value) {
            Objects.requireNonNull(key, "key cannot be null");

            values.put(key, JsonNumber.create(new BigDecimal(value)));
            return this;
        }

        /**
         * Sets a BigDecimal value for the specified key.
         *
         * @param key the key to set
         * @param value the BigDecimal value to set
         * @return this builder for method chaining
         */
        public Builder set(String key, BigDecimal value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, JsonNumber.create(value));
            return this;
        }

        /**
         * Sets a list of JsonValue instances for the specified key.
         *
         * @param key the key to set
         * @param value the list of JsonValue instances
         * @return this builder for method chaining
         */
        public Builder setValues(String key, List<JsonValue> value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, JsonArray.create(value));
            return this;
        }

        /**
         * Sets a list of strings for the specified key.
         *
         * @param key the key to set
         * @param value the list of strings
         * @return this builder for method chaining
         */
        public Builder setStrings(String key, List<String> value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, JsonArray.createStrings(value));
            return this;
        }

        /**
         * Sets a list of longs for the specified key.
         *
         * @param key the key to set
         * @param value the list of longs
         * @return this builder for method chaining
         */
        public Builder setLongs(String key, List<Long> value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, JsonArray.createNumbers(value.stream()
                                                            .map(BigDecimal::new)
                                                            .toList()));
            return this;
        }

        /**
         * Sets a list of doubles for the specified key.
         *
         * @param key the key to set
         * @param value the list of doubles
         * @return this builder for method chaining
         */
        public Builder setDoubles(String key, List<Double> value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");
            values.put(key, JsonArray.createNumbers(value.stream()
                                                            .map(BigDecimal::new)
                                                            .toList()));
            return this;
        }

        /**
         * Sets a list of BigDecimal instances for the specified key.
         *
         * @param key the key to set
         * @param value the list of BigDecimal instances
         * @return this builder for method chaining
         */
        public Builder setNumbers(String key, List<BigDecimal> value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, JsonArray.createNumbers(value));
            return this;
        }

        /**
         * Sets a list of booleans for the specified key.
         *
         * @param key the key to set
         * @param value the list of booleans
         * @return this builder for method chaining
         */
        public Builder setBooleans(String key, List<Boolean> value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, JsonArray.createBooleans(value));
            return this;
        }
    }
}
