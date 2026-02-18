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

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for JsonGenerator functionality.
 * Tests basic generation, object/array structures, different data types,
 * and error conditions.
 */
abstract class JsonGeneratorTest {

    // Basic value tests
    @Test
    public void testWriteString() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write("hello");
        }

        assertThat(getGeneratedJson(), is("\"hello\""));
    }

    @Test
    public void testWriteInt() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write(42);
        }

        assertThat(getGeneratedJson(), is("42"));
    }

    @Test
    public void testWriteLong() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write(123456789L);
        }

        assertThat(getGeneratedJson(), is("123456789"));
    }

    @Test
    public void testWriteFloat() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write(3.14f);
        }

        assertThat(getGeneratedJson(), is("3.14"));
    }

    @Test
    public void testWriteDouble() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write(2.71828);
        }

        assertThat(getGeneratedJson(), is("2.71828"));
    }

    @Test
    public void testWriteBooleanTrue() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write(true);
        }

        assertThat(getGeneratedJson(), is("true"));
    }

    @Test
    public void testWriteBooleanFalse() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write(false);
        }

        assertThat(getGeneratedJson(), is("false"));
    }

    @Test
    public void testWriteNull() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.writeNull();
        }

        assertThat(getGeneratedJson(), is("null"));
    }

    @Test
    public void testWriteChar() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write('A');
        }

        assertThat(getGeneratedJson(), is("\"A\""));
    }

    @Test
    public void testWriteByte() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write((byte) 127);
        }

        assertThat(getGeneratedJson(), is("127"));
    }

    @Test
    public void testWriteShort() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write((short) 32767);
        }

        assertThat(getGeneratedJson(), is("32767"));
    }

    // Object tests
    @Test
    public void testWriteSimpleObject() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.writeObjectStart()
                    .write("name", "John")
                    .write("age", 30)
                    .writeObjectEnd();
        }

        assertThat(getGeneratedJson(), is("{\"name\":\"John\",\"age\":30}"));
    }

    @Test
    public void testWriteNestedObject() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.writeObjectStart()
                    .writeKey("person")
                    .writeObjectStart()
                    .write("name", "John")
                    .write("age", 30)
                    .writeObjectEnd()
                    .writeObjectEnd();
        }

        assertThat(getGeneratedJson(), is("{\"person\":{\"name\":\"John\",\"age\":30}}"));
    }

    @Test
    public void testWriteObjectWithBoolean() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.writeObjectStart()
                    .write("active", true)
                    .write("deleted", false)
                    .writeObjectEnd();
        }

        assertThat(getGeneratedJson(), is("{\"active\":true,\"deleted\":false}"));
    }

    @Test
    public void testWriteObjectWithNull() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.writeObjectStart()
                    .write("name", "John")
                    .write("nickname", (String) null)
                    .writeObjectEnd();
        }

        assertThat(getGeneratedJson(), is("{\"name\":\"John\",\"nickname\":null}"));
    }

    // Array tests
    @Test
    public void testWriteSimpleArray() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.writeArrayStart()
                    .write("a")
                    .write("b")
                    .write("c")
                    .writeArrayEnd();
        }

        assertThat(getGeneratedJson(), is("[\"a\",\"b\",\"c\"]"));
    }

    @Test
    public void testWriteMixedArray() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.writeArrayStart()
                    .write("string")
                    .write(42)
                    .write(true)
                    .writeNull()
                    .writeArrayEnd();
        }

        assertThat(getGeneratedJson(), is("[\"string\",42,true,null]"));
    }

    @Test
    public void testWriteNestedArray() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.writeArrayStart()
                    .writeArrayStart()
                    .write(1)
                    .write(2)
                    .writeArrayEnd()
                    .writeArrayStart()
                    .write(3)
                    .write(4)
                    .writeArrayEnd()
                    .writeArrayEnd();
        }

        assertThat(getGeneratedJson(), is("[[1,2],[3,4]]"));
    }

    // Complex structure tests
    @Test
    public void testWriteComplexObject() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.writeObjectStart()
                    .writeKey("users")
                    .writeArrayStart()
                    .writeObjectStart()
                    .write("id", 1)
                    .write("name", "Alice")
                    .writeObjectEnd()
                    .writeObjectStart()
                    .write("id", 2)
                    .write("name", "Bob")
                    .writeObjectEnd()
                    .writeArrayEnd()
                    .write("total", 2)
                    .writeObjectEnd();
        }

        assertThat(getGeneratedJson(), is("{\"users\":[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}],\"total\":2}"));
    }

    // Error condition tests
    @Test
    public void testWriteKeyOutsideObject() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            assertThrows(JsonException.class, () -> generator.writeKey("key"));
        }
    }

    @Test
    public void testWriteValueWithoutKeyInObject() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.writeObjectStart();

            assertThrows(JsonException.class, () -> generator.write("value"));
        }
    }

    @Test
    public void testWriteMultipleRootValues() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write("first");

            assertThrows(JsonException.class, () -> generator.write("second"));
        }
    }

    @Test
    public void testWriteKeyTwice() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.writeObjectStart()
                    .writeKey("key1");

            assertThrows(JsonException.class, () -> generator.writeKey("key2"));
        }
    }

    // String escaping tests
    @Test
    public void testWriteStringWithQuotes() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write("He said \"Hello\"");
        }

        assertThat(getGeneratedJson(), is("\"He said \\\"Hello\\\"\""));
    }

    @Test
    public void testWriteStringWithBackslash() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write("path\\to\\file");
        }

        assertThat(getGeneratedJson(), is("\"path\\\\to\\\\file\""));
    }

    @Test
    public void testWriteStringWithNewline() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write("line1\nline2");
        }

        assertThat(getGeneratedJson(), is("\"line1\\nline2\""));
    }

    @Test
    public void testWriteStringWithControlChars() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write("tab:\tcontrol:\u0001");
        }

        assertThat(getGeneratedJson(), is("\"tab:\\tcontrol:\\u0001\""));
    }

    // Special number cases
    @Test
    public void testWriteZero() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write(0);
        }

        assertThat(getGeneratedJson(), is("0"));
    }

    @Test
    public void testWriteNegativeNumber() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write(-42);
        }

        assertThat(getGeneratedJson(), is("-42"));
    }

    @Test
    public void testWriteFloatZero() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write(0.0f);
        }

        assertThat(getGeneratedJson(), is("0.0"));
    }

    @Test
    public void testWriteDoubleZero() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write(0.0);
        }

        assertThat(getGeneratedJson(), is("0.0"));
    }

    @Test
    public void testWriteFloatNaN() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write(Float.NaN);
        }

        assertThat(getGeneratedJson(), is("NaN"));
    }

    @Test
    public void testWriteFloatPositiveInfinity() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write(Float.POSITIVE_INFINITY);
        }

        assertThat(getGeneratedJson(), is("Infinity"));
    }

    @Test
    public void testWriteFloatNegativeInfinity() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write(Float.NEGATIVE_INFINITY);
        }

        assertThat(getGeneratedJson(), is("-Infinity"));
    }

    @Test
    public void testWriteDoubleNaN() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write(Double.NaN);
        }

        assertThat(getGeneratedJson(), is("NaN"));
    }

    @Test
    public void testWriteDoubleNegativeInfinity() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write(Double.NEGATIVE_INFINITY);
        }
        assertThat(getGeneratedJson(), is("-Infinity"));
    }

    @Test
    public void testWriteDoublePositiveInfinity() throws Exception {
        try (JsonGenerator generator = createGenerator()) {
            generator.write(Double.POSITIVE_INFINITY);
        }
        assertThat(getGeneratedJson(), is("Infinity"));
    }

    abstract JsonGenerator createGenerator();

    abstract String getGeneratedJson() throws Exception;

}