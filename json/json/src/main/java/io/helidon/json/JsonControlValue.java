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
 * This object is never returned anywhere and servers as parser token placeholder.
 */
final class JsonControlValue extends JsonValue {

    // Reuse common control tokens to reduce allocations during JsonValueParser traversal
    static final JsonControlValue OBJECT_END = new JsonControlValue('}');
    static final JsonControlValue ARRAY_END = new JsonControlValue(']');
    static final JsonControlValue COLON = new JsonControlValue(':');
    static final JsonControlValue COMMA = new JsonControlValue(',');
    private final byte controlChar;

    JsonControlValue(char controlChar) {
        this.controlChar = (byte) controlChar;
    }

    @Override
    public JsonValueType type() {
        return JsonValueType.UNKNOWN;
    }

    @Override
    public void toJson(JsonGenerator generator) {
        throw new UnsupportedOperationException("This is a parser token placeholder value. Serialization is not supported.");
    }

    @Override
    byte jsonStartChar() {
        return controlChar;
    }

    @Override
    public String toString() {
        return "" + (char) controlChar;
    }
}
