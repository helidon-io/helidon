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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class JsonValueParsingTest {

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testJsonStringValueParsing(ParserMethod parserMethod) {
        String json = "\"stringValue\"";
        JsonParser parser = parserMethod.createParser(json);
        JsonValue jsonValue = parser.readJsonValue();

        assertThat(jsonValue.type(), is(JsonValueType.STRING));
        assertThat(jsonValue.asString().value(), is("stringValue"));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testJsonNumberValueParsing(ParserMethod parserMethod) {
        String json = "123";
        JsonParser parser = parserMethod.createParser(json);
        JsonValue jsonValue = parser.readJsonValue();

        assertThat(jsonValue.type(), is(JsonValueType.NUMBER));
        assertThat(jsonValue.asNumber().doubleValue(), is(123.0));

        json = "123.456";
        parser = parserMethod.createParser(json);
        jsonValue = parser.readJsonValue();

        assertThat(jsonValue.type(), is(JsonValueType.NUMBER));
        assertThat(jsonValue.asNumber().doubleValue(), is(123.456));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testJsonObjectParsing(ParserMethod parserMethod) {
        String expected = "value".repeat(6);
        String json = "{\"test\":\"" + expected + "\"}";
        JsonParser parser = parserMethod.createParser(json);
        JsonValue jsonValue = parser.readJsonValue();

        assertThat(jsonValue.type(), is(JsonValueType.OBJECT));
        assertThat(jsonValue.asObject().containsKey("test"), is(true));
        assertThat(jsonValue.asObject().stringValue("test").orElseThrow(), is(expected));
        assertThat(jsonValue.asObject().containsKey("missing"), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testJsonObjectWithNumberParsing(ParserMethod parserMethod) {
        String expected = "123".repeat(3);
        String json = "{\"test\":" + expected + "}";
        JsonParser parser = parserMethod.createParser(json);
        JsonValue jsonValue = parser.readJsonValue();

        assertThat(jsonValue.type(), is(JsonValueType.OBJECT));
        assertThat(jsonValue.asObject().containsKey("test"), is(true));
        assertThat(jsonValue.asObject().numberValue("test").orElseThrow(), is(new BigDecimal(expected)));
        assertThat(jsonValue.asObject().containsKey("missing"), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testJsonBooleanValueParsingTrue(ParserMethod parserMethod) {
        String json = "true";
        JsonParser parser = parserMethod.createParser(json);
        JsonValue jsonValue = parser.readJsonValue();

        assertThat(jsonValue.type(), is(JsonValueType.BOOLEAN));
        assertThat(jsonValue.asBoolean().value(), is(true));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testJsonBooleanValueParsingFalse(ParserMethod parserMethod) {
        String json = "false";
        JsonParser parser = parserMethod.createParser(json);
        JsonValue jsonValue = parser.readJsonValue();

        assertThat(jsonValue.type(), is(JsonValueType.BOOLEAN));
        assertThat(jsonValue.asBoolean().value(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testJsonObjectWithBooleanParsing(ParserMethod parserMethod) {
        String json = "{\"flag\":true,\"enabled\":false}";
        JsonParser parser = parserMethod.createParser(json);
        JsonValue jsonValue = parser.readJsonValue();

        assertThat(jsonValue.type(), is(JsonValueType.OBJECT));
        assertThat(jsonValue.asObject().booleanValue("flag").orElseThrow(), is(true));
        assertThat(jsonValue.asObject().booleanValue("enabled").orElseThrow(), is(false));
        assertThat(jsonValue.asObject().containsKey("missing"), is(false));
    }

}
