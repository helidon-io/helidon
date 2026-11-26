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

package io.helidon.json.smile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Map;

import io.helidon.common.Size;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.json.JsonException;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonParser;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Stream-backed execution of the spec-mapped parser cases documented in {@link SmileParserTestBase}.
 */
class SmileParserStreamTest extends SmileParserTestBase {
    @Override
    JsonParser createParser(byte[] smileData) {
        return SmileParser.create(new OneByteAtATimeInputStream(smileData), 5);
    }

    @Test
    void excessiveBigIntegerExpansionReportsStreamPosition() throws Exception {
        byte[] smileData = generateSmileBytes(gen -> gen.write(new BigDecimal("1E+4097")));
        JsonException exception = assertThrows(JsonException.class,
                                               () -> createParser(smileData).readBigInteger());

        assertThat(exception.getMessage(), containsString("4096"));
        assertThat(exception.getMessage(), containsString("stream position ~"));
    }

    @Test
    void fullMarkedBufferFailsBeforeReadingAnEndMarkerOrSibling() {
        String value = "sample".repeat(120);
        for (boolean object : new boolean[] {false, true}) {
            byte[] root = replayDocument(value, object, false);
            // Four header bytes and one end marker are outside the marked value.
            int capacity = root.length - 5;
            SmileConfig limit = SmileConfig.builder().maxReplaySize(Size.create(capacity)).build();
            for (boolean sibling : new boolean[] {false, true}) {
                byte[] bytes = sibling ? replayDocument(value, object, true) : root;
                var input = new ByteArrayInputStream(bytes);
                JsonParser parser = SmileParser.create(input, limit);
                if (sibling) {
                    parser.nextToken();
                }
                parser.mark();
                JsonException exception = assertThrows(JsonException.class, parser::skip);
                assertThat(exception.getMessage(), containsString("max-replay-size of " + capacity + " bytes"));
                int prefix = sibling ? 5 : 4; // Header and optional enclosing array start.
                assertThat(input.available(), is(bytes.length - prefix - capacity));
                parser.resetToMark();
                String result = object ? parser.readJsonObject().stringValue("name").orElseThrow() : parser.readString();
                assertThat(result, is(value));
                if (sibling) {
                    assertThat(parser.nextToken(), is((byte) ','));
                    parser.nextToken();
                    assertThat(parser.readInt(), is(42));
                    assertThat(parser.nextToken(), is((byte) ']'));
                }
            }
        }
    }

    @Test
    void writerOptionsFromConfigDoNotOverrideAnIncomingHeader() {
        Config node = Config.just(ConfigSources.create(Map.of("shared-key-strings", "false",
                                                             "shared-value-strings", "true",
                                                             "raw-binary-enabled", "true",
                                                             "emit-end-mark", "true",
                                                             "max-replay-size", "1 KiB")));
        SmileConfig readerConfig = SmileConfig.create(node);
        assertThat(readerConfig.sharedKeyStrings(), is(false));
        assertThat(readerConfig.sharedValueStrings(), is(true));
        assertThat(readerConfig.rawBinaryEnabled(), is(true));
        assertThat(readerConfig.emitEndMark(), is(true));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator generator = SmileGenerator.create(bytes)) {
            generator.writeArrayStart();
            generator.writeObjectStart().write("name", "first").writeObjectEnd();
            generator.writeObjectStart().write("name", "second").writeObjectEnd();
            generator.writeArrayEnd();
        }
        JsonParser parser = SmileParser.create(new ByteArrayInputStream(bytes.toByteArray()), readerConfig);
        assertThat(parser.readJsonArray(), is(JsonParser.create("[{\"name\":\"first\"},{\"name\":\"second\"}]").readJsonArray()));
    }

    private static byte[] replayDocument(String value, boolean object, boolean sibling) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JsonGenerator generator = SmileGenerator.create(bytes, SmileConfig.builder().emitEndMark(true).build())) {
            if (sibling) {
                generator.writeArrayStart();
            }
            if (object) {
                generator.writeObjectStart().write("name", value).writeObjectEnd();
            } else {
                generator.write(value);
            }
            if (sibling) {
                generator.write(42).writeArrayEnd();
            }
        }
        return bytes.toByteArray();
    }

    private static final class OneByteAtATimeInputStream extends InputStream {
        private final byte[] bytes;
        private int index;

        private OneByteAtATimeInputStream(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public int read() {
            if (index == bytes.length) {
                return -1;
            }
            return bytes[index++] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (len == 0) {
                return 0;
            }
            if (index == bytes.length) {
                return -1;
            }
            b[off] = bytes[index++];
            return 1;
        }
    }
}
