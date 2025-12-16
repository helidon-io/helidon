/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

abstract class AbstractGenerator implements Generator {

    static final int STACK_SIZE = 64;

    static final byte QUOTES = '"';
    static final byte COMMA = ',';
    static final byte COLON = ':';
    static final byte ARRAY_START = '[';
    static final byte ARRAY_END = ']';
    static final byte OBJECT_START = '{';
    static final byte OBJECT_END = '}';
    static final byte SLASH = '\\';
    static final byte ZERO = '0';
    static final byte MINUS = '-';

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
                writeByte(COMMA);
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
    public Generator writeKey(String key) {
        if (depth == 0 || !structureType[depth - 1]) {
            throw new JsonException("Key can be written only into the object");
        } else if (keyWritten) {
            throw new JsonException("Cannot write key twice");
        }
        beforeWrite();
        writeString(key);
        writeByte(COLON);
        keyWritten = true;
        return this;
    }

    @Override
    public Generator write(String key, String value) {
        if (depth == 0 || !structureType[depth - 1]) {
            throw new JsonException("Key can be written only into the object");
        } else if (keyWritten) {
            throw new JsonException("Cannot write key twice");
        }
        beforeWrite();
        writeString(key);
        writeByte(COLON);
        writeString(value);
        return this;
    }

    @Override
    public Generator write(String key, int value) {
        if (depth == 0 || !structureType[depth - 1]) {
            throw new JsonException("Key can be written only into the object");
        } else if (keyWritten) {
            throw new JsonException("Cannot write key twice");
        }
        beforeWrite();
        writeString(key);
        writeByte(COLON);
        writeLong(value);
        return this;
    }

    @Override
    public Generator write(String key, long value) {
        if (depth == 0 || !structureType[depth - 1]) {
            throw new JsonException("Key can be written only into the object");
        } else if (keyWritten) {
            throw new JsonException("Cannot write key twice");
        }
        beforeWrite();
        writeString(key);
        writeByte(COLON);
        writeLong(value);
        return this;
    }

    @Override
    public Generator write(String key, float value) {
        if (depth == 0 || !structureType[depth - 1]) {
            throw new JsonException("Key can be written only into the object");
        } else if (keyWritten) {
            throw new JsonException("Cannot write key twice");
        }
        beforeWrite();
        writeString(key);
        writeByte(COLON);
        writeFloat(value);
        return this;
    }

    @Override
    public Generator write(String key, double value) {
        if (depth == 0 || !structureType[depth - 1]) {
            throw new JsonException("Key can be written only into the object");
        } else if (keyWritten) {
            throw new JsonException("Cannot write key twice");
        }
        beforeWrite();
        writeString(key);
        writeByte(COLON);
        writeDouble(value);
        return this;
    }

    @Override
    public Generator write(String key, boolean value) {
        if (depth == 0 || !structureType[depth - 1]) {
            throw new JsonException("Key can be written only into the object");
        } else if (keyWritten) {
            throw new JsonException("Cannot write key twice");
        }
        beforeWrite();
        writeString(key);
        writeByte(COLON);
        writeBoolean(value);
        return this;
    }

    @Override
    public Generator write(String key, char value) {
        if (depth == 0 || !structureType[depth - 1]) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        } else if (keyWritten) {
            throw new JsonException("Cannot write key twice");
        }
        beforeWrite();
        writeString(key);
        writeByte(COLON);
        writeChar(value);
        return this;
    }

    @Override
    public Generator write(String key, JsonValue value) {
        if (depth == 0 || !structureType[depth - 1]) {
            throw new JsonException("Key can be written only into the object");
        } else if (keyWritten) {
            throw new JsonException("Cannot write key twice");
        }
        beforeWrite();
        writeString(key);
        writeByte(COLON);
        writeJsonValue(value);
        return this;
    }

    @Override
    public Generator write(String value) {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeString(value);
        return this;
    }

    @Override
    public Generator write(byte value) {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeByte(value);
        return this;
    }

    @Override
    public Generator write(short value) {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeLong(value);
        return this;
    }

    @Override
    public Generator write(int value) {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeLong(value);
        return this;
    }

    @Override
    public Generator write(long value) {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeLong(value);
        return this;
    }

    @Override
    public Generator write(float value) {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeFloat(value);
        return this;
    }

    @Override
    public Generator write(double value) {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeDouble(value);
        return this;
    }

    @Override
    public Generator write(boolean value) {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeBoolean(value);
        return this;
    }

    @Override
    public Generator write(char value) {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeChar(value);
        return this;
    }

    @Override
    public Generator write(JsonValue value) {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeJsonValue(value);
        return this;
    }

    @Override
    public Generator writeNull() {
        if (depth > 0 && structureType[depth - 1] && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
        beforeWrite();
        writeNullValue();
        return this;
    }

    @Override
    public Generator writeArrayStart() {
        if (!keyWritten) {
            beforeWrite();
        } else {
            keyWritten = false;
        }
        pushStructureType(false);
        writeByte(ARRAY_START);
        return this;
    }

    @Override
    public Generator writeArrayEnd() {
        popStackType();
        writeByte(ARRAY_END);
        first = false;
        return this;
    }

    @Override
    public Generator writeObjectStart() {
        if (!keyWritten) {
            beforeWrite();
        } else {
            keyWritten = false;
        }
        writeByte(OBJECT_START);
        pushStructureType(true);
        return this;
    }

    @Override
    public Generator writeObjectEnd() {
        popStackType();
        writeByte(OBJECT_END);
        first = false;
        return this;
    }

    void writeJsonValue(JsonValue value) {
        value.toJson(this);
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
