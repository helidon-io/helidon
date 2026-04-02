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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.Bytes;

class JsonParserArray extends JsonParserBase {

    private static final int FAST_ASCII_STRING_LIMIT = 64;

    static final int FNV_OFFSET_BASIS = 0x811c9dc5;
    static final int FNV_PRIME = 0x01000193;

    //We need this to check if the next number digit overflows int max capacity
    static final byte BYTE_SIZE_BORDER = Byte.MAX_VALUE / 10;
    static final short SHORT_SIZE_BORDER = Short.MAX_VALUE / 10;
    static final int INT_SIZE_BORDER = Integer.MAX_VALUE / 10;
    static final long LONG_SIZE_BORDER = Long.MAX_VALUE / 10;

    //Lookup table used for transformation of a number character in a byte form to its int equivalent
    static final int[] WHOLE_NUMBER_PARTS = new int[256];
    //Contains true for any number related char
    static final boolean[] VALID_NUMBER_PARTS = new boolean[256];
    //Lookup table for whitespace detection
    static final boolean[] WHITESPACE_CHARS = new boolean[256];

    /**
     * Cached POW10 for double fast path calculations.
     */
    static final double[] POW10_DOUBLE_CACHE = {
            1.0, 10.0, 100.0, 1000.0, 10000.0, 100000.0, 1000000.0, 10000000.0,
            100000000.0, 1000000000.0, 10000000000.0, 100000000000.0,
            1000000000000.0, 10000000000000.0, 100000000000000.0,
            1000000000000000.0, 10000000000000000.0, 100000000000000000.0,
            1000000000000000000.0, 10000000000000000000.0, 1.0e20, 1.0e21, 1.0e22
    };
    static final int POW10_DOUBLE_CACHE_SIZE = POW10_DOUBLE_CACHE.length;
    static final int DOT_MARK = -2;

    static {
        Arrays.fill(WHOLE_NUMBER_PARTS, -1);
        for (int i = '0'; i <= '9'; ++i) {
            WHOLE_NUMBER_PARTS[i] = i - '0';
        }
        //Marker for number resolving
        //if dot has been found and we wanted just the decimal part, we need to skip the rest of the number
        WHOLE_NUMBER_PARTS[Bytes.DOT_BYTE] = DOT_MARK;

        //'e', 'E', '.', '-', '+', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'
        for (int i = '0'; i <= '9'; ++i) {
            VALID_NUMBER_PARTS[i] = true;
        }
        VALID_NUMBER_PARTS['-'] = true;
        VALID_NUMBER_PARTS['+'] = true;
        VALID_NUMBER_PARTS['.'] = true;
        VALID_NUMBER_PARTS['e'] = true;
        VALID_NUMBER_PARTS['E'] = true;

        // ASCII whitespace
        WHITESPACE_CHARS[0x09] = true; // TAB
        WHITESPACE_CHARS[0x0A] = true; // LF
        WHITESPACE_CHARS[0x0D] = true; // CR
        WHITESPACE_CHARS[0x20] = true; // SPACE
    }

    private int stringBufferLength = 64;
    private char[] stringBuffer;
    private boolean expectLowSurrogate = false;

    private final byte[] buffer;
    private int currentIndex = 0;
    private final int bufferLength;

    private int mark = -1;
    private boolean replayMarked = false;

    JsonParserArray(byte[] buffer) {
        this.buffer = buffer;
        this.bufferLength = buffer.length;
    }

    JsonParserArray(byte[] buffer, int start, int length) {
        this.buffer = buffer;
        this.currentIndex = start;
        this.bufferLength = start + length;
    }

    @Override
    public byte currentByte() {
        return buffer[currentIndex];
    }

    @Override
    public byte nextToken() {
        byte b;
        if (++currentIndex == bufferLength) {
            throw createException("Unexpected end of the JSON found");
        }
        b = buffer[currentIndex];
        if (!WHITESPACE_CHARS[b & 0xFF]) {
            return b;
        }
        if (++currentIndex == bufferLength) {
            throw createException("Unexpected end of the JSON found");
        }
        b = buffer[currentIndex];
        if (!WHITESPACE_CHARS[b & 0xFF]) {
            return b;
        }
        //We dont know how many spaces, new lines etc is there present, lets start looping
        for (int i = currentIndex + 1; i < bufferLength; i++) {
            b = buffer[i];
            if (!WHITESPACE_CHARS[b & 0xFF]) {
                currentIndex = i;
                return b;
            }
        }
        throw createException("Unexpected end of the JSON found");
    }

    @Override
    public boolean hasNext() {
        return currentIndex + 1 < bufferLength;
    }

    byte readNextByte() {
        return buffer[++currentIndex];
    }

    @Override
    public JsonString readJsonString() {
        int start = currentIndex + 1;
        skipString();
        int length = currentIndex - start;
        return JsonString.create(buffer, start, length);
    }

    @Override
    public JsonNumber readJsonNumber() {
        int start = currentIndex;
        skipNumber();
        return JsonNumber.create(buffer, start, currentIndex - start + 1);
    }

    @Override
    public String readString() {
        if (checkNull()) {
            return null;
        } else if (currentByte() != '"') {
            throw createException("Expected start of string", currentByte());
        }
        ensureStringBuffer();
        expectLowSurrogate = false;
        int index = ++currentIndex;
        int readableBytes = bufferLength - currentIndex;
        int firstRun = Math.min(stringBufferLength, readableBytes);
        byte b;
        int stringBuffIndex = 0;
        for (; stringBuffIndex < firstRun; stringBuffIndex++) {
            b = this.buffer[index++];
            if (b == '"') {
                currentIndex = --index;
                return new String(stringBuffer, 0, stringBuffIndex);
            } else if ((b ^ '\\') < 1) { //Either \ or UTF-8 byte detected
                //Either escaped sequence or multibyte detected
                currentIndex = --index;
                break;
            } else if (b < 0x20) {
                currentIndex = index;
                throw createException("Unescaped control character not allowed in string", b);
            }
            stringBuffer[stringBuffIndex] = (char) b;
        }
        if (stringBuffIndex == firstRun) {
            currentIndex = index;
        }

        if (stringBuffIndex == stringBufferLength) {
            increaseStringBuffer();
        }

        for (; currentIndex < bufferLength; currentIndex++) {
            b = buffer[currentIndex];
            if (b == '\\') {
                stringBuffer[stringBuffIndex++] = processEscapedSequence();
            } else if (expectLowSurrogate) {
                throw createException("Low surrogate must follow the high surrogate.", b);
            } else if (b == '"') {
                return new String(stringBuffer, 0, stringBuffIndex);
            } else if (b >= 0) {
                if (b < 0x20) {
                    currentIndex = index;
                    throw createException("Unescaped control character not allowed in string", b);
                }
                stringBuffer[stringBuffIndex++] = (char) b;
            } else {
                // Decode UTF-8 multibyte sequence starting with this byte
                stringBuffIndex = decodeUtf8(stringBuffIndex, b);
            }
            if (stringBuffIndex == stringBufferLength) {
                increaseStringBuffer();
            }
        }
        throw createException("End of the string expected. Incomplete JSON");
    }

    @Override
    public char readChar() {
        if (currentByte() != '\"') {
            throw createException("Start of a string expected", currentByte());
        }
        ensure(1);
        byte b = this.buffer[++currentIndex];
        char c;
        if (b == '\\') {
            c = processEscapedSequence();
        } else if (b >= 0) {
            if (b < 0x20) {
                throw createException("Unescaped control character not allowed in string", b);
            }
            c = (char) b;
        } else {
            c = decodeUtf8ToChar(b);
        }
        if (expectLowSurrogate) {
            throw createException("Low surrogate must follow the high surrogate.");
        }
        if (nextToken() != '\"') {
            throw createException("End of a string expected", currentByte());
        }
        return c;
    }

    @Override
    public boolean readBoolean() {
        byte b = currentByte();
        if (b == 't') {
            ensure(3);
            if (buffer[++currentIndex] == 'r'
                    && buffer[++currentIndex] == 'u'
                    && buffer[++currentIndex] == 'e') {
                return true;
            }
            throw createException("Expected value true");
        } else if (b == 'f') {
            ensure(4);
            if (buffer[++currentIndex] == 'a'
                    && buffer[++currentIndex] == 'l'
                    && buffer[++currentIndex] == 's'
                    && buffer[++currentIndex] == 'e') {
                return false;
            }
            throw createException("Expected value false");
        }
        throw createException("Expected boolean value", b);
    }

    @Override
    public byte readByte() {
        if (currentByte() == '-') {
            currentIndex = currentIndex + 1;
            return (byte) -parseByte(true);
        } else {
            return parseByte(false);
        }
    }

    @Override
    public short readShort() {
        if (currentByte() == '-') {
            currentIndex = currentIndex + 1;
            return (short) -parseShort(true);
        } else {
            return parseShort(false);
        }
    }

    @Override
    public int readInt() {
        if (currentByte() == '-') {
            currentIndex++;
            return -parseInt(true);
        } else {
            return parseInt(false);
        }
    }

