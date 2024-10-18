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
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HsonTest {
    @Test
    void testWrongTypes() {
        Hson.Struct object = Hson.structBuilder()
                .set("number", 1)
                .set("string", "hi")
                .setLongs("numbers", List.of(1L, 2L, 3L))
                .setStrings("strings", List.of("hi", "there"))
                .build();

        assertThrows(HsonException.class,
                     () -> object.stringValue("number"));
        assertThrows(HsonException.class,
                     () -> object.booleanValue("number"));
        assertThrows(HsonException.class,
                     () -> object.doubleValue("string"));
        assertThrows(HsonException.class,
                     () -> object.structValue("string"));
        assertThrows(HsonException.class,
                     () -> object.stringArray("string"));
        assertThrows(HsonException.class,
                     () -> object.structArray("string"));
        assertThrows(HsonException.class,
                     () -> object.booleanArray("string"));
        assertThrows(HsonException.class,
                     () -> object.numberArray("string"));

        assertThrows(HsonException.class,
                     () -> object.stringValue("strings"));
        assertThrows(HsonException.class,
                     () -> object.booleanValue("strings"));
        assertThrows(HsonException.class,
                     () -> object.doubleValue("strings"));
        assertThrows(HsonException.class,
                     () -> object.structValue("strings"));
        assertThrows(HsonException.class,
                     () -> object.stringArray("numbers"));
        assertThrows(HsonException.class,
                     () -> object.structArray("strings"));
        assertThrows(HsonException.class,
                     () -> object.booleanArray("strings"));
        assertThrows(HsonException.class,
                     () -> object.numberArray("strings"));
    }

    @Test
    void testReads() {
        Hson.Struct empty = Hson.Struct.create();

        assertThat(empty.booleanValue("anyKey"), optionalEmpty());
        assertThat(empty.booleanValue("anyKey", true), is(true));

        assertThat(empty.intValue("anyKey"), optionalEmpty());
        assertThat(empty.intValue("anyKey", 876), is(876));

        assertThat(empty.doubleValue("anyKey"), optionalEmpty());
        assertThat(empty.doubleValue("anyKey", 876), is(876d));

        assertThat(empty.stringValue("anyKey"), optionalEmpty());
        assertThat(empty.stringValue("anyKey", "default"), is("default"));

        assertThat(empty.numberValue("anyKey"), optionalEmpty());
        BigDecimal bd = new BigDecimal(14);
        assertThat(empty.numberValue("anyKey", bd), sameInstance(bd));

        assertThat(empty.stringArray("anyKey"), optionalEmpty());
        assertThat(empty.structArray("anyKey"), optionalEmpty());
        assertThat(empty.numberArray("anyKey"), optionalEmpty());
    }

    @Test
    void testSetNumbers() {

        BigDecimal one = new BigDecimal(14);
        BigDecimal two = new BigDecimal(15);

        Hson.Struct jObject = Hson.structBuilder()
                .setNumbers("numbers", List.of(one, two))
                .build();

        List<BigDecimal> objects = jObject.numberArray("numbers")
                .orElseThrow(() -> new IllegalStateException("numbers key should be filled with array"));

        assertThat(objects, hasItems(one, two));
    }

    @Test
    void testSetBooleans() {
        Hson.Struct jObject = Hson.structBuilder()
                .setBooleans("booleans", List.of(true, false, true))
                .build();

        List<Boolean> objects = jObject.booleanArray("booleans")
                .orElseThrow(() -> new IllegalStateException("booleans key should be filled with array"));

        assertThat(objects, hasItems(true, false, true));
    }

    @Test
    void testWriteStringArray() {
        Hson.Array array = Hson.Array.createStrings(List.of("a", "b", "c"));

        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            array.write(pw);
        }

        String string = sw.toString();
        assertThat(string, is("[\"a\",\"b\",\"c\"]"));
    }

    @Test
    void testWriteLongArray() {
        Hson.Array array = Hson.Array.create(2L, 3L, 4L);

        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            array.write(pw);
        }

        String string = sw.toString();
        assertThat(string, is("[2,3,4]"));
    }

    @Test
    void testWriteDoubleArray() {
        Hson.Array array = Hson.Array.createNumbers(List.of(new BigDecimal(2), new BigDecimal(3), new BigDecimal("4.2")));

        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            array.write(pw);
        }

        String string = sw.toString();
        assertThat(string, is("[2,3,4.2]"));
    }

    @Test
    void testWriteBooleanArray() {
        Hson.Array array = Hson.Array.createBooleans(List.of(true, false, true));

        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            array.write(pw);
        }

        String string = sw.toString();
        assertThat(string, is("[true,false,true]"));
    }

    @Test
    void testWriteObjectArray() {
        Hson.Struct first = Hson.structBuilder()
                .set("string", "value")
                .set("long", 4L)
                .set("double", 4d)
                .set("boolean", true)
                .setStrings("strings", List.of("a", "b"))
                .setLongs("longs", List.of(1L, 2L))
                .setDoubles("doubles", List.of(1.5d, 2.5d))
                .setBooleans("booleans", List.of(true, false))
                .build();
        Hson.Struct second = Hson.structBuilder()
                .set("string", "value2")
                .build();

        Hson.Array array = Hson.Array.create(List.of(first, second));

        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            array.write(pw);
        }

        String string = sw.toString();
        String expected = "[{\"string\":\"value\","
                + "\"long\":4,"
                + "\"double\":4.0,"
                + "\"boolean\":true,"
                + "\"strings\":[\"a\",\"b\"],"
                + "\"longs\":[1,2],"
                + "\"doubles\":[1.5,2.5],"
                + "\"booleans\":[true,false]},"
                + "{\"string\":\"value2\"}]";
        assertThat(string, is(expected));

        Hson.Value<?> read = Hson.parse(new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8)));
        assertThat(read.type(), is(Hson.Type.ARRAY));

        Hson.Array objects = read.asArray();
        List<Hson.Struct> value = objects.getStructs();
        assertThat(value, hasSize(2));
        Hson.Struct readFirst = value.get(0);
        assertThat(readFirst, is(first));
        Hson.Struct readSecond = value.get(1);
        assertThat(readSecond, is(second));
    }

    @Test
    void testSetStructs() {
        Hson.Struct first = Hson.structBuilder()
                .set("key", "value1")
                .build();
        Hson.Struct second = Hson.structBuilder()
                .set("key", "value2")
                .build();
        Hson.Struct withArray = Hson.structBuilder()
                .setStructs("objects", List.of(first, second))
                .build();

        List<Hson.Struct> objects = withArray.structArray("objects").get();
        assertThat(objects, hasItems(first, second));
    }

    @Test
    void testNull() {
        Hson.Struct object = Hson.structBuilder()
                .setNull("null-key")
                .build();

        assertThat(object.value("null-key"), optionalPresent());
        assertThat(object.value("null-key").get().type(), is(Hson.Type.NULL));
    }

    @Test
    void testUnset() {
        Hson.Struct object = Hson.structBuilder()
                .setNull("null-key")
                .unset("null-key")
                .build();

        assertThat(object.value("null-key"), optionalEmpty());
    }

    @Test
    void testFunnyDoubles() {
        StringWriter stringWriter = new StringWriter();
        try (PrintWriter pw = new PrintWriter(stringWriter)) {
            Hson.structBuilder()
                    .set("first", 1.1d)
                    .set("second", 1.2f)
                    .set("third", 1.3)
                    .set("fourth", 4)
                    .set("fifth", 5L)
                    .build()
                    .write(pw);
        }
        assertThat(stringWriter.toString(), is("{\"first\":1.1,\"second\":1.2,\"third\":1.3,\"fourth\":4,\"fifth\":5}"));
    }

    @Test
    void testFeatures() {
        Hson.Value<?> parsedValue = Hson.parse(HsonTest.class.getResourceAsStream("/json-features.json"));
        assertThat(parsedValue.type(), is(Hson.Type.STRUCT));
        Hson.Struct object = parsedValue.asStruct();

        testFeaturesNull(object);
        testFeaturesArray(object);
        testFeaturesEscapes(object);
        testFeaturesNumbers(object);
    }

    private void testFeaturesNumbers(Hson.Struct object) {
        Optional<Hson.Struct> maybeObject = object.structValue("numbers");
        assertThat(maybeObject, optionalPresent());
        Hson.Struct numbers = maybeObject.get();

        assertThat(numbers.numberValue("number1"), optionalValue(is(new BigDecimal(1))));
        assertThat(numbers.numberValue("number2"), optionalValue(is(new BigDecimal("1.5"))));
        assertThat(numbers.numberValue("number3"), optionalValue(is(new BigDecimal("1.5e2"))));
        assertThat(numbers.numberValue("number4"), optionalValue(is(new BigDecimal("1.5e-2"))));
        assertThat(numbers.numberValue("number5"), optionalValue(is(new BigDecimal("1.5e2"))));
    }

    private void testFeaturesEscapes(Hson.Struct object) {
        Optional<Hson.Struct> maybeObject = object.structValue("escapes");
        assertThat(maybeObject, optionalPresent());
        Hson.Struct escapes = maybeObject.get();

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

    private void testFeaturesArray(Hson.Struct object) {
        Optional<Hson.Array> maybeArray = object.arrayValue("array");
        assertThat(maybeArray, optionalPresent());
        List<Hson.Value<?>> value = maybeArray.get().value();
        assertThat("There should be 6 elements in array", value, hasSize(6));
        assertThat(value.get(0).type(), is(Hson.Type.BOOLEAN));
        assertThat(value.get(1).type(), is(Hson.Type.NULL));
        assertThat(value.get(2).type(), is(Hson.Type.STRING));
        assertThat(value.get(3).type(), is(Hson.Type.NUMBER));
        assertThat(value.get(4).type(), is(Hson.Type.STRUCT));
        assertThat(value.get(5).type(), is(Hson.Type.ARRAY));
    }

    private void testFeaturesNull(Hson.Struct object) {
        Optional<Hson.Value<?>> maybeValue = object.structValue("nulls")
                .flatMap(it -> it.value("field"));
        assertThat(maybeValue, optionalPresent());
        Hson.Value<?> value = maybeValue.get();
        assertThat(value.type(), is(Hson.Type.NULL));
    }
}
