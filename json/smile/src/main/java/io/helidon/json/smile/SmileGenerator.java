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

package io.helidon.json.smile;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import io.helidon.common.Api;
import io.helidon.common.buffers.Bytes;
import io.helidon.json.JsonException;
import io.helidon.json.JsonGenerator;
import io.helidon.json.JsonGeneratorBase;
import io.helidon.json.JsonKey;

/**
 * Smile binary JSON generator implementation.
 *
 * <p>This class is not thread safe.
 */
@Api.Preview
public final class SmileGenerator extends JsonGeneratorBase {

    private static final int SHARED_TABLE_SIZE = 1024;

    private final OutputStream outputStream;
    private final byte[] digits = new byte[10];
    private final byte[] buffer = new byte[256];
    private boolean closed;
    private int index = 0;
    private final boolean sharedKeyStrings;
    private final boolean sharedValueStrings;
    private final boolean rawBinaryEnabled;
    private final boolean emitEndMark;
    private int nextSharedValueIndex;
    private int nextSharedKeyIndex;
    private final Map<String, Integer> sharedValueIndex = new HashMap<>();
    private final Map<String, Integer> sharedKeyIndex = new HashMap<>();

    private SmileGenerator(OutputStream outputStream, SmileConfig config) {
        super(false);
        this.outputStream = outputStream;
        this.sharedKeyStrings = config.sharedKeyStrings();
        this.sharedValueStrings = config.sharedValueStrings();
        this.rawBinaryEnabled = config.rawBinaryEnabled();
        this.emitEndMark = config.emitEndMark();
    }

    /**
     * Create a {@link io.helidon.json.JsonGenerator} instance producing Smile binary JSON into the provided {@link OutputStream}.
     *
     * @param outputStream output stream to write Smile data to
     * @return new Smile generator instance
     */
    public static JsonGenerator create(OutputStream outputStream) {
        return create(outputStream, SmileConfig.create());
    }

    /**
     * Create a {@link JsonGenerator} instance producing Smile binary JSON into the provided {@link OutputStream}.
     *
     * @param outputStream output stream to write Smile data to
     * @param config Smile configuration
     * @return new Smile generator instance
     */
    public static JsonGenerator create(OutputStream outputStream, SmileConfig config) {
        SmileGenerator smileGenerator = new SmileGenerator(outputStream, config);
        smileGenerator.writeHeader();
        return smileGenerator;
    }

    @Override
    public JsonGenerator write(BigDecimal value) {
        if (value != null && value.scale() == 0) {
            return write(value.toBigIntegerExact());
        }
        return super.write(value);
    }