    @Override
    public long readLong() {
        if (currentByte() == '-') {
            currentIndex++;
            return -parseLong(true);
        } else {
            return parseLong(false);
        }
    }

    @Override
    public float readFloat() {
        return (float) readDouble();
    }

    @Override
    @SuppressWarnings("checkstyle:MethodLength")
    public double readDouble() {
        if (currentByte() == '"') {
            return readQuotedSpecialDouble();
        }

        int start = currentIndex;
        int i = start;

        // Check for sign
        boolean negative = false;
        byte b = buffer[i];
        if (b == '-') {
            negative = true;
            i++;
        }
        // Check for special values (NaN, Infinity)
        b = buffer[i];
        if (b == 'N') {
            if (i + 2 <= bufferLength && buffer[i + 1] == 'a' && buffer[i + 2] == 'N') {
                currentIndex = i + 2;
                return Double.NaN;
            }
            throw createException("Invalid double number");
        } else if (b == 'I' || b == 'i') {
            if (i + 7 <= bufferLength
                    && buffer[i + 1] == 'n'
                    && buffer[i + 2] == 'f'
                    && buffer[i + 3] == 'i'
                    && buffer[i + 4] == 'n'
                    && buffer[i + 5] == 'i'
                    && buffer[i + 6] == 't'
                    && buffer[i + 7] == 'y') {
                currentIndex = i + 7;
                return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            }
            throw createException("Invalid double number");
        } else if (b < '0' || b > '9') {
            throw createException("Invalid double number");
        }

        // Parse mantissa
        long mantissa = 0;
        int digitsBeforeDecimal = 0;
        int significantDigits = 0;
        int leadingZerosAfterDecimal = 0;
        boolean hasDecimal = false;
        boolean foundNonZero = false;
        boolean delegateToJava = false;
        boolean hasFractionDigits = false;

        // Parse all digits
        while (i < bufferLength) {
            b = buffer[i];
            int digit = WHOLE_NUMBER_PARTS[b & 0xFF];
            if (digit > -1) {
                if (hasDecimal) {
                    hasFractionDigits = true;
                    // After decimal point
                    if (!foundNonZero && digit == 0) {
                        leadingZerosAfterDecimal++;
                    } else {
                        foundNonZero = true;
                        if (significantDigits < 17) {
                            mantissa = mantissa * 10 + digit;
                            significantDigits++;
                        } else {
                            delegateToJava = true;
                            break;
                        }
                    }
                } else {
                    // Before decimal point
                    if (digit != 0 || foundNonZero) {
                        foundNonZero = true;
                        digitsBeforeDecimal++;
                        if (significantDigits < 17) {
                            mantissa = mantissa * 10 + digit;
                            significantDigits++;
                        } else {
                            delegateToJava = true;
                            break;
                        }
                    }
                }
                i++;
            } else if (b == '.') {
                if (hasDecimal) {
                    currentIndex = i;
                    throw createException("Multiple decimal separators detected");
                }
                hasDecimal = true;
                i++;
            } else {
                currentIndex = i - 1;
                break;
            }
        }
        if (delegateToJava) {
            currentIndex = i;
            skipNumber();
            return Double.parseDouble(new String(buffer, start, currentIndex - start + 1, StandardCharsets.UTF_8));
        } else if (hasDecimal && !hasFractionDigits) {
            currentIndex = i;
            throw createException("Parsed Number is not having any fraction digits after the separator");
        }

        // Calculate the base decimal exponent
        int decimalExponent = 0;
        if (digitsBeforeDecimal > 0) {
            // 123.456 -> mantissa=123456, digitsBeforeDecimal=3, significantDigits=6 -> Base exponent -3
            decimalExponent = digitsBeforeDecimal - significantDigits;
        } else if (hasDecimal && foundNonZero) {
            // 0.00123 -> mantissa=123, leadingZerosAfterDecimal=2, significantDigits=3 -> Base exponent -5
            decimalExponent = -(leadingZerosAfterDecimal + significantDigits);
        }

        // Parse explicit exponent
        int explicitExp = 0;
        b = i < bufferLength ? buffer[i] : -1;
        if (b == 'e' || b == 'E') {
            i++;
            boolean expNegative = false;
            if (i < bufferLength) {
                if (buffer[i] == '-') {
                    expNegative = true;
                    i++;
                } else if (buffer[i] == '+') {
                    i++;
                }
            } else {
                currentIndex = i - 1;
                throw createException("Missing exponent value");
            }

            int digit = -1;
            boolean foundExponentValue = false;
            while (i < bufferLength && (digit = WHOLE_NUMBER_PARTS[buffer[i] & 0xFF]) > -1) {
                explicitExp = explicitExp * 10 + digit;
                if (explicitExp > 1000) {
                    break;
                }
                i++;
                foundExponentValue = true;
            }
            currentIndex = i - 1;
            if (!foundExponentValue) {
                throw createException("Exponent did not have a value specified");
            }
            if (digit == -1) {
                b = buffer[i];
                if (b == 'e' || b == 'E' || b == '.') {
                    throw createException("Duplicit exponent or decimal point detected");
                }
            }
            decimalExponent += expNegative ? -explicitExp : explicitExp;
        }
        if (currentIndex == start) {
            currentIndex = i;
        }

        // Handle zero
        if (mantissa == 0) {
            return negative ? -0.0 : 0.0;
        }

        if (decimalExponent >= POW10_DOUBLE_CACHE_SIZE || decimalExponent <= -POW10_DOUBLE_CACHE_SIZE) {
            skipNumber();
            // Delegate to Java for exact result
            try {
                return Double.parseDouble(new String(buffer, start, currentIndex - start + 1, StandardCharsets.UTF_8));
            } catch (NumberFormatException ex) {
                throw createException("Invalid number", ex);
            }
        }

        // FAST PATH: Handle common cases ourselves
        double result = mantissa;

        // Apply exponent using lookup table
        if (decimalExponent > 0) {
            result *= POW10_DOUBLE_CACHE[decimalExponent];
        } else if (decimalExponent < 0) {
            result /= POW10_DOUBLE_CACHE[-decimalExponent];
        }

        return negative ? -result : result;
    }

    private double readQuotedSpecialDouble() {
        ensure(4);
        int valueIndex = currentIndex + 1;
        byte b = buffer[valueIndex];
        if (b == 'N') {
            if (buffer[valueIndex + 1] == 'a'
                    && buffer[valueIndex + 2] == 'N'
                    && buffer[valueIndex + 3] == '"') {
                currentIndex = valueIndex + 3;
                return Double.NaN;
            }
            throw createException("Invalid double number");
        }
        if (b == '-') {
            ensure(10);
            if (matchesInfinity(valueIndex + 1) && buffer[valueIndex + 9] == '"') {
                currentIndex = valueIndex + 9;
                return Double.NEGATIVE_INFINITY;
            }
            throw createException("Invalid double number");
        }
        ensure(9);
        if (matchesInfinity(valueIndex) && buffer[valueIndex + 8] == '"') {
            currentIndex = valueIndex + 8;
            return Double.POSITIVE_INFINITY;
        }
        throw createException("Invalid double number");
    }

    private boolean matchesInfinity(int index) {
        byte b = buffer[index];
        return (b == 'I' || b == 'i')
                && buffer[index + 1] == 'n'
                && buffer[index + 2] == 'f'
                && buffer[index + 3] == 'i'
                && buffer[index + 4] == 'n'
                && buffer[index + 5] == 'i'
                && buffer[index + 6] == 't'
                && buffer[index + 7] == 'y';
    }

    @Override
    public BigInteger readBigInteger() {
        boolean inString = false;
        int start = currentIndex;
        if (buffer[start] == '"') {
            ensure(1);
            start = ++currentIndex;
            inString = true;
        }
        skipNumber();
        int length = currentIndex - start + 1;
        try {
            BigInteger bigInteger = new BigInteger(new String(buffer, start, length, StandardCharsets.US_ASCII));
            if (inString) {
                ensure(1);
                if (buffer[++currentIndex] != '"') {
                    throw createException("Expected the end of the string", buffer[currentIndex]);
                }
            }
            return bigInteger;
        } catch (NumberFormatException ex) {
            throw createException("Invalid number", ex);
        }
    }

    @Override
    public BigDecimal readBigDecimal() {
        boolean inString = false;
        if (buffer[currentIndex] == '"') {
            ensure(1);
            currentIndex++;
            inString = true;
        }
        char[] chars = readNumberAsCharArray();
        char first = chars[0];
        char last = chars[chars.length - 1];
        if (first == '.') {
            throw createException("Invalid number: leading decimal point");
        } else if (last == '.') {
            throw createException("Invalid number: trailing decimal point");
        } else if (first == '+') {
            throw createException("Invalid number: leading plus sign");
        } else if (first == '-' && chars.length > 1) {
            char second = chars[1];
            if (second == '.') {
                throw createException("Invalid number: leading minus sign followed by decimal point");
            }
        }
        try {
            BigDecimal bigDecimal = new BigDecimal(chars);
            if (inString) {
                ensure(1);
                if (buffer[++currentIndex] != '"') {
                    throw createException("Expected the end of the string", buffer[currentIndex]);
                }
            }
            return bigDecimal;
        } catch (NumberFormatException ex) {
            throw createException("Invalid number", ex);
        }
    }

