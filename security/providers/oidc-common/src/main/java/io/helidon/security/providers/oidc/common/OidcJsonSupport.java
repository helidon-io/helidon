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

package io.helidon.security.providers.oidc.common;

import java.io.StringReader;
import java.util.Collections;

import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;

import jakarta.json.Json;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;

final class OidcJsonSupport {
    private static final JsonReaderFactory JSONP = Json.createReaderFactory(Collections.emptyMap());

    private OidcJsonSupport() {
    }

    static JsonObject toHelidonJson(jakarta.json.JsonObject jsonObject) {
        if (jsonObject == null) {
            return null;
        }
        return JsonParser.create(jsonObject.toString()).readJsonObject();
    }

    static jakarta.json.JsonObject toJsonp(JsonObject jsonObject) {
        if (jsonObject == null) {
            return null;
        }
        try (JsonReader reader = JSONP.createReader(new StringReader(jsonObject.toString()))) {
            return reader.readObject();
        }
    }
}
