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

package io.helidon.metadata.hson;

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

final class HsonStruct implements Hson.Struct {
    private final Map<String, Hson.Value<?>> values;

    private HsonStruct(Map<String, Hson.Value<?>> values) {
        this.values = values;
    }

    @Override
    public Hson.Struct value() {
        return this;
    }

    @Override
    public Optional<Hson.Value<?>> value(String key) {
        return Optional.ofNullable(values.get(key));
    }

    @Override
    public Optional<Boolean> booleanValue(String key) {
        return getValue(key, Hson.Type.BOOLEAN);
    }

    @Override
    public boolean booleanValue(String key, boolean defaultValue) {
        return booleanValue(key).orElse(defaultValue);
    }

    @Override
    public Optional<Hson.Struct> structValue(String key) {
        return getValue(key, Hson.Type.STRUCT);
    }

    @Override
    public Optional<String> stringValue(String key) {
        return getValue(key, Hson.Type.STRING);
    }

    @Override
    public String stringValue(String key, String defaultValue) {
        return stringValue(key).orElse(defaultValue);
    }

    @Override
    public Optional<Integer> intValue(String key) {
        return this.<BigDecimal>getValue(key, Hson.Type.NUMBER).map(BigDecimal::intValue);
    }

    @Override
    public int intValue(String key, int defaultValue) {
        return intValue(key).orElse(defaultValue);
    }

    @Override
    public Optional<Double> doubleValue(String key) {
        return this.<BigDecimal>getValue(key, Hson.Type.NUMBER).map(BigDecimal::doubleValue);
    }

    @Override
    public double doubleValue(String key, double defaultValue) {
        return doubleValue(key).orElse(defaultValue);
    }

    @Override
    public Optional<BigDecimal> numberValue(String key) {
        return this.getValue(key, Hson.Type.NUMBER);
    }

    @Override
    public BigDecimal numberValue(String key, BigDecimal defaultValue) {
        return numberValue(key).orElse(defaultValue);
    }

    @Override
    public Optional<List<String>> stringArray(String key) {
        return getTypedList(key, Hson.Array::getStrings);
    }

    @Override
    public Optional<List<Hson.Struct>> structArray(String key) {
        return getTypedList(key, Hson.Array::getStructs);
    }

    @Override
    public Optional<List<BigDecimal>> numberArray(String key) {
        return getTypedList(key, Hson.Array::getNumbers);
    }

    @Override
    public Optional<List<Boolean>> booleanArray(String key) {
        return getTypedList(key, Hson.Array::getBooleans);
    }

    @Override
    public Optional<Hson.Array> arrayValue(String key) {
        Hson.Value<?> jValue = values.get(key);
        if (jValue == null) {
            return Optional.empty();
        }
        if (jValue.type() != Hson.Type.ARRAY) {
            throw new HsonException(exceptionMessage(key, " array requested, yet this key is not an array, but " + type()));
        }

        return Optional.of((Hson.Array) jValue);
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
    public Hson.Type type() {
        return Hson.Type.STRUCT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HsonStruct struct)) {
            return false;
        }
        return Objects.equals(values, struct.values);
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
    private <T> Optional<T> getValue(String key, Hson.Type type) {
        Hson.Value<?> jValue = values.get(key);
        if (jValue == null || jValue.type() == Hson.Type.NULL) {
            return Optional.empty();
        }
        if (jValue.type() != type) {
            throw new HsonException(exceptionMessage(key, "requested as a " + type
                    + ", but it is of type " + jValue.type()));
        }
        return Optional.of((T) jValue.value());
    }

    private <T> Optional<List<T>> getTypedList(String key, Function<Hson.Array, List<T>> arrayFunction) {
        try {
            return arrayValue(key).map(arrayFunction);
        } catch (HsonException e) {
            throw new HsonException(exceptionMessage(key, " failed to get typed array"), e);
        }
    }

    private String exceptionMessage(String key, String message) {
        return "Struct key \"" + key + "\": " + message;
    }

    private void writeNext(PrintWriter metaWriter, AtomicBoolean first) {
        if (first.get()) {
            first.set(false);
            return;
        }
        metaWriter.write(',');
    }

    static class Builder implements Hson.Struct.Builder {
        private final Map<String, Hson.Value<?>> values = new LinkedHashMap<>();

        Builder() {
        }

        @Override
        public Hson.Struct build() {
            return new HsonStruct(new LinkedHashMap<>(values));
        }

        @Override
        public Builder unset(String key) {
            Objects.requireNonNull(key, "key cannot be null");

            values.remove(key);
            return this;
        }

        @Override
        public Hson.Struct.Builder setNull(String key) {
            values.put(key, HsonValues.NullValue.INSTANCE);
            return this;
        }

        @Override
        public Builder set(String key, Hson.Value<?> value) {
            values.put(key, value);
            return this;
        }

        @Override
        public Builder set(String key, String value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, HsonValues.StringValue.create(value));
            return this;
        }

        @Override
        public Builder set(String key, boolean value) {
            Objects.requireNonNull(key, "key cannot be null");

            values.put(key, HsonValues.BooleanValue.create(value));
            return this;
        }

        @Override
        public Builder set(String key, float value) {
            Objects.requireNonNull(key, "key cannot be null");

            return set(key, new BigDecimal(String.valueOf(value)));
        }

        @Override
        public Builder set(String key, double value) {
            Objects.requireNonNull(key, "key cannot be null");

            return set(key, new BigDecimal(String.valueOf(value)));
        }

        @Override
        public Builder set(String key, int value) {
            Objects.requireNonNull(key, "key cannot be null");

            values.put(key, HsonValues.NumberValue.create(new BigDecimal(value)));
            return this;
        }

        @Override
        public Builder set(String key, long value) {
            Objects.requireNonNull(key, "key cannot be null");

            values.put(key, HsonValues.NumberValue.create(new BigDecimal(value)));
            return this;
        }

        @Override
        public Builder set(String key, BigDecimal value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, HsonValues.NumberValue.create(value));
            return this;
        }

        @Override
        public Builder set(String key, Hson.Array value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, value);
            return this;
        }

        @Override
        public Builder setStructs(String key, List<Hson.Struct> value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, Hson.Array.create(value));
            return this;
        }

        @Override
        public Builder setStrings(String key, List<String> value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, Hson.Array.createStrings(value));
            return this;
        }

        @Override
        public Builder setLongs(String key, List<Long> value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, Hson.Array.createNumbers(value.stream()
                                                             .map(BigDecimal::new)
                                                             .collect(Collectors.toUnmodifiableList())));
            return this;
        }

        @Override
        public Builder setDoubles(String key, List<Double> value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");
            values.put(key, Hson.Array.createNumbers(value.stream()
                                                             .map(BigDecimal::new)
                                                             .collect(Collectors.toUnmodifiableList())));
            return this;
        }

        @Override
        public Builder setNumbers(String key, List<BigDecimal> value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, Hson.Array.createNumbers(value));
            return this;
        }

        @Override
        public Builder setBooleans(String key, List<Boolean> value) {
            Objects.requireNonNull(key, "key cannot be null");
            Objects.requireNonNull(value, "value cannot be null");

            values.put(key, Hson.Array.createBooleans(value));
            return this;
        }
    }
}
