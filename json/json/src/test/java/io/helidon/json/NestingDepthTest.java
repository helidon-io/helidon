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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NestingDepthTest {

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void readJsonArrayAllowsMaximumDepth(ParserMethod parserMethod) {
        JsonParser parser = parserMethod.createParser(nestedArrays(JsonParser.MAX_NESTING_DEPTH));
        JsonArray array = parser.readJsonArray();

        assertThat(array.type(), is(JsonValueType.ARRAY));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void readJsonObjectAllowsMaximumDepth(ParserMethod parserMethod) {
        JsonParser parser = parserMethod.createParser(nestedObjects(JsonParser.MAX_NESTING_DEPTH));
        JsonObject object = parser.readJsonObject();

        assertThat(object.type(), is(JsonValueType.OBJECT));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void readJsonArrayRejectsExcessiveDepth(ParserMethod parserMethod) {
        JsonParser parser = parserMethod.createParser(nestedArrays(JsonParser.MAX_NESTING_DEPTH + 1));
        JsonException exception = assertThrows(JsonException.class, parser::readJsonArray);

        assertThat(exception.getMessage(), containsString("Maximum JSON nesting depth exceeded"));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void readJsonObjectRejectsExcessiveDepth(ParserMethod parserMethod) {
        JsonParser parser = parserMethod.createParser(nestedObjects(JsonParser.MAX_NESTING_DEPTH + 1));
        JsonException exception = assertThrows(JsonException.class, parser::readJsonObject);

        assertThat(exception.getMessage(), containsString("Maximum JSON nesting depth exceeded"));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void skipArrayRejectsExcessiveDepth(ParserMethod parserMethod) {
        JsonParser parser = parserMethod.createParser(nestedArrays(JsonParser.MAX_NESTING_DEPTH + 1));
        JsonException exception = assertThrows(JsonException.class, parser::skip);

        assertThat(exception.getMessage(), containsString("Maximum JSON nesting depth exceeded"));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void skipObjectRejectsExcessiveDepth(ParserMethod parserMethod) {
        JsonParser parser = parserMethod.createParser(nestedObjects(JsonParser.MAX_NESTING_DEPTH + 1));
        JsonException exception = assertThrows(JsonException.class, parser::skip);

        assertThat(exception.getMessage(), containsString("Maximum JSON nesting depth exceeded"));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    void failedReadDoesNotRetainNestingDepth(ParserMethod parserMethod) {
        JsonParser parser = parserMethod.createParser(nestedArrays(JsonParser.MAX_NESTING_DEPTH + 1));

        assertThrows(JsonException.class, parser::readJsonArray);

        parser.skip();
    }

    private static String nestedArrays(int depth) {
        StringBuilder json = new StringBuilder(depth * 2 + 1);
        for (int i = 0; i < depth; i++) {
            json.append('[');
        }
        json.append('0');
        for (int i = 0; i < depth; i++) {
            json.append(']');
        }
        return json.toString();
    }

    private static String nestedObjects(int depth) {
        StringBuilder json = new StringBuilder(depth * 6 + 1);
        for (int i = 0; i < depth; i++) {
            json.append("{\"a\":");
        }
        json.append('0');
        for (int i = 0; i < depth; i++) {
            json.append('}');
        }
        return json.toString();
    }
}
