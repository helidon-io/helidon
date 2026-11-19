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
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonParserReplayTest {

    @ParameterizedTest
    @EnumSource(Input.class)
    void replaysWithoutFirstMiddleOrLastProperty(Input input) {
        String nested = "\"data\":{\"kind\":\"nested\",\"array\":[1,{\"x\":2}]}";
        List<String> properties = List.of("\"name\":\"sample\"", nested, "\"kind\":\"sample\"");
        for (int index = 0; index < 3; index++) {
            String json = switch (index) {
            case 0 -> "{" + properties.get(2) + "," + properties.get(0) + "," + properties.get(1) + "}";
            case 1 -> "{" + properties.get(0) + "," + properties.get(2) + "," + properties.get(1) + "}";
            default -> "{" + properties.get(0) + "," + properties.get(1) + "," + properties.get(2) + "}";
            };
            JsonParser parser = input.parser("[" + json + ",42]");
            parser.nextToken();
            parser.mark();
            parser.skip();
            parser.resetToMark();
            JsonParser replay = ObjectStartParser.create(parser, index);
            JsonObject result = replay.readJsonObject();
            JsonObject expected = JsonParser.create("{" + properties.get(0) + "," + properties.get(1) + "}")
                    .readJsonObject();
            assertThat(result, is(expected));
            assertThat(parser.nextToken(), is((byte) ','));
            parser.nextToken();
            assertThat(parser.readInt(), is(42));
            assertThat(parser.nextToken(), is((byte) ']'));
        }
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void replayMarkRestoresOmittedPropertyState(Input input) {
        JsonParser parser = input.parser("{\"name\":\"sample\",\"kind\":\"sample\",\"tail\":7}");
        JsonParser replay = ObjectStartParser.create(parser, 1);
        replay.mark();
        JsonObject first = replay.readJsonObject();
        replay.resetToMark();
        assertThat(replay.readJsonObject(), is(first));
        assertThat(first.size(), is(2));
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void skipNestedPrefixPreservesFollowingProperties(Input input) {
        JsonParser parser = input.parser("{\"ignored\":[{\"kind\":\"nested\"}],\"kind\":\"sample\",\"value\":7}");
        JsonParser replay = ObjectStartParser.create(parser, 1);
        replay.nextToken();
        replay.readString();
        replay.nextToken();
        replay.nextToken();
        replay.skip();
        assertThat(replay.nextToken(), is((byte) ','));
        replay.nextToken();
        assertThat(replay.readString(), is("value"));
        replay.nextToken();
        replay.nextToken();
        assertThat(replay.readInt(), is(7));
        assertThat(replay.nextToken(), is((byte) '}'));
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void propertyOmissionPreservesTheCallersMark(Input input) {
        JsonParser parser = input.parser("[{\"name\":\"sample\",\"kind\":\"sample\"},42]");
        parser.mark();
        assertThat(parser.nextToken(), is((byte) '{'));

        JsonParser replay = ObjectStartParser.create(parser, 1);

        assertThat(parser.currentByte(), is((byte) '{'));
        assertThat(replay.readJsonObject(), is(JsonParser.create("{\"name\":\"sample\"}").readJsonObject()));
        parser.resetToMark();
        assertThat(parser.currentByte(), is((byte) '['));
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void propertyOmissionRequiresAPreparedParser(Input input) {
        JsonParser parser = input.parser("{\"kind\":\"sample\"}");
        parser.mark();
        parser.skip();

        assertThrows(JsonException.class, () -> ObjectStartParser.create(parser, 0));

        assertThat(parser.currentByte(), is((byte) '}'));
        JsonParser prepared = input.parser("{\"kind\":\"sample\"}");
        assertThat(ObjectStartParser.create(prepared, 0).readJsonObject(), is(JsonObject.empty()));
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void scalarSkipLeavesSeparatorForNextToken(Input input) {
        JsonParser parser = input.parser("[1,\"value\",true,null,2]");
        for (int i = 0; i < 4; i++) {
            parser.nextToken();
            parser.skip();
            assertThat(parser.nextToken(), is((byte) ','));
        }
        parser.nextToken();
        assertThat(parser.readInt(), is(2));
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void syntheticStartCanBeMarkedAgainAfterReplay(Input input) {
        JsonParser parser = input.parser("{\"kind\":\"sample\",\"name\":\"value\"}");
        parser.nextToken();
        parser.readString();
        parser.nextToken();
        parser.nextToken();
        parser.readString();
        parser.nextToken();
        parser.nextToken();
        JsonParser start = ObjectStartParser.create(parser);
        start.mark();
        start.nextToken();
        start.resetToMark();
        start.nextToken();
        start.mark();
        assertThat(start.readString(), is("name"));
        start.resetToMark();
        assertThat(start.currentByte(), is((byte) '"'));
        assertThat(start.readString(), is("name"));
    }

    @ParameterizedTest
    @EnumSource(Input.class)
    void customObjectReadFromSyntheticStartConsumesNestedValues(Input input) {
        JsonParser parser = input.parser("{\"kind\":\"sample\",\"nested\":{\"list\":[1,2]},\"value\":3}");
        parser.nextToken();
        parser.readString();
        parser.nextToken();
        parser.nextToken();
        parser.readString();
        parser.nextToken();
        parser.nextToken();
        JsonParser start = ObjectStartParser.create(parser);
        assertThat(start.readJsonObject(), is(JsonParser.create("{\"nested\":{\"list\":[1,2]},\"value\":3}")
                                                     .readJsonObject()));
    }

    enum Input {
        ARRAY,
        STREAM,
        VALUE;

        JsonParser parser(String json) {
            return switch (this) {
            case ARRAY -> JsonParser.create(json);
            case STREAM -> JsonParser.create(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), 6);
            case VALUE -> JsonParser.create(JsonParser.create(json).readJsonValue());
            };
        }
    }
}
