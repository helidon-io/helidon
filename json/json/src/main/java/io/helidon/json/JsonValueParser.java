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

import java.nio.charset.StandardCharsets;
import java.util.Set;

class JsonValueParser implements JsonParser {

    private JsonValue[] values = new JsonValue[500];
    private JsonValue current;
    private int index = 0;

    JsonValueParser(JsonValue jsonValue) {
        this.current = jsonValue;
        values[0] = JsonNoopValue.INSTANCE;
    }

    @Override
    public boolean hasNext() {
        if (current != null) {
            if (current.type() == JsonValueType.OBJECT || current.type() == JsonValueType.ARRAY) {
                return true;
            }
        }
        return index - 1 >= 0;
    }

    @Override
    public byte nextToken() {
        if (current != null) {
            if (current.type() == JsonValueType.OBJECT) {
                JsonObject object = current.asObject();
                Set<JsonString> keys = object.keys();
                //We need to calculate how many values we need to add + how many commas
                //key size needs to be multiplied by 4, because for every key, nad value we will add : and , (-1 for the last
                // object)
                int size = (keys.size() * 4) - 1;
                ensureCapacity(size + 1);
                if (index > 0) {
                    //We are having some values before this one. index need to be raised to prevet overwriting.
                    index++;
                }
                values[index++] = JsonControlValue.OBJECT_END;
                for (JsonString key : keys) {
                    values[index + --size] = key;
                    values[index + --size] = JsonControlValue.COLON;
                    values[index + --size] = object.value(key.value(), JsonNull.instance());
                    if (size > 0) {
                        values[index + --size] = JsonControlValue.COMMA;
                    }
                }
                index += (keys.size() * 4) - 2;
            } else if (current.type() == JsonValueType.ARRAY) {
                JsonArray array = current.asArray();
                //We need to calculate how many values we need to add + how many commas
                //value size needs to be multiplied by 2, because for every value we will add , (-1 for the last object)
                int size = (array.values().size() * 2) - 1;
                ensureCapacity(size + 1);
                if (index > 0) {
                    //We are having some values before this one. index need to be raised to prevet overwriting.
                    index++;
                }
                values[++index] = JsonControlValue.ARRAY_END;
                for (JsonValue value : array.values()) {
                    values[index + --size] = value;
                    if (size > 0) {
                        values[index + --size] = JsonControlValue.COMMA;
                    }
                }
                index += (array.values().size() * 2) - 2;
            }
        }
        if (index >= 0) {
            current = values[index];
            values[index--] = null;
            if (current == null) {
                throw new JsonException("No more JSON Values available");
            }
            return current.jsonStartChar();
        }
        throw new JsonException("No more JSON Values available");
    }

    void ensureCapacity(int capacity) {
        if (index + capacity > values.length) {
            JsonValue[] newValues = new JsonValue[values.length * 2];
            System.arraycopy(values, 0, newValues, 0, index);
            values = newValues;
        }
    }

    @Override
    public byte currentByte() {
        return current.jsonStartChar();
    }

    @Override
    public JsonValue readJsonValue() {
        return current;
    }

    @Override
    public JsonObject readJsonObject() {
        return current.asObject();
    }

    @Override
    public JsonArray readJsonArray() {
        return current.asArray();
    }

    @Override
    public JsonString readJsonString() {
        return current.asString();
    }

    @Override
    public JsonNumber readJsonNumber() {
        return current.asNumber();
    }

    @Override
    public String readString() {
        return current.asString().value();
    }

    @Override
    public int readStringAsHash() {
        String key = current.asString().value();
        int fnvHash = ArrayJsonParser.FNV_OFFSET_BASIS;
        for (byte b : key.getBytes(StandardCharsets.UTF_8)) {
            fnvHash ^= (b & 0xFF);
            fnvHash *= ArrayJsonParser.FNV_PRIME;
        }
        return fnvHash;
    }

    @Override
    public char[] readCharArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public char readChar() {
        return current.asString().value().charAt(0);
    }

    @Override
    public boolean readBoolean() {
        return current.asBoolean().value();
    }

    @Override
    public byte readByte() {
        throw new UnsupportedOperationException();
    }

    @Override
    public short readShort() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int readInt() {
        return current.asNumber().intValue();
    }

    @Override
    public long readLong() {
        return current.asNumber().intValue();
    }

    @Override
    public float readFloat() {
        return (float) current.asNumber().doubleValue();
    }

    @Override
    public double readDouble() {
        return current.asNumber().doubleValue();
    }

    @Override
    public boolean checkNull() {
        return current.type() == JsonValueType.NULL;
    }

    @Override
    public void skip() {
        if (current.type() == JsonValueType.OBJECT) {
            for (int i = index; i > -1; i--) {
                JsonValue value = values[i];
                values[i] = null;
                if (value == JsonNoopValue.INSTANCE) {
                    //This can happen when the very first value in the parser is the object and we skip it.
                    index = i;
                    current = JsonControlValue.OBJECT_END;
                    return;
                } else if (value == JsonControlValue.OBJECT_END) {
                    index = i;
                    current = value;
                    return;
                }
            }
            throw new JsonException("Invalid state while skipping JsonValue object.");
        } else if (current.type() == JsonValueType.ARRAY) {
            for (int i = index; i > -1; i--) {
                JsonValue value = values[i];
                values[i] = null;
                if (value == JsonNoopValue.INSTANCE) {
                    //This can happen when the very first value in the parser is the array and we skip it.
                    index = i;
                    current = JsonControlValue.ARRAY_END;
                    return;
                } else if (value == JsonControlValue.ARRAY_END) {
                    index = i;
                    current = value;
                    return;
                }
            }
            throw new JsonException("Invalid state while skipping JsonValue array.");
        } else if (index == 0) {
            index = -1;
            current = JsonNoopValue.INSTANCE;
        } else {
            nextToken();
        }
    }

    @Override
    public JsonException createException(String message) {
        return new JsonException(message);
    }

}
