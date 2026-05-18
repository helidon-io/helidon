/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

import io.helidon.common.Errors;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;

class JwtClaims {
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    protected JwtClaims() {
    }

    /**
     * Decode a Base64 URL encoded JWT segment.
     *
     * @param base64 base64 URL encoded value
     * @param collector error collector
     * @param description description of the decoded value
     * @return decoded value, or {@code null} when decoding fails
     */
    protected static String decode(String base64, Errors.Collector collector, String description) {
        try {
            return new String(URL_DECODER.decode(base64), StandardCharsets.UTF_8);
        } catch (Exception e) {
            collector.fatal(base64, description + " is not a base64 encoded string.");
            return null;
        }
    }

    /**
     * Parse a decoded JWT segment as JSON object.
     *
     * @param jsonString decoded JSON string
     * @param collector error collector
     * @param base64 original Base64 URL encoded value
     * @param description description of the decoded value
     * @return parsed JSON object, or {@code null} when parsing fails
     */
    protected static JsonObject parseJson(String jsonString, Errors.Collector collector, String base64, String description) {
        try {
            return JsonParser.create(new StringReader(jsonString)).readJsonObject();
        } catch (Exception e) {
            collector.fatal(base64, description + " is not a valid JSON object (value is base64 encoded)");
            return null;
        }
    }
}
