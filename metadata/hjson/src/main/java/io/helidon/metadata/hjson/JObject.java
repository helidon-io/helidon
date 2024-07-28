/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.metadata.hjson;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * JSON Object.
 * <p>
 * A mutable representation of a JSON object.
 */
public final class JObject implements JValue<JObject> {
    private final Map<String, JValue<?>> values;

    private JObject(Map<String, JValue<?>> values) {
        this.values = values;
    }

    /**
     * A new fluent API builder to construct a JSON Object.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create an empty object.
     *
     * @return new empty instance
     */
    public static JObject create() {
        return builder().build();
    }

    @Override
    public JObject value() {
        return this;
    }

    /**
     * Get a value.
     *
     * @param key key under this object
     * @return value of that key, or empty if not present; may return value that represents null
     * @see io.helidon.metadata.hjson.JType
     */
    public Optional<JValue<?>> value(String key) {
        return Optional.ofNullable(values.get(key));
    }

    /**
     * Get a boolean value.
     *
     * @param key key under this object
     * @return boolean value if present
     * @throws io.helidon.metadata.hjson.JException in case the key exists, but is not a {@code boolean}
     */
    public Optional<Boolean> booleanValue(String key) {
        return getValue(key, JType.BOOLEAN);
    }

    /**
     * Get a boolean value with default if not defined.
     *
     * @param key          key under this object
     * @param defaultValue default value to use if the key does not exist
     * @return boolean value, or default value if the key does not exist
     * @throws io.helidon.metadata.hjson.JException in case the key exists, but is not a
     *                                              {@link io.helidon.metadata.hjson.JType#BOOLEAN}
     */
    public boolean booleanValue(String key, boolean defaultValue) {
        return booleanValue(key).orElse(defaultValue);
    }

    /**
     * Get object value. If the value represents {@code null}, returns empty optional.
     *
     * @param key key under this object
     * @return object value if present
     * @throws io.helidon.metadata.hjson.JException in case the key exists, but is not a
     *                                              {@link io.helidon.metadata.hjson.JType#OBJECT}
     */
    public Optional<JObject> objectValue(String key) {
        return getValue(key, JType.OBJECT);
    }

    /**
     * Get string value.
     *
     * @param key key under this object
     * @return string value if present
     * @throws io.helidon.metadata.hjson.JException in case the key exists, but is not a
     *                                              {@link io.helidon.metadata.hjson.JType#STRING}
     */
    public Optional<String> stringValue(String key) {
        return getValue(key, JType.STRING);
    }

    /**
     * Get a string value with default if not defined.
     *
     * @param key          key under this object
     * @param defaultValue default value to use if the key does not exist
     * @return string value, or default value if the key does not exist
     * @throws io.helidon.metadata.hjson.JException in case the key exists, but is not a
     *                                              {@link io.helidon.metadata.hjson.JType#STRING}
     */
    public String stringValue(String key, String defaultValue) {
        return stringValue(key).orElse(defaultValue);
    }

    /**
     * Get int value.
     *
     * @param key key under this object
     * @return int value if present, from {@link java.math.BigDecimal#intValue()}
     * @throws io.helidon.metadata.hjson.JException in case the key exists, but is not a
     *                                              {@link io.helidon.metadata.hjson.JType#NUMBER}
     */
    public Optional<Integer> intValue(String key) {
        return this.<BigDecimal>getValue(key, JType.NUMBER).map(BigDecimal::intValue);
    }

    /**
     * Get an int value with default if not defined.
     *
     * @param key          key under this object
     * @param defaultValue default value to use if the key does not exist
     * @return int value, or default value if the key does not exist
     * @throws io.helidon.metadata.hjson.JException in case the key exists, but is not a
     *                                              {@link io.helidon.metadata.hjson.JType#NUMBER}
     * @see #intValue(String)
     */
    public int intValue(String key, int defaultValue) {
        return intValue(key).orElse(defaultValue);
    }

    /**
     * Get double value.
     *
     * @param key key under this object
     * @return double value if present, from {@link java.math.BigDecimal#doubleValue()}
     * @throws io.helidon.metadata.hjson.JException in case the key exists, but is not a
     *                                              {@link io.helidon.metadata.hjson.JType#NUMBER}
     */
    public Optional<Double> doubleValue(String key) {
        return this.<BigDecimal>getValue(key, JType.NUMBER).map(BigDecimal::doubleValue);
    }

    /**
     * Get a double value with default if not defined (or null).
     *
     * @param key          key under this object
     * @param defaultValue default value to use if the key does not exist
     * @return double value, or default value if the key does not exist
     * @throws io.helidon.metadata.hjson.JException in case the key exists, but is not a
     *                                              {@link io.helidon.metadata.hjson.JType#NUMBER}
     * @see #doubleValue(String)
     */
    public double doubleValue(String key, double defaultValue) {
        return doubleValue(key).orElse(defaultValue);
    }