    @Override
    public JsonGenerator write(BigInteger value) {
        if (value != null && value.bitLength() <= 31) {
            return super.write(value.intValue());
        } else if (value != null && value.bitLength() <= 63) {
            return super.write(value.longValue());
        }
        return super.write(value);
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            try {
                outputStream.write(buffer, 0, index);
                if (emitEndMark) {
                    outputStream.write(SmileConstants.END_OF_CONTENT);
                }
                outputStream.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected void writeControlByte(byte value) {
        if (value == Bytes.COLON_BYTE || value == Bytes.COMMA_BYTE) {
            //Ignore these control characters.
            return;
        } else if (value == Bytes.SQUARE_BRACKET_OPEN_BYTE) {
            ensureCapacity(1);
            buffer[index++] = SmileConstants.TOKEN_START_ARRAY;
            return;
        } else if (value == Bytes.SQUARE_BRACKET_CLOSE_BYTE) {
            ensureCapacity(1);
            buffer[index++] = SmileConstants.TOKEN_END_ARRAY;
            return;
        } else if (value == Bytes.BRACE_OPEN_BYTE) {
            ensureCapacity(1);
            buffer[index++] = SmileConstants.TOKEN_START_OBJECT;
            return;
        } else if (value == Bytes.BRACE_CLOSE_BYTE) {
            ensureCapacity(1);
            buffer[index++] = SmileConstants.TOKEN_END_OBJECT;
            return;
        }
        writeByteExact(value);
    }

    @Override
    protected void writeByteExact(byte value) {
        writeInt(value);
    }

    @Override
    protected void writeNewLineIndent(int indentLevel) {
        // Smile is a binary format and does not support whitespace-based pretty printing.
    }

    @Override
    protected void writeInt(int value) {
        if (value >= -16 && value <= 15) {
            // Small integer: one byte – 0xC0 | zigzag-encoded 5 LSB.
            // spec: "5 LSB used to get values from -16 to +15".
            ensureCapacity(1);
            buffer[index++] = (byte) (SmileConstants.VALUE_SMALL_INT_MIN | (zigzagInt(value) & 0x1F));
        } else {
            ensureCapacity(1);
            // "`0x24` - 32-bit integer; zigzag encoded, 1 - 5 data bytes"
            buffer[index++] = SmileConstants.TOKEN_INT32;
            writeVInt(zigzagInt(value) & 0xFFFFFFFFL);
        }
    }

    @Override
    protected void writeLong(long value) {
        if (value >= -16 && value <= 15) {
            // Small integer: one byte – 0xC0 | zigzag-encoded 5 LSB.
            // spec: "5 LSB used to get values from -16 to +15".
            ensureCapacity(1);
            buffer[index++] = (byte) (SmileConstants.VALUE_SMALL_INT_MIN | (zigzagInt((int) value) & 0x1F));
        } else if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            ensureCapacity(1);
            // "`0x24` - 32-bit integer; zigzag encoded, 1 - 5 data bytes"
            buffer[index++] = SmileConstants.TOKEN_INT32;
            writeVInt(zigzagInt((int) value) & 0xFFFFFFFFL);
        } else {
            ensureCapacity(1);
            buffer[index++] = SmileConstants.TOKEN_INT64;
            writeVInt(zigzagLong(value));
        }
    }

    @Override
    protected void writeFloat(float value) {
        // 0x28 + 5 seven-bit bytes, MSB first.
        ensureCapacity(1);
        buffer[index++] = SmileConstants.TOKEN_FLOAT32;
        writeFloat32(Float.floatToRawIntBits(value));
    }

    @Override
    protected void writeDouble(double value) {
        // 0x29 + 10 seven-bit bytes, MSB first.
        ensureCapacity(1);
        buffer[index++] = SmileConstants.TOKEN_FLOAT64;
        writeDouble64(Double.doubleToRawLongBits(value));
    }

    @Override
    protected void writeBigDecimal(BigDecimal value) {
        ensureCapacity(1);
        buffer[index++] = SmileConstants.TOKEN_BIG_DEC;
        writeVInt(zigzagInt(value.scale()) & 0xFFFFFFFFL);
        byte[] magnitude = value.unscaledValue().toByteArray();
        writeVInt(magnitude.length);
        write7Bit(magnitude);
    }

    @Override
    protected void writeBigInteger(BigInteger value) {
        ensureCapacity(1);
        buffer[index++] = SmileConstants.TOKEN_BIG_INT;
        byte[] magnitude = value.toByteArray();
        writeVInt(magnitude.length);
        write7Bit(magnitude);
    }

    @Override
    protected void writeString(String value) {
        if (value == null) {
            writeNullValue();
            return;
        } else if (value.isEmpty()) {
            ensureCapacity(1);
            buffer[index++] = SmileConstants.TOKEN_EMPTY_STRING;
            return;
        }

        if (sharedValueStrings) {
            Integer sharedRef = sharedValueIndex.get(value);
            if (sharedRef != null) {
                writeSharedValueReference(sharedRef);
                return;
            }
        }

        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        int len = utf8.length;
        boolean ascii = isPureAscii(utf8);

        ensureCapacity(1);
        if (ascii) {
            if (len <= 32) {
                // Tiny ASCII: 0x40 | (len - 1)
                buffer[index++] = (byte) (SmileConstants.VALUE_TINY_ASCII_PREFIX | (len - 1));
                writeBytes(utf8);
            } else if (len <= 64) {
                // Short ASCII: 0x60 | (len - 33)
                buffer[index++] = (byte) (SmileConstants.VALUE_SHORT_ASCII_PREFIX | (len - 33));
                writeBytes(utf8);
            } else {
                // Long ASCII
                buffer[index++] = SmileConstants.VALUE_LONG_ASCII;
                writeBytes(utf8);
                ensureCapacity(1);
                buffer[index++] = SmileConstants.END_OF_STRING;
            }
        } else {
            if (len <= 33) {
                // Tiny Unicode: 0x80 | (byteLen - 2)
                // Note: len >= 2 because any non-ASCII UTF-8 codepoint needs >= 2 bytes.
                buffer[index++] = (byte) (SmileConstants.VALUE_TINY_UNICODE_PREFIX | (len - 2));
                writeBytes(utf8);
            } else if (len <= 65) {
                // Short Unicode: 0xA0 | (byteLen - 34)
                buffer[index++] = (byte) (SmileConstants.VALUE_SHORT_UNICODE_PREFIX | (len - 34));
                writeBytes(utf8);
            } else {
                // Long Unicode
                buffer[index++] = SmileConstants.VALUE_LONG_UNICODE;
                writeBytes(utf8);
                ensureCapacity(1);
                buffer[index++] = SmileConstants.END_OF_STRING;
            }
        }

        // Value string sharing is defined for short/tiny value strings (<= 64 bytes).
        if (sharedValueStrings && len <= SmileConstants.SHARED_STRING_VALUES_MAX_BYTES) {
            registerSharedValue(value);
        }
    }

    @Override
    protected void writeKeyName(String key) {
        if (key == null || key.isEmpty()) {
            ensureCapacity(1);
            buffer[index++] = SmileConstants.TOKEN_EMPTY_STRING;
            return;
        }

        if (sharedKeyStrings) {
            Integer sharedRef = sharedKeyIndex.get(key);
            if (sharedRef != null) {
                writeSharedKeyReference(sharedRef);
                return;
            }
        }

        byte[] utf8 = key.getBytes(StandardCharsets.UTF_8);
        int len = utf8.length;
        boolean ascii = isPureAscii(utf8);

        ensureCapacity(1);
        if (ascii) {
            if (len <= 64) {
                // Short ASCII key: 0x80 | (len - 1)
                buffer[index++] = (byte) (SmileConstants.KEY_SHORT_ASCII_PREFIX | (len - 1));
                writeBytes(utf8);
            } else {
                // Long Unicode key
                buffer[index++] = SmileConstants.KEY_LONG_UNICODE;
                writeBytes(utf8);
                ensureCapacity(1);
                buffer[index++] = SmileConstants.END_OF_STRING;
            }
        } else {
            if (len >= 2 && len <= 57) {
                // Short Unicode key: 0xC0 | (byteLen - 2)
                buffer[index++] = (byte) (SmileConstants.KEY_SHORT_UNICODE_PREFIX | (len - 2));
                writeBytes(utf8);
            } else {
                // Long Unicode key
                buffer[index++] = SmileConstants.KEY_LONG_UNICODE;
                writeBytes(utf8);
                ensureCapacity(1);
                buffer[index++] = SmileConstants.END_OF_STRING;
            }
        }

        if (sharedKeyStrings) {
            registerSharedKey(key);
        }
    }

    @Override
    protected void writeKeyName(JsonKey key) {
        writeKeyName(key.value());
    }

    @Override
    protected void writeChar(char value) {
        writeString(String.valueOf(value));
    }

    @Override
    protected void writeBoolean(boolean value) {
        ensureCapacity(1);
        buffer[index++] = value ? SmileConstants.TOKEN_TRUE : SmileConstants.TOKEN_FALSE;
    }

    @Override
    protected void writeBinaryArray(byte[] value) {
        if (rawBinaryEnabled) {
            // 0xFD + VInt length + raw bytes verbatim
            ensureCapacity(1);
            buffer[index++] = SmileConstants.TOKEN_BINARY_RAW;
            writeVInt(value.length);
            writeBytes(value);
        } else {
            // 0xE8 + VInt length + 7-bit safe encoded bytes
            ensureCapacity(1);
            buffer[index++] = SmileConstants.TOKEN_BINARY_7BIT;
            writeVInt(value.length);
            write7Bit(value);
        }
    }

    @Override
    protected void writeNullValue() {
        ensureCapacity(1);
        buffer[index++] = SmileConstants.TOKEN_NULL;
    }

    @Override
    protected void ensureCapacity(int extra) {
        if (index + extra >= buffer.length) {
            writeBuffer();
        }
    }

    /** Writes the 4-byte Smile format header. */
    private void writeHeader() {
        ensureCapacity(4);
        buffer[index++] = SmileConstants.HEADER_0;
        buffer[index++] = SmileConstants.HEADER_1;
        buffer[index++] = SmileConstants.HEADER_2;
        byte features = 0;
        // Spec quote:
        // "Bit 0 (mask `0x01`): Whether \"'shared property name\" checking was enabled during encoding"
        if (sharedKeyStrings) {
            features |= SmileConstants.HEADER_FEATURE_SHARED_KEYS;
        }
        // Spec quote:
        // "Bit 1 (mask `0x02`): Whether \"shared String value\" checking was enabled during encoding"
        if (sharedValueStrings) {
            features |= SmileConstants.HEADER_FEATURE_SHARED_VALUES;
        }
        // Bit 2 (mask `0x04`) Whether "raw binary" (unescaped 8-bit) values may be present in content
        if (rawBinaryEnabled) {
            features |= SmileConstants.HEADER_FEATURE_RAW_BINARY;
        }
        buffer[index++] = features;
    }

    /**
     * Returns {@code true} if every byte in {@code utf8} has its MSB clear (pure ASCII).
     */
    private static boolean isPureAscii(byte[] utf8) {
        for (byte b : utf8) {
            if ((b & 0x80) != 0) {
                return false;
            }
        }
        return true;
    }

    private void writeBytes(byte[] bytes) {
        if (bytes.length > buffer.length) {
            writeBuffer();
            writeArray(bytes, bytes.length);
        } else {
            ensureCapacity(bytes.length);
            System.arraycopy(bytes, 0, buffer, index, bytes.length);
            index += bytes.length;
        }
    }

    private void writeBuffer() {
        writeArray(buffer, index);
        index = 0;
    }

    private void writeArray(byte[] array, int length) {
        if (length == 0) {
            return;
        }
        try {
            outputStream.write(array, 0, length);
        } catch (IOException e) {
            throw new JsonException("Stream write failed", e);
        }
    }

    /**
     * ZigZag-encodes a signed 32-bit integer to an unsigned representation.
     *
     * <p>Formula: {@code (n << 1) ^ (n >> 31)}.  This maps 0→0, -1→1, 1→2, -2→3, ...
     * which places the sign bit at the LSB position, making small absolute values
     * produce small unsigned results (and thus shorter VInts).
     */
    private static int zigzagInt(int n) {
        return (n << 1) ^ (n >> 31);
    }

    /**
     * ZigZag-encodes a signed 64-bit long.
     *
     * <p>Formula: {@code (n << 1) ^ (n >> 63)}.
     */
    private static long zigzagLong(long n) {
        return (n << 1) ^ (n >> 63);
    }

    /**
     * Encodes a 32-bit IEEE 754 float as 5 bytes of 7-bit data (MSB first).
     *
     * <p>The spec says floats are "right-aligned" and each byte uses only the 7 LSBs.
     * For 32 bits split into chunks: [4 bits][7][7][7][7] = 5 bytes.
     *
     * <pre>
     *   byte 0: (bits >> 28) &amp; 0x0F   (4 data bits, 4 leading padding zeros)
     *   byte 1: (bits >> 21) &amp; 0x7F   (7 data bits)
     *   byte 2: (bits >> 14) &amp; 0x7F
     *   byte 3: (bits >>  7) &amp; 0x7F
     *   byte 4:  bits        &amp; 0x7F
     * </pre>
     */
    private void writeFloat32(int bits) {
        ensureCapacity(5);
        buffer[index++] = (byte) ((bits >>> 28) & 0x0F);
        buffer[index++] = (byte) ((bits >>> 21) & 0x7F);
        buffer[index++] = (byte) ((bits >>> 14) & 0x7F);
        buffer[index++] = (byte) ((bits >>> 7) & 0x7F);
        buffer[index++] = (byte) ((bits) & 0x7F);
    }

    /**
     * Encodes a 64-bit IEEE 754 double as 10 bytes of 7-bit data (MSB first).
     *
     * <p>For 64 bits split into chunks: [1 bit][7][7][7][7][7][7][7][7][7] = 10 bytes.
     *
     * <pre>
     *   byte 0: (bits >> 63) &amp; 0x01   (1 data bit)
     *   byte 1: (bits >> 56) &amp; 0x7F
     *   ...
     *   byte 9:  bits        &amp; 0x7F
     * </pre>
     */
    private void writeDouble64(long bits) {
        ensureCapacity(10);
        buffer[index++] = (byte) ((bits >>> 63) & 0x01);
        buffer[index++] = (byte) ((bits >>> 56) & 0x7F);
        buffer[index++] = (byte) ((bits >>> 49) & 0x7F);
        buffer[index++] = (byte) ((bits >>> 42) & 0x7F);
        buffer[index++] = (byte) ((bits >>> 35) & 0x7F);
        buffer[index++] = (byte) ((bits >>> 28) & 0x7F);
        buffer[index++] = (byte) ((bits >>> 21) & 0x7F);
        buffer[index++] = (byte) ((bits >>> 14) & 0x7F);
        buffer[index++] = (byte) ((bits >>> 7) & 0x7F);
        buffer[index++] = (byte) ((bits) & 0x7F);
    }

    /**
     * Writes a variable-length integer (VInt) as defined by the Smile spec.
     *
     * <p>Encoding (big-endian, MSB first):
     * <ul>
     *   <li>All bytes except the last have their MSB = 0, carrying 7 data bits each.</li>
     *   <li>The last byte has MSB = 1 and bit 6 = 0, carrying 6 data bits
     *       (this prevents {@code 0xFF} from appearing in the stream).</li>
     * </ul>
     *
     * @param value unsigned value to encode (pass zigzag-encoded result for signed integers)
     */
    private void writeVInt(long value) {
        // Build bytes in LSB-first order into a small buffer, then reverse.
        int position = 0;

        // Last byte: MSB=1, bit6=0, bits 5-0 hold the 6 LSBs of value.
        digits[position++] = (byte) (0x80 | (value & 0x3F));
        value >>>= 6;

        // Intermediate bytes: MSB=0, 7 data bits each.
        while (value != 0) {
            digits[position++] = (byte) (value & 0x7F);
            value >>>= 7;
        }

        ensureCapacity(position);
        // Write in big-endian (most significant byte first).
        for (int i = position - 1; i >= 0; i--) {
            buffer[index++] = digits[i];
        }
    }

    /**
     * Encodes {@code raw} bytes into Smile "safe binary" format.
     *
     * Each output byte uses only its 7 LSBs (MSB is always 0), so the byte
     * stream never contains values >= 0x80.  Bits are packed MSB-first across
     * 7-bit output units.  The final output byte is right-aligned: the actual
     * data bits sit in the LSBs and any unused MSBs are left as 0.
     *
     * Example — 4 raw bytes (32 bits):
     *   4 full 7-bit bytes (bits 31..4) + 1 partial byte (bits 3..0, right-aligned)
     *   → 5 output bytes total.
     */
    private void write7Bit(byte[] raw) {
        if (raw.length == 0) {
            return;
        }
        // Total output bytes = ceil(rawLen * 8 / 7). Pre-reserve to avoid per-byte capacity checks.
        int outLen = encodedLength7Bit(raw.length);
        ensureCapacity(outLen);
        int accumulator = 0;
        int bitsHeld = 0;
        for (byte rawByte : raw) {
            accumulator = (accumulator << 8) | (rawByte & 0xFF);
            bitsHeld += 8;
            // Flush complete 7-bit groups
            while (bitsHeld >= 7) {
                if (index == buffer.length) {
                    writeBuffer();
                }
                bitsHeld -= 7;
                buffer[index++] = (byte) ((accumulator >> bitsHeld) & 0x7F);
            }
        }
        // Write any remaining bits right-aligned in the last byte
        if (bitsHeld > 0) {
            buffer[index++] = (byte) (accumulator & ((1 << bitsHeld) - 1));
        }
    }

    /**
     * Returns the number of 7-bit-encoded bytes required to represent
     * {@code rawLen} raw bytes: ceil(rawLen * 8 / 7).
     */
    private static int encodedLength7Bit(int rawLen) {
        return (rawLen * 8 + 6) / 7;
    }

    private void writeSharedValueReference(int ref) {
        if (ref <= SmileConstants.VALUE_SHARED_SHORT_MAX_INDEX) {
            ensureCapacity(1);
            buffer[index++] = (byte) (SmileConstants.VALUE_SHARED_SHORT_MIN + ref);
            return;
        }
        ensureCapacity(2);
        buffer[index++] = (byte) (SmileConstants.VALUE_SHARED_LONG_MIN
                                          | ((ref >> 8) & SmileConstants.LONG_SHARED_REFERENCE_PREFIX_MASK));
        buffer[index++] = (byte) (ref & 0xFF);
    }

    private void writeSharedKeyReference(int ref) {
        if (ref <= SmileConstants.KEY_SHARED_SHORT_MAX_INDEX) {
            ensureCapacity(1);
            buffer[index++] = (byte) (SmileConstants.KEY_SHARED_SHORT_MIN + ref);
            return;
        }
        ensureCapacity(2);
        buffer[index++] = (byte) (SmileConstants.KEY_SHARED_LONG_MIN
                                          | ((ref >> 8) & SmileConstants.LONG_SHARED_REFERENCE_PREFIX_MASK));
        buffer[index++] = (byte) (ref & 0xFF);
    }

    private void registerSharedValue(String value) {
        // Spec quote:
        // "If it already has 1024 values, it MUST clear out buffer and start from first entry."
        if (nextSharedValueIndex == SHARED_TABLE_SIZE) {
            sharedValueIndex.clear();
            nextSharedValueIndex = 0;
        }
        // Spec quote:
        // "NOTE: second byte MUST NOT BE `0xFE` or `0xFF` -- generator MUST ensure avoidance"
        // "Generators can implement block in different ways but possible the simplest is to simply
        //  not store lookup entries for these indexes."
        if (isAllowedSharedIndex(nextSharedValueIndex)) {
            sharedValueIndex.put(value, nextSharedValueIndex);
        }
        nextSharedValueIndex++;
    }

    private void registerSharedKey(String value) {
        // Spec quote:
        // "Shared key resolution is done same way as shared String value resolution, but buffers used are separate.
        //  Buffer sizes are same, 1024."
        // "If it already has 1024 values, it MUST clear out buffer and start from first entry."
        if (nextSharedKeyIndex == SHARED_TABLE_SIZE) {
            sharedKeyIndex.clear();
            nextSharedKeyIndex = 0;
        }
        // Spec quote:
        // "NOTE: second byte MUST NOT BE `0xFE` or `0xFF` -- generator MUST ensure avoidance"
        // "Generators can implement block in different ways but possible the simplest is to simply
        //  not store lookup entries for these indexes."
        if (isAllowedSharedIndex(nextSharedKeyIndex)) {
            sharedKeyIndex.put(value, nextSharedKeyIndex);
        }
        nextSharedKeyIndex++;
    }

    private static boolean isAllowedSharedIndex(int index) {
        int low = index & 0xFF;
        return low != SmileConstants.SHARED_INDEX_FORBIDDEN_LOW_BYTE_1 && low != SmileConstants.SHARED_INDEX_FORBIDDEN_LOW_BYTE_2;
    }

}