    @Override
    public byte[] readBinary() {
        if (currentByte() != '\"') {
            throw createException("Binary data should be in a Base64 format and enclosed with double quotes", currentByte());
        }
        int start = currentIndex + 1;
        skipString();
        int length = currentIndex - start;
        byte[] bytes = new byte[length];
        System.arraycopy(buffer, start, bytes, 0, length);
        return Base64.getDecoder().decode(bytes);
    }

    void ensure(int amount) {
        if (currentIndex + amount >= bufferLength) {
            throw createException("There is not enough data to be read. Incomplete JSON");
        }
    }

    @Override
    public boolean checkNull() {
        if (currentByte() == 'n') {
            ensure(3);
            if (buffer[++currentIndex] == 'u'
                    && buffer[++currentIndex] == 'l'
                    && buffer[++currentIndex] == 'l') {
                return true;
            }
            throw createException("Unexpected value in JSON");
        }
        return false;
    }

    @Override
    @SuppressWarnings("checkstyle:MethodLength") // unrolled on purpose for the hot ASCII-key path
    public int readStringAsHash() {
        if (buffer[currentIndex] != '"') {
            throw createException("Hash calculation is intended only for String values");
        }
        int index = currentIndex + 1;
        int fnv1aHash = FNV_OFFSET_BASIS;
        // JSON object keys are typically short ASCII names, so handle the first 16 bytes without loop overhead.
        if (index + 15 < bufferLength) {
            byte b = buffer[index];
            if (b == '"') {
                currentIndex = index;
                return fnv1aHash;
            }
            if (b == '\\' || b < 0) {
                return continueStringAsHash(index, fnv1aHash);
            }
            fnv1aHash = updateFnv1aHash(fnv1aHash, b & 0xFF);

            b = buffer[index + 1];
            if (b == '"') {
                currentIndex = index + 1;
                return fnv1aHash;
            }
            if (b == '\\' || b < 0) {
                return continueStringAsHash(index + 1, fnv1aHash);
            }
            fnv1aHash = updateFnv1aHash(fnv1aHash, b & 0xFF);

            b = buffer[index + 2];
            if (b == '"') {
                currentIndex = index + 2;
                return fnv1aHash;
            }
            if (b == '\\' || b < 0) {
                return continueStringAsHash(index + 2, fnv1aHash);
            }
            fnv1aHash = updateFnv1aHash(fnv1aHash, b & 0xFF);

            b = buffer[index + 3];
            if (b == '"') {
                currentIndex = index + 3;
                return fnv1aHash;
            }
            if (b == '\\' || b < 0) {
                return continueStringAsHash(index + 3, fnv1aHash);
            }
            fnv1aHash = updateFnv1aHash(fnv1aHash, b & 0xFF);

            b = buffer[index + 4];
            if (b == '"') {
                currentIndex = index + 4;
                return fnv1aHash;
            }
            if (b == '\\' || b < 0) {
                return continueStringAsHash(index + 4, fnv1aHash);
            }
            fnv1aHash = updateFnv1aHash(fnv1aHash, b & 0xFF);

            b = buffer[index + 5];
            if (b == '"') {
                currentIndex = index + 5;
                return fnv1aHash;
            }
            if (b == '\\' || b < 0) {
                return continueStringAsHash(index + 5, fnv1aHash);
            }
            fnv1aHash = updateFnv1aHash(fnv1aHash, b & 0xFF);

            b = buffer[index + 6];
            if (b == '"') {
                currentIndex = index + 6;
                return fnv1aHash;
            }
            if (b == '\\' || b < 0) {
                return continueStringAsHash(index + 6, fnv1aHash);
            }
            fnv1aHash = updateFnv1aHash(fnv1aHash, b & 0xFF);

            b = buffer[index + 7];
            if (b == '"') {
                currentIndex = index + 7;
                return fnv1aHash;
            }
            if (b == '\\' || b < 0) {
                return continueStringAsHash(index + 7, fnv1aHash);
            }
            fnv1aHash = updateFnv1aHash(fnv1aHash, b & 0xFF);

            b = buffer[index + 8];
            if (b == '"') {
                currentIndex = index + 8;
                return fnv1aHash;
            }
            if (b == '\\' || b < 0) {
                return continueStringAsHash(index + 8, fnv1aHash);
            }
            fnv1aHash = updateFnv1aHash(fnv1aHash, b & 0xFF);

            b = buffer[index + 9];
            if (b == '"') {
                currentIndex = index + 9;
                return fnv1aHash;
            }
            if (b == '\\' || b < 0) {
                return continueStringAsHash(index + 9, fnv1aHash);
            }
            fnv1aHash = updateFnv1aHash(fnv1aHash, b & 0xFF);

            b = buffer[index + 10];
            if (b == '"') {
                currentIndex = index + 10;
                return fnv1aHash;
            }
            if (b == '\\' || b < 0) {
                return continueStringAsHash(index + 10, fnv1aHash);
            }
            fnv1aHash = updateFnv1aHash(fnv1aHash, b & 0xFF);

            b = buffer[index + 11];
            if (b == '"') {
                currentIndex = index + 11;
                return fnv1aHash;
            }
            if (b == '\\' || b < 0) {
                return continueStringAsHash(index + 11, fnv1aHash);
            }
            fnv1aHash = updateFnv1aHash(fnv1aHash, b & 0xFF);

            b = buffer[index + 12];
            if (b == '"') {
                currentIndex = index + 12;
                return fnv1aHash;
            }
            if (b == '\\' || b < 0) {
                return continueStringAsHash(index + 12, fnv1aHash);
            }
            fnv1aHash = updateFnv1aHash(fnv1aHash, b & 0xFF);

            b = buffer[index + 13];
            if (b == '"') {
                currentIndex = index + 13;
                return fnv1aHash;
            }
            if (b == '\\' || b < 0) {
                return continueStringAsHash(index + 13, fnv1aHash);
            }
            fnv1aHash = updateFnv1aHash(fnv1aHash, b & 0xFF);

            b = buffer[index + 14];
            if (b == '"') {
                currentIndex = index + 14;
                return fnv1aHash;
            }
            if (b == '\\' || b < 0) {
                return continueStringAsHash(index + 14, fnv1aHash);
            }
            fnv1aHash = updateFnv1aHash(fnv1aHash, b & 0xFF);

            b = buffer[index + 15];
            if (b == '"') {
                currentIndex = index + 15;
                return fnv1aHash;
            }
            if (b == '\\' || b < 0) {
                return continueStringAsHash(index + 15, fnv1aHash);
            }
            fnv1aHash = updateFnv1aHash(fnv1aHash, b & 0xFF);
            index += 16;
        }
        return continueStringAsHash(index, fnv1aHash);
    }

    private int continueStringAsHash(int index, int fnv1aHash) {
        while (index < bufferLength) {
            byte b = buffer[index];
            if (b == '"') {
                currentIndex = index;
                return fnv1aHash;
            }
            if (b == '\\') {
                currentIndex = index;
                return readEscapedStringAsHash(fnv1aHash);
            }
            if (b >= 0) {
                fnv1aHash = updateFnv1aHash(fnv1aHash, b & 0xFF);
                index++;
            } else {
                currentIndex = index;
                fnv1aHash = hashUtf8Bytes(fnv1aHash, b);
                index = currentIndex + 1;
            }
        }
        throw createException("Unexpected end of string value. Probably incomplete JSON");
    }

    private int readEscapedStringAsHash(int fnv1aHash) {
        char highSurrogate = 0;
        for (; currentIndex < bufferLength; currentIndex++) {
            byte b = buffer[currentIndex];
            if (b == '\\') {
                char escaped = readEscapedCodeUnit();
                if (Character.isHighSurrogate(escaped)) {
                    if (highSurrogate != 0) {
                        throw createException("A high surrogate must always be followed by a low surrogate");
                    }
                    highSurrogate = escaped;
                } else if (Character.isLowSurrogate(escaped)) {
                    if (highSurrogate == 0) {
                        throw createException("A low surrogate must always follow a high surrogate");
                    }
                    fnv1aHash = hashUtf8CodePoint(fnv1aHash, Character.toCodePoint(highSurrogate, escaped));
                    highSurrogate = 0;
                } else {
                    if (highSurrogate != 0) {
                        throw createException("Low surrogate was expected to follow the high surrogate, "
                                                      + "but found " + Parsers.toPrintableForm(escaped));
                    }
                    fnv1aHash = hashUtf8CodePoint(fnv1aHash, escaped);
                }
            } else if (highSurrogate != 0) {
                throw createException("Low surrogate must follow the high surrogate.", b);
            } else if (b == '"') {
                return fnv1aHash;
            } else if (b >= 0) {
                fnv1aHash = updateFnv1aHash(fnv1aHash, b & 0xFF);
            } else {
                fnv1aHash = hashUtf8Bytes(fnv1aHash, b);
            }
        }
        throw createException("End of the string expected. Incomplete JSON");
    }

