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

import java.io.InputStream;

import io.helidon.json.JsonParser;

/**
 * Stream-backed execution of the spec-mapped parser cases documented in {@link SmileParserTestBase}.
 */
class SmileParserStreamTest extends SmileParserTestBase {
    @Override
    JsonParser createParser(byte[] smileData) {
        return SmileParser.create(new OneByteAtATimeInputStream(smileData), 5);
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
