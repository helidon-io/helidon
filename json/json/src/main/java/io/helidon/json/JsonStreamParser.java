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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class JsonStreamParser extends ArrayJsonParser {

    private static final int DEFAULT_BUFFER_SIZE = 512;

    private final int configuredBufferSize;
    private final InputStream inputStream;
    private boolean finished;
    private boolean bufferingJsonValue;
    private int jsonValueStart;

    JsonStreamParser(InputStream inputStream, int bufferSize) {
        this.configuredBufferSize = bufferSize;
        this.inputStream = inputStream;
        currentIndex = 0;
        buffer = new byte[bufferSize];
        try {
            int read = inputStream.read(buffer);
            bufferLength = (read == -1 ? 0 : read);
            finished = (read == -1);
        } catch (IOException e) {
            throw new RuntimeException("Error occurred while reading JSON to the buffer", e);
        }
    }

    JsonStreamParser(InputStream inputStream) {
        this(inputStream, DEFAULT_BUFFER_SIZE);
    }

    JsonStreamParser() {
        this(new ByteArrayInputStream(new byte[0]), DEFAULT_BUFFER_SIZE);
    }

    @Override
    public boolean hasNext() {
        if (!finished && currentIndex + 1 >= bufferLength) {
            fetchData();
        }
        return super.hasNext();
    }

    @Override
    public byte readNextByte() {
        if (currentIndex + 1 == bufferLength) {
            if (finished) {
                throw createException("Incomplete JSON data");
            }
            readMoreData();
        }
        return buffer[++currentIndex];
    }

    @Override
    void ensure(int amount) {
        if (currentIndex + amount >= bufferLength) {
            fetchData();
            super.ensure(amount);
        }
    }

    void fetchData() {
        if (finished) {
            throw createException("There are no more data to fetch. Incomplete JSON");
        }
        readMoreData();
    }

    @Override
    public int readStringAsHash() {
        if (currentByte() != '"') {
            throw createException("Hash calculation is intended only for String values");
        } else if (!hasNext()) {
            throw createException("Incomplete JSON");
        }

        int i = currentIndex + 1;
        while (true) {
            //Based on recommended offset basis and prime values.
            int fnv1aHash = FNV_OFFSET_BASIS;
            byte b;
            while (i < bufferLength) {
                b = buffer[i++];
                if (b == '"') {
                    currentIndex = i - 1;
                    return fnv1aHash;
                }
                fnv1aHash ^= (b & 0xFF);
                fnv1aHash *= FNV_PRIME;
            }
            fetchData();
            i = currentIndex;
        }
    }

    @Override
    public JsonNumber readJsonNumber() {
        bufferingJsonValue = true;
        jsonValueStart = currentIndex;
        skipNumber();
        int length = currentIndex - jsonValueStart + 1;
        byte[] numberBytes = new byte[length];
        System.arraycopy(buffer, jsonValueStart, numberBytes, 0, length);
        bufferingJsonValue = false;
        return JsonNumber.create(numberBytes, 0, length);
    }

    @Override
    void skipNumber() {
        byte b;
        int index;
        while (true) {
            for (index = this.currentIndex; index < this.bufferLength; index++) {
                b = this.buffer[index];
                //we do not need to validate whether this is a valid number since we are not processing it.
                //simply skip until you find any non-numeric bound character
                if (!VALID_NUMBER_PARTS[b]) {
                    this.currentIndex = index - 1;
                    return;
                }
            }
            if (!finished) {
                currentIndex = index;
                readMoreData();
            } else {
                this.currentIndex = index;
                break;
            }
        }
    }

    @Override
    public JsonString readJsonString() {
        if (currentByte() != '"') {
            throw createException("Reading JsonString values is allowed only for a string JSON values", currentByte());
        }
        bufferingJsonValue = true;
        jsonValueStart = currentIndex + 1;
        skipString();
        int length = currentIndex - jsonValueStart;
        byte[] stringBytes = new byte[length];
        System.arraycopy(buffer, jsonValueStart, stringBytes, 0, length);
        bufferingJsonValue = false;
        return JsonString.create(stringBytes, 0, length);
    }

    @Override
    void skipString() {
        boolean isEscaped = false;
        byte b;
        while (true) {
            int index;
            for (index = this.currentIndex + 1; index < this.bufferLength; index++) {
                b = this.buffer[index];
                if (b == '\\') {
                    isEscaped = !isEscaped;
                } else if (b == '"' && !isEscaped) {
                    this.currentIndex = index;
                    return;
                } else {
                    isEscaped = false;
                }
            }
            currentIndex = index;
            if (finished) {
                throw createException("Unexpected end of string. Incomplete JSON or incorrect use of the skip method");
            }
            readMoreData();
        }
    }

    @Override
    @SuppressWarnings("checkstyle:MethodLength")
    public double readDouble() {
        bufferingJsonValue = true;
        jsonValueStart = currentIndex;

        // Check for sign
        boolean negative = false;
        byte b = buffer[currentIndex];
        if (b == '-') {
            negative = true;
            currentIndex++;
        } else if (b == '+') {
            currentIndex++;
        }
        if (currentIndex >= bufferLength) {
            fetchData();
            if (currentIndex >= bufferLength) {
                throw createException("Empty number");
            }
        }
        // Check for special values (NaN, Infinity)
        b = buffer[currentIndex];
        if (b == 'N') {
            if (expectedNext('a') && expectedNext('N')) {
                currentIndex += 2;
                bufferingJsonValue = false;
                return Double.NaN;
            }
            throw createException("Invalid double number");
        } else if (b == 'I' || b == 'i') {
            if (expectedNext('n')
                    && expectedNext('f')
                    && expectedNext('i')
                    && expectedNext('n')
                    && expectedNext('i')
                    && expectedNext('t')
                    && expectedNext('y')) {
                currentIndex += 7;
                bufferingJsonValue = false;
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
        while (currentIndex < bufferLength) {
            b = buffer[currentIndex];
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
                currentIndex++;
                if (currentIndex == bufferLength) {
                    fetchData();
                }
            } else if (b == '.') {
                if (hasDecimal) {
                    throw createException("Multiple decimal separators detected");
                }
                hasDecimal = true;
                currentIndex++;
                if (currentIndex == bufferLength) {
                    fetchData();
                }
            } else {
                break;
            }
        }
        if (delegateToJava) {
            skipNumber();
            bufferingJsonValue = false;
            return Double.parseDouble(new String(buffer, jsonValueStart, currentIndex - jsonValueStart, StandardCharsets.UTF_8));
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
        b = buffer[currentIndex];
        if (b == 'e' || b == 'E') {
            boolean expNegative = false;
            if (hasNext()) {
                b = buffer[++currentIndex];
                if (b == '-') {
                    expNegative = true;
                    currentIndex++;
                } else if (b == '+') {
                    currentIndex++;
                }
            } else {
                throw createException("Missing exponent value");
            }
            if (currentIndex == bufferLength) {
                fetchData();
            }

            int digit = -1;
            while ((currentIndex < bufferLength) && (digit = WHOLE_NUMBER_PARTS[buffer[currentIndex] & 0xFF]) > -1) {
                explicitExp = explicitExp * 10 + digit;
                if (explicitExp > 1000) {
                    break;
                }
                currentIndex++;
                if (currentIndex == bufferLength && !finished) {
                    fetchData();
                }
            }
            if (digit == -1) {
                b = buffer[currentIndex];
                if (b == 'e' || b == 'E' || b == '.') {
                    throw createException("Duplicit exponent or decimal point detected");
                }
            }
            decimalExponent += expNegative ? -explicitExp : explicitExp;
        }

        // Handle zero
        if (mantissa == 0) {
            bufferingJsonValue = false;
            return negative ? -0.0 : 0.0;
        }

        if (decimalExponent >= POW10_DOUBLE_CACHE_SIZE || decimalExponent <= -POW10_DOUBLE_CACHE_SIZE) {
            skipNumber();
            bufferingJsonValue = false;
            // Delegate to Java for exact result
            return Double.parseDouble(new String(buffer, jsonValueStart, currentIndex - jsonValueStart + 1));
        }

        // FAST PATH: Handle common cases ourselves
        double result = mantissa;

        // Apply exponent using lookup table
        if (decimalExponent > 0) {
            result *= POW10_DOUBLE_CACHE[decimalExponent];
        } else if (decimalExponent < 0) {
            result /= POW10_DOUBLE_CACHE[-decimalExponent];
        }

        bufferingJsonValue = false;
        return negative ? -result : result;
    }

    private boolean expectedNext(char c) {
        return hasNext() && buffer[++currentIndex] == c;
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

        if (currentIndex == this.bufferLength) {
            if (finished) {
                throw createException("End of the string expected. Incomplete JSON");
            }
            readMoreData();
            currentIndex++;
        }

        while (true) {
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
            if (finished) {
                throw createException("End of the string expected. Incomplete JSON");
            }
            readMoreData();
            currentIndex++;
        }
    }

    @Override
    public byte nextToken() {
        //Optimization for faster reading data without a space
        //No loop is used.
        byte b = readNextByte();
        if (!WHITESPACE_CHARS[b & 0xFF]) {
            return b;
        }
        //If since space or why character was used between tokens, we should still try to optimize
        b = readNextByte();
        if (!WHITESPACE_CHARS[b & 0xFF]) {
            return b;
        }
        //We dont know how many spaces, new lines etc is there present, lets start looping
        while (true) {
            b = readNextByte();
            if (!WHITESPACE_CHARS[b & 0xFF]) {
                return b;
            }
        }
    }

    /**
     * Reads more data from the input stream into the buffer, handling buffering for JSON values that span multiple reads.
     * There are two modes: bufferingJsonValue (for {@link io.helidon.json.JsonValue} related types)
     * and non-buffering (for structural parsing).
     */
    private void readMoreData() {
        try {
            if (bufferingJsonValue) {
                // When buffering a JSON value (e.g., string or number), we need to preserve the value across reads
                jsonNumberBuffering();
            } else if (currentIndex == bufferLength) {
                // For structural parsing, keep one byte of look-ahead to detect value boundaries
                // Preserve the byte before currentIndex to allow backtracking
                buffer[0] = buffer[currentIndex - 1];
                int lastRead = inputStream.read(buffer, 1, buffer.length - 1);
                if (lastRead == -1) {
                    finished = true;
                    bufferLength = 1; // Only the preserved byte
                } else {
                    bufferLength = lastRead + 1;
                    finished = false;
                }
                currentIndex = 0; // Reset to beginning
            } else {
                // Previous buffer not fully drained. We are trying to read more data, if available
                int kept = bufferLength - currentIndex;
                System.arraycopy(buffer, currentIndex, buffer, 0, kept);
                int lastRead = inputStream.read(buffer, kept, buffer.length - kept);
                if (lastRead == -1) {
                    finished = true;
                    bufferLength = kept;
                } else {
                    bufferLength = lastRead + kept;
                    finished = false;
                }
                currentIndex = 0;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void jsonNumberBuffering() throws IOException {
        if (jsonValueStart > 0) {
            // Move the partial value to the beginning of the buffer to make room for more data
            int valueLen = bufferLength - jsonValueStart;
            System.arraycopy(buffer, jsonValueStart, buffer, 0, valueLen);
            currentIndex = valueLen - 1; // Position at end of moved value
            jsonValueStart = 0; // Reset start position
            int lastRead = inputStream.read(buffer, valueLen, buffer.length - valueLen);
            if (lastRead == -1) {
                finished = true;
                bufferLength = valueLen; // Only the moved value remains
            } else {
                bufferLength = valueLen + lastRead;
                finished = false;
            }
        } else {
            // Buffer is full of the value, need to expand
            int newCap = buffer.length + configuredBufferSize;
            byte[] tmp = new byte[newCap];
            System.arraycopy(buffer, 0, tmp, 0, bufferLength); // Copy existing data
            int lastRead = inputStream.read(tmp, bufferLength, configuredBufferSize);
            buffer = tmp; // Replace buffer
            if (lastRead == -1) {
                finished = true;
            } else {
                bufferLength = bufferLength + lastRead;
                finished = false;
            }
        }
    }
}
