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

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SingleValueTest {

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseString(ParserMethod parserMethod) {
        String expected = "Test String value";
        JsonParser parser = parserMethod.createParser("\"" + expected + "\"");

        assertThat(parser.readString(), is(expected));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseByte(ParserMethod parserMethod) {
        byte expected = 125;
        String template = "125";
        JsonParser parser = parserMethod.createParser(template);

        assertThat(parser.readByte(), is(expected));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseShort(ParserMethod parserMethod) {
        short expected = 12345;
        String template = "12345";
        JsonParser parser = parserMethod.createParser(template);

        assertThat(parser.readShort(), is(expected));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseInt(ParserMethod parserMethod) {
        int expected = 1234;
        String template = "1234";
        JsonParser parser = parserMethod.createParser(template);

        assertThat(parser.readInt(), is(expected));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseLong(ParserMethod parserMethod) {
        long expected = 123456789123456L;
        String template = "123456789123456";
        JsonParser parser = parserMethod.createParser(template);

        assertThat(parser.readLong(), is(expected));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseDouble(ParserMethod parserMethod) {
        double expected = 123.456e10;
        String template = "123.456e10";
        JsonParser parser = parserMethod.createParser(template);

        assertThat(parser.readDouble(), is(expected));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseFloat(ParserMethod parserMethod) {
        float expected = 123.456e10F;
        String template = "123.456e10";
        JsonParser parser = parserMethod.createParser(template);

        assertThat(parser.readFloat(), is(expected));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseQuotedDoubleNaN(ParserMethod parserMethod) {
        JsonParser parser = parserMethod.createParser("\"NaN\"");

        assertThat(Double.isNaN(parser.readDouble()), is(true));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseQuotedDoublePositiveInfinity(ParserMethod parserMethod) {
        JsonParser parser = parserMethod.createParser("\"Infinity\"");

        assertThat(parser.readDouble(), is(Double.POSITIVE_INFINITY));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseQuotedDoubleNegativeInfinity(ParserMethod parserMethod) {
        JsonParser parser = parserMethod.createParser("\"-Infinity\"");

        assertThat(parser.readDouble(), is(Double.NEGATIVE_INFINITY));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseQuotedFloatNaN(ParserMethod parserMethod) {
        JsonParser parser = parserMethod.createParser("\"NaN\"");

        assertThat(Float.isNaN(parser.readFloat()), is(true));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseQuotedFloatPositiveInfinity(ParserMethod parserMethod) {
        JsonParser parser = parserMethod.createParser("\"Infinity\"");

        assertThat(parser.readFloat(), is(Float.POSITIVE_INFINITY));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseQuotedFloatNegativeInfinity(ParserMethod parserMethod) {
        JsonParser parser = parserMethod.createParser("\"-Infinity\"");

        assertThat(parser.readFloat(), is(Float.NEGATIVE_INFINITY));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseBoolean(ParserMethod parserMethod) {
        boolean expected = true;
        String template = "true";
        JsonParser parser = parserMethod.createParser(template);

        assertThat(parser.readBoolean(), is(expected));
        assertThat(parser.hasNext(), is(false));

        expected = false;
        template = "false";
        parser = parserMethod.createParser(template);

        assertThat(parser.readBoolean(), is(expected));
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testParseNull(ParserMethod parserMethod) {
        String template = "null";
        JsonParser parser = parserMethod.createParser(template);

        assertThat(parser.checkNull(), is(true));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseSelectedByteArraySlice() {
        JsonParser parser = JsonParser.create("x42y".getBytes(StandardCharsets.UTF_8), 1, 2);

        assertThat(parser.readInt(), is(42));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testRejectsEmptyByteArraySlice() {
        assertThrows(JsonException.class, () -> JsonParser.create(new byte[] {1}, 1, 0));
    }

}
