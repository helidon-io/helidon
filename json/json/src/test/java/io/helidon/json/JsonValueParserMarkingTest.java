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
 * Tests for JsonValueParser marking mechanism.
 * JsonValueParser uses a replay queue to implement marking/resetToMark functionality.
 */
class JsonValueParserMarkingTest {

    @Test
    public void testMarkAndResetToMarkWithString() {
        JsonValue original = JsonString.create("test string");
        JsonParser parser = JsonParser.create(original);

        // Mark current position
        parser.mark();

        // Read the value
        assertThat(parser.readString(), is("test string"));
        assertThat(parser.hasNext(), is(false));

        // Reset to mark should restore the value
        parser.resetToMark();
        assertThat(parser.readString(), is("test string"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testMarkAndResetToMarkWithNumber() {
        JsonValue original = JsonNumber.create(new BigDecimal("123.45"));
        JsonParser parser = JsonParser.create(original);

        parser.mark();
        assertThat(parser.readDouble(), is(123.45));
        assertThat(parser.hasNext(), is(false));

        parser.resetToMark();
        assertThat(parser.readDouble(), is(123.45));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testMarkAndResetToMarkWithBoolean() {
        JsonValue original = JsonBoolean.TRUE;
        JsonParser parser = JsonParser.create(original);

        parser.mark();
        assertThat(parser.readBoolean(), is(true));
        assertThat(parser.hasNext(), is(false));

        parser.resetToMark();
        assertThat(parser.readBoolean(), is(true));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testMarkAndResetToMarkWithNull() {
        JsonValue original = JsonNull.instance();
        JsonParser parser = JsonParser.create(original);

        parser.mark();
        assertThat(parser.checkNull(), is(true));
        assertThat(parser.hasNext(), is(false));

        parser.resetToMark();
        assertThat(parser.checkNull(), is(true));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testMarkAndResetToMarkWithObject() {
        JsonValue original = JsonObject.create(
                Map.of("key1", JsonString.create("value1"),
                       "key2", JsonNumber.create(new BigDecimal("42")))
        );
        JsonParser parser = JsonParser.create(original);

        parser.mark();

        // Read part of the object structure
        assertThat(parser.currentByte(), is((byte) '{'));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("key1"));
        assertThat(parser.nextToken(), is((byte) ':'));

        // Reset should restore to beginning
        parser.resetToMark();
        assertThat(parser.currentByte(), is((byte) '{'));
        JsonObject result = parser.readJsonObject();
        assertThat(result.stringValue("key1").orElseThrow(), is("value1"));
        assertThat(result.intValue("key2").orElseThrow(), is(42));
    }

    @Test
    public void testMarkAndResetToMarkWithArray() {
        JsonValue original = JsonArray.create(List.of(
                JsonString.create("item1"),
                JsonNumber.create(new BigDecimal("123")),
                JsonBoolean.TRUE)
        );
        JsonParser parser = JsonParser.create(original);

        parser.mark();

        // Read part of the array structure
        assertThat(parser.currentByte(), is((byte) '['));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("item1"));

        // Reset should restore to beginning
        parser.resetToMark();
        assertThat(parser.currentByte(), is((byte) '['));
        JsonArray result = parser.readJsonArray();
        assertThat(result.values().size(), is(3));
        assertThat(result.get(0, JsonNull.instance()).asString().value(), is("item1"));
        assertThat(result.get(1, JsonNull.instance()).asNumber().intValue(), is(123));
        assertThat(result.get(2, JsonNull.instance()).asBoolean().value(), is(true));
    }

    @Test
    public void testMarkAndDumpMark() {
        JsonValue original = JsonString.create("test");
        JsonParser parser = JsonParser.create(original);

        parser.mark();
        parser.clearMark();

        // Should not be able to reset after dump
        assertThrows(IllegalStateException.class, parser::resetToMark);
    }

    @Test
    public void testMultipleMarksThrowsException() {
        JsonValue original = JsonString.create("test");
        JsonParser parser = JsonParser.create(original);

        parser.mark();

        // Second mark should throw exception since replayMarked is already true
        assertThrows(IllegalStateException.class, parser::mark);
    }

    @Test
    public void testResetToMarkWithoutMark() {
        JsonValue original = JsonString.create("test");
        JsonParser parser = JsonParser.create(original);

        // Reset without mark should throw exception
        assertThrows(IllegalStateException.class, parser::resetToMark);
    }

    @Test
    public void testResetToMarkAfterDumpMark() {
        JsonValue original = JsonString.create("test");
        JsonParser parser = JsonParser.create(original);

        parser.mark();
        parser.clearMark();

        // Reset after dump should throw exception
        assertThrows(IllegalStateException.class, parser::resetToMark);
    }

    @Test
    public void testMarkAfterResetToMark() {
        JsonValue original = JsonString.create("test");
        JsonParser parser = JsonParser.create(original);

        parser.mark();
        parser.resetToMark();

        // Should be able to mark again after reset
        parser.mark();
        assertThat(parser.readString(), is("test"));
    }

    @Test
    public void testComplexObjectWithMarkReset() {
        JsonValue original = JsonObject.create(
                Map.of("users", JsonArray.create(
                        JsonObject.create(Map.of(
                                "name", JsonString.create("Alice"),
                                "active", JsonBoolean.TRUE
                        )),
                        JsonObject.create(Map.of(
                                "name", JsonString.create("Bob"),
                                "active", JsonBoolean.FALSE
                        ))
                ))
        );
        JsonParser parser = JsonParser.create(original);

        // Read to a certain point
        assertThat(parser.currentByte(), is((byte) '{'));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("users"));
        assertThat(parser.nextToken(), is((byte) ':'));

        // Mark before reading array
        parser.mark();
        assertThat(parser.nextToken(), is((byte) '['));

        // Read first array element
        assertThat(parser.nextToken(), is((byte) '{'));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("name"));
        assertThat(parser.nextToken(), is((byte) ':'));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("Alice"));

        // Reset to mark (back to array start)
        parser.resetToMark();
        assertThat(parser.nextToken(), is((byte) '['));
        JsonArray users = parser.readJsonArray();

        assertThat(users.values().size(), is(2));
        JsonObject alice = users.get(0, JsonNull.instance()).asObject();
        assertThat(alice.stringValue("name").orElseThrow(), is("Alice"));
        assertThat(alice.booleanValue("active").orElseThrow(), is(true));
    }

    @Test
    public void testMarkAndSkip() {
        JsonValue original = JsonObject.create(
                Map.of("skipMe", JsonString.create("skipped"),
                       "keepMe", JsonString.create("kept"))
        );
        JsonParser parser = JsonParser.create(original);

        assertThat(parser.currentByte(), is((byte) '{'));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("skipMe"));
        assertThat(parser.nextToken(), is((byte) ':'));

        parser.mark();

        // Skip the value
        parser.skip();

        // Reset and read instead
        parser.resetToMark();
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("skipped"));
        assertThat(parser.nextToken(), is((byte) ','));

        // Continue reading
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("keepMe"));
        assertThat(parser.nextToken(), is((byte) ':'));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("kept"));
    }

    @Test
    public void testNestedMarkingNotSupported() {
        // JsonValueParser doesn't support nested marks like other parsers
        JsonValue original = JsonString.create("test");
        JsonParser parser = JsonParser.create(original);

        parser.mark();

        // Cannot mark again - this is expected behavior for JsonValueParser
        assertThrows(IllegalStateException.class, parser::mark);
    }

}