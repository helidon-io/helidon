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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.helidon.common.buffers.BufferData;

class ArrayJsonParser implements JsonParser {

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
    //Lookup table for converting hexadecimal characters to their numeric values
    static final int[] HEX_DIGITS = new int[256];

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

    static {
        Arrays.fill(WHOLE_NUMBER_PARTS, -1);
        for (int i = '0'; i <= '9'; ++i) {
            WHOLE_NUMBER_PARTS[i] = i - '0';
        }

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
        WHITESPACE_CHARS[0x0B] = true; // VT
        WHITESPACE_CHARS[0x0C] = true; // FF
        WHITESPACE_CHARS[0x0D] = true; // CR
        WHITESPACE_CHARS[0x20] = true; // SPACE

        Arrays.fill(HEX_DIGITS, -1);
        for (int i = '0'; i <= '9'; ++i) {
            HEX_DIGITS[i] = (i - '0');
        }
        for (int i = 'a'; i <= 'f'; ++i) {
            HEX_DIGITS[i] = ((i - 'a') + 10);
        }
        for (int i = 'A'; i <= 'F'; ++i) {
            HEX_DIGITS[i] = ((i - 'A') + 10);
        }
    }

    private int stringBufferLength = 64;
    private char[] stringBuffer = new char[stringBufferLength];
    private boolean expectLowSurrogate = false;

    private final byte[] buffer;
    private int currentIndex = 0;
    private final int bufferLength;

    ArrayJsonParser(byte[] buffer) {
        this.buffer = buffer;
        this.bufferLength = buffer.length;
    }

