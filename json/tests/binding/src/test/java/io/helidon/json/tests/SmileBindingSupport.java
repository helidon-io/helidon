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

package io.helidon.json.tests;

import java.io.ByteArrayOutputStream;

import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonParser;
import io.helidon.json.binding.JsonBinding;
import io.helidon.json.smile.SmileConfig;
import io.helidon.json.smile.SmileGenerator;
import io.helidon.json.smile.SmileParser;

/**
 * Smile helpers for JSON binding tests.
 */
final class SmileBindingSupport {
    private SmileBindingSupport() {
    }

    public static byte[] serializeSmile(JsonBinding jsonBinding, Object value) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JsonGenerator generator = SmileGenerator.create(baos)) {
            jsonBinding.serialize(generator, value);
        }
        return baos.toByteArray();
    }

    public static byte[] serializeSmile(JsonBinding jsonBinding, Object value, SmileConfig config) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JsonGenerator generator = SmileGenerator.create(baos, config)) {
            jsonBinding.serialize(generator, value);
        }
        return baos.toByteArray();
    }

    public static <T> T deserializeSmile(JsonBinding jsonBinding, byte[] data, Class<T> type) {
        JsonParser parser = SmileParser.create(data);
        return jsonBinding.deserialize(parser, type);
    }
}
