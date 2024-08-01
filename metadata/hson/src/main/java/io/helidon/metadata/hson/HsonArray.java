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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

final class HsonArray implements Hson.Array {
    private final List<Hson.Value<?>> values;

    private HsonArray(List<? extends Hson.Value<?>> array) {
        this.values = List.copyOf(array);
    }

    static Hson.Array create() {
        return new HsonArray(List.of());
    }

    static Hson.Array create(List<? extends Hson.Value<?>> values) {
        return new HsonArray(values);
    }

    static Hson.Array createStrings(List<String> strings) {
        List<HsonValues.StringValue> values = strings.stream()
                .map(HsonValues.StringValue::create)
                .collect(Collectors.toList());

        return new HsonArray(values);
    }

    static Hson.Array createNumbers(List<BigDecimal> numbers) {
        return new HsonArray(numbers.stream()
                                     .map(HsonValues.NumberValue::create)
                                     .collect(Collectors.toUnmodifiableList()));
    }

    static Hson.Array createBooleans(List<Boolean> booleans) {
        return new HsonArray(booleans.stream()
                                     .map(HsonValues.BooleanValue::create)
                                     .collect(Collectors.toUnmodifiableList()));
    }

    static Hson.Array create(long... values) {
        List<BigDecimal> collect = LongStream.of(values)
                .mapToObj(BigDecimal::new)
                .collect(Collectors.toUnmodifiableList());
        return Hson.Array.createNumbers(collect);
    }

    static Hson.Array create(int... values) {
        List<BigDecimal> collect = IntStream.of(values)
                .mapToObj(BigDecimal::new)
                .collect(Collectors.toUnmodifiableList());
        return Hson.Array.createNumbers(collect);
    }

    static Hson.Array create(double... values) {
        List<BigDecimal> collect = DoubleStream.of(values)
                .mapToObj(String::valueOf)
                .map(BigDecimal::new)
                .collect(Collectors.toUnmodifiableList());
        return Hson.Array.createNumbers(collect);
    }

    static Hson.Array create(float... values) {
        List<BigDecimal> list = new ArrayList<>(values.length);
        for (float value : values) {
            list.add(new BigDecimal(String.valueOf(value)));
        }

        return Hson.Array.createNumbers(list);
    }

    @Override
    public List<Hson.Value<?>> value() {
        return values;
    }

    @Override
    public void write(PrintWriter metaWriter) {
        metaWriter.write('[');

        for (int i = 0; i < values.size(); i++) {
            values.get(i).write(metaWriter);
            if (i < (values.size() - 1)) {
                metaWriter.write(',');
            }
        }

        metaWriter.write(']');
    }

    @Override
    public Hson.Type type() {
        return Hson.Type.ARRAY;
    }

    @Override
    public List<String> getStrings() {
        return getTypedList(Hson.Type.STRING);
    }

    @Override
    public List<Boolean> getBooleans() {
        return getTypedList(Hson.Type.BOOLEAN);
    }

    @Override
    public List<BigDecimal> getNumbers() {
        return getTypedList(Hson.Type.NUMBER);
    }

    @Override
    public List<Hson.Object> getObjects() {
        return getTypedList(Hson.Type.OBJECT);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HsonArray jArray)) {
            return false;
        }
        return Objects.equals(values, jArray.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return "["
                + "values=" + values
                + ']';
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> getTypedList(Hson.Type type) {
        if (values.isEmpty()) {
            return List.of();
        }

        List<T> list = new ArrayList<>();

        for (Hson.Value<?> value : values) {
            if (value.type() == Hson.Type.NULL) {
                // null values are ignored
                continue;
            }
            if (value.type() != type) {
                throw new HsonException("Requested array of " + type + ", but array element is of type: "
                                                + value.type());
            }
            list.add((T) value.value());
        }

        return List.copyOf(list);
    }
}
