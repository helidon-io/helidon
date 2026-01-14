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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for JsonStreamParser buffer management and edge cases.
 * Covers different buffer sizes, buffer overflow scenarios, and streaming behavior.
 */
class StreamBufferTest {

    @Test
    public void testStreamParserWithSmallBuffer() {
        String json = "{\"key\":\"value\"}";
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(jsonBytes);

        // Use a very small buffer to force multiple reads
        JsonParser parser = JsonParser.create(inputStream, 6);

        JsonObject result = parser.readJsonObject();
        assertThat(result.stringValue("key").orElseThrow(), is("value"));
    }

    @Test
    public void testStreamParserWithBufferSizeEqualToContent() {
        String json = "[1,2,3]";
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(jsonBytes);

        // Buffer size exactly matches content
        JsonParser parser = JsonParser.create(inputStream, jsonBytes.length);

        JsonArray result = parser.readJsonArray();
        assertThat(result.values().size(), is(3));
        assertThat(result.get(0, JsonNull.instance()).asNumber().intValue(), is(1));
        assertThat(result.get(1, JsonNull.instance()).asNumber().intValue(), is(2));
        assertThat(result.get(2, JsonNull.instance()).asNumber().intValue(), is(3));
    }

    @Test
    public void testStreamParserWithLargeBuffer() {
        String json = "\"simple string\"";
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(jsonBytes);

        // Use a large buffer
        JsonParser parser = JsonParser.create(inputStream, 1000);

        String result = parser.readString();
        assertThat(result, is("simple string"));
    }

    @Test
    public void testStreamParserWithStringSpanningBuffers() {
        // Create a long string that will span multiple buffer reads
        StringBuilder longString = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longString.append("word").append(i).append(" ");
        }
        String content = longString.toString().trim();
        String json = "\"" + content + "\"";

        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(jsonBytes);

        // Small buffer to force multiple reads during string parsing
        JsonParser parser = JsonParser.create(inputStream, 50);

        String result = parser.readString();
        assertThat(result, is(content));
    }

    @Test
    public void testStreamParserWithNumberSpanningBuffers() {
        // Large number that spans buffers
        String json = "1234567890123456789012345678901234567890";

        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(jsonBytes);

        // Small buffer
        JsonParser parser = JsonParser.create(inputStream, 10);

        BigDecimal result = new BigDecimal(parser.readCharArray());
        assertThat(result, is(new BigDecimal(json)));
    }

    @Test
    public void testStreamParserBufferExpansion() {
        // Create JSON that requires buffer expansion during parsing
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\"data\":[");
        for (int i = 0; i < 100; i++) {
            if (i > 0) {
                jsonBuilder.append(",");
            }
            jsonBuilder.append("{\"id\":").append(i).append(",\"value\":\"item").append(i).append("\"}");
        }
        jsonBuilder.append("]}");

        String json = jsonBuilder.toString();
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(jsonBytes);

        // Start with very small buffer that will need expansion
        JsonParser parser = JsonParser.create(inputStream, 16);

        JsonObject result = parser.readJsonObject();
        JsonArray data = result.arrayValue("data").orElseThrow();
        assertThat(data.values().size(), is(100));

        for (int i = 0; i < 100; i++) {
            JsonObject item = data.get(i, JsonNull.instance()).asObject();
            assertThat(item.intValue("id").orElseThrow(), is(i));
            assertThat(item.stringValue("value").orElseThrow(), is("item" + i));
        }
    }

    @Test
    public void testStreamParserWithEmptyStream() {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[0]);
        JsonParser parser = JsonParser.create(inputStream, 10);

        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testStreamParserWithVerySmallBuffer() {
        String json = "42";
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(jsonBytes);

        // Minimum allowed buffer size is 6
        JsonParser parser = JsonParser.create(inputStream, 6);

        int result = parser.readInt();
        assertThat(result, is(42));
    }

    @Test
    public void testStreamParserMultipleReads() {
        String json = "[\"first\",\"second\",\"third\"]";
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(jsonBytes);

        JsonParser parser = JsonParser.create(inputStream, 8);

        JsonArray result = parser.readJsonArray();
        assertThat(result.values().size(), is(3));
        assertThat(result.get(0, JsonNull.instance()).asString().value(), is("first"));
        assertThat(result.get(1, JsonNull.instance()).asString().value(), is("second"));
        assertThat(result.get(2, JsonNull.instance()).asString().value(), is("third"));
    }

}
