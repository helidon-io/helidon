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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * An immutable representation of JSON array.
 * JSON array is an array of values (of any type).
 */
public final class JArray implements JValue<List<JValue<?>>> {
    private final List<JValue<?>> values;

    private JArray(List<? extends JValue<?>> array) {
        this.values = List.copyOf(array);
    }

    /**
     * Create an empty JArray.
     *
     * @return empty array
     */
    public static JArray create() {
        return new JArray(List.of());
    }

    /**
     * Create a new array of JSON values.
     *
     * @param values list of values
     * @return a new array
     */
    public static JArray create(List<? extends JValue<?>> values) {
        return new JArray(values);
    }

    /**
     * Create a new array of Strings.
     *
     * @param strings String list
     * @return a new string array
     */
    public static JArray createStrings(List<String> strings) {
        List<JValues.StringValue> values = strings.stream()
                .map(JValues.StringValue::create)
                .collect(Collectors.toList());

        return new JArray(values);
    }

    /**
     * Create a new array of Numbers.
     *
     * @param numbers {@link java.math.BigDecimal} list
     * @return a new number array
     */
    public static JArray createNumbers(List<BigDecimal> numbers) {
        return new JArray(numbers.stream()
                                  .map(JValues.NumberValue::create)
                                  .collect(Collectors.toUnmodifiableList()));
    }

    /**
     * Create a new array of booleans.
     *
     * @param booleans boolean list
     * @return a new boolean array
     */
    public static JArray createBooleans(List<Boolean> booleans) {
        return new JArray(booleans.stream()
                                  .map(JValues.BooleanValue::create)
                                  .collect(Collectors.toUnmodifiableList()));
    }

    /**
     * Create a new array of Numbers from long values.
     *
     * @param values long numbers
     * @return a new number array
     */
    public static JArray create(long... values) {
        List<BigDecimal> collect = LongStream.of(values)
                .mapToObj(BigDecimal::new)
                .collect(Collectors.toUnmodifiableList());
        return JArray.createNumbers(collect);
    }

    /**
     * Create a new array of Numbers from int values.
     *
     * @param values int numbers
     * @return a new number array
     */
    public static JArray create(int... values) {
        List<BigDecimal> collect = IntStream.of(values)
                .mapToObj(BigDecimal::new)
                .collect(Collectors.toUnmodifiableList());
        return JArray.createNumbers(collect);
    }

    /**
     * Create a new array of Numbers from double values.
     *
     * @param values double numbers
     * @return a new number array
     */
    public static JArray create(double... values) {
        List<BigDecimal> collect = DoubleStream.of(values)
                .mapToObj(BigDecimal::new)
                .collect(Collectors.toUnmodifiableList());
        return JArray.createNumbers(collect);
    }

    /**
     * Create a new array of Numbers from float values.
     *
     * @param values float numbers
     * @return a new number array
     */
    public static JArray create(float... values) {
        List<BigDecimal> list = new ArrayList<>(values.length);
        for (float value : values) {
            list.add(new BigDecimal(value));
        }

        return JArray.createNumbers(list);
    }

    @Override
    public List<JValue<?>> value() {
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
    public JType type() {
        return JType.ARRAY;
    }

    /**
     * Assume this is an array of strings, and return the list.
     *
     * @return all string values of this array, except for nulls
     * @throws io.helidon.metadata.hjson.JException in case not all elements of this array are strings (or nulls)
     */
    public List<String> getStrings() {
        return getTypedList(JType.STRING);
    }

    /**
     * Assume this is an array of booleans, and return the list.
     *
     * @return all boolean values of this array, except for nulls
     * @throws io.helidon.metadata.hjson.JException in case not all elements of this array are booleans (or nulls)
     */
    public List<Boolean> getBooleans() {
        return getTypedList(JType.BOOLEAN);
    }

    /**
     * Assume this is an array of numbers, and return the list.
     *
     * @return all big decimal values of this array, except for nulls
     * @throws io.helidon.metadata.hjson.JException in case not all elements of this array are numbers (or nulls)
     */
    public List<BigDecimal> getNumbers() {
        return getTypedList(JType.NUMBER);
    }

    /**
     * Assume this is an array of objects, and return the list.
     *
     * @return all object values of this array, except for nulls
     * @throws io.helidon.metadata.hjson.JException in case not all elements of this array are objects (or nulls)
     */
    public List<JObject> getObjects() {
        return getTypedList(JType.OBJECT);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JArray jArray)) {
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
    private <T> List<T> getTypedList(JType type) {
        if (values.isEmpty()) {
            return List.of();
        }

        List<T> list = new ArrayList<>();

        for (JValue<?> value : values) {
            if (value.type() == JType.NULL) {
                // null values are ignored
                continue;
            }
            if (value.type() != type) {
                throw new JException("Requested array of " + type + ", but array element is of type: "
                                             + value.type());
            }
            list.add((T) value.value());
        }

        return List.copyOf(list);
    }
}
