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

enum Utf8InputMethod {
    ARRAY {
        @Override
        JsonParser createParser(byte[] json, int streamBufferSize) {
            return JsonParser.create(json);
        }
    },
    STREAM {
        @Override
        JsonParser createParser(byte[] json, int streamBufferSize) {
            return JsonParser.create(new ByteArrayInputStream(json), streamBufferSize);
        }
    };

    JsonParser createParser(byte[] json) {
        return createParser(json, 6);
    }

    abstract JsonParser createParser(byte[] json, int streamBufferSize);
}
