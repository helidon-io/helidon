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

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JsonTest {
    @Test
    void testWrongTypes() {
        JObject object = JObject.builder()
                .set("number", 1)
                .set("string", "hi")
                .setLongs("numbers", List.of(1L, 2L, 3L))
                .setStrings("strings", List.of("hi", "there"))
                .build();

        assertThrows(JException.class,
                     () -> object.stringValue("number"));
        assertThrows(JException.class,
                     () -> object.booleanValue("number"));
        assertThrows(JException.class,
                     () -> object.doubleValue("string"));
        assertThrows(JException.class,
                     () -> object.objectValue("string"));
        assertThrows(JException.class,
                     () -> object.stringArray("string"));
        assertThrows(JException.class,
                     () -> object.objectArray("string"));
        assertThrows(JException.class,
                     () -> object.booleanArray("string"));
        assertThrows(JException.class,
                     () -> object.numberArray("string"));

        assertThrows(JException.class,
                     () -> object.stringValue("strings"));
        assertThrows(JException.class,
                     () -> object.booleanValue("strings"));
        assertThrows(JException.class,
                     () -> object.doubleValue("strings"));
        assertThrows(JException.class,
                     () -> object.objectValue("strings"));
        assertThrows(JException.class,
                     () -> object.stringArray("numbers"));
        assertThrows(JException.class,
                     () -> object.objectArray("strings"));
        assertThrows(JException.class,
                     () -> object.booleanArray("strings"));
        assertThrows(JException.class,
                     () -> object.numberArray("strings"));
    }

    @Test
    void testReads() {
        JObject empty = JObject.create();

        assertThat(empty.booleanValue("anyKey"), optionalEmpty());
        assertThat(empty.booleanValue("anyKey", true), is(true));

        assertThat(empty.intValue("anyKey"), optionalEmpty());
        assertThat(empty.intValue("anyKey", 876), is(876));

        assertThat(empty.doubleValue("anyKey"), optionalEmpty());
        assertThat(empty.doubleValue("anyKey", 876), is(876d));

        assertThat(empty.stringArray("anyKey"), optionalEmpty());
        assertThat(empty.objectArray("anyKey"), optionalEmpty());
        assertThat(empty.numberArray("anyKey"), optionalEmpty());
    }

    @Test
    void testSetNumbers() {

        BigDecimal one = new BigDecimal(14);
        BigDecimal two = new BigDecimal(15);

        JObject jObject = JObject.builder()
                .setNumbers("numbers", List.of(one, two))
                .build();

        List<BigDecimal> objects = jObject.numberArray("numbers")
                .orElseThrow(() -> new IllegalStateException("numbers key should be filled with array"));

        assertThat(objects, hasItems(one, two));
    }

    @Test
    void testSetBooleans() {
        JObject jObject = JObject.builder()
                .setBooleans("booleans", List.of(true, false, true))
                .build();

        List<Boolean> objects = jObject.booleanArray("booleans")
                .orElseThrow(() -> new IllegalStateException("booleans key should be filled with array"));

        assertThat(objects, hasItems(true, false, true));
    }

    @Test
    void testWriteStringArray() {
        JArray array = JArray.createStrings(List.of("a", "b", "c"));

        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            array.write(pw);
        }

        String string = sw.toString();
        assertThat(string, is("[\"a\",\"b\",\"c\"]"));
    }

    @Test
    void testWriteLongArray() {
        JArray array = JArray.create(2L, 3L, 4L);

        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            array.write(pw);
        }

        String string = sw.toString();
        assertThat(string, is("[2,3,4]"));
    }

    @Test
    void testWriteDoubleArray() {
        JArray array = JArray.createNumbers(List.of(new BigDecimal(2), new BigDecimal(3), new BigDecimal("4.2")));

        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            array.write(pw);
        }

        String string = sw.toString();
        assertThat(string, is("[2,3,4.2]"));
    }

    @Test
    void testWriteBooleanArray() {
        JArray array = JArray.createBooleans(List.of(true, false, true));

        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            array.write(pw);
        }

        String string = sw.toString();
        assertThat(string, is("[true,false,true]"));
    }

    @Test
    void testWriteObjectArray() {
        JObject first = JObject.builder()
                .set("string", "value")
                .set("long", 4L)
                .set("double", 4d)
                .set("boolean", true)
                .setStrings("strings", List.of("a", "b"))
                .setLongs("longs", List.of(1L, 2L))
                .setDoubles("doubles", List.of(1.5d, 2.5d))
                .setBooleans("booleans", List.of(true, false))
                .build();
        JObject second = JObject.builder()
                .set("string", "value2")
                .build();

        JArray array = JArray.create(List.of(first, second));

        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            array.write(pw);
        }

        String string = sw.toString();
        String expected = "[{\"string\":\"value\","
                + "\"long\":4,"
                + "\"double\":4,"
                + "\"boolean\":true,"
                + "\"strings\":[\"a\",\"b\"],"
                + "\"longs\":[1,2],"
                + "\"doubles\":[1.5,2.5],"
                + "\"booleans\":[true,false]},"
                + "{\"string\":\"value2\"}]";
        assertThat(string, is(expected));

        JValue<?> read = JValue.read(new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8)));
        assertThat(read.type(), is(JType.ARRAY));

        JArray objects = read.asArray();
        List<JObject> value = objects.getObjects();
        assertThat(value, hasSize(2));
        JObject readFirst = value.get(0);
        assertThat(readFirst, is(first));
        JObject readSecond = value.get(1);
        assertThat(readSecond, is(second));
    }

    @Test
    void testFeatures() {
        JValue<?> parsedValue = JValue.read(JsonTest.class.getResourceAsStream("/json-features.json"));
        assertThat(parsedValue.type(), is(JType.OBJECT));
        JObject object = parsedValue.asObject();

        testFeaturesNull(object);
        testFeaturesArray(object);
        testFeaturesEscapes(object);
        testFeaturesNumbers(object);
    }

    private void testFeaturesNumbers(JObject object) {
        Optional<JObject> maybeObject = object.objectValue("numbers");
        assertThat(maybeObject, optionalPresent());
        JObject numbers = maybeObject.get();

        assertThat(numbers.numberValue("number1"), optionalValue(is(new BigDecimal(1))));
        assertThat(numbers.numberValue("number2"), optionalValue(is(new BigDecimal("1.5"))));
        assertThat(numbers.numberValue("number3"), optionalValue(is(new BigDecimal("1.5e2"))));
        assertThat(numbers.numberValue("number4"), optionalValue(is(new BigDecimal("1.5e-2"))));
        assertThat(numbers.numberValue("number5"), optionalValue(is(new BigDecimal("1.5e2"))));
    }

    private void testFeaturesEscapes(JObject object) {
        Optional<JObject> maybeObject = object.objectValue("escapes");
        assertThat(maybeObject, optionalPresent());
        JObject escapes = maybeObject.get();

        assertThat(escapes.stringValue("newline"), optionalValue(is("a\nb")));
        assertThat(escapes.stringValue("quotes"), optionalValue(is("a\"b")));
        assertThat(escapes.stringValue("backslash"), optionalValue(is("a\\b")));
        assertThat(escapes.stringValue("slash"), optionalValue(is("a/b")));
        assertThat(escapes.stringValue("backspace"), optionalValue(is("a\bb")));
        assertThat(escapes.stringValue("formfeed"), optionalValue(is("a\fb")));
        assertThat(escapes.stringValue("cr"), optionalValue(is("a\rb")));
        assertThat(escapes.stringValue("tab"), optionalValue(is("a\tb")));
        assertThat(escapes.stringValue("unicode"), optionalValue(is("aHb")));

    }

    private void testFeaturesArray(JObject object) {
        Optional<JArray> maybeArray = object.arrayValue("array");
        assertThat(maybeArray, optionalPresent());
        List<JValue<?>> value = maybeArray.get().value();
        assertThat("There should be 6 elements in array", value, hasSize(6));
        assertThat(value.get(0).type(), is(JType.BOOLEAN));
        assertThat(value.get(1).type(), is(JType.NULL));
        assertThat(value.get(2).type(), is(JType.STRING));
        assertThat(value.get(3).type(), is(JType.NUMBER));
        assertThat(value.get(4).type(), is(JType.OBJECT));
        assertThat(value.get(5).type(), is(JType.ARRAY));
    }

    private void testFeaturesNull(JObject object) {
        Optional<JValue<?>> maybeValue = object.objectValue("nulls")
                .flatMap(it -> it.value("field"));
        assertThat(maybeValue, optionalPresent());
        JValue<?> value = maybeValue.get();
        assertThat(value.type(), is(JType.NULL));
    }
}