    private char readEscapedCodeUnit() {
        if (!hasNext()) {
            throw createException("Error while processing an escaped string sequence. Incomplete JSON");
        }
        byte b = buffer[++currentIndex];
        return switch (b) {
            case '\\', '"', '/' -> (char) b;
            case 'b' -> '\b';
            case 't' -> '\t';
            case 'n' -> '\n';
            case 'f' -> '\f';
            case 'r' -> '\r';
            case 'u' -> {
                ensure(4);
                yield (char) (
                        (Parsers.translateHex(buffer[++currentIndex], this) << 12)
                                + (Parsers.translateHex(buffer[++currentIndex], this) << 8)
                                + (Parsers.translateHex(buffer[++currentIndex], this) << 4)
                                + Parsers.translateHex(buffer[++currentIndex], this));
            }
            default -> throw createException("Invalid escaped value", b);
        };
    }

    private int hashUtf8Bytes(int fnv1aHash, byte currentByte) {
        fnv1aHash = updateFnv1aHash(fnv1aHash, currentByte & 0xFF);
        if ((currentByte & 0xE0) == 0xC0) {
            ensure(1);
            byte second = readNextByte();
            Parsers.decodeUtf8TwoByte(currentByte, second, this);
            fnv1aHash = updateFnv1aHash(fnv1aHash, second & 0xFF);
            return fnv1aHash;
        }
        if ((currentByte & 0xF0) == 0xE0) {
            ensure(2);
            byte second = buffer[++currentIndex];
            byte third = buffer[++currentIndex];
            Parsers.decodeUtf8ThreeByte(currentByte, second, third, this);
            fnv1aHash = updateFnv1aHash(fnv1aHash, second & 0xFF);
            fnv1aHash = updateFnv1aHash(fnv1aHash, third & 0xFF);
            return fnv1aHash;
        }
        if ((currentByte & 0xF8) == 0xF0) {
            ensure(3);
            byte b2 = buffer[++currentIndex];
            byte b3 = buffer[++currentIndex];
            byte b4 = buffer[++currentIndex];
            Parsers.decodeUtf8FourByte(currentByte, b2, b3, b4, this);
            fnv1aHash = updateFnv1aHash(fnv1aHash, b2 & 0xFF);
            fnv1aHash = updateFnv1aHash(fnv1aHash, b3 & 0xFF);
            fnv1aHash = updateFnv1aHash(fnv1aHash, b4 & 0xFF);
            return fnv1aHash;
        }
        throw createException("Invalid UTF-8 byte", currentByte);
    }

    private static int hashUtf8CodePoint(int fnv1aHash, int codePoint) {
        if (codePoint <= 0x7F) {
            return updateFnv1aHash(fnv1aHash, codePoint);
        }
        if (codePoint <= 0x7FF) {
            fnv1aHash = updateFnv1aHash(fnv1aHash, 0xC0 | (codePoint >> 6));
            return updateFnv1aHash(fnv1aHash, 0x80 | (codePoint & 0x3F));
        }
        if (codePoint <= 0xFFFF) {
            fnv1aHash = updateFnv1aHash(fnv1aHash, 0xE0 | (codePoint >> 12));
            fnv1aHash = updateFnv1aHash(fnv1aHash, 0x80 | ((codePoint >> 6) & 0x3F));
            return updateFnv1aHash(fnv1aHash, 0x80 | (codePoint & 0x3F));
        }
        fnv1aHash = updateFnv1aHash(fnv1aHash, 0xF0 | (codePoint >> 18));
        fnv1aHash = updateFnv1aHash(fnv1aHash, 0x80 | ((codePoint >> 12) & 0x3F));
        fnv1aHash = updateFnv1aHash(fnv1aHash, 0x80 | ((codePoint >> 6) & 0x3F));
        return updateFnv1aHash(fnv1aHash, 0x80 | (codePoint & 0x3F));
    }

    private static int updateFnv1aHash(int fnv1aHash, int unsignedByte) {
        fnv1aHash ^= unsignedByte;
        fnv1aHash *= FNV_PRIME;
        return fnv1aHash;
    }

