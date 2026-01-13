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

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

abstract class JsonValueParsingTest {

    @Test
    public void testJsonStringValueParsing() {
        String json = "\"stringValue\"";
        JsonParser parser = createParser(json);
        JsonValue jsonValue = parser.readJsonValue();

        assertThat(jsonValue.type(), is(JsonValueType.STRING));
        assertThat(jsonValue.asString().value(), is("stringValue"));
    }

    @Test
    public void testJsonNumberValueParsing() {
        String json = "123";
        JsonParser parser = createParser(json);
        JsonValue jsonValue = parser.readJsonValue();

        assertThat(jsonValue.type(), is(JsonValueType.NUMBER));
        assertThat(jsonValue.asNumber().doubleValue(), is(123.0));

        json = "123.456";
        parser = createParser(json);
        jsonValue = parser.readJsonValue();

        assertThat(jsonValue.type(), is(JsonValueType.NUMBER));
        assertThat(jsonValue.asNumber().doubleValue(), is(123.456));
    }

    @Test
    public void testJsonObjectParsing() {
        String expected = "value".repeat(6);
        String json = "{\"test\":\"" + expected + "\"}";
        JsonParser parser = JsonParser.create(json);
        JsonValue jsonValue = parser.readJsonValue();

        assertThat(jsonValue.type(), is(JsonValueType.OBJECT));
        assertThat(jsonValue.asObject().containsKey("test"), is(true));
        assertThat(jsonValue.asObject().stringValue("test").orElseThrow(), is(expected));
        assertThat(jsonValue.asObject().containsKey("missing"), is(false));
    }

    @Test
    public void testJsonObjectWithNumberParsing() {
        String expected = "123".repeat(3);
        String json = "{\"test\":" + expected + "}";
        JsonParser parser = JsonParser.create(json);
        JsonValue jsonValue = parser.readJsonValue();

        assertThat(jsonValue.type(), is(JsonValueType.OBJECT));
        assertThat(jsonValue.asObject().containsKey("test"), is(true));
        assertThat(jsonValue.asObject().numberValue("test").orElseThrow(), is(new BigDecimal(expected)));
        assertThat(jsonValue.asObject().containsKey("missing"), is(false));
    }

    @Test
    public void testJsonBooleanValueParsingTrue() {
        String json = "true";
        JsonParser parser = createParser(json);
        JsonValue jsonValue = parser.readJsonValue();

        assertThat(jsonValue.type(), is(JsonValueType.BOOLEAN));
        assertThat(jsonValue.asBoolean().value(), is(true));
    }

    @Test
    public void testJsonBooleanValueParsingFalse() {
        String json = "false";
        JsonParser parser = createParser(json);
        JsonValue jsonValue = parser.readJsonValue();

        assertThat(jsonValue.type(), is(JsonValueType.BOOLEAN));
        assertThat(jsonValue.asBoolean().value(), is(false));
    }

    @Test
    public void testJsonObjectWithBooleanParsing() {
        String json = "{\"flag\":true,\"enabled\":false}";
        JsonParser parser = JsonParser.create(json);
        JsonValue jsonValue = parser.readJsonValue();

        assertThat(jsonValue.type(), is(JsonValueType.OBJECT));
        assertThat(jsonValue.asObject().booleanValue("flag").orElseThrow(), is(true));
        assertThat(jsonValue.asObject().booleanValue("enabled").orElseThrow(), is(false));
        assertThat(jsonValue.asObject().containsKey("missing"), is(false));
    }

    abstract JsonParser createParser(String template);

}
