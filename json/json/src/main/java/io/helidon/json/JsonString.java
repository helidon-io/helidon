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

import io.helidon.common.Api;

/**
 * Represents a JSON string value.
 */
@Api.Preview
public final class JsonString extends JsonValue {

    private final byte[] buffer;
    private final int start;
    private final int length;
    private String resolvedValue;

    private JsonString(byte[] buffer, int start, int length) {
        this.buffer = buffer;
        this.start = start;
        this.length = length;
    }

    private JsonString(String value) {
        this.buffer = EMPTY_BYTES;
        this.start = -1;
        this.length = value.length();
        this.resolvedValue = value;
    }

    /**
     * Create a JsonString from a String value.
     *
     * @param value the string value
     * @return a new JsonString
     */
    public static JsonString create(String value) {
        return new JsonString(value);
    }

    /**
     * Create a JsonString from an array of UTF-8 encoded bytes.
     *
     * @param value the UTF-8 encoded string bytes
     * @return a new JsonString
     */
    public static JsonString create(byte[] value) {
        return new JsonString(value, 0, value.length);
    }

    /**
     * Create a JsonString from a range within a UTF-8 encoded byte buffer.
     *
     * @param buffer the buffer containing the UTF-8 encoded string
     * @param start the starting offset within the buffer
     * @param length the number of bytes that belong to the string value
     * @return a new JsonString
     */
    public static JsonString create(byte[] buffer, int start, int length) {
        return new JsonString(buffer, start, length);
    }

    @Override
    byte jsonStartChar() {
        return '"';
    }

    /**
     * Return the string value of this JsonString.
     *
     * @return the string value
     */
    public String value() {
        if (resolvedValue == null) {
            resolveValue();
        }
        return resolvedValue;
    }

    private void resolveValue() {
        resolvedValue = Parsers.decodeJsonString(buffer, start, length);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        String value = value();
        if (obj instanceof JsonString jsonString) {
            return jsonString.value().equals(value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return value().hashCode();
    }

    @Override
    public JsonValueType type() {
        return JsonValueType.STRING;
    }

    @Override
    public void toJson(JsonGenerator generator) {
        generator.write(value());
    }
}
