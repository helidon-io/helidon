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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Base64;
import java.util.Set;

class JsonValueParser implements JsonParser {

    private JsonValue[] values = new JsonValue[100];
    private boolean[] valuesExpanded = new boolean[100];
    private JsonValue[] replay = new JsonValue[10];
    private boolean[] replayExpanded = new boolean[10];
    private JsonValue current;
    private boolean currentExpanded = false;
    private int index = 0;

    private boolean replayMarked = false;
    private int replayIndex = replay.length - 1;

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
        return index > 0 || (index == 0 && values[0] != null && values[0] != JsonNoopValue.INSTANCE);
    }

    @Override
    public byte nextToken() {
        boolean expandContainer = !currentExpanded;
        if (replayMarked && expandContainer && current != null
                && (current.type() == JsonValueType.OBJECT || current.type() == JsonValueType.ARRAY)) {
            replayExpanded[replayIndex] = true;
        }
        currentExpanded = false;
        if (current != null && expandContainer) {
            if (current.type() == JsonValueType.OBJECT) {
                JsonObject object = current.asObject();
                Set<JsonString> keys = object.keys();
                //We need to calculate how many values we need to add + how many commas
                //key size needs to be multiplied by 4, because for every key, nad value we will add : and , (-1 for the last
                // object)
                int size = (keys.size() * 4) - 1;
                if (index < 0) {
                    index = 0;
                } else if (values[index] != JsonNoopValue.INSTANCE) {
                    //We are having some values before this one. index need to be raised to prevet overwriting.
                    index++;
                }
                ensureCapacity(Math.max(1, size + 1));
                putValue(index++, JsonControlValue.OBJECT_END);
                if (keys.isEmpty()) {
                    index--;
                } else {
                    for (JsonString key : keys) {
                        putValue(index + --size, key);
                        putValue(index + --size, JsonControlValue.COLON);
                        putValue(index + --size, object.value(key.value(), JsonNull.instance()));
                        if (size > 0) {
                            putValue(index + --size, JsonControlValue.COMMA);
                        }
                    }
                    index += (keys.size() * 4) - 2;
                }
            } else if (current.type() == JsonValueType.ARRAY) {
                JsonArray array = current.asArray();
                //We need to calculate how many values we need to add + how many commas
                //value size needs to be multiplied by 2, because for every value we will add , (-1 for the last object)
                int size = (array.values().size() * 2) - 1;
                if (index < 0) {
                    index = 0;
                } else if (values[index] != JsonNoopValue.INSTANCE) {
                    //We are having some values before this one. index need to be raised to prevet overwriting.
                    index++;
                }
                ensureCapacity(Math.max(1, size + 1));
                putValue(index++, JsonControlValue.ARRAY_END);
                if (array.values().isEmpty()) {
                    index--;
                } else {
                    for (JsonValue value : array.values()) {
                        putValue(index + --size, value);
                        if (size > 0) {
                            putValue(index + --size, JsonControlValue.COMMA);
                        }
                    }
                    index += (array.values().size() * 2) - 2;
                }
            }
        }
        if (index >= 0) {
            int currentIndex = index--;
            current = values[currentIndex];
            currentExpanded = valuesExpanded[currentIndex];
            values[currentIndex] = null;
            valuesExpanded[currentIndex] = false;
            if (current == null) {
                throw new JsonException("No more JSON Values available");
            }
            if (replayMarked) {
                recordToReplayQueue();
            }
            return current.jsonStartChar();
        }
        throw new JsonException("No more JSON Values available");
    }

    private void putValue(int targetIndex, JsonValue value) {
        values[targetIndex] = value;
        valuesExpanded[targetIndex] = false;
    }

    private void recordToReplayQueue() {
        recordToReplayQueue(current, currentExpanded);
    }

    private void recordToReplayQueue(JsonValue value, boolean expanded) {
        if (replayIndex == 0) {
            int active = replay.length - replayIndex;
            int newLength = replay.length * 2;
            int newStart = newLength - active;
            JsonValue[] newReplay = new JsonValue[newLength];
            boolean[] newReplayExpanded = new boolean[newLength];
            System.arraycopy(replay, replayIndex, newReplay, newStart, active);
            System.arraycopy(replayExpanded, replayIndex, newReplayExpanded, newStart, active);
            replay = newReplay;
            replayExpanded = newReplayExpanded;
            replayIndex = newStart;
        }
        replay[--replayIndex] = value;
        replayExpanded[replayIndex] = expanded;
    }

    void ensureCapacity(int capacity) {
        int required = Math.max(0, index) + capacity + 1;
        if (required > values.length) {
            int newLength = Math.max(values.length * 2, required);
            int amount = Math.max(0, index + 1);
            JsonValue[] newValues = new JsonValue[newLength];
            boolean[] newValuesExpanded = new boolean[newLength];
            System.arraycopy(values, 0, newValues, 0, amount);
            System.arraycopy(valuesExpanded, 0, newValuesExpanded, 0, amount);
            values = newValues;
            valuesExpanded = newValuesExpanded;
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
        return JsonParserArray.fnv1aHashUtf8(current.asString().value());
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
        return (float) readDouble();
    }

    @Override
    public double readDouble() {
        if (current.type() == JsonValueType.STRING) {
            String value = current.asString().value();
            return switch (value) {
                case "NaN" -> Double.NaN;
                case "Infinity", "infinity" -> Double.POSITIVE_INFINITY;
                case "-Infinity", "-infinity" -> Double.NEGATIVE_INFINITY;
                default -> throw createException("Invalid double number");
            };
        }
        return current.asNumber().doubleValue();
    }

    @Override
    public BigInteger readBigInteger() {
        return current.asNumber().bigDecimalValue().toBigInteger();
    }

    @Override
    public BigDecimal readBigDecimal() {
        return current.asNumber().bigDecimalValue();
    }

    @Override
    public byte[] readBinary() {
        String value = current.asString().value();
        return Base64.getDecoder().decode(value);
    }

    @Override
    public boolean checkNull() {
        return current.type() == JsonValueType.NULL;
    }

    @Override
    public void skip() {
        if (current.type() == JsonValueType.OBJECT) {
            if (!currentExpanded) {
                current = JsonControlValue.OBJECT_END;
                return;
            }
            skipExpandedContainer(JsonValueType.OBJECT,
                                  JsonControlValue.OBJECT_END,
                                  "Invalid state while skipping JsonValue object.");
        } else if (current.type() == JsonValueType.ARRAY) {
            if (!currentExpanded) {
                current = JsonControlValue.ARRAY_END;
                return;
            }
            skipExpandedContainer(JsonValueType.ARRAY,
                                  JsonControlValue.ARRAY_END,
                                  "Invalid state while skipping JsonValue array.");
        } else if (index == 0 && values[0] == JsonNoopValue.INSTANCE) {
            values[0] = null;
            valuesExpanded[0] = false;
            index = -1;
            current = JsonNoopValue.INSTANCE;
            currentExpanded = false;
        } else {
            nextToken();
        }
    }

    private void skipExpandedContainer(JsonValueType containerType, JsonValue containerEnd, String errorMessage) {
        int nested = 0;
        for (int i = index; i > -1; i--) {
            JsonValue value = values[i];
            boolean expanded = valuesExpanded[i];
            if (replayMarked && value != null) {
                recordToReplayQueue(value, expanded);
            }
            values[i] = null;
            valuesExpanded[i] = false;
            if (value != null && expanded && value.type() == containerType) {
                nested++;
            } else if (value == containerEnd) {
                if (nested == 0) {
                    index = i - 1;
                    current = containerEnd;
                    currentExpanded = false;
                    return;
                }
                nested--;
            }
        }
        throw new JsonException(errorMessage);
    }

    @Override
    public JsonException createException(String message) {
        return new JsonException(message);
    }

    @Override
    public JsonException createException(String message, Exception e) {
        return new JsonException(message, e);
    }

    @Override
    public void mark() {
        if (replayMarked) {
            throw new IllegalStateException("Parser has already been marked for replaying. "
                                                    + "Cant do it twice without consuming the mark with either "
                                                    + "clearMark or resetToMark methods.");
        }
        replayIndex = replay.length - 1;
        replayMarked = true;
        replay[replayIndex] = current;
        replayExpanded[replayIndex] = currentExpanded;
    }

    @Override
    public void clearMark() {
        replayMarked = false;
    }

    @Override
    public void resetToMark() {
        if (replayMarked) {
            replayMarked = false;
            int amount = replay.length - replayIndex;
            ensureCapacity(amount + 1);
            int from = index + 1;
            System.arraycopy(replay, replayIndex, values, from, amount);
            System.arraycopy(replayExpanded, replayIndex, valuesExpanded, from, amount);
            index = from + amount - 1;
            int currentIndex = index--;
            current = values[currentIndex];
            currentExpanded = valuesExpanded[currentIndex];
            values[currentIndex] = null;
            valuesExpanded[currentIndex] = false;
        } else {
            throw new IllegalStateException("Parser tried to reset to the marked place, but no mark was found");
        }
    }

}
