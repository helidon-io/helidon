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

import io.helidon.common.buffers.Bytes;

abstract class AbstractJsonGenerator implements JsonGenerator {

    static final int STACK_SIZE = 64;

    // stack structure tracking: true = object, false = array
    private final boolean[] structureType = new boolean[STACK_SIZE];

    // first: true if this is the first item in the current object/array
    private boolean first = true;
    // keyWritten: true if a key has been written for the current object entry (awaiting value)
    private boolean keyWritten = false;
    // depth: current nesting level in objects/arrays
    private int depth = 0;

    abstract void writeByte(byte value);

    abstract void writeLong(long value);

    abstract void writeFloat(float value);

    abstract void writeDouble(double value);

    abstract void writeString(String value);

    abstract void writeChar(char value);

    abstract void writeBoolean(boolean value);

    abstract void writeNullValue();

    void beforeWrite() {
        if (depth > 0) {
            if (first) {
                first = false;
            } else if (keyWritten) {
                keyWritten = false;
            } else {
                ensureCapacity(1);
                writeByte(Bytes.COMMA_BYTE);
            }
        } else if (first) {
            first = false;
        } else {
            throw new JsonException("Multiple values not supported as a root value");
        }
    }

    void ensureCapacity(int extra) {
        //NOOP by default
    }

    @Override
    public JsonGenerator writeKey(String key) {
        checkAndWriteKey(key);
        keyWritten = true;
        return this;
    }

    @Override
    public JsonGenerator write(String key, String value) {
        checkAndWriteKey(key);
        writeString(value);
        return this;
    }

    @Override
    public JsonGenerator write(String key, int value) {
        checkAndWriteKey(key);
        writeLong(value);
        return this;
    }

    @Override
    public JsonGenerator write(String key, long value) {
        checkAndWriteKey(key);
        writeLong(value);
        return this;
    }

    @Override
    public JsonGenerator write(String key, float value) {
        checkAndWriteKey(key);
        writeFloat(value);
        return this;
    }

    @Override
    public JsonGenerator write(String key, double value) {
        checkAndWriteKey(key);
        writeDouble(value);
        return this;
    }

    @Override
    public JsonGenerator write(String key, boolean value) {
        checkAndWriteKey(key);
        writeBoolean(value);
        return this;
    }

    @Override
    public JsonGenerator write(String key, char value) {
        checkAndWriteKey(key);
        writeChar(value);
        return this;
    }

    @Override
    public JsonGenerator write(String key, JsonValue value) {
        checkAndWriteKey(key);
        writeJsonValue(value);
        return this;
    }

    @Override
    public JsonGenerator write(String value) {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeString(value);
        return this;
    }

    @Override
    public JsonGenerator write(byte value) {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeByte(value);
        return this;
    }

    @Override
    public JsonGenerator write(short value) {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeLong(value);
        return this;
    }

    @Override
    public JsonGenerator write(int value) {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeLong(value);
        return this;
    }

    @Override
    public JsonGenerator write(long value) {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeLong(value);
        return this;
    }

    @Override
    public JsonGenerator write(float value) {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeFloat(value);
        return this;
    }

    @Override
    public JsonGenerator write(double value) {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeDouble(value);
        return this;
    }

    @Override
    public JsonGenerator write(boolean value) {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeBoolean(value);
        return this;
    }

    @Override
    public JsonGenerator write(char value) {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeChar(value);
        return this;
    }

    @Override
    public JsonGenerator write(JsonValue value) {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeJsonValue(value);
        return this;
    }

    @Override
    public JsonGenerator writeNull() {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeNullValue();
        return this;
    }

    @Override
    public JsonGenerator writeArrayStart() {
        if (!keyWritten) {
            beforeWrite();
        } else {
            keyWritten = false;
        }
        pushStructureType(false);
        writeByte(Bytes.SQUARE_BRACKET_OPEN_BYTE);
        return this;
    }

    @Override
    public JsonGenerator writeArrayEnd() {
        popStackType();
        writeByte(Bytes.SQUARE_BRACKET_CLOSE_BYTE);
        first = false;
        return this;
    }

    @Override
    public JsonGenerator writeObjectStart() {
        if (!keyWritten) {
            beforeWrite();
        } else {
            keyWritten = false;
        }
        writeByte(Bytes.BRACE_OPEN_BYTE);
        pushStructureType(true);
        return this;
    }

    @Override
    public JsonGenerator writeObjectEnd() {
        popStackType();
        writeByte(Bytes.BRACE_CLOSE_BYTE);
        first = false;
        return this;
    }

    void writeJsonValue(JsonValue value) {
        value.toJson(this);
    }

    private void checkAndWriteKey(String key) {
        if (depth == 0 || !structureType[depth - 1]) {
            throw new JsonException("Key can be written only into the object");
        } else if (keyWritten) {
            throw new JsonException("Cannot write key twice");
        }
        beforeWrite();
        writeString(key);
        writeByte(Bytes.COLON_BYTE);
    }

    private void pushStructureType(boolean isObject) {
        if (depth >= STACK_SIZE) {
            throw new IllegalStateException("Nesting too deep");
        }
        structureType[depth] = isObject;
        first = true;
        depth++;
    }

    private void popStackType() {
        depth--;
        if (depth < 0) {
            throw new IllegalStateException("Invalid JSON structure");
        }
    }
}