    static int fnv1aHashUtf8(String value) {
        int fnvHash = FNV_OFFSET_BASIS;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            fnvHash = updateFnv1aHash(fnvHash, b & 0xFF);
        }
        return fnvHash;
    }

    @Override
    public JsonException createException(String message) {
        clearMark();
        return new JsonException(exceptionMessage(message));
    }

    @Override
    public JsonException createException(String message, Exception e) {
        clearMark();
        return new JsonException(exceptionMessage(message), e);
    }

    private String exceptionMessage(String message) {
        int start = Math.max(currentIndex - 10, 0);
        int length = Math.min(currentIndex + 10, bufferLength - start);
        int dataIndex = currentIndex - start;
        BufferData bufferData = BufferData.create(buffer, start, length);

        return message + "\n"
                + "Error at JSON index: " + currentIndex + "\n"
                + "Data index: " + dataIndex + "\n"
                + "Data: \n"
                + bufferData.debugDataHex(false);
    }

    @Override
    public void mark() {
        if (replayMarked) {
            throw new IllegalStateException("Parser has already been marked for replaying. "
                                                    + "Cant do it twice without consuming the mark with either "
                                                    + "clearMark or resetToMark methods");
        }
        replayMarked = true;
        mark = currentIndex;
    }

    @Override
    public void clearMark() {
        replayMarked = false;
        mark = -1;
    }

    @Override
    public void resetToMark() {
        if (replayMarked) {
            replayMarked = false;
            currentIndex = mark;
        } else {
            throw new IllegalStateException("Parser tried to reset to the marked place, but no mark was found");
        }
    }

    @Override
    public void skip() {
        switch (currentByte()) {
        case '"':
            skipString();
            break;
        case '{':
            skipObject();
            break;
        case '[':
            skipArray();
            break;
        case '-':
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
            skipNumber();
            break;
        case 't':
        case 'n':
            ensure(3);
            currentIndex += 3;
            break;
        case 'f':
            ensure(4);
            currentIndex += 4;
            break;
        case ',':
        case ':':
            //NOOP
            break;
        default:
            throw createException("Invalid JSON value to skip", currentByte());
        }
    }

    void skipString() {
        boolean isEscaped = false;
        for (int index = this.currentIndex + 1; index < this.bufferLength; index++) {
            byte b = this.buffer[index];
            if (b == '\\') {
                isEscaped = !isEscaped;
                continue;
            } else if (b == '"') {
                if (!isEscaped) {
                    this.currentIndex = index;
                    return;
                }
            }
            isEscaped = false;
        }
        throw createException("Unexpected end of string. Incomplete JSON or incorrect use of the skip method");
    }

    void skipNumber() {
        byte b;
        for (int index = this.currentIndex; index < this.bufferLength; index++) {
            b = this.buffer[index];
            //we do not need to validate whether this is a valid number since we are not processing it.
            //simply skip until you find any non-numeric bound character
            if (!VALID_NUMBER_PARTS[b]) {
                this.currentIndex = index - 1;
                return;
            }
        }
        this.currentIndex = this.bufferLength - 1;
    }

    char processEscapedSequence() {
        if (!hasNext()) {
            throw createException("Error while processing an escaped string sequence. Incomplete JSON");
        }
        byte b = buffer[++currentIndex];
        if (expectLowSurrogate && b != 'u') {
            throw createException("Low surrogate must follow the high surrogate.", b);
        }
        switch (b) {
        case '\\':
        case '"':
        case '/':
            return (char) b;
        case 'b':
            return '\b';
        case 't':
            return '\t';
        case 'n':
            return '\n';
        case 'f':
            return '\f';
        case 'r':
            return '\r';
        case 'u':
            ensure(4); // Need 4 hex digits
            char tmp = (char) (
                    (Parsers.translateHex(buffer[++currentIndex], this) << 12)
                            + (Parsers.translateHex(buffer[++currentIndex], this) << 8)
                            + (Parsers.translateHex(buffer[++currentIndex], this) << 4)
                            + Parsers.translateHex(buffer[++currentIndex], this));
            // Handle JSON's UTF-16 surrogate pair encoding (\\uXXXX\\uYYYY for code points > U+FFFF)
            if (Character.isHighSurrogate(tmp)) {
                // High surrogate: must be followed by a low surrogate
                if (expectLowSurrogate) {
                    throw createException("A high surrogate must always be followed by a low surrogate");
                } else {
                    expectLowSurrogate = true; // Expect low surrogate next
                }
            } else if (Character.isLowSurrogate(tmp)) {
                // Low surrogate: must follow a high surrogate
                if (expectLowSurrogate) {
                    expectLowSurrogate = false; // Pair complete
                } else {
                    throw createException("A low surrogate must always follow a high surrogate");
                }
            } else if (expectLowSurrogate) {
                // Expected low surrogate but got neither high nor low
                throw createException("Low surrogate was expected to follow the high surrogate, "
                                              + "but found " + Parsers.toPrintableForm(tmp));
            }
            return tmp;
        default:
            throw createException("Invalid escaped value", b);
        }
    }

    /**
     * Decodes a UTF-8 encoded byte sequence into one or more UTF-16 characters (using surrogates for Unicode code points above
     * U+FFFF).
     * This method handles variable-length UTF-8 sequences (2, 3, or 4 bytes) as defined by RFC 3629.
     *
     * UTF-8 encoding uses variable byte sequences to represent Unicode code points:
     * - 2-byte sequences (110xxxxx 10yyyyyy): represent code points U+0080 to U+07FF
     * - 3-byte sequences (1110xxxx 10yyyyyy 10zzzzzz): represent code points U+0800 to U+FFFF
     * - 4-byte sequences (11110www 10xxxxxx 10yyyyyy 10zzzzzz): represent code points U+10000 to U+10FFFF
     *
     * For code points above U+FFFF, this method converts them to UTF-16 surrogate pairs since Java's char type
     * can only represent values up to U+FFFF. The conversion follows the standard UTF-16 encoding scheme:
     * - Subtract 0x10000 from the code point to get a 20-bit value
     * - High surrogate = (value >> 10) + 0xD800 (takes the upper 10 bits)
     * - Low surrogate = (value & 0x3FF) + 0xDC00 (takes the lower 10 bits)
     *
     * @param position the current position in the string buffer to write the decoded characters
     * @param currentByte the first byte of the UTF-8 sequence (must have high bit set, indicating multibyte sequence)
     * @return the new position in the string buffer after writing the decoded characters
     * @throws JsonException if the UTF-8 sequence is invalid, incomplete, or represents an out-of-range code point
     */
    int decodeUtf8(int position, byte currentByte) {
        int codePoint = readUtf8CodePoint(currentByte);
        if (codePoint >= 0x10000) {
            codePoint -= 0x10000;
            stringBuffer[position++] = (char) ((codePoint >> 10) + 0xD800);
            if (position == stringBufferLength) {
                increaseStringBuffer();
            }
            stringBuffer[position++] = (char) ((codePoint & 0x3FF) + 0xDC00);
        } else {
            stringBuffer[position++] = (char) codePoint;
        }
        return position;
    }

    private int readUtf8CodePoint(byte currentByte) {
        int value = currentByte & 0xFF;
        if ((value & 0xE0) == 0xC0) {
            ensure(1);
            return Parsers.decodeUtf8TwoByte(currentByte, readNextByte(), this);
        }
        if ((value & 0xF0) == 0xE0) {
            ensure(2);
            byte second = buffer[++currentIndex];
            byte third = buffer[++currentIndex];
            return Parsers.decodeUtf8ThreeByte(currentByte, second, third, this);
        }
        if ((value & 0xF8) == 0xF0) {
            ensure(3);
            byte second = buffer[++currentIndex];
            byte third = buffer[++currentIndex];
            byte fourth = buffer[++currentIndex];
            return Parsers.decodeUtf8FourByte(currentByte, second, third, fourth, this);
        }
        throw createException("Invalid UTF-8 byte", currentByte);
    }

    void increaseStringBuffer() {
        increaseStringBuffer(stringBufferLength * 2);
    }

    private char[] readNumberAsCharArray() {
        ensureStringBuffer();
        int readableBytes = bufferLength - currentIndex;
        int firstRun = Math.min(stringBufferLength, readableBytes);
        stringBuffer[0] = (char) currentByte();
        int stringBuffIndex = 1;
        byte b;
        for (; stringBuffIndex < firstRun; stringBuffIndex++) {
            b = this.buffer[++currentIndex];
            if (!VALID_NUMBER_PARTS[b]) {
                currentIndex--;
                char[] chars = new char[stringBuffIndex];
                System.arraycopy(stringBuffer, 0, chars, 0, stringBuffIndex);
                return chars;
            }
            stringBuffer[stringBuffIndex] = (char) b;
        }
        if (stringBuffIndex == stringBufferLength) {
            increaseStringBuffer();
        }

        while (hasNext()) {
            b = readNextByte();
            if (!VALID_NUMBER_PARTS[b]) {
                currentIndex--;
                char[] chars = new char[stringBuffIndex];
                System.arraycopy(stringBuffer, 0, chars, 0, stringBuffIndex);
                return chars;
            }
            stringBuffer[stringBuffIndex++] = (char) b;
            if (stringBuffIndex == stringBufferLength) {
                increaseStringBuffer();
            }
        }
        char[] chars = new char[stringBuffIndex];
        System.arraycopy(stringBuffer, 0, chars, 0, stringBuffIndex);
        return chars;
    }

    private void increaseStringBuffer(int size) {
        stringBufferLength = size;
        char[] newBuf = new char[stringBufferLength];
        if (stringBuffer != null) {
            System.arraycopy(stringBuffer, 0, newBuf, 0, stringBuffer.length);
        }
        stringBuffer = newBuf;
    }

    private void ensureStringBuffer() {
        if (stringBuffer == null) {
            stringBuffer = new char[stringBufferLength];
        }
    }

    /**
     * Decodes a UTF-8 encoded byte sequence into a single char, rejecting code points that require UTF-16 surrogates.
     * This method handles the same UTF-8 sequences as decodeUtf8(), but is designed for contexts where only single chars
     * are acceptable (e.g., readChar() method). If a 4-byte UTF-8 sequence would produce a code point above U+FFFF
     * that requires surrogate pairs, it throws an exception instead of converting.
     *
     * The decoding process follows the same UTF-8 rules as decodeUtf8(), extracting payload bits from continuation bytes
     * (which always start with 10xxxxxx) and assembling them with the leading byte. However, unlike decodeUtf8(),
     * this method ensures the final code point can be represented as a single Java char (U+0000 to U+FFFF).
     *
     * @param currentByte the first byte of the UTF-8 sequence (must have high bit set, indicating multibyte sequence)
     * @return the decoded char value if the code point fits in a single char
     * @throws JsonException if the UTF-8 sequence is invalid, incomplete, or represents a code point that would require
     * surrogates
     */
    private char decodeUtf8ToChar(byte currentByte) {
        int codePoint = readUtf8CodePoint(currentByte);
        if (codePoint >= 0x10000) {
            throw createException("UTF-16 high and low surrogates cannot be represented as a single char");
        }
        return (char) codePoint;
    }

    private byte parseByte(boolean negative) {
        int digit1 = WHOLE_NUMBER_PARTS[currentByte() & 0xFF];
        if (digit1 < 0) {
            throw createException("Expected number", currentByte());
        }
        if (currentIndex + 4 < bufferLength) {
            int digit2 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
            if (digit2 < 0) {
                skipRemaining(digit2);
                return (byte) digit1;
            }
            int digit3 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
            int possibleResult = digit1 * 10 + digit2;
            if (digit3 < 0) {
                skipRemaining(digit3);
                return (byte) possibleResult;
            }
            int digit4 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
            if (digit4 < 0) {
                skipRemaining(digit4);
                if (negative) {
                    if (-possibleResult > -BYTE_SIZE_BORDER || (-possibleResult == -BYTE_SIZE_BORDER && digit3 <= 8)) {
                        return (byte) (possibleResult * 10 + digit3);
                    }
                } else if (possibleResult < BYTE_SIZE_BORDER || (possibleResult == BYTE_SIZE_BORDER && digit3 <= 7)) {
                    return (byte) (possibleResult * 10 + digit3);
                }
            }
            throw createException("The number is too big for a byte value");
        }
        boolean hasNext = hasNext();
        int digit2 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit2 < 0) {
            if (hasNext) {
                skipRemaining(digit2);
            }
            return (byte) digit1;
        }
        hasNext = hasNext();
        int digit3 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        int possibleResult = digit1 * 10 + digit2;
        if (digit3 < 0) {
            if (hasNext) {
                skipRemaining(digit3);
            }
            return (byte) possibleResult;
        }
        hasNext = hasNext();
        int digit4 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit4 < 0) {
            if (hasNext) {
                skipRemaining(digit4);
            }
            if (negative) {
                if (-possibleResult > -BYTE_SIZE_BORDER || (-possibleResult == -BYTE_SIZE_BORDER && digit3 <= 8)) {
                    return (byte) (possibleResult * 10 + digit3);
                }
            } else if (possibleResult < BYTE_SIZE_BORDER || (possibleResult == BYTE_SIZE_BORDER && digit3 <= 7)) {
                return (byte) (possibleResult * 10 + digit3);
            }
        }
        throw createException("The number is too big for a byte value");
    }

    private short parseShort(boolean negative) {
        int digit1 = WHOLE_NUMBER_PARTS[currentByte() & 0xFF];
        if (digit1 < 0) {
            throw createException("Expected number", currentByte());
        }
        if (currentIndex + 6 < bufferLength) {
            int digit2 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
            if (digit2 < 0) {
                skipRemaining(digit2);
                return (short) digit1;
            }
            int digit3 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
            if (digit3 < 0) {
                skipRemaining(digit3);
                return (short) (digit1 * 10 + digit2);
            }
            int digit4 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
            if (digit4 < 0) {
                skipRemaining(digit4);
                return (short) (digit1 * 100 + digit2 * 10 + digit3);
            }
            int digit5 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
            short possibleResult = (short) (digit1 * 1000 + digit2 * 100 + digit3 * 10 + digit4);
            if (digit5 < 0) {
                skipRemaining(digit5);
                return possibleResult;
            }
            int digit6 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
            if (digit6 < 0) {
                skipRemaining(digit6);
                if (negative) {
                    if (-possibleResult > -SHORT_SIZE_BORDER || (-possibleResult == -SHORT_SIZE_BORDER && digit5 <= 8)) {
                        return (short) (possibleResult * 10 + digit5);
                    }
                } else if (possibleResult < SHORT_SIZE_BORDER || (possibleResult == SHORT_SIZE_BORDER && digit5 <= 7)) {
                    return (short) (possibleResult * 10 + digit5);
                }
            }
            throw createException("The number is too big for a short value");
        }
        boolean hasNext = hasNext();
        int digit2 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit2 < 0) {
            if (hasNext) {
                skipRemaining(digit2);
            }
            return (short) digit1;
        }
        hasNext = hasNext();
        int digit3 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit3 < 0) {
            if (hasNext) {
                skipRemaining(digit3);
            }
            return (short) (digit1 * 10 + digit2);
        }
        hasNext = hasNext();
        int digit4 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit4 < 0) {
            if (hasNext) {
                skipRemaining(digit4);
            }
            return (short) (digit1 * 100 + digit2 * 10 + digit3);
        }
        hasNext = hasNext();
        int digit5 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        short possibleResult = (short) (digit1 * 1000 + digit2 * 100 + digit3 * 10 + digit4);
        if (digit5 < 0) {
            if (hasNext) {
                skipRemaining(digit5);
            }
            return possibleResult;
        }
        hasNext = hasNext();
        int digit6 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit6 < 0) {
            if (hasNext) {
                skipRemaining(digit6);
            }
            if (negative) {
                if (-possibleResult > -SHORT_SIZE_BORDER || (-possibleResult == -SHORT_SIZE_BORDER && digit5 <= 8)) {
                    return (short) (possibleResult * 10 + digit5);
                }
            } else if (possibleResult < SHORT_SIZE_BORDER || (possibleResult == SHORT_SIZE_BORDER && digit5 <= 7)) {
                return (short) (possibleResult * 10 + digit5);
            }
        }
        throw createException("The number is too big for a short value");
    }

    private int parseInt(boolean negative) {
        if (currentIndex + 11 < bufferLength) {
            return parseIntFast(negative);
        }
        int digit1 = WHOLE_NUMBER_PARTS[currentByte()];
        if (digit1 < 0) {
            throw createException("Expected number", currentByte());
        }
        boolean hasNext = hasNext();
        int digit2 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit2 < 0) {
            if (hasNext) {
                skipRemaining(digit2);
            }
            return digit1;
        }
        hasNext = hasNext();
        int digit3 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit3 < 0) {
            if (hasNext) {
                skipRemaining(digit3);
            }
            return digit1 * 10 + digit2;
        }
        hasNext = hasNext();
        int digit4 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit4 < 0) {
            if (hasNext) {
                skipRemaining(digit4);
            }
            return digit1 * 100
                    + digit2 * 10
                    + digit3;
        }
        hasNext = hasNext();
        int digit5 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit5 < 0) {
            if (hasNext) {
                skipRemaining(digit5);
            }
            return digit1 * 1000
                    + digit2 * 100
                    + digit3 * 10
                    + digit4;
        }
        hasNext = hasNext();
        int digit6 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit6 < 0) {
            if (hasNext) {
                skipRemaining(digit6);
            }
            return digit1 * 10000
                    + digit2 * 1000
                    + digit3 * 100
                    + digit4 * 10
                    + digit5;
        }
        hasNext = hasNext();
        int digit7 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit7 < 0) {
            if (hasNext) {
                skipRemaining(digit7);
            }
            return digit1 * 100000
                    + digit2 * 10000
                    + digit3 * 1000
                    + digit4 * 100
                    + digit5 * 10
                    + digit6;
        }
        hasNext = hasNext();
        int digit8 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit8 < 0) {
            if (hasNext) {
                skipRemaining(digit8);
            }
            return digit1 * 1000000
                    + digit2 * 100000
                    + digit3 * 10000
                    + digit4 * 1000
                    + digit5 * 100
                    + digit6 * 10
                    + digit7;
        }
        hasNext = hasNext();
        int digit9 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit9 < 0) {
            if (hasNext) {
                skipRemaining(digit9);
            }
            return digit1 * 10000000
                    + digit2 * 1000000
                    + digit3 * 100000
                    + digit4 * 10000
                    + digit5 * 1000
                    + digit6 * 100
                    + digit7 * 10
                    + digit8;
        }
        hasNext = hasNext();
        int digit10 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        int possibleResult = digit1 * 100000000
                + digit2 * 10000000
                + digit3 * 1000000
                + digit4 * 100000
                + digit5 * 10000
                + digit6 * 1000
                + digit7 * 100
                + digit8 * 10
                + digit9;
        if (digit10 < 0) {
            if (hasNext) {
                skipRemaining(digit10);
            }
            return possibleResult;
        }
        hasNext = hasNext();
        int digit11 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit11 < 0) {
            if (hasNext) {
                skipRemaining(digit11);
            }
            // Check for overflow before adding the last digit
            // INT_SIZE_BORDER = Integer.MAX_VALUE / 10 = 214748364
            // For positive: possibleResult < 214748364 or (== and digit10 <= 7) since 2147483647 is max
            // For negative: -possibleResult > -214748364 or (== and digit10 <= 8) since -2147483648 is min
            if (negative) {
                if (-possibleResult > -INT_SIZE_BORDER || (-possibleResult == -INT_SIZE_BORDER && digit10 <= 8)) {
                    return possibleResult * 10 + digit10;
                }
            } else if (possibleResult < INT_SIZE_BORDER || (possibleResult == INT_SIZE_BORDER && digit10 <= 7)) {
                return possibleResult * 10 + digit10;
            }
        }
        throw createException("The number is too big for an int value");
    }

    private int parseIntFast(boolean negative) {
        int digit1 = WHOLE_NUMBER_PARTS[currentByte() & 0xFF];
        if (digit1 < 0) {
            throw createException("Expected number", currentByte());
        }
        int digit2 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit2 < 0) {
            skipRemaining(digit2);
            return digit1;
        }
        int digit3 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit3 < 0) {
            skipRemaining(digit3);
            return digit1 * 10 + digit2;
        }
        int digit4 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit4 < 0) {
            skipRemaining(digit4);
            return digit1 * 100
                    + digit2 * 10
                    + digit3;
        }
        int digit5 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit5 < 0) {
            skipRemaining(digit5);
            return digit1 * 1000
                    + digit2 * 100
                    + digit3 * 10
                    + digit4;
        }
        int digit6 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit6 < 0) {
            skipRemaining(digit6);
            return digit1 * 10000
                    + digit2 * 1000
                    + digit3 * 100
                    + digit4 * 10
                    + digit5;
        }
        int digit7 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit7 < 0) {
            skipRemaining(digit7);
            return digit1 * 100000
                    + digit2 * 10000
                    + digit3 * 1000
                    + digit4 * 100
                    + digit5 * 10
                    + digit6;
        }
        int digit8 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit8 < 0) {
            skipRemaining(digit8);
            return digit1 * 1000000
                    + digit2 * 100000
                    + digit3 * 10000
                    + digit4 * 1000
                    + digit5 * 100
                    + digit6 * 10
                    + digit7;
        }
        int digit9 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit9 < 0) {
            skipRemaining(digit9);
            return digit1 * 10000000
                    + digit2 * 1000000
                    + digit3 * 100000
                    + digit4 * 10000
                    + digit5 * 1000
                    + digit6 * 100
                    + digit7 * 10
                    + digit8;
        }
        int digit10 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        int possibleResult = digit1 * 100000000
                + digit2 * 10000000
                + digit3 * 1000000
                + digit4 * 100000
                + digit5 * 10000
                + digit6 * 1000
                + digit7 * 100
                + digit8 * 10
                + digit9;
        if (digit10 < 0) {
            skipRemaining(digit10);
            return possibleResult;
        }
        int digit11 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit11 < 0) {
            skipRemaining(digit11);
            if (negative) {
                if (-possibleResult > -INT_SIZE_BORDER || (-possibleResult == -INT_SIZE_BORDER && digit10 <= 8)) {
                    return possibleResult * 10 + digit10;
                }
            } else if (possibleResult < INT_SIZE_BORDER || (possibleResult == INT_SIZE_BORDER && digit10 <= 7)) {
                return possibleResult * 10 + digit10;
            }
        }
        throw createException("The number is too big for an int value");
    }

    @SuppressWarnings("checkstyle:MethodLength")
    private long parseLong(boolean negative) {
        if (currentIndex + 19 < bufferLength) {
            return parseLongFast(negative);
        }
        boolean hasNext = hasNext();
        int digit1 = WHOLE_NUMBER_PARTS[currentByte()];
        if (digit1 < 0) {
            throw createException("Expected number", currentByte());
        }
        int digit2 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit2 < 0) {
            if (hasNext) {
                skipRemaining(digit2);
            }
            return digit1;
        }
        hasNext = hasNext();
        int digit3 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit3 < 0) {
            if (hasNext) {
                skipRemaining(digit3);
            }
            return digit1 * 10L + digit2;
        }
        hasNext = hasNext();
        int digit4 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit4 < 0) {
            if (hasNext) {
                skipRemaining(digit4);
            }
            return digit1 * 100L + digit2 * 10L + digit3;
        }
        hasNext = hasNext();
        int digit5 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit5 < 0) {
            if (hasNext) {
                skipRemaining(digit5);
            }
            return digit1 * 1000L + digit2 * 100L + digit3 * 10L + digit4;
        }
        hasNext = hasNext();
        int digit6 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit6 < 0) {
            if (hasNext) {
                skipRemaining(digit6);
            }
            return digit1 * 10000L + digit2 * 1000L + digit3 * 100L + digit4 * 10L + digit5;
        }
        hasNext = hasNext();
        int digit7 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit7 < 0) {
            if (hasNext) {
                skipRemaining(digit7);
            }
            return digit1 * 100000L + digit2 * 10000L + digit3 * 1000L + digit4 * 100L + digit5 * 10L + digit6;
        }
        hasNext = hasNext();
        int digit8 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit8 < 0) {
            if (hasNext) {
                skipRemaining(digit8);
            }
            return digit1 * 1000000L + digit2 * 100000L + digit3 * 10000L + digit4 * 1000L + digit5 * 100L + digit6 * 10L + digit7;
        }
        hasNext = hasNext();
        int digit9 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit9 < 0) {
            if (hasNext) {
                skipRemaining(digit9);
            }
            return digit1 * 10000000L + digit2 * 1000000L + digit3 * 100000L + digit4 * 10000L + digit5 * 1000L + digit6 * 100L
                    + digit7 * 10L + digit8;
        }
        hasNext = hasNext();
        int digit10 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit10 < 0) {
            if (hasNext) {
                skipRemaining(digit10);
            }
            return digit1 * 100000000L
                    + digit2 * 10000000L
                    + digit3 * 1000000L
                    + digit4 * 100000L
                    + digit5 * 10000L
                    + digit6 * 1000L
                    + digit7 * 100L
                    + digit8 * 10L
                    + digit9;
        }
        hasNext = hasNext();
        int digit11 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit11 < 0) {
            if (hasNext) {
                skipRemaining(digit11);
            }
            return digit1 * 1000000000L
                    + digit2 * 100000000L
                    + digit3 * 10000000L
                    + digit4 * 1000000L
                    + digit5 * 100000L
                    + digit6 * 10000L
                    + digit7 * 1000L
                    + digit8 * 100L
                    + digit9 * 10L
                    + digit10;
        }
        hasNext = hasNext();
        int digit12 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit12 < 0) {
            if (hasNext) {
                skipRemaining(digit12);
            }
            return digit1 * 10000000000L
                    + digit2 * 1000000000L
                    + digit3 * 100000000L
                    + digit4 * 10000000L
                    + digit5 * 1000000L
                    + digit6 * 100000L
                    + digit7 * 10000L
                    + digit8 * 1000L
                    + digit9 * 100L
                    + digit10 * 10L
                    + digit11;
        }
        hasNext = hasNext();
        int digit13 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit13 < 0) {
            if (hasNext) {
                skipRemaining(digit13);
            }
            return digit1 * 100000000000L
                    + digit2 * 10000000000L
                    + digit3 * 1000000000L
                    + digit4 * 100000000L
                    + digit5 * 10000000L
                    + digit6 * 1000000L
                    + digit7 * 100000L
                    + digit8 * 10000L
                    + digit9 * 1000L
                    + digit10 * 100L
                    + digit11 * 10L
                    + digit12;
        }
        hasNext = hasNext();
        int digit14 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit14 < 0) {
            if (hasNext) {
                skipRemaining(digit14);
            }
            return digit1 * 1000000000000L
                    + digit2 * 100000000000L
                    + digit3 * 10000000000L
                    + digit4 * 1000000000L
                    + digit5 * 100000000L
                    + digit6 * 10000000L
                    + digit7 * 1000000L
                    + digit8 * 100000L
                    + digit9 * 10000L
                    + digit10 * 1000L
                    + digit11 * 100L
                    + digit12 * 10L
                    + digit13;
        }
        hasNext = hasNext();
        int digit15 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit15 < 0) {
            if (hasNext) {
                skipRemaining(digit15);
            }
            return digit1 * 10000000000000L
                    + digit2 * 1000000000000L
                    + digit3 * 100000000000L
                    + digit4 * 10000000000L
                    + digit5 * 1000000000L
                    + digit6 * 100000000L
                    + digit7 * 10000000L
                    + digit8 * 1000000L
                    + digit9 * 100000L
                    + digit10 * 10000L
                    + digit11 * 1000L
                    + digit12 * 100L
                    + digit13 * 10L
                    + digit14;
        }
        hasNext = hasNext();
        int digit16 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit16 < 0) {
            if (hasNext) {
                skipRemaining(digit16);
            }
            return digit1 * 100000000000000L
                    + digit2 * 10000000000000L
                    + digit3 * 1000000000000L
                    + digit4 * 100000000000L
                    + digit5 * 10000000000L
                    + digit6 * 1000000000L
                    + digit7 * 100000000L
                    + digit8 * 10000000L
                    + digit9 * 1000000L
                    + digit10 * 100000L
                    + digit11 * 10000L
                    + digit12 * 1000L
                    + digit13 * 100L
                    + digit14 * 10L
                    + digit15;
        }
        hasNext = hasNext();
        int digit17 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit17 < 0) {
            if (hasNext) {
                skipRemaining(digit17);
            }
            return digit1 * 1000000000000000L
                    + digit2 * 100000000000000L
                    + digit3 * 10000000000000L
                    + digit4 * 1000000000000L
                    + digit5 * 100000000000L
                    + digit6 * 10000000000L
                    + digit7 * 1000000000L
                    + digit8 * 100000000L
                    + digit9 * 10000000L
                    + digit10 * 1000000L
                    + digit11 * 100000L
                    + digit12 * 10000L
                    + digit13 * 1000L
                    + digit14 * 100L
                    + digit15 * 10L
                    + digit16;
        }
        hasNext = hasNext();
        int digit18 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit18 < 0) {
            if (hasNext) {
                skipRemaining(digit18);
            }
            return digit1 * 10000000000000000L
                    + digit2 * 1000000000000000L
                    + digit3 * 100000000000000L
                    + digit4 * 10000000000000L
                    + digit5 * 1000000000000L
                    + digit6 * 100000000000L
                    + digit7 * 10000000000L
                    + digit8 * 1000000000L
                    + digit9 * 100000000L
                    + digit10 * 10000000L
                    + digit11 * 1000000L
                    + digit12 * 100000L
                    + digit13 * 10000L
                    + digit14 * 1000L
                    + digit15 * 100L
                    + digit16 * 10L
                    + digit17;
        }
        hasNext = hasNext();
        int digit19 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        long possibleResult = digit1 * 100000000000000000L
                + digit2 * 10000000000000000L
                + digit3 * 1000000000000000L
                + digit4 * 100000000000000L
                + digit5 * 10000000000000L
                + digit6 * 1000000000000L
                + digit7 * 100000000000L
                + digit8 * 10000000000L
                + digit9 * 1000000000L
                + digit10 * 100000000L
                + digit11 * 10000000L
                + digit12 * 1000000L
                + digit13 * 100000L
                + digit14 * 10000L
                + digit15 * 1000L
                + digit16 * 100L
                + digit17 * 10L
                + digit18;
        if (digit19 < 0) {
            if (hasNext) {
                skipRemaining(digit19);
            }
            return possibleResult;
        }
        hasNext = hasNext();
        int digit20 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit20 < 0) {
            if (hasNext) {
                skipRemaining(digit20);
            }
            if (negative) {
                if (-possibleResult > -LONG_SIZE_BORDER || (-possibleResult == -LONG_SIZE_BORDER && digit19 <= 8)) {
                    return possibleResult * 10 + digit19;
                }
            } else if (possibleResult < LONG_SIZE_BORDER || (possibleResult == LONG_SIZE_BORDER && digit19 <= 7)) {
                return possibleResult * 10 + digit19;
            }
        }
        throw createException("The number is too big for a long value");
    }

    @SuppressWarnings("checkstyle:MethodLength")
    private long parseLongFast(boolean negative) {
        int digit1 = WHOLE_NUMBER_PARTS[currentByte()];
        if (digit1 < 0) {
            throw createException("Expected number", currentByte());
        }
        int digit2 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit2 < 0) {
            skipRemaining(digit2);
            return digit1;
        }
        int digit3 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit3 < 0) {
            skipRemaining(digit3);
            return digit1 * 10L + digit2;
        }
        int digit4 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit4 < 0) {
            skipRemaining(digit4);
            return digit1 * 100L + digit2 * 10L + digit3;
        }
        int digit5 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit5 < 0) {
            skipRemaining(digit5);
            return digit1 * 1000L + digit2 * 100L + digit3 * 10L + digit4;
        }
        int digit6 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit6 < 0) {
            skipRemaining(digit6);
            return digit1 * 10000L + digit2 * 1000L + digit3 * 100L + digit4 * 10L + digit5;
        }
        int digit7 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit7 < 0) {
            skipRemaining(digit7);
            return digit1 * 100000L + digit2 * 10000L + digit3 * 1000L + digit4 * 100L + digit5 * 10L + digit6;
        }
        int digit8 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit8 < 0) {
            skipRemaining(digit8);
            return digit1 * 1000000L + digit2 * 100000L + digit3 * 10000L + digit4 * 1000L + digit5 * 100L + digit6 * 10L + digit7;
        }
        int digit9 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit9 < 0) {
            skipRemaining(digit9);
            return digit1 * 10000000L + digit2 * 1000000L + digit3 * 100000L + digit4 * 10000L + digit5 * 1000L + digit6 * 100L
                    + digit7 * 10L + digit8;
        }
        int digit10 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit10 < 0) {
            skipRemaining(digit10);
            return digit1 * 100000000L
                    + digit2 * 10000000L
                    + digit3 * 1000000L
                    + digit4 * 100000L
                    + digit5 * 10000L
                    + digit6 * 1000L
                    + digit7 * 100L
                    + digit8 * 10L
                    + digit9;
        }
        int digit11 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit11 < 0) {
            skipRemaining(digit11);
            return digit1 * 1000000000L
                    + digit2 * 100000000L
                    + digit3 * 10000000L
                    + digit4 * 1000000L
                    + digit5 * 100000L
                    + digit6 * 10000L
                    + digit7 * 1000L
                    + digit8 * 100L
                    + digit9 * 10L
                    + digit10;
        }
        int digit12 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit12 < 0) {
            skipRemaining(digit12);
            return digit1 * 10000000000L
                    + digit2 * 1000000000L
                    + digit3 * 100000000L
                    + digit4 * 10000000L
                    + digit5 * 1000000L
                    + digit6 * 100000L
                    + digit7 * 10000L
                    + digit8 * 1000L
                    + digit9 * 100L
                    + digit10 * 10L
                    + digit11;
        }
        int digit13 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit13 < 0) {
            skipRemaining(digit13);
            return digit1 * 100000000000L
                    + digit2 * 10000000000L
                    + digit3 * 1000000000L
                    + digit4 * 100000000L
                    + digit5 * 10000000L
                    + digit6 * 1000000L
                    + digit7 * 100000L
                    + digit8 * 10000L
                    + digit9 * 1000L
                    + digit10 * 100L
                    + digit11 * 10L
                    + digit12;
        }
        int digit14 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit14 < 0) {
            skipRemaining(digit14);
            return digit1 * 1000000000000L
                    + digit2 * 100000000000L
                    + digit3 * 10000000000L
                    + digit4 * 1000000000L
                    + digit5 * 100000000L
                    + digit6 * 10000000L
                    + digit7 * 1000000L
                    + digit8 * 100000L
                    + digit9 * 10000L
                    + digit10 * 1000L
                    + digit11 * 100L
                    + digit12 * 10L
                    + digit13;
        }
        int digit15 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit15 < 0) {
            skipRemaining(digit15);
            return digit1 * 10000000000000L
                    + digit2 * 1000000000000L
                    + digit3 * 100000000000L
                    + digit4 * 10000000000L
                    + digit5 * 1000000000L
                    + digit6 * 100000000L
                    + digit7 * 10000000L
                    + digit8 * 1000000L
                    + digit9 * 100000L
                    + digit10 * 10000L
                    + digit11 * 1000L
                    + digit12 * 100L
                    + digit13 * 10L
                    + digit14;
        }
        int digit16 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit16 < 0) {
            skipRemaining(digit16);
            return digit1 * 100000000000000L
                    + digit2 * 10000000000000L
                    + digit3 * 1000000000000L
                    + digit4 * 100000000000L
                    + digit5 * 10000000000L
                    + digit6 * 1000000000L
                    + digit7 * 100000000L
                    + digit8 * 10000000L
                    + digit9 * 1000000L
                    + digit10 * 100000L
                    + digit11 * 10000L
                    + digit12 * 1000L
                    + digit13 * 100L
                    + digit14 * 10L
                    + digit15;
        }
        int digit17 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit17 < 0) {
            skipRemaining(digit17);
            return digit1 * 1000000000000000L
                    + digit2 * 100000000000000L
                    + digit3 * 10000000000000L
                    + digit4 * 1000000000000L
                    + digit5 * 100000000000L
                    + digit6 * 10000000000L
                    + digit7 * 1000000000L
                    + digit8 * 100000000L
                    + digit9 * 10000000L
                    + digit10 * 1000000L
                    + digit11 * 100000L
                    + digit12 * 10000L
                    + digit13 * 1000L
                    + digit14 * 100L
                    + digit15 * 10L
                    + digit16;
        }
        int digit18 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit18 < 0) {
            skipRemaining(digit18);
            return digit1 * 10000000000000000L
                    + digit2 * 1000000000000000L
                    + digit3 * 100000000000000L
                    + digit4 * 10000000000000L
                    + digit5 * 1000000000000L
                    + digit6 * 100000000000L
                    + digit7 * 10000000000L
                    + digit8 * 1000000000L
                    + digit9 * 100000000L
                    + digit10 * 10000000L
                    + digit11 * 1000000L
                    + digit12 * 100000L
                    + digit13 * 10000L
                    + digit14 * 1000L
                    + digit15 * 100L
                    + digit16 * 10L
                    + digit17;
        }
        int digit19 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        long possibleResult = digit1 * 100000000000000000L
                + digit2 * 10000000000000000L
                + digit3 * 1000000000000000L
                + digit4 * 100000000000000L
                + digit5 * 10000000000000L
                + digit6 * 1000000000000L
                + digit7 * 100000000000L
                + digit8 * 10000000000L
                + digit9 * 1000000000L
                + digit10 * 100000000L
                + digit11 * 10000000L
                + digit12 * 1000000L
                + digit13 * 100000L
                + digit14 * 10000L
                + digit15 * 1000L
                + digit16 * 100L
                + digit17 * 10L
                + digit18;
        if (digit19 < 0) {
            skipRemaining(digit19);
            return possibleResult;
        }
        int digit20 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit20 < 0) {
            skipRemaining(digit20);
            if (negative) {
                if (-possibleResult > -LONG_SIZE_BORDER || (-possibleResult == -LONG_SIZE_BORDER && digit19 <= 8)) {
                    return possibleResult * 10 + digit19;
                }
            } else if (possibleResult < LONG_SIZE_BORDER || (possibleResult == LONG_SIZE_BORDER && digit19 <= 7)) {
                return possibleResult * 10 + digit19;
            }
        }
        throw createException("The number is too big for a long value");
    }

    private void skipRemaining(int mark) {
        if (mark == DOT_MARK) {
            skipNumber();
        } else {
            currentIndex--;
        }
    }

    private void skipObject() {
        byte b = nextToken();
        if (b == '}') {
            return;
        }
        if (b == '"') {
            skipString();
            b = nextToken();
        } else {
            throw createException("Key name start expected", b);
        }
        if (b != ':') {
            throw createException("Colon expected after the key", b);
        }
        nextToken();
        skip();
        b = nextToken();
        while (b == ',') {
            b = nextToken();
            if (b == '"') {
                skipString();
                b = nextToken();
            } else {
                throw createException("Key name start expected", b);
            }
            if (b != ':') {
                throw createException("Colon expected after the key", b);
            }
            nextToken();
            skip();
            b = nextToken();
        }

        if (b == '}') {
            return;
        }
        throw createException("Comma or the end of the object expected", b);
    }

    private void skipArray() {
        byte b = nextToken();
        if (b == ']') {
            return;
        }
        skip(); // Skip the first array value
        b = nextToken();
        while (b == ',') {
            nextToken();
            skip();
            b = nextToken();
        }

        if (b == ']') {
            return;
        }
        throw createException("Comma or the end of the array expected", b);
    }

}
