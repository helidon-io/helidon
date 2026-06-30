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

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ReaderInputStreamTest {

    @Test
    void testSingleByteReadsReturnLeftoverUtf8Bytes() throws IOException {
        ReaderInputStream inputStream = new ReaderInputStream(new StringReader("\u20AC"));
        byte[] expected = "\u20AC".getBytes(StandardCharsets.UTF_8);

        assertThat(inputStream.read(), is(expected[0] & 0xFF));
        assertThat(inputStream.read(), is(expected[1] & 0xFF));
        assertThat(inputStream.read(), is(expected[2] & 0xFF));
        assertThat(inputStream.read(), is(-1));
    }

    @Test
    void testParserReadsJsonFromShortReader() {
        JsonParser parser = JsonParser.create(new OneCharAtATimeReader("{\"value\":\"\u20AC\uD83D\uDE00\",\"ok\":true}"));

        JsonObject object = parser.readJsonObject();

        assertThat(object.stringValue("value").orElseThrow(), is("\u20AC\uD83D\uDE00"));
        assertThat(object.booleanValue("ok").orElseThrow(), is(true));
    }

    private static final class OneCharAtATimeReader extends Reader {

        private final String value;
        private int index;

        private OneCharAtATimeReader(String value) {
            this.value = value;
        }

        @Override
        public int read(char[] cbuf, int off, int len) {
            if (len == 0) {
                return 0;
            }
            if (index == value.length()) {
                return -1;
            }
            cbuf[off] = value.charAt(index++);
            return 1;
        }

        @Override
        public void close() {
        }
    }
}
