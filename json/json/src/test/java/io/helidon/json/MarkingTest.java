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
 * Tests for JsonParser marking mechanism.
 * Covers mark, resetToMark, and dumpMark functionality.
 */
abstract class MarkingTest {

    @Test
    public void testMarkAndResetToMarkWithString() {
        String json = "\"test string\"";
        JsonParser parser = createParser(json);

        // Mark current position
        parser.mark();

        // Read the value
        assertThat(parser.readString(), is("test string"));
        assertThat(parser.hasNext(), is(false));

        // Reset to mark should restore the position
        parser.resetToMark();
        assertThat(parser.readString(), is("test string"));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testMarkAndResetToMarkWithNumber() {
        String json = "123.45";
        JsonParser parser = createParser(json);

        parser.mark();
        assertThat(parser.readDouble(), is(123.45));
        assertThat(parser.hasNext(), is(false));

        parser.resetToMark();
        assertThat(parser.readDouble(), is(123.45));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testMarkAndResetToMarkWithBoolean() {
        String json = "true";
        JsonParser parser = createParser(json);

        parser.mark();
        assertThat(parser.readBoolean(), is(true));
        assertThat(parser.hasNext(), is(false));

        parser.resetToMark();
        assertThat(parser.readBoolean(), is(true));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testMarkAndResetToMarkWithNull() {
        String json = "null";
        JsonParser parser = createParser(json);

        parser.mark();
        assertThat(parser.checkNull(), is(true));
        assertThat(parser.hasNext(), is(false));

        parser.resetToMark();
        assertThat(parser.checkNull(), is(true));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testMarkAndResetToMarkWithObject() {
        String json = "{\"key1\": \"value1\", \"key2\": 42}";
        JsonParser parser = createParser(json);

        parser.mark();

        // Read part of the object structure
        assertThat(parser.currentByte(), is((byte) '{'));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("key1"));
        assertThat(parser.nextToken(), is((byte) ':'));

        // Reset should restore to beginning
        parser.resetToMark();
        JsonObject result = parser.readJsonObject();
        assertThat(result.stringValue("key1").orElseThrow(), is("value1"));
        assertThat(result.intValue("key2").orElseThrow(), is(42));
    }

    @Test
    public void testMarkAndResetToMarkWithArray() {
        String json = "[\"item1\", 123, true]";
        JsonParser parser = createParser(json);

        parser.mark();

        // Read part of the array structure
        assertThat(parser.currentByte(), is((byte) '['));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("item1"));

        // Reset should restore to beginning
        parser.resetToMark();
        JsonArray result = parser.readJsonArray();
        assertThat(result.values().size(), is(3));
        assertThat(result.get(0, JsonNull.instance()).asString().value(), is("item1"));
        assertThat(result.get(1, JsonNull.instance()).asNumber().intValue(), is(123));
        assertThat(result.get(2, JsonNull.instance()).asBoolean().value(), is(true));
    }

    @Test
    public void testMarkAndDumpMark() {
        String json = "\"test\"";
        JsonParser parser = createParser(json);

        parser.mark();
        parser.dumpMark();

        // Should not be able to reset after dump
        assertThrows(IllegalStateException.class, parser::resetToMark);
    }

    @Test
    public void testMultipleMarksThrowsException() {
        String json = "\"test\"";
        JsonParser parser = createParser(json);

        parser.mark();

        // Second mark should throw exception since replayMarked is already true
        assertThrows(IllegalStateException.class, parser::mark);
    }

    @Test
    public void testResetToMarkWithoutMark() {
        String json = "\"test\"";
        JsonParser parser = createParser(json);

        // Reset without mark should throw exception
        assertThrows(IllegalStateException.class, parser::resetToMark);
    }

    @Test
    public void testResetToMarkAfterDumpMark() {
        String json = "\"test\"";
        JsonParser parser = createParser(json);

        parser.mark();
        parser.dumpMark();

        // Reset after dump should throw exception
        assertThrows(IllegalStateException.class, parser::resetToMark);
    }

    @Test
    public void testMarkAfterResetToMark() {
        String json = "\"test\"";
        JsonParser parser = createParser(json);

        parser.mark();
        parser.resetToMark();

        // Should be able to mark again after reset
        parser.mark();
        assertThat(parser.readString(), is("test"));
    }

    @Test
    public void testComplexObjectWithMarkReset() {
        String json = "{\"users\": [{\"name\": \"Alice\", \"active\": true}, {\"name\": \"Bob\", \"active\": false}]}";
        JsonParser parser = createParser(json);

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
        String json = "{\"skipMe\": \"skipped\", \"keepMe\": \"kept\"}";
        JsonParser parser = createParser(json);

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
    public void testMarkInMiddleOfTokenReading() {
        String json = "{\"key\": \"value\"}";
        JsonParser parser = createParser(json);

        // Start reading object
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("key"));
        assertThat(parser.nextToken(), is((byte) ':'));

        // Mark right before reading value
        parser.mark();

        // Read part of the value token
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString().charAt(0), is('v')); // Read first char

        // Reset should go back to before the value
        parser.resetToMark();
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("value"));
    }

    @Test
    public void testMarkResetWithNestedStructures() {
        String json = "{\"data\": {\"nested\": [1, 2, {\"deep\": \"value\"}]}}";
        JsonParser parser = createParser(json);

        // Navigate deep into the structure
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("data"));
        assertThat(parser.nextToken(), is((byte) ':'));
        assertThat(parser.nextToken(), is((byte) '{'));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("nested"));
        assertThat(parser.nextToken(), is((byte) ':'));

        // Mark before reading array
        parser.mark();
        assertThat(parser.nextToken(), is((byte) '['));
        assertThat(parser.nextToken(), is((byte) '1'));
        assertThat(parser.readInt(), is(1));

        // Reset to mark
        parser.resetToMark();
        assertThat(parser.nextToken(), is((byte) '['));
        JsonArray array = parser.readJsonArray();

        assertThat(array.values().size(), is(3));
        assertThat(array.get(0, JsonNull.instance()).asNumber().intValue(), is(1));
        assertThat(array.get(1, JsonNull.instance()).asNumber().intValue(), is(2));
        JsonObject deep = array.get(2, JsonNull.instance()).asObject();
        assertThat(deep.stringValue("deep").orElseThrow(), is("value"));
    }

    @Test
    public void testMarkResetWithWhitespace() {
        String json = "{\n  \"key\"  :   \"value\"   \n}";
        JsonParser parser = createParser(json);

        // Skip whitespace and start reading
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("key"));
        assertThat(parser.nextToken(), is((byte) ':'));

        // Mark before value (after whitespace)
        parser.mark();

        // Skip to value
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("value"));

        // Reset should work correctly with whitespace
        parser.resetToMark();
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("value"));
    }

    @Test
    public void testMarkResetMultipleTimes() {
        String json = "[1, 2, 3, 4, 5]";
        JsonParser parser = createParser(json);

        // Mark at beginning
        parser.mark();

        // Read first element
        assertThat(parser.nextToken(), is((byte) '1'));
        assertThat(parser.readInt(), is(1));

        // Reset and read again
        parser.resetToMark();
        assertThat(parser.nextToken(), is((byte) '1'));
        assertThat(parser.readInt(), is(1));

        // Mark again at new position
        parser.mark();
        assertThat(parser.nextToken(), is((byte) ','));

        // Reset to second mark
        parser.resetToMark();
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '2'));
        assertThat(parser.readInt(), is(2));
    }

    abstract JsonParser createParser(String template);

}