/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.security.jwt;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReaderFactory;

import io.helidon.common.Errors;

class JwtClaims {
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final JsonReaderFactory JSON = Json.createReaderFactory(Collections.emptyMap());

    protected static String decode(String base64, Errors.Collector collector, String description) {
        try {
            return new String(URL_DECODER.decode(base64), StandardCharsets.UTF_8);
        } catch (Exception e) {
            collector.fatal(base64, description + " is not a base64 encoded string.");
            return null;
        }
    }

    protected static JsonObject parseJson(String jsonString, Errors.Collector collector, String base64, String description) {
        try {
            return JSON.createReader(new StringReader(jsonString)).readObject();
        } catch (Exception e) {
            collector.fatal(base64, description + " is not a valid JSON object (value is base64 encoded)");
            return null;
        }
    }
}
