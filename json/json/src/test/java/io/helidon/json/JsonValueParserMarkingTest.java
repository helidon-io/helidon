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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for JsonValueParser marking mechanism.
 * JsonValueParser snapshots traversal state to implement marking/resetToMark functionality.
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
        JsonValue original = JsonObject.builder()
                .set("key1", JsonString.create("value1"))
                .set("key2", JsonNumber.create(new BigDecimal("42")))
                .build();
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
    public void testMarkAndResetToMarkWithEmptyObject() {
        assertMarkAndResetToMarkWithEmptyContainer(JsonObject.empty(), (byte) '{', (byte) '}');
    }

    @Test
    public void testMarkAndResetToMarkWithEmptyArray() {
        assertMarkAndResetToMarkWithEmptyContainer(JsonArray.empty(), (byte) '[', (byte) ']');
    }

    @Test
    public void testMarkAndResetToMarkBeforeNestedEmptyContainers() {
        assertMarkAndResetToMarkBeforeNestedEmptyContainer(JsonObject.empty(), (byte) '{', (byte) '}');
        assertMarkAndResetToMarkBeforeNestedEmptyContainer(JsonArray.empty(), (byte) '[', (byte) ']');
    }

    @Test
    public void testRepeatedMarkAndResetToMarkWithEmptyContainers() {
        assertRepeatedMarkAndResetToMarkWithEmptyContainer(JsonObject.empty(), (byte) '{', (byte) '}');
        assertRepeatedMarkAndResetToMarkWithEmptyContainer(JsonArray.empty(), (byte) '[', (byte) ']');
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
                        JsonObject.builder()
                                .set("name", "Alice")
                                .set("active", true)
                                .build(),
                        JsonObject.builder()
                                .set("name", "Bob")
                                .set("active", false)
                                .build()
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
        JsonValue original = JsonObject.builder()
                .set("skipMe", JsonString.create("skipped"))
                .set("keepMe", JsonString.create("kept"))
                .build();
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

    @Test
    void testMarkSkipResetAndSkipRootScalars() {
        List<JsonValue> values = List.of(JsonString.create("value"),
                                         JsonNumber.create(42),
                                         JsonBoolean.TRUE,
                                         JsonNull.instance());

        for (JsonValue value : values) {
            JsonParser parser = JsonParser.create(value);
            parser.mark();
            parser.skip();
            assertThat(parser.hasNext(), is(false));

            parser.resetToMark();
            assertThat(parser.readJsonValue(), is(value));
            parser.skip();
            assertThat(parser.hasNext(), is(false));
        }
    }

    @Test
    void testResetRestoresEveryNestedTokenBoundary() {
        JsonValue original = JsonObject.builder()
                .set("array", JsonArray.create(
                        JsonObject.builder().set("name", "value").build(),
                        JsonArray.empty(),
                        JsonNumber.create(7)))
                .set("tail", true)
                .build();
        List<String> expected = remainingTokens(JsonParser.create(original));

        for (int markPosition = 0; markPosition < expected.size(); markPosition++) {
            JsonParser parser = JsonParser.create(original);
            for (int i = 0; i < markPosition; i++) {
                parser.nextToken();
            }

            parser.mark();
            List<String> beforeReset = remainingTokens(parser);
            parser.resetToMark();
            List<String> afterReset = remainingTokens(parser);

            assertThat(beforeReset, is(expected.subList(markPosition, expected.size())));
            assertThat(afterReset, is(beforeReset));
        }
    }

    @Test
    void testMarkResetAndRepeatedlySkipNestedContainer() {
        assertMarkResetAndRepeatedlySkipNestedContainer(
                JsonObject.builder().set("nested", JsonArray.createStrings(List.of("one", "two"))).build(),
                (byte) '}');
        assertMarkResetAndRepeatedlySkipNestedContainer(
                JsonArray.create(JsonString.create("one"), JsonObject.builder().set("two", 2).build()),
                (byte) ']');
    }

    @Test
    void testResetRestoresFramesBeyondInitialCapacity() {
        JsonValue value = JsonString.create("leaf");
        int nestingDepth = 10;
        for (int i = 0; i < nestingDepth; i++) {
            value = JsonArray.create(value);
        }

        JsonParser parser = JsonParser.create(value);
        for (int i = 1; i < nestingDepth; i++) {
            assertThat(parser.nextToken(), is((byte) '['));
        }
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("leaf"));

        List<String> expected = null;
        for (int i = 0; i < 2; i++) {
            parser.mark();
            List<String> actual = remainingTokens(parser);
            parser.resetToMark();
            if (expected == null) {
                expected = actual;
            } else {
                assertThat(actual, is(expected));
            }
        }
        assertThat(remainingTokens(parser), is(expected));
    }

    private static void assertMarkResetAndRepeatedlySkipNestedContainer(JsonValue nestedContainer, byte endToken) {
        JsonParser parser = JsonParser.create(JsonArray.create(nestedContainer, JsonString.create("after")));

        assertThat(parser.nextToken(), is(nestedContainer.jsonStartChar()));
        parser.mark();
        parser.skip();
        assertThat(parser.currentByte(), is(endToken));

        parser.resetToMark();
        assertThat(parser.currentByte(), is(nestedContainer.jsonStartChar()));
        parser.skip();
        assertThat(parser.currentByte(), is(endToken));
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("after"));
        assertThat(parser.nextToken(), is((byte) ']'));
        assertThat(parser.hasNext(), is(false));
    }

    private static List<String> remainingTokens(JsonParser parser) {
        List<String> result = new ArrayList<>();
        result.add(currentToken(parser));
        while (parser.hasNext()) {
            parser.nextToken();
            result.add(currentToken(parser));
        }
        return result;
    }

    private static String currentToken(JsonParser parser) {
        return switch (parser.currentByte()) {
        case '"' -> "\"" + parser.readString();
        case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> "#" + parser.readBigDecimal().toPlainString();
        default -> Character.toString(parser.currentByte());
        };
    }

    private static void assertMarkAndResetToMarkWithEmptyContainer(JsonValue value, byte start, byte end) {
        JsonParser parser = JsonParser.create(value);

        parser.mark();
        assertThat(parser.currentByte(), is(start));
        assertThat(parser.nextToken(), is(end));
        assertThat(parser.hasNext(), is(false));

        parser.resetToMark();
        assertThat(parser.currentByte(), is(start));
        assertThat(parser.nextToken(), is(end));
        assertThat(parser.hasNext(), is(false));
    }

    private static void assertMarkAndResetToMarkBeforeNestedEmptyContainer(JsonValue value, byte start, byte end) {
        JsonParser parser = JsonParser.create(JsonArray.create(JsonString.create("before"), value, JsonString.create("after")));

        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("before"));
        assertThat(parser.nextToken(), is((byte) ','));
        parser.mark();
        assertThat(parser.nextToken(), is(start));
        assertThat(parser.nextToken(), is(end));

        parser.resetToMark();
        assertThat(parser.currentByte(), is((byte) ','));
        assertThat(parser.nextToken(), is(start));
        assertThat(parser.nextToken(), is(end));
        assertThat(parser.nextToken(), is((byte) ','));
        assertThat(parser.nextToken(), is((byte) '"'));
        assertThat(parser.readString(), is("after"));
        assertThat(parser.nextToken(), is((byte) ']'));
        assertThat(parser.hasNext(), is(false));
    }

    private static void assertRepeatedMarkAndResetToMarkWithEmptyContainer(JsonValue value, byte start, byte end) {
        JsonParser parser = JsonParser.create(value);

        parser.mark();
        assertThat(parser.nextToken(), is(end));
        parser.resetToMark();
        assertThat(parser.currentByte(), is(start));

        parser.mark();
        parser.resetToMark();
        assertThat(parser.currentByte(), is(start));
        assertThat(parser.nextToken(), is(end));
        assertThat(parser.hasNext(), is(false));
    }

}
