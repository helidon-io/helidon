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

import io.helidon.common.buffers.Bytes;

/**
 * Base implementation of {@link JsonGenerator} with shared structure and state handling.
 */
public abstract class JsonGeneratorBase implements JsonGenerator {

    static final int MAX_DEPTH = 64;
    static final int STACK_SIZE = 64;
    static final int INDENT_SIZE = 3;
    static final char[] INDENT = new char[STACK_SIZE * INDENT_SIZE];

    static {
        java.util.Arrays.fill(INDENT, ' ');
    }

    private long structureTypes;

    // first: true if this is the first item in the current object/array
    private boolean first = true;
    // keyWritten: true if a key has been written for the current object entry (awaiting value)
    private boolean keyWritten = false;
    // depth: current nesting level in objects/arrays
    private int depth = 0;
    private final boolean prettyPrint;

    /**
     * Protected default constructor for subclasses.
     *
     * @param prettyPrint whether to pretty print generated JSON output
     */
    protected JsonGeneratorBase(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    /**
     * Writes a structural control byte, such as a comma, colon, or bracket.
     *
     * @param value control byte to write
     */
    protected void writeControlByte(byte value) {
        writeByteExact(value);
    }

    /**
     * Writes the byte into the output.
     * Example: byte 47 -&gt; {@code -}.
     *
     * @param value byte value
     */
    protected abstract void writeByteExact(byte value);

    /**
     * Writes an integer value.
     *
     * @param value integer value to write
     */
    protected abstract void writeInt(int value);

    /**
     * Writes a long value.
     *
     * @param value long value to write
     */
    protected abstract void writeLong(long value);

    /**
     * Writes a float value.
     *
     * @param value float value to write
     */
    protected abstract void writeFloat(float value);

    /**
     * Writes a double value.
     *
     * @param value double value to write
     */
    protected abstract void writeDouble(double value);

    /**
     * Writes a big decimal value.
     *
     * @param value big decimal value to write
     */
    protected abstract void writeBigDecimal(BigDecimal value);

    /**
     * Writes a big integer value.
     *
     * @param value big integer value to write
     */
    protected abstract void writeBigInteger(BigInteger value);

    /**
     * Writes an object key name.
     *
     * @param value key name to write
     */
    protected void writeKeyName(String value) {
        writeString(value);
    }

    /**
     * Writes an object key name using a precomputed key.
     *
     * @param value key name to write
     */
    protected void writeKeyName(JsonKey value) {
        writeString(value.value());
    }

    /**
     * Writes a string value.
     *
     * @param value string value to write
     */
    protected abstract void writeString(String value);

    /**
     * Writes a character value.
     *
     * @param value character value to write
     */
    protected abstract void writeChar(char value);

    /**
     * Writes a boolean value.
     *
     * @param value boolean value to write
     */
    protected abstract void writeBoolean(boolean value);

    /**
     * Writes a binary value represented as a byte array.
     *
     * @param value binary value to write
     */
    protected abstract void writeBinaryArray(byte[] value);

    /**
     * Writes the JSON {@code null} value.
     */
    protected abstract void writeNullValue();

    /**
     * Writes a newline followed by indentation for the provided nesting depth.
     *
     * @param indentLevel nesting depth to indent
     */
    protected abstract void writeNewLineIndent(int indentLevel);

    void beforeWrite() {
        if (depth > 0) {
            if (first) {
                first = false;
                if (prettyPrint) {
                    writeNewLineIndent(depth);
                }
            } else if (keyWritten) {
                keyWritten = false;
            } else {
                ensureCapacity(1);
                writeControlByte(Bytes.COMMA_BYTE);
                if (prettyPrint) {
                    writeNewLineIndent(depth);
                }
            }
        } else if (first) {
            first = false;
        } else {
            throw new JsonException("Multiple values not supported as a root value");
        }
    }

    /**
     * Ensures there is enough capacity for an upcoming write operation.
     * Implementations with internal buffering can override this method.
     *
     * @param extra number of additional bytes or characters about to be written
     */
    protected void ensureCapacity(int extra) {
        //NOOP by default
    }

    @Override
    public JsonGenerator writeKey(String key) {
        checkAndWriteKey(key);
        return this;
    }

    @Override
    public JsonGenerator writeKey(JsonKey key) {
        checkAndWriteKey(key);
        return this;
    }

    @Override
    public JsonGenerator writePrecomputedKey(JsonKey key) {
        return writeKey(key);
    }

    @Override
    public JsonGenerator write(String key, String value) {
        checkAndWriteKey(key);
        if (value == null) {
            writeNullValue();
        } else {
            writeString(value);
        }
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(JsonKey key, String value) {
        checkAndWriteKey(key);
        if (value == null) {
            writeNullValue();
        } else {
            writeString(value);
        }
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(String key, int value) {
        checkAndWriteKey(key);
        writeInt(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(JsonKey key, int value) {
        checkAndWriteKey(key);
        writeInt(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(String key, long value) {
        checkAndWriteKey(key);
        writeLong(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(JsonKey key, long value) {
        checkAndWriteKey(key);
        writeLong(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(String key, float value) {
        checkAndWriteKey(key);
        writeFloat(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(JsonKey key, float value) {
        checkAndWriteKey(key);
        writeFloat(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(String key, double value) {
        checkAndWriteKey(key);
        writeDouble(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(JsonKey key, double value) {
        checkAndWriteKey(key);
        writeDouble(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(String key, boolean value) {
        checkAndWriteKey(key);
        writeBoolean(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(JsonKey key, boolean value) {
        checkAndWriteKey(key);
        writeBoolean(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(String key, char value) {
        checkAndWriteKey(key);
        writeChar(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(JsonKey key, char value) {
        checkAndWriteKey(key);
        writeChar(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(String key, BigDecimal value) {
        checkAndWriteKey(key);
        writeBigDecimal(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(JsonKey key, BigDecimal value) {
        checkAndWriteKey(key);
        writeBigDecimal(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(String key, BigInteger value) {
        checkAndWriteKey(key);
        writeBigInteger(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(JsonKey key, BigInteger value) {
        checkAndWriteKey(key);
        writeBigInteger(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(String key, JsonValue value) {
        checkAndWriteKey(key);
        writeJsonValue(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(JsonKey key, JsonValue value) {
        checkAndWriteKey(key);
        writeJsonValue(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator writeBinary(String key, byte[] value) {
        checkAndWriteKey(key);
        writeBinaryArray(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator writeBinary(JsonKey key, byte[] value) {
        checkAndWriteKey(key);
        writeBinaryArray(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(String value) {
        validateValueWrite();
        beforeWrite();
        if (value == null) {
            writeNullValue();
        } else {
            writeString(value);
        }
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(byte value) {
        validateValueWrite();
        beforeWrite();
        writeLong(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(short value) {
        validateValueWrite();
        beforeWrite();
        writeLong(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(int value) {
        validateValueWrite();
        beforeWrite();
        writeInt(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(long value) {
        validateValueWrite();
        beforeWrite();
        writeLong(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(float value) {
        validateValueWrite();
        beforeWrite();
        writeFloat(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(double value) {
        validateValueWrite();
        beforeWrite();
        writeDouble(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(boolean value) {
        validateValueWrite();
        beforeWrite();
        writeBoolean(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(char value) {
        validateValueWrite();
        beforeWrite();
        writeChar(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(BigDecimal value) {
        validateValueWrite();
        beforeWrite();
        writeBigDecimal(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(BigInteger value) {
        validateValueWrite();
        beforeWrite();
        writeBigInteger(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator write(JsonValue value) {
        validateValueWrite();
        writeJsonValue(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator writeBinary(byte[] value) {
        validateValueWrite();
        writeBinaryArray(value);
        keyWritten = false;
        return this;
    }

    @Override
    public JsonGenerator writeNull() {
        validateValueWrite();
        beforeWrite();
        writeNullValue();
        keyWritten = false;
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
        writeControlByte(Bytes.SQUARE_BRACKET_OPEN_BYTE);
        return this;
    }

    @Override
    public JsonGenerator writeArrayEnd() {
        boolean hasItems = !first;
        popStackType();
        if (hasItems && prettyPrint) {
            writeNewLineIndent(depth);
        }
        writeControlByte(Bytes.SQUARE_BRACKET_CLOSE_BYTE);
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
        writeControlByte(Bytes.BRACE_OPEN_BYTE);
        pushStructureType(true);
        return this;
    }

    @Override
    public JsonGenerator writeObjectEnd() {
        boolean hasItems = !first;
        popStackType();
        if (hasItems && prettyPrint) {
            writeNewLineIndent(depth);
        }
        writeControlByte(Bytes.BRACE_CLOSE_BYTE);
        first = false;
        return this;
    }

    void writeJsonValue(JsonValue value) {
        value.toJson(this);
    }

    private void validateKeyWrite() {
        if (!inObject()) {
            throw new JsonException("Key can be written only into the object");
        } else if (keyWritten) {
            throw new JsonException("Cannot write key twice");
        }
    }

    private void checkAndWriteKey(String key) {
        validateKeyWrite();
        if (key == null) {
            throw new JsonException("Key cannot be null");
        }
        beforeWrite();
        writeKeyName(key);
        writeControlByte(Bytes.COLON_BYTE);
        if (prettyPrint) {
            writeByteExact(Bytes.SPACE_BYTE);
        }
        keyWritten = true;
    }

    private void checkAndWriteKey(JsonKey key) {
        validateKeyWrite();
        if (key == null) {
            throw new JsonException("Key cannot be null");
        }
        beforeWrite();
        writeKeyName(key);
        writeControlByte(Bytes.COLON_BYTE);
        if (prettyPrint) {
            writeByteExact(Bytes.SPACE_BYTE);
        }
        keyWritten = true;
    }

    private void pushStructureType(boolean isObject) {
        if (depth >= MAX_DEPTH) {
            throw new IllegalStateException("Nesting too deep");
        }
        if (isObject) {
            structureTypes |= 1L << depth;
        } else {
            structureTypes &= ~(1L << depth);
        }
        first = true;
        depth++;
    }

    private void popStackType() {
        depth--;
        if (depth < 0) {
            throw new IllegalStateException("Invalid JSON structure");
        }
        structureTypes &= ~(1L << depth);
    }

    private boolean inObject() {
        return depth > 0 && (structureTypes & (1L << (depth - 1))) != 0;
    }

    private void validateValueWrite() {
        if (inObject() && !keyWritten) {
            throw new JsonException("Value without key is supported only as a root or in the array");
        }
    }
}
