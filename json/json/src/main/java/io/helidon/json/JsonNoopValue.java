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
 * This object is never returned anywhere and is used as a placeholder.
 */
final class JsonNoopValue extends JsonValue {

    static final JsonNoopValue INSTANCE = new JsonNoopValue();

    private JsonNoopValue() {
    }

    @Override
    public JsonValueType type() {
        return JsonValueType.UNKNOWN;
    }

    @Override
    public void toJson(JsonGenerator generator) {
        throw new UnsupportedOperationException("This is noop placeholder value. Serialization is not supported");
    }

    @Override
    byte jsonStartChar() {
        throw new JsonException("No json values are remaining");
    }
}
