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

/**
 * Represents a JSON null value.
 */
public final class JsonNull extends JsonValue {

    private static final JsonNull INSTANCE = new JsonNull();

    private JsonNull() {
    }

    /**
     * Return the singleton instance of JsonNull.
     *
     * @return the JsonNull instance
     */
    public static JsonNull instance() {
        return INSTANCE;
    }

    @Override
    public JsonValueType type() {
        return JsonValueType.NULL;
    }

    @Override
    public void toJson(JsonGenerator generator) {
        generator.writeNull();
    }

    @Override
    byte jsonStartChar() {
        return 'n';
    }

}