    /**
     * Get number value.
     *
     * @param key key under this object
     * @return big decimal value if present
     * @throws io.helidon.metadata.hjson.JException in case the key exists, but is not a
     *                                              {@link io.helidon.metadata.hjson.JType#NUMBER}
     */
    public Optional<BigDecimal> numberValue(String key) {
        return this.getValue(key, JType.NUMBER);
    }

    /**
     * Get number value with default if not defined (or null).
     *
     * @param key          key under this object
     * @param defaultValue default value to use if not present or null
     * @return big decimal value
     */
    public BigDecimal numberValue(String key, BigDecimal defaultValue) {
        return numberValue(key).orElse(defaultValue);
    }

    /**
     * Get string array value.
     *
     * @param key key under this object
     * @return string array value, if the key exists
     * @throws io.helidon.metadata.hjson.JException in case the key exists, is an array, but elements are not strings
     * @throws io.helidon.metadata.hjson.JException in case the key exists, but is not an array
     */
    public Optional<List<String>> stringArray(String key) {
        return getTypedList(key, JArray::getStrings);
    }

    /**
     * Get object array value.
     *
     * @param key key under this object
     * @return object array value, if the key exists
     * @throws io.helidon.metadata.hjson.JException in case the key exists, is an array, but elements are not objects
     * @throws io.helidon.metadata.hjson.JException in case the key exists, but is not an array
     */
    public Optional<List<JObject>> objectArray(String key) {
        return getTypedList(key, JArray::getObjects);
    }

    /**
     * Get number array value.
     *
     * @param key key under this object
     * @return number array value, if the key exists
     * @throws io.helidon.metadata.hjson.JException in case the key exists, is an array, but elements are not numbers
     * @throws io.helidon.metadata.hjson.JException in case the key exists, but is not an array
     */
    public Optional<List<BigDecimal>> numberArray(String key) {
        return getTypedList(key, JArray::getNumbers);
    }

    /**
     * Get boolean array value.
     *
     * @param key key under this object
     * @return boolean array value, if the key exists
     * @throws io.helidon.metadata.hjson.JException in case the key exists, is an array, but elements are not booleans
     * @throws io.helidon.metadata.hjson.JException in case the key exists, but is not an array
     */
    public Optional<List<Boolean>> booleanArray(String key) {
        return getTypedList(key, JArray::getBooleans);
    }

    /**
     * Get array value.
     *
     * @param key key under this object
     * @return array value, if the key exists
     * @throws io.helidon.metadata.hjson.JException in case the key exists, but is not an array
     */
    public Optional<JArray> arrayValue(String key) {
        JValue<?> jValue = values.get(key);
        if (jValue == null) {
            return Optional.empty();
        }
        if (jValue.type() != JType.ARRAY) {
            throw new JException(exceptionMessage(key, " array requested, yet this key is not an array, but " + type()));
        }

        return Optional.of((JArray) jValue);
    }

    @Override
    public void write(PrintWriter writer) {
        Objects.requireNonNull(writer);

        writer.write('{');
        AtomicBoolean first = new AtomicBoolean(true);

        values.forEach((key, value) -> {
            writeNext(writer, first);
            writer.write('\"');
            writer.write(key);
            writer.write("\":");
            value.write(writer);
        });

        writer.write('}');
    }