    ArrayJsonParser(byte[] buffer, int start, int length) {
        this.buffer = buffer;
        this.currentIndex = start;
        this.bufferLength = length;
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
    public JsonValue readJsonValue() {
        return switch (currentByte()) {
            case '{' -> readJsonObject();
            case '[' -> readJsonArray();
            case '"' -> readJsonString();
            case '-', '.', '+', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> readJsonNumber();
            case 't', 'f' -> JsonBoolean.create(readBoolean());
            case 'n' -> {
                checkNull();
                yield JsonNull.instance();
            }
            default -> throw createException("Unexpected JSON value type", currentByte());
        };
    }

    @Override
    public JsonObject readJsonObject() {
        if (currentByte() != '{') {
            throw createException("Object start expected", currentByte());
        }
        byte b = nextToken();
        if (b == '}') {
            return JsonObject.EMPTY_OBJECT;
        }
        List<JsonObject.Pair> pairs = new ArrayList<>();
        while (hasNext()) {
            JsonString key;
            if (b == '"') {
                key = readJsonString();
            } else {
                throw createException("Key name start expected", b);
            }
            b = nextToken();
            if (b != ':') {
                throw createException("Colon expected", b);
            }
            b = nextToken();
            switch (b) {
            case '"':
                pairs.add(new JsonObject.Pair(key, readJsonString()));
                break;
            case '{':
                pairs.add(new JsonObject.Pair(key, readJsonObject()));
                break;
            case '[':
                pairs.add(new JsonObject.Pair(key, readJsonArray()));
                break;
            case '-':
            case '.':
            case '+':
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
                pairs.add(new JsonObject.Pair(key, readJsonNumber()));
                break;
            case 'n':
                checkNull();
                pairs.add(new JsonObject.Pair(key, JsonNull.instance()));
                break;
            case 't':
            case 'f':
                pairs.add(new JsonObject.Pair(key, JsonBoolean.create(readBoolean())));
                break;
            default:
                throw createException("Unexpected json value type", b);
            }
            b = nextToken();
            if (b == '}') {
                return JsonObject.create(pairs);
            } else if (b != ',') {
                throw createException("Comma or object end expected", b);
            }
            b = nextToken();
        }
        throw createException("Unexpected end of the object. Possibly incomplete JSON");
    }

    @Override
    public JsonArray readJsonArray() {
        byte b = nextToken();
        if (b == ']') {
            return JsonArray.EMPTY_ARRAY;
        }
        List<JsonValue> values = new ArrayList<>();
        while (hasNext()) {
            switch (b) {
            case '"':
                values.add(readJsonString());
                break;
            case '{':
                values.add(readJsonObject());
                break;
            case '[':
                values.add(readJsonArray());
                break;
            case '-':
            case '.':
            case '+':
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
                values.add(readJsonNumber());
                break;
            case 'n':
                checkNull();
                values.add(JsonNull.instance());
                break;
            case 't':
            case 'f':
                values.add(JsonBoolean.create(readBoolean()));
                break;
            default:
                throw createException("Invalid JSON value type", b);
            }
            b = nextToken();
            if (b == ']') {
                return JsonArray.create(values);
            } else if (b != ',') {
                throw createException("Comma or array end expected", b);
            }
            b = nextToken();
        }
        throw createException("Unexpected end of the array. Possibly incomplete JSON");
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
            } else if ((b & 0x80) == 0) {
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
    public char[] readCharArray() {
        switch (currentByte()) {
        case '"':
            return readStringCharArray();
        case '{':
            throw createException("Handling object as a character array is not supported");
        case '[':
            throw createException("Handling array as a character array is not supported");
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
            return readNumberAsCharArray();
        case 't':
            ensure(3);
            if (buffer[++currentIndex] == 'r'
                    && buffer[++currentIndex] == 'u'
                    && buffer[++currentIndex] == 'e') {
                return new char[] {'t', 'r', 'u', 'e'};
            }
            throw createException("True value expect. Invalid JSON value");
        case 'n':
            ensure(3);
            if (buffer[++currentIndex] == 'u'
                    && buffer[++currentIndex] == 'l'
                    && buffer[++currentIndex] == 'l') {
                return new char[] {'n', 'u', 'l', 'l'};
            }
            throw createException("Null value expect. Invalid JSON value");
        case 'f':
            ensure(4);
            if (buffer[++currentIndex] == 'a'
                    && buffer[++currentIndex] == 'l'
                    && buffer[++currentIndex] == 's'
                    && buffer[++currentIndex] == 'e') {
                return new char[] {'f', 'a', 'l', 's', 'e'};
            }
            throw createException("False value expect. Invalid JSON value");
        default:
            throw createException("Invalid JSON value to skip", currentByte());
        }
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
        } else if ((b & 0x80) == 0) {
            c = (char) b;
        } else {
            c = decodeUtf8ToChar(b);
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
        int start = currentIndex;
        int i = start;

        // Check for sign
        boolean negative = false;
        byte b = buffer[i];
        if (b == '-') {
            negative = true;
            i++;
        } else if (b == '+') {
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

        // Parse all digits
        while (i < bufferLength) {
            b = buffer[i];
            int digit = WHOLE_NUMBER_PARTS[b & 0xFF];
            if (digit > -1) {
                if (hasDecimal) {
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
            while (i < bufferLength && (digit = WHOLE_NUMBER_PARTS[buffer[i] & 0xFF]) > -1) {
                explicitExp = explicitExp * 10 + digit;
                if (explicitExp > 1000) {
                    break;
                }
                i++;
            }
            currentIndex = i - 1;
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
            return Double.parseDouble(new String(buffer, start, currentIndex - start + 1, StandardCharsets.UTF_8));
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
    public int readStringAsHash() {
        if (currentByte() != '"') {
            throw createException("Hash calculation is intended only for String values");
        }
        // Compute FNV-1a hash of the string content (excluding quotes) using recommended offset basis and prime values.
        // This optimized loop scans the buffer directly without calling readNextByte() for each character.
        int fnv1aHash = FNV_OFFSET_BASIS;
        byte b;
        int i = ++currentIndex;
        for (; i < bufferLength; i++) {
            b = buffer[i];
            if (b == '"') {
                currentIndex = i;
                return fnv1aHash;
            }
            fnv1aHash ^= (b & 0xFF);
            fnv1aHash *= FNV_PRIME;
        }
        throw createException("Unexpected end of string value. Probably incomplete JSON");
    }

    @Override
    public JsonException createException(String message) {
        int start = Math.max(currentIndex - 10, 0);
        int length = Math.min(currentIndex + 10, bufferLength - start);
        int dataIndex = currentIndex - start;
        BufferData bufferData = BufferData.create(buffer, start, length);

        return new JsonException(message + "\n"
                                         + "Error at JSON index: " + currentIndex + "\n"
                                         + "Data index: " + dataIndex + "\n"
                                         + "Data: \n"
                                         + bufferData.debugDataHex(false));
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
                    (translateHex(buffer[++currentIndex]) << 12)
                            + (translateHex(buffer[++currentIndex]) << 8)
                            + (translateHex(buffer[++currentIndex]) << 4)
                            + translateHex(buffer[++currentIndex]));
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
        if ((currentByte & 0xE0) == 0xC0) {
            // 2-byte UTF-8 sequence: 110xxxxx 10yyyyyy -> U+0080 to U+07FF
            int c2 = readNextByte() & 0x3F; // Second byte must be 10yyyyyy
            int codePoint = ((currentByte & 0x1F) << 6) | c2; // Assemble code point: xxxxx yyyyyy
            stringBuffer[position++] = (char) codePoint;
        } else if ((currentByte & 0xF0) == 0xE0) {
            // 3-byte UTF-8 sequence: 1110xxxx 10yyyyyy 10zzzzzz -> U+0800 to U+FFFF
            ensure(2); // Ensure we have at least 2 more bytes
            int c2 = buffer[++currentIndex] & 0x3F; // Second byte: 10yyyyyy
            int c3 = buffer[++currentIndex] & 0x3F; // Third byte: 10zzzzzz
            int codePoint = ((currentByte & 0x0F) << 12) | (c2 << 6) | c3; // Assemble: xxxx yyyyyy zzzzzz
            stringBuffer[position++] = (char) codePoint;
        } else if ((currentByte & 0xF8) == 0xF0) {
            // 4-byte UTF-8 sequence: 11110www 10xxxxxx 10yyyyyy 10zzzzzz -> U+10000 to U+10FFFF
            ensure(3); // Ensure we have at least 3 more bytes
            int c2 = buffer[++currentIndex] & 0x3F; // Second byte: 10xxxxxx
            int c3 = buffer[++currentIndex] & 0x3F; // Third byte: 10yyyyyy
            int c4 = buffer[++currentIndex] & 0x3F; // Fourth byte: 10zzzzzz
            int codePoint = ((currentByte & 0x07) << 18) | (c2 << 12) | (c3 << 6) | c4; // Assemble: www xxxxxx yyyyyy zzzzzz
            if (codePoint >= 0x10000) {
                // Code point requires UTF-16 surrogates
                if (codePoint >= 0x110000) {
                    // Beyond valid Unicode range
                    throw createException("Invalid UTF-8 code point: " + Integer.toHexString(codePoint));
                }
                // Convert to UTF-16 surrogate pair
                codePoint -= 0x10000; // Subtract U+10000 to get 20-bit value
                stringBuffer[position++] = (char) ((codePoint >> 10) + 0xD800); // High surrogate: U+D800 + high 10 bits
                if (position == stringBufferLength) {
                    increaseStringBuffer(); // Ensure space for low surrogate
                }
                stringBuffer[position++] = (char) ((codePoint & 0x3FF) + 0xDC00); // Low surrogate: U+DC00 + low 10 bits
            } else {
                // Code point fits in a single char (U+0000 to U+FFFF)
                stringBuffer[position++] = (char) codePoint;
            }
        } else {
            // Invalid UTF-8 leading byte
            throw createException("Invalid UTF-8 byte", currentByte);
        }
        return position;
    }

    void increaseStringBuffer() {
        increaseStringBuffer(stringBufferLength * 2);
    }

    private char[] readStringCharArray() {
        int readableBytes = bufferLength - currentIndex - 1;
        int firstRun = Math.min(stringBufferLength, readableBytes);
        int stringBuffIndex = 0;
        byte b;
        for (; stringBuffIndex < firstRun; stringBuffIndex++) {
            b = this.buffer[++currentIndex];
            if (b == '"') {
                char[] chars = new char[stringBuffIndex];
                System.arraycopy(stringBuffer, 0, chars, 0, stringBuffIndex);
                return chars;
            }
            if (b == '\\' || b < 0) {
                //Specialized character handling is likely required
                //Either escaped sequence or multibyte detected
                currentIndex--;
                break;
            }
            stringBuffer[stringBuffIndex] = (char) b;
        }

        if (stringBuffIndex == stringBufferLength) {
            increaseStringBuffer();
        }

        while (hasNext()) {
            b = readNextByte();
            if (b == '\\') {
                stringBuffer[stringBuffIndex++] = processEscapedSequence();
            } else if (b == '"') {
                char[] chars = new char[stringBuffIndex];
                System.arraycopy(stringBuffer, 0, chars, 0, stringBuffIndex);
                return chars;
            } else if ((b & 0x80) == 0) {
                stringBuffer[stringBuffIndex++] = (char) b;
            } else {
                stringBuffIndex = decodeUtf8(stringBuffIndex, b);
            }
            if (stringBuffIndex == stringBufferLength) {
                increaseStringBuffer();
            }
        }
        throw createException("End of the string expected. Incomplete JSON");
    }

    private char[] readNumberAsCharArray() {
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
        System.arraycopy(stringBuffer, 0, newBuf, 0, stringBuffer.length);
        stringBuffer = newBuf;
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
        if ((currentByte & 0xE0) == 0xC0) {
            // 2-byte UTF-8 sequence: 110xxxxx 10yyyyyy -> U+0080 to U+07FF
            int c2 = readNextByte() & 0x3F; // Second byte must be 10yyyyyy
            int codePoint = ((currentByte & 0x1F) << 6) | c2; // Assemble code point: xxxxx yyyyyy
            return (char) codePoint;
        } else if ((currentByte & 0xF0) == 0xE0) {
            // 3-byte UTF-8 sequence: 1110xxxx 10yyyyyy 10zzzzzz -> U+0800 to U+FFFF
            ensure(2); // Ensure we have at least 2 more bytes
            int c2 = buffer[++currentIndex] & 0x3F; // Second byte: 10yyyyyy
            int c3 = buffer[++currentIndex] & 0x3F; // Third byte: 10zzzzzz
            int codePoint = ((currentByte & 0x0F) << 12) | (c2 << 6) | c3; // Assemble: xxxx yyyyyy zzzzzz
            return (char) codePoint;
        } else if ((currentByte & 0xF8) == 0xF0) {
            // 4-byte UTF-8 sequence: 11110www 10xxxxxx 10yyyyyy 10zzzzzz -> U+10000 to U+10FFFF
            ensure(3); // Ensure we have at least 3 more bytes
            int c2 = buffer[++currentIndex] & 0x3F; // Second byte: 10xxxxxx
            int c3 = buffer[++currentIndex] & 0x3F; // Third byte: 10yyyyyy
            int c4 = buffer[++currentIndex] & 0x3F; // Fourth byte: 10zzzzzz
            int codePoint = ((currentByte & 0x07) << 18) | (c2 << 12) | (c3 << 6) | c4; // Assemble: www xxxxxx yyyyyy zzzzzz
            if (codePoint >= 0x10000) {
                // Code point requires UTF-16 surrogates, which cannot fit in a single char
                if (codePoint >= 0x110000) {
                    // Beyond valid Unicode range
                    throw createException("Invalid UTF-8 code point: " + Integer.toHexString(codePoint));
                }
                throw createException("UTF-16 high and low surrogates cannot be represented as a single char");
            } else {
                // Code point fits in a single char (U+0000 to U+FFFF)
                return (char) codePoint;
            }
        } else {
            // Invalid UTF-8 leading byte
            throw createException("Invalid UTF-8 byte", currentByte);
        }
    }

    private byte parseByte(boolean negative) {
        int digit1 = WHOLE_NUMBER_PARTS[currentByte() & 0xFF];
        if (digit1 == -1) {
            throw createException("Expected number", currentByte());
        }
        if (currentIndex + 4 < bufferLength) {
            int digit2 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
            if (digit2 == -1) {
                currentIndex--;
                return (byte) digit1;
            }
            int digit3 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
            int possibleResult = digit1 * 10 + digit2;
            if (digit3 == -1) {
                currentIndex--;
                return (byte) possibleResult;
            }
            int digit4 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
            if (digit4 == -1) {
                currentIndex--;
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
        if (digit2 == -1) {
            if (hasNext) {
                currentIndex--;
            }
            return (byte) digit1;
        }
        hasNext = hasNext();
        int digit3 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        int possibleResult = digit1 * 10 + digit2;
        if (digit3 == -1) {
            if (hasNext) {
                currentIndex--;
            }
            return (byte) possibleResult;
        }
        hasNext = hasNext();
        int digit4 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit4 == -1) {
            if (hasNext) {
                currentIndex--;
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
        if (digit1 == -1) {
            throw createException("Expected number", currentByte());
        }
        if (currentIndex + 6 < bufferLength) {
            int digit2 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
            if (digit2 == -1) {
                currentIndex--;
                return (short) digit1;
            }
            int digit3 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
            if (digit3 == -1) {
                currentIndex--;
                return (short) (digit1 * 10 + digit2);
            }
            int digit4 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
            if (digit4 == -1) {
                currentIndex--;
                return (short) (digit1 * 100 + digit2 * 10 + digit3);
            }
            int digit5 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
            short possibleResult = (short) (digit1 * 1000 + digit2 * 100 + digit3 * 10 + digit4);
            if (digit5 == -1) {
                currentIndex--;
                return possibleResult;
            }
            int digit6 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
            if (digit6 == -1) {
                currentIndex--;
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
        if (digit2 == -1) {
            if (hasNext) {
                currentIndex--;
            }
            return (short) digit1;
        }
        hasNext = hasNext();
        int digit3 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit3 == -1) {
            if (hasNext) {
                currentIndex--;
            }
            return (short) (digit1 * 10 + digit2);
        }
        hasNext = hasNext();
        int digit4 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit4 == -1) {
            if (hasNext) {
                currentIndex--;
            }
            return (short) (digit1 * 100 + digit2 * 10 + digit3);
        }
        hasNext = hasNext();
        int digit5 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        short possibleResult = (short) (digit1 * 1000 + digit2 * 100 + digit3 * 10 + digit4);
        if (digit5 == -1) {
            if (hasNext) {
                currentIndex--;
            }
            return possibleResult;
        }
        hasNext = hasNext();
        int digit6 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit6 == -1) {
            if (hasNext) {
                currentIndex--;
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
        if (digit1 == -1) {
            throw createException("Expected number", currentByte());
        }
        boolean hasNext = hasNext();
        int digit2 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit2 == -1) {
            if (hasNext) {
                currentIndex--;
            }
            return digit1;
        }
        hasNext = hasNext();
        int digit3 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit3 == -1) {
            if (hasNext) {
                currentIndex--;
            }
            return digit1 * 10 + digit2;
        }
        hasNext = hasNext();
        int digit4 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit4 == -1) {
            if (hasNext) {
                currentIndex--;
            }
            return digit1 * 100
                    + digit2 * 10
                    + digit3;
        }
        hasNext = hasNext();
        int digit5 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit5 == -1) {
            if (hasNext) {
                currentIndex--;
            }
            return digit1 * 1000
                    + digit2 * 100
                    + digit3 * 10
                    + digit4;
        }
        hasNext = hasNext();
        int digit6 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit6 == -1) {
            if (hasNext) {
                currentIndex--;
            }
            return digit1 * 10000
                    + digit2 * 1000
                    + digit3 * 100
                    + digit4 * 10
                    + digit5;
        }
        hasNext = hasNext();
        int digit7 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit7 == -1) {
            if (hasNext) {
                currentIndex--;
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
        if (digit8 == -1) {
            if (hasNext) {
                currentIndex--;
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
        if (digit9 == -1) {
            if (hasNext) {
                currentIndex--;
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
        if (digit10 == -1) {
            if (hasNext) {
                currentIndex--;
            }
            return possibleResult;
        }
        hasNext = hasNext();
        int digit11 = hasNext ? WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF] : -1;
        if (digit11 == -1) {
            if (hasNext) {
                currentIndex--;
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
        if (digit1 == -1) {
            throw createException("Expected number", currentByte());
        }
        int digit2 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit2 == -1) {
            currentIndex--;
            return digit1;
        }
        int digit3 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit3 == -1) {
            currentIndex--;
            return digit1 * 10 + digit2;
        }
        int digit4 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit4 == -1) {
            currentIndex--;
            return digit1 * 100
                    + digit2 * 10
                    + digit3;
        }
        int digit5 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit5 == -1) {
            currentIndex--;
            return digit1 * 1000
                    + digit2 * 100
                    + digit3 * 10
                    + digit4;
        }
        int digit6 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit6 == -1) {
            currentIndex--;
            return digit1 * 10000
                    + digit2 * 1000
                    + digit3 * 100
                    + digit4 * 10
                    + digit5;
        }
        int digit7 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit7 == -1) {
            currentIndex--;
            return digit1 * 100000
                    + digit2 * 10000
                    + digit3 * 1000
                    + digit4 * 100
                    + digit5 * 10
                    + digit6;
        }
        int digit8 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit8 == -1) {
            currentIndex--;
            return digit1 * 1000000
                    + digit2 * 100000
                    + digit3 * 10000
                    + digit4 * 1000
                    + digit5 * 100
                    + digit6 * 10
                    + digit7;
        }
        int digit9 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit9 == -1) {
            currentIndex--;
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
        if (digit10 == -1) {
            currentIndex--;
            return possibleResult;
        }
        int digit11 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit11 == -1) {
            currentIndex--;
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
        if (digit1 == -1) {
            throw createException("Expected number", currentByte());
        }
        int digit2 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit2 == -1) {
            if (hasNext) {
                currentIndex--;
            }
            return digit1;
        }
        hasNext = hasNext();
        int digit3 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit3 == -1) {
            if (hasNext) {
                currentIndex--;
            }
            return digit1 * 10L + digit2;
        }
        hasNext = hasNext();
        int digit4 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit4 == -1) {
            if (hasNext) {
                currentIndex--;
            }
            return digit1 * 100L + digit2 * 10L + digit3;
        }
        hasNext = hasNext();
        int digit5 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit5 == -1) {
            if (hasNext) {
                currentIndex--;
            }
            return digit1 * 1000L + digit2 * 100L + digit3 * 10L + digit4;
        }
        hasNext = hasNext();
        int digit6 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit6 == -1) {
            if (hasNext) {
                currentIndex--;
            }
            return digit1 * 10000L + digit2 * 1000L + digit3 * 100L + digit4 * 10L + digit5;
        }
        hasNext = hasNext();
        int digit7 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit7 == -1) {
            if (hasNext) {
                currentIndex--;
            }
            return digit1 * 100000L + digit2 * 10000L + digit3 * 1000L + digit4 * 100L + digit5 * 10L + digit6;
        }
        hasNext = hasNext();
        int digit8 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit8 == -1) {
            if (hasNext) {
                currentIndex--;
            }
            return digit1 * 1000000L + digit2 * 100000L + digit3 * 10000L + digit4 * 1000L + digit5 * 100L + digit6 * 10L + digit7;
        }
        hasNext = hasNext();
        int digit9 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit9 == -1) {
            if (hasNext) {
                currentIndex--;
            }
            return digit1 * 10000000L + digit2 * 1000000L + digit3 * 100000L + digit4 * 10000L + digit5 * 1000L + digit6 * 100L
                    + digit7 * 10L + digit8;
        }
        hasNext = hasNext();
        int digit10 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit10 == -1) {
            if (hasNext) {
                currentIndex--;
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
        if (digit11 == -1) {
            if (hasNext) {
                currentIndex--;
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
        if (digit12 == -1) {
            if (hasNext) {
                currentIndex--;
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
        if (digit13 == -1) {
            if (hasNext) {
                currentIndex--;
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
        if (digit14 == -1) {
            if (hasNext) {
                currentIndex--;
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
        if (digit15 == -1) {
            if (hasNext) {
                currentIndex--;
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
        if (digit16 == -1) {
            if (hasNext) {
                currentIndex--;
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
        if (digit17 == -1) {
            if (hasNext) {
                currentIndex--;
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
        if (digit18 == -1) {
            if (hasNext) {
                currentIndex--;
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
        if (digit19 == -1) {
            if (hasNext) {
                currentIndex--;
            }
            return possibleResult;
        }
        hasNext = hasNext();
        int digit20 = hasNext ? WHOLE_NUMBER_PARTS[readNextByte() & 0xFF] : -1;
        if (digit20 == -1) {
            if (hasNext) {
                currentIndex--;
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
        if (digit1 == -1) {
            throw createException("Expected number", currentByte());
        }
        int digit2 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit2 == -1) {
            currentIndex--;
            return digit1;
        }
        int digit3 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit3 == -1) {
            currentIndex--;
            return digit1 * 10L + digit2;
        }
        int digit4 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit4 == -1) {
            currentIndex--;
            return digit1 * 100L + digit2 * 10L + digit3;
        }
        int digit5 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit5 == -1) {
            currentIndex--;
            return digit1 * 1000L + digit2 * 100L + digit3 * 10L + digit4;
        }
        int digit6 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit6 == -1) {
            currentIndex--;
            return digit1 * 10000L + digit2 * 1000L + digit3 * 100L + digit4 * 10L + digit5;
        }
        int digit7 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit7 == -1) {
            currentIndex--;
            return digit1 * 100000L + digit2 * 10000L + digit3 * 1000L + digit4 * 100L + digit5 * 10L + digit6;
        }
        int digit8 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit8 == -1) {
            currentIndex--;
            return digit1 * 1000000L + digit2 * 100000L + digit3 * 10000L + digit4 * 1000L + digit5 * 100L + digit6 * 10L + digit7;
        }
        int digit9 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit9 == -1) {
            currentIndex--;
            return digit1 * 10000000L + digit2 * 1000000L + digit3 * 100000L + digit4 * 10000L + digit5 * 1000L + digit6 * 100L
                    + digit7 * 10L + digit8;
        }
        int digit10 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit10 == -1) {
            currentIndex--;
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
        if (digit11 == -1) {
            currentIndex--;
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
        if (digit12 == -1) {
            currentIndex--;
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
        if (digit13 == -1) {
            currentIndex--;
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
        if (digit14 == -1) {
            currentIndex--;
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
        if (digit15 == -1) {
            currentIndex--;
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
        if (digit16 == -1) {
            currentIndex--;
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
        if (digit17 == -1) {
            currentIndex--;
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
        if (digit18 == -1) {
            currentIndex--;
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
        if (digit19 == -1) {
            currentIndex--;
            return possibleResult;
        }
        int digit20 = WHOLE_NUMBER_PARTS[buffer[++currentIndex] & 0xFF];
        if (digit20 == -1) {
            currentIndex--;
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

    private int translateHex(byte b) {
        int val = HEX_DIGITS[b & 0xFF];
        if (val == -1) {
            throw createException("Invalid hex digit found", b);
        }
        return val;
    }

}
