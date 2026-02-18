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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for JsonValueParser functionality.
 * JsonValueParser wraps existing JsonValue objects and allows them to be used as parsers.
 */
class JsonValueParserTest {

    @Test
    public void testJsonValueParserWithString() {
        JsonValue original = JsonString.create("test string");
        JsonParser parser = JsonParser.create(original);

        assertThat(parser.readJsonValue(), is(original));
        assertThat(parser.readString(), is("test string"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testJsonValueParserWithNumber() {
        JsonValue original = JsonNumber.create(new BigDecimal("123.45"));
        JsonParser parser = JsonParser.create(original);

        assertThat(parser.readJsonValue(), is(original));
        assertThat(parser.readDouble(), is(123.45));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testJsonValueParserWithBoolean() {
        JsonValue original = JsonBoolean.TRUE;
        JsonParser parser = JsonParser.create(original);

        assertThat(parser.readJsonValue(), is(original));
        assertThat(parser.readBoolean(), is(true));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testJsonValueParserWithNull() {
        JsonValue original = JsonNull.instance();
        JsonParser parser = JsonParser.create(original);

        assertThat(parser.readJsonValue(), is(original));
        assertThat(parser.checkNull(), is(true));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testJsonValueParserWithSimpleObject() {
        JsonValue original = JsonObject.create(
                Map.of("key1", JsonString.create("value1"),
                       "key2", JsonNumber.create(new BigDecimal("42")))
        );
        JsonParser parser = JsonParser.create(original);

        JsonObject result = parser.readJsonObject();
        assertThat(result.stringValue("key1").orElseThrow(), is("value1"));
        assertThat(result.intValue("key2").orElseThrow(), is(42));
    }

    @Test
    public void testJsonValueParserWithSimpleArray() {
        JsonValue original = JsonArray.create(List.of(
                JsonString.create("item1"),
                JsonNumber.create(new BigDecimal("123")),
                JsonBoolean.TRUE)
        );
        JsonParser parser = JsonParser.create(original);

        JsonArray result = parser.readJsonArray();
        assertThat(result.values().size(), is(3));
        assertThat(result.get(0, JsonNull.instance()).asString().value(), is("item1"));
        assertThat(result.get(1, JsonNull.instance()).asNumber().intValue(), is(123));
        assertThat(result.get(2, JsonNull.instance()).asBoolean().value(), is(true));
    }

    @Test
    public void testJsonValueParserSkip() {
        JsonValue original = JsonString.create("test");
        JsonParser parser = JsonParser.create(original);

        parser.skip();
        // After skip, parser should be "finished"
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testJsonValueParserSkipString() {
        JsonValue original = JsonString.create("hello world");
        JsonParser parser = JsonParser.create(original);

        parser.skip();
        assertThat(parser.hasNext(), is(false));

        // Verify we can't read after skip
        assertThrows(JsonException.class, parser::readString);
    }

    @Test
    public void testJsonValueParserSkipNumber() {
        JsonValue original = JsonNumber.create(new BigDecimal("123.45"));
        JsonParser parser = JsonParser.create(original);

        parser.skip();
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testJsonValueParserSkipBoolean() {
        JsonValue original = JsonBoolean.TRUE;
        JsonParser parser = JsonParser.create(original);

        parser.skip();
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testJsonValueParserSkipNull() {
        JsonValue original = JsonNull.instance();
        JsonParser parser = JsonParser.create(original);

        parser.skip();
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testJsonValueParserSkipObject() {
        JsonValue original = JsonObject.create(
                Map.of("key", JsonString.create("value"))
        );
        JsonParser parser = JsonParser.create(original);

        parser.skip();
        assertThat(parser.hasNext(), is(false));

        // Verify we can't read after skip
        assertThrows(JsonException.class, parser::readJsonObject);
    }

    @Test
    public void testJsonValueParserSkipArray() {
        JsonValue original = JsonArray.create(List.of(
                JsonString.create("item1"),
                JsonNumber.create(new BigDecimal("123"))
        ));
        JsonParser parser = JsonParser.create(original);

        parser.skip();
        assertThat(parser.hasNext(), is(false));

        // Verify we can't read after skip
        assertThrows(JsonException.class, parser::readJsonArray);
    }

    @Test
    public void testJsonValueParserSkipComplexObject() {
        JsonValue original = JsonObject.create(
                Map.of("users", JsonArray.create(
                        JsonObject.create(Map.of(
                                "name", JsonString.create("Alice"),
                                "active", JsonBoolean.TRUE
                        ))
                ))
        );
        JsonParser parser = JsonParser.create(original);

        parser.skip();
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testJsonValueParserSkipAfterRead() {
        JsonValue original = JsonString.create("test");
        JsonParser parser = JsonParser.create(original);

        // Read first, then skip
        assertThat(parser.readString(), is("test"));
        assertThat(parser.hasNext(), is(false));

        // Skip on already consumed parser should be safe
        parser.skip();
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testJsonValueParserReadChar() {
        JsonValue original = JsonString.create("A");
        JsonParser parser = JsonParser.create(original);

        assertThat(parser.readChar(), is('A'));
    }

    @Test
    public void testJsonValueParserReadStringAsHash() {
        JsonValue original = JsonString.create("test");
        JsonParser parser = JsonParser.create(original);

        int hash = parser.readStringAsHash();
        // Hash should be non-zero for non-empty string
        assertThat(hash != 0, is(true));
    }

    @Test
    public void testJsonValueParserNextTokenAdvancesCorrectly() {
        JsonValue original = JsonObject.create(
                Map.of("key", JsonString.create("value"))
        );
        JsonParser parser = JsonParser.create(original);

        // First token should be '{'
        assertThat(parser.currentByte(), is((byte) '{'));
        // Next should be '"'
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("key"));
        assertThat(parser.nextToken(), is((byte) ':'));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("value"));
        assertThat(parser.nextToken(), is((byte) '}'));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testJsonValueParserComplexObject() {
        JsonValue original = JsonObject.create(
                Map.of("users", JsonArray.create(
                        JsonObject.create(Map.of(
                                "name", JsonString.create("Alice"),
                                "age", JsonNumber.create(new BigDecimal("30")),
                                "active", JsonBoolean.TRUE
                        )),
                        JsonObject.create(Map.of(
                                "name", JsonString.create("Bob"),
                                "age", JsonNumber.create(new BigDecimal("25")),
                                "active", JsonBoolean.FALSE
                        ))
                ))
        );
        JsonParser parser = JsonParser.create(original);

        JsonObject result = parser.readJsonObject();
        JsonArray users = result.arrayValue("users").orElseThrow();

        assertThat(users.values().size(), is(2));

        JsonObject alice = users.get(0, JsonNull.instance()).asObject();
        assertThat(alice.stringValue("name").orElseThrow(), is("Alice"));
        assertThat(alice.intValue("age").orElseThrow(), is(30));
        assertThat(alice.booleanValue("active").orElseThrow(), is(true));

        JsonObject bob = users.get(1, JsonNull.instance()).asObject();
        assertThat(bob.stringValue("name").orElseThrow(), is("Bob"));
        assertThat(bob.intValue("age").orElseThrow(), is(25));
        assertThat(bob.booleanValue("active").orElseThrow(), is(false));
    }

}
