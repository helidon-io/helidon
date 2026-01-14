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
 * Represents a JSON boolean value (true or false).
 */
public final class JsonBoolean extends JsonValue {

    /**
     * The singleton instance representing the JSON boolean value false.
     */
    public static final JsonBoolean FALSE = new JsonBoolean(false);

    /**
     * The singleton instance representing the JSON boolean value true.
     */
    public static final JsonBoolean TRUE = new JsonBoolean(true);

    private final boolean value;

    private JsonBoolean(boolean value) {
        this.value = value;
    }

    /**
     * Create a JsonBoolean from a boolean value.
     * <p>
     * Return the appropriate singleton instance (TRUE or FALSE) for efficiency.
     * </p>
     *
     * @param value the boolean value
     * @return the JsonBoolean instance
     */
    public static JsonBoolean create(boolean value) {
        return value ? TRUE : FALSE;
    }

    /**
     * Return the boolean value of this JsonBoolean.
     *
     * @return the boolean value
     */
    public boolean value() {
        return value;
    }

    @Override
    public JsonValueType type() {
        return JsonValueType.BOOLEAN;
    }

    @Override
    public void toJson(JsonGenerator generator) {
        generator.write(value);
    }

    @Override
    byte jsonStartChar() {
        return (byte) (value ? 't' : 'f');
    }

}
