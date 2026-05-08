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

import java.util.Base64;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BinaryValueTest {

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testReadBinaryEmpty(ParserMethod parserMethod) {
        JsonParser parser = parserMethod.createParser("\"\"");
        assertArrayEquals(new byte[0], parser.readBinary());
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testReadBinarySingleByte(ParserMethod parserMethod) {
        byte[] bytes = new byte[] {42};
        JsonParser parser = parserMethod.createParser("\"" + Base64.getEncoder().encodeToString(bytes) + "\"");
        assertArrayEquals(bytes, parser.readBinary());
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testReadBinaryPaddingVariants(ParserMethod parserMethod) {
        assertArrayEquals(new byte[] {1}, parserMethod.createParser("\"AQ==\"").readBinary());
        assertArrayEquals(new byte[] {1, 2}, parserMethod.createParser("\"AQI=\"").readBinary());
        assertArrayEquals(new byte[] {1, 2, 3}, parserMethod.createParser("\"AQID\"").readBinary());
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testReadBinaryLarge(ParserMethod parserMethod) {
        byte[] expected = new byte[4096];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) (i & 0xFF);
        }
        JsonParser parser = parserMethod.createParser("\"" + Base64.getEncoder().encodeToString(expected) + "\"");
        assertArrayEquals(expected, parser.readBinary());
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testReadBinaryFromObjectValue(ParserMethod parserMethod) {
        byte[] bytes = new byte[] {-1, 0, 1, 2, 127};
        JsonParser parser = parserMethod.createParser("{\"payload\":\"" + Base64.getEncoder().encodeToString(bytes) + "\"}");
        parser.nextToken(); // key
        parser.readString();
        parser.nextToken(); // colon
        parser.nextToken(); // value
        assertArrayEquals(bytes, parser.readBinary());
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testReadBinaryFromArrayValue(ParserMethod parserMethod) {
        byte[] first = new byte[] {10, 11};
        byte[] second = new byte[] {12, 13, 14};
        JsonParser parser = parserMethod.createParser("[\""
                                                 + Base64.getEncoder().encodeToString(first)
                                                 + "\",\""
                                                 + Base64.getEncoder().encodeToString(second)
                                                 + "\"]");
        parser.nextToken();
        assertArrayEquals(first, parser.readBinary());
        parser.nextToken();
        parser.nextToken();
        assertArrayEquals(second, parser.readBinary());
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testReadBinaryRejectsNonStringValue(ParserMethod parserMethod) {
        JsonParser parser = parserMethod.createParser("123");
        assertThrows(JsonException.class, parser::readBinary);
    }

    @ParameterizedTest
    @EnumSource(ParserMethod.class)
    public void testReadBinaryRejectsInvalidBase64(ParserMethod parserMethod) {
        JsonParser parser = parserMethod.createParser("\"not-base64***\"");
        assertThrows(IllegalArgumentException.class, parser::readBinary);
    }
}
