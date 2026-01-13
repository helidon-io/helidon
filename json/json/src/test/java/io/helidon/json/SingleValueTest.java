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

abstract class SingleValueTest {

    @Test
    public void testParseString() {
        String expected = "Test String value";
        JsonParser parser = createParser("\"" + expected + "\"");

        assertThat(parser.readString(), is(expected));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseByte() {
        byte expected = 125;
        String template = "125";
        JsonParser parser = createParser(template);

        assertThat(parser.readByte(), is(expected));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseShort() {
        short expected = 12345;
        String template = "12345";
        JsonParser parser = createParser(template);

        assertThat(parser.readShort(), is(expected));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseInt() {
        int expected = 1234;
        String template = "1234";
        JsonParser parser = createParser(template);

        assertThat(parser.readInt(), is(expected));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseLong() {
        long expected = 123456789123456L;
        String template = "123456789123456";
        JsonParser parser = createParser(template);

        assertThat(parser.readLong(), is(expected));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseDouble() {
        double expected = 123.456e10;
        String template = "123.456e10";
        JsonParser parser = createParser(template);

        assertThat(parser.readDouble(), is(expected));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseFloat() {
        float expected = 123.456e10F;
        String template = "123.456e10";
        JsonParser parser = createParser(template);

        assertThat(parser.readFloat(), is(expected));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseBoolean() {
        boolean expected = true;
        String template = "true";
        JsonParser parser = createParser(template);

        assertThat(parser.readBoolean(), is(expected));
        assertThat(parser.hasNext(), is(false));

        expected = false;
        template = "false";
        parser = createParser(template);

        assertThat(parser.readBoolean(), is(expected));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseNull() {
        String template = "null";
        JsonParser parser = createParser(template);

        assertThat(parser.checkNull(), is(true));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseStringCharArray() {
        String template = "\"myStringValue\"";
        JsonParser parser = createParser(template);
        char[] expected = "myStringValue".toCharArray();

        assertThat(parser.readCharArray(), is(expected));
        assertThat(parser.hasNext(), is(false));

        template = "\"special chars ěščřžýáíé\"";
        parser = createParser(template);
        expected = "special chars ěščřžýáíé".toCharArray();

        assertThat(parser.readCharArray(), is(expected));
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testParseNumberAsCharArray() {
        String template = "-123";
        JsonParser parser = createParser(template);
        char[] expected = template.toCharArray();

        assertThat(parser.readCharArray(), is(expected));
        assertThat(parser.hasNext(), is(false));

        template = "123.456";
        parser = createParser(template);
        expected = template.toCharArray();

        assertThat(parser.readCharArray(), is(expected));
        assertThat(parser.hasNext(), is(false));

        template = "-123.456E+10";
        parser = createParser(template);
        expected = template.toCharArray();

        assertThat(parser.readCharArray(), is(expected));
        assertThat(parser.hasNext(), is(false));
    }

    abstract JsonParser createParser(String template);

}
