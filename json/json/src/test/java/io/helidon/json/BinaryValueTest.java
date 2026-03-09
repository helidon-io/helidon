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

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

abstract class BinaryValueTest {

    @Test
    public void testReadBinaryEmpty() {
        JsonParser parser = createParser("\"\"");
        assertArrayEquals(new byte[0], parser.readBinary());
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testReadBinarySingleByte() {
        byte[] bytes = new byte[] {42};
        JsonParser parser = createParser("\"" + Base64.getEncoder().encodeToString(bytes) + "\"");
        assertArrayEquals(bytes, parser.readBinary());
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testReadBinaryPaddingVariants() {
        assertArrayEquals(new byte[] {1}, createParser("\"AQ==\"").readBinary());
        assertArrayEquals(new byte[] {1, 2}, createParser("\"AQI=\"").readBinary());
        assertArrayEquals(new byte[] {1, 2, 3}, createParser("\"AQID\"").readBinary());
    }

    @Test
    public void testReadBinaryLarge() {
        byte[] expected = new byte[4096];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) (i & 0xFF);
        }
        JsonParser parser = createParser("\"" + Base64.getEncoder().encodeToString(expected) + "\"");
        assertArrayEquals(expected, parser.readBinary());
        assertThat(parser.hasNext(), is(false));
    }

    @Test
    public void testReadBinaryFromObjectValue() {
        byte[] bytes = new byte[] {-1, 0, 1, 2, 127};
        JsonParser parser = createParser("{\"payload\":\"" + Base64.getEncoder().encodeToString(bytes) + "\"}");
        parser.nextToken(); // key
        parser.readString();
        parser.nextToken(); // colon
        parser.nextToken(); // value
        assertArrayEquals(bytes, parser.readBinary());
    }

    @Test
    public void testReadBinaryFromArrayValue() {
        byte[] first = new byte[] {10, 11};
        byte[] second = new byte[] {12, 13, 14};
        JsonParser parser = createParser("[\""
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

    @Test
    public void testReadBinaryRejectsNonStringValue() {
        JsonParser parser = createParser("123");
        assertThrows(JsonException.class, parser::readBinary);
    }

    @Test
    public void testReadBinaryRejectsInvalidBase64() {
        JsonParser parser = createParser("\"not-base64***\"");
        assertThrows(IllegalArgumentException.class, parser::readBinary);
    }

    abstract JsonParser createParser(String template);
}
