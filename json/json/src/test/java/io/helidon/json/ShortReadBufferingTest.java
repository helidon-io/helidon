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
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

class ShortReadBufferingTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 7})
    void bufferedNumbersStayBounded(int maxRead) {
        for (String value : new String[] {"1000000000", "1234567890".repeat(8)}) {
            String json = "[" + (value + ",").repeat(63) + value + "]";
            int capacityLimit = 4 * Math.max(16, value.length() + 1);
            JsonParser parser = JsonParser.create(new ShortReadInputStream(json, maxRead, capacityLimit), 16);
            BigInteger expected = new BigInteger(value);
            for (int i = 0; i < 64; i++) {
                assertThat(parser.nextToken(), is((byte) value.charAt(0)));
                assertThat(parser.readBigInteger(), is(expected));
                assertThat(parser.nextToken(), is(i == 63 ? (byte) ']' : (byte) ','));
            }
            assertThat(parser.hasNext(), is(false));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 7})
    void bufferedStringsAndDoublesStayBounded(int maxRead) {
        String encodedString = "\"" + "abc\\\"def\\\\ghi".repeat(30) + "\"";
        String expected = "abc\"def\\ghi".repeat(30);
        String pair = encodedString + ",-123456.25e-2";
        String json = "[" + (pair + ",").repeat(31) + pair + "]";
        JsonParser parser = JsonParser.create(new ShortReadInputStream(json, maxRead, 4 * encodedString.length()), 16);
        for (int i = 0; i < 32; i++) {
            assertThat(parser.nextToken(), is((byte) '"'));
            assertThat(parser.readJsonString().value(), is(expected));
            assertThat(parser.nextToken(), is((byte) ','));
            assertThat(parser.nextToken(), is((byte) '-'));
            assertThat(parser.readDouble(), is(-1234.5625));
            assertThat(parser.nextToken(), is(i == 31 ? (byte) ']' : (byte) ','));
        }
        assertThat(parser.hasNext(), is(false));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 1460})
    void markedObjectsStayBounded(int maxRead) {
        String value = "a".repeat(3000);
        String object = "{\"id\":123456789,\"data\":\"" + value + "\"}";
        int capacityLimit = 4 * object.length();

        // A root object starts its mark at zero, while array elements have a consumed prefix.
        JsonParser rootParser = JsonParser.create(new ShortReadInputStream(object, maxRead, capacityLimit), 512);
        rootParser.mark();
        rootParser.skip();
        rootParser.resetToMark();
        assertThat(rootParser.readJsonObject().stringValue("data").orElseThrow(), is(value));
        assertThat(rootParser.hasNext(), is(false));

        String json = "[" + (object + ",").repeat(23) + object + "]";
        JsonParser parser = JsonParser.create(new ShortReadInputStream(json, maxRead, capacityLimit), 512);
        for (int i = 0; i < 24; i++) {
            assertThat(parser.nextToken(), is((byte) '{'));
            parser.mark();
            parser.skip();
            if (i % 2 == 0) {
                parser.resetToMark();
                assertThat(parser.readJsonObject().stringValue("data").orElseThrow(), is(value));
            } else {
                parser.clearMark();
            }
            assertThat(parser.nextToken(), is(i == 23 ? (byte) ']' : (byte) ','));
        }
        assertThat(parser.hasNext(), is(false));
    }

    private static final class ShortReadInputStream extends ByteArrayInputStream {
        private final int maxRead;
        private final int capacityLimit;

        ShortReadInputStream(String json, int maxRead, int capacityLimit) {
            super(json.getBytes(StandardCharsets.UTF_8));
            this.maxRead = maxRead;
            this.capacityLimit = capacityLimit;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            // Retained memory should depend on the largest value, not the number of short reads.
            // Check each refill so a regression fails before allocating large arrays repeatedly.
            assertThat("stream buffer capacity", bytes.length, lessThanOrEqualTo(capacityLimit));
            return super.read(bytes, offset, Math.min(length, maxRead));
        }
    }
}