    @Override
    public JType type() {
        return JType.OBJECT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JObject jObject)) {
            return false;
        }
        return Objects.equals(values, jObject.values);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(values);
    }

    @Override
    public String toString() {
        return "{"
                + values
                + '}';
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> getValue(String key, JType type) {
        JValue<?> jValue = values.get(key);
        if (jValue == null || jValue.type() == JType.NULL) {
            return Optional.empty();
        }
        if (jValue.type() != type) {
            throw new JException(exceptionMessage(key, "requested as a " + type
                    + ", but it is of type " + jValue.type()));
        }
        return Optional.of((T) jValue.value());
    }

    private <T> Optional<List<T>> getTypedList(String key, Function<JArray, List<T>> arrayFunction) {
        try {
            return arrayValue(key).map(arrayFunction);
        } catch (JException e) {
            throw new JException(exceptionMessage(key, " failed to get typed array"), e);
        }
    }

    private String exceptionMessage(String key, String message) {
        return "Object key \"" + key + "\": " + message;
    }

    private void writeNext(PrintWriter metaWriter, AtomicBoolean first) {
        if (first.get()) {
            first.set(false);
            return;
        }
        metaWriter.write(',');
    }

    /**
     * Fluent API builder for {@link io.helidon.metadata.hjson.JObject}.
     *
     * @see #build()
     */
    public static class Builder implements io.helidon.common.Builder<Builder, JObject> {
        private final Map<String, JValue<?>> values = new LinkedHashMap<>();

        private Builder() {
        }

        @Override
        public JObject build() {
            return new JObject(new LinkedHashMap<>(values));
        }

        /**
         * Unset an existing value assigned to the key.
         * This method does not care if the key is mapped or not.
         *
         * @param key key to unset
         * @return updated instance (this instance)
         */
        public Builder unset(String key) {
            Objects.requireNonNull(key, "key cannot be null");

            values.remove(key);
            return this;
        }

        /**
         * Set a value.
         *
         * @param key   key to set
         * @param value value to assign to the key
         */
        public void set(String key, JValue<?> value) {
            values.put(key, value);
        }

        /**
         * Set a string value.
         *
         * @param key   key to set
         * @param value value to assign to the key
         * @return updated instance (this instance)
         */
        public Builder set(String key, String value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, JValues.StringValue.create(value));
            return this;
        }

        /**
         * Set a boolean value.
         *
         * @param key   key to set
         * @param value value to assign to the key
         * @return updated instance (this instance)
         */
        public Builder set(String key, boolean value) {
            Objects.requireNonNull(key, "key cannot be null");

            values.put(key, JValues.BooleanValue.create(value));
            return this;
        }

        /**
         * Set a double value.
         *
         * @param key   key to set
         * @param value value to assign to the key
         * @return updated instance (this instance)
         */
        public Builder set(String key, double value) {
            Objects.requireNonNull(key, "key cannot be null");

            values.put(key, JValues.NumberValue.create(new BigDecimal(value)));
            return this;
        }

        /**
         * Set an int value.
         *
         * @param key   key to set
         * @param value value to assign to the key
         * @return updated instance (this instance)
         */
        public Builder set(String key, int value) {
            Objects.requireNonNull(key, "key cannot be null");

            values.put(key, JValues.NumberValue.create(new BigDecimal(value)));
            return this;
        }

        /**
         * Set a long value.
         *
         * @param key   key to set
         * @param value value to assign to the key
         * @return updated instance (this instance)
         */
        public Builder set(String key, long value) {
            Objects.requireNonNull(key, "key cannot be null");

            values.put(key, JValues.NumberValue.create(new BigDecimal(value)));
            return this;
        }

        /**
         * Set a {@link java.math.BigDecimal} value.
         *
         * @param key   key to set
         * @param value value to assign to the key
         * @return updated instance (this instance)
         */
        public Builder set(String key, BigDecimal value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, JValues.NumberValue.create(value));
            return this;
        }

        /**
         * Set an array.
         *
         * @param key   key to set
         * @param value value to assign to the key
         * @return updated instance (this instance)
         */
        public Builder set(String key, JArray value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, value);
            return this;
        }

        /**
         * Set an array of objects.
         *
         * @param key   key to set
         * @param value value to assign to the key
         * @return updated instance (this instance)
         */
        public Builder setObjects(String key, List<JObject> value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, JArray.create(value));
            return this;
        }

        /**
         * Set an array of strings.
         *
         * @param key   key to set
         * @param value value to assign to the key
         * @return updated instance (this instance)
         */
        public Builder setStrings(String key, List<String> value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, JArray.createStrings(value));
            return this;
        }

        /**
         * Set an array of longs.
         *
         * @param key   key to set
         * @param value value to assign to the key
         * @return updated instance (this instance)
         */
        public Builder setLongs(String key, List<Long> value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, JArray.createNumbers(value.stream()
                                                         .map(BigDecimal::new)
                                                         .collect(Collectors.toUnmodifiableList())));
            return this;
        }

        /**
         * Set an array of doubles.
         *
         * @param key   key to set
         * @param value value to assign to the key
         * @return updated instance (this instance)
         */
        public Builder setDoubles(String key, List<Double> value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");
            values.put(key, JArray.createNumbers(value.stream()
                                                         .map(BigDecimal::new)
                                                         .collect(Collectors.toUnmodifiableList())));
            return this;
        }

        /**
         * Set an array of numbers (such as {@link java.math.BigDecimal}).
         *
         * @param key   key to set
         * @param value value to assign to the key
         * @return updated instance (this instance)
         */
        public Builder setNumbers(String key, List<BigDecimal> value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, JArray.createNumbers(value));
            return this;
        }

        /**
         * Set an array of booleans.
         *
         * @param key   key to set
         * @param value value to assign to the key
         * @return updated instance (this instance)
         */
        public Builder setBooleans(String key, List<Boolean> value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, JArray.createBooleans(value));
            return this;
        }
    }
}


