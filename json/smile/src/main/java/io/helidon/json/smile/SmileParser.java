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

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

import io.helidon.common.Api;
import io.helidon.common.buffers.Bytes;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonBoolean;
import io.helidon.json.JsonException;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonNumber;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.json.JsonParserBase;
import io.helidon.json.JsonString;
import io.helidon.json.JsonValue;
import io.helidon.json.Parsers;

import static io.helidon.json.Parsers.translateHex;

/**
 * Smile binary JSON parser implementation.
 *
 * <p>This class is not thread safe.
 */
@Api.Preview
public final class SmileParser extends JsonParserBase {

    private static final JsonString[] EMPTY = new JsonString[0];
    private static final LazyHash[] EMPTY_HASH = new LazyHash[0];

    private static final int SHARED_TABLE_SIZE_MAX = 1024;
    private static final int SHARED_TABLE_SIZE_INIT = 24;
    private static final int FNV_OFFSET_BASIS = 0x811c9dc5;
    private static final int FNV_PRIME = 0x01000193;

    private static final BigDecimal MAX_LONG_BD = BigDecimal.valueOf(Long.MAX_VALUE);
    private static final BigDecimal MIN_LONG_BD = BigDecimal.valueOf(Long.MIN_VALUE);
    private static final BigInteger MAX_LONG_BI = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger MIN_LONG_BI = BigInteger.valueOf(Long.MIN_VALUE);

    private static final BigDecimal MAX_INT_BD = BigDecimal.valueOf(Integer.MAX_VALUE);
    private static final BigDecimal MIN_INT_BD = BigDecimal.valueOf(Integer.MIN_VALUE);
    private static final BigInteger MAX_INT_BI = BigInteger.valueOf(Integer.MAX_VALUE);
    private static final BigInteger MIN_INT_BI = BigInteger.valueOf(Integer.MIN_VALUE);

    private static final BigDecimal MAX_SHORT_BD = BigDecimal.valueOf(Short.MAX_VALUE);
    private static final BigDecimal MIN_SHORT_BD = BigDecimal.valueOf(Short.MIN_VALUE);
    private static final BigInteger MAX_SHORT_BI = BigInteger.valueOf(Short.MAX_VALUE);
    private static final BigInteger MIN_SHORT_BI = BigInteger.valueOf(Short.MIN_VALUE);

    private static final BigDecimal MAX_BYTE_BD = BigDecimal.valueOf(Byte.MAX_VALUE);
    private static final BigDecimal MIN_BYTE_BD = BigDecimal.valueOf(Byte.MIN_VALUE);
    private static final BigInteger MAX_BYTE_BI = BigInteger.valueOf(Byte.MAX_VALUE);
    private static final BigInteger MIN_BYTE_BI = BigInteger.valueOf(Byte.MIN_VALUE);

    private static final byte[] TOKEN_LOOKUP;

    static {
        byte[] tmp = new byte[256];
        tmp[SmileConstants.TOKEN_START_OBJECT & 0xFF] = Bytes.BRACE_OPEN_BYTE;
        tmp[SmileConstants.TOKEN_END_OBJECT & 0xFF] = Bytes.BRACE_CLOSE_BYTE;
        tmp[SmileConstants.TOKEN_START_ARRAY & 0xFF] = Bytes.SQUARE_BRACKET_OPEN_BYTE;
        tmp[SmileConstants.TOKEN_END_ARRAY & 0xFF] = Bytes.SQUARE_BRACKET_CLOSE_BYTE;
        tmp[SmileConstants.TOKEN_NULL & 0xFF] = 'n';
        tmp[SmileConstants.TOKEN_TRUE & 0xFF] = 't';
        tmp[SmileConstants.TOKEN_FALSE & 0xFF] = 'f';
        tmp[SmileConstants.TOKEN_EMPTY_STRING & 0xFF] = Bytes.DOUBLE_QUOTE_BYTE;
        tmp[SmileConstants.TOKEN_INT32 & 0xFF] = '1';
        tmp[SmileConstants.TOKEN_INT64 & 0xFF] = '1';
        tmp[SmileConstants.TOKEN_BIG_INT & 0xFF] = '1';
        tmp[SmileConstants.TOKEN_BIG_DEC & 0xFF] = '1';
        tmp[SmileConstants.TOKEN_FLOAT32 & 0xFF] = '1';
        tmp[SmileConstants.TOKEN_FLOAT64 & 0xFF] = '1';
        tmp[SmileConstants.VALUE_LONG_ASCII & 0xFF] = Bytes.DOUBLE_QUOTE_BYTE;
        tmp[SmileConstants.VALUE_LONG_UNICODE & 0xFF] = Bytes.DOUBLE_QUOTE_BYTE;
        tmp[SmileConstants.TOKEN_BINARY_7BIT & 0xFF] = 'B';
        tmp[SmileConstants.TOKEN_BINARY_RAW & 0xFF] = 'B';
        for (int i = SmileConstants.VALUE_TINY_ASCII_MIN; i <= SmileConstants.VALUE_TINY_ASCII_MAX; i++) {
            tmp[i] = Bytes.DOUBLE_QUOTE_BYTE;
        }
        for (int i = SmileConstants.VALUE_SHORT_ASCII_MIN; i <= SmileConstants.VALUE_SHORT_ASCII_MAX; i++) {
            tmp[i] = Bytes.DOUBLE_QUOTE_BYTE;
        }
        for (int i = SmileConstants.VALUE_TINY_UNICODE_MIN; i <= SmileConstants.VALUE_TINY_UNICODE_MAX; i++) {
            tmp[i] = Bytes.DOUBLE_QUOTE_BYTE;
        }
        for (int i = SmileConstants.VALUE_SHORT_UNICODE_MIN; i <= SmileConstants.VALUE_SHORT_UNICODE_MAX; i++) {
            tmp[i] = Bytes.DOUBLE_QUOTE_BYTE;
        }
        for (int i = SmileConstants.VALUE_SHARED_SHORT_MIN; i <= SmileConstants.VALUE_SHARED_SHORT_MAX; i++) {
            tmp[i] = Bytes.DOUBLE_QUOTE_BYTE;
        }
        for (int i = SmileConstants.VALUE_SHARED_LONG_MIN; i <= SmileConstants.VALUE_SHARED_LONG_MAX; i++) {
            tmp[i] = Bytes.DOUBLE_QUOTE_BYTE;
        }
        for (int i = SmileConstants.KEY_SHARED_SHORT_MIN; i <= SmileConstants.KEY_SHARED_SHORT_MAX; i++) {
            tmp[i] = Bytes.DOUBLE_QUOTE_BYTE;
        }
        for (int i = SmileConstants.VALUE_SMALL_INT_MIN; i <= SmileConstants.VALUE_SMALL_INT_MAX; i++) {
            tmp[i] = '1';
        }
        TOKEN_LOOKUP = tmp;
    }

    private final byte[] buffer;
    private final int bufferLength;

    private boolean inObject = false;
    private boolean keyExpected = false;
    private byte forcedEvent = -1;

    private final boolean[] structureStack = new boolean[64];
    private int stackDepth = 0;

    private byte currentToken;
    private int currentByte;
    private int currentIndex;
    private boolean endOfContent = false;

    //String parsing related fields
    private int stringOffset = 0;
    private boolean stringAscii = false;
    private boolean shareable = false;
    private boolean expectLowSurrogate = false;

    private int mark = -1;
    private byte markToken = -1;
    private int markByte = -1;
    private int markDepth = -1;
    private boolean markInObject = false;
    private boolean markKeyExpected = false;
    private byte markForcedEvent = -1;
    private boolean markEndOfContent = false;
    private JsonString[] markSharedKeyStrings = EMPTY;
    private JsonString[] markSharedValueStrings = EMPTY;
    private LazyHash[] markSharedKeyHashes = EMPTY_HASH;
    private LazyHash[] markSharedValueHashes = EMPTY_HASH;
    private int markNextSharedValueIndex;
    private int markNextSharedKeyIndex;

    private JsonString[] sharedKeyStrings = EMPTY;
    private JsonString[] sharedValueStrings = EMPTY;
    private LazyHash[] sharedKeyHashes = EMPTY_HASH;
    private LazyHash[] sharedValueHashes = EMPTY_HASH;
    private int nextSharedValueIndex;
    private int nextSharedKeyIndex;

    private boolean sharedValuesEnabled;
    private boolean sharedKeysEnabled = true;
    private boolean rawBinaryEnabled;

    private SmileParser(byte[] buffer, int offset, int bufferLength) {
        this.buffer = buffer;
        this.bufferLength = bufferLength;
        this.currentIndex = offset;
    }

    /**
     * Create a Smile parser from in-memory Smile-encoded bytes.
     *
     * @param json Smile binary data
     * @return a new Smile parser instance
     */
    public static JsonParser create(byte[] json) {
        Objects.requireNonNull(json);
        return new SmileParser(json, 0, json.length).initializeFromHeaderOrDefaults();
    }

    /**
     * Create a Smile parser from an input stream.
     *
     * @param inputStream stream containing Smile data
     * @return a new Smile parser instance
     */
    public static JsonParser create(InputStream inputStream) {
        Objects.requireNonNull(inputStream);
        SmileInputStreamParser parser = new SmileInputStreamParser(inputStream, SmileInputStreamParser.DEFAULT_BUFFER_SIZE);
        parser.initializeFromHeaderOrDefaults();
        return parser;
    }

    /**
     * Create a Smile parser from an input stream.
     *
     * @param inputStream stream containing Smile data
     * @param bufferSize size of the internal streaming buffer
     * @return a new Smile parser instance
     */
    public static JsonParser create(InputStream inputStream, int bufferSize) {
        Objects.requireNonNull(inputStream);
        SmileInputStreamParser parser = new SmileInputStreamParser(inputStream, bufferSize);
        parser.initializeFromHeaderOrDefaults();
        return parser;
    }

    @Override
    public boolean hasNext() {
        return !endOfContent && currentIndex + 1 < bufferLength;
    }

    @Override
    public byte nextToken() {
        byte toThrow = forcedEvent;
        if (toThrow != -1) {
            this.forcedEvent = -1;
            this.currentToken = toThrow;
            this.currentByte = toThrow;
            return toThrow;
        }
        if (endOfContent) {
            throw createException("End of content encountered. No more data to be parsed");
        }
        byte b = readNextByte();
        int currentByte = b & 0xFF;
        validateFeatureToken(currentByte);
        byte currentToken = translateToken(currentByte);
        if (currentToken == 0) {
            throw createException("Invalid Smile token 0x" + Integer.toHexString(currentByte)
                                          + (keyExpected ? " in key position" : ""));
        }
        if (currentByte >= (SmileConstants.TOKEN_START_ARRAY & 0xFF)
                && currentByte <= (SmileConstants.TOKEN_END_OBJECT & 0xFF)) {
            if (currentToken == Bytes.BRACE_OPEN_BYTE) {
                structureStack[++stackDepth] = true;
                inObject = true;
            } else if (currentToken == Bytes.SQUARE_BRACKET_OPEN_BYTE) {
                structureStack[++stackDepth] = false;
                inObject = false;
            } else if (currentToken == Bytes.SQUARE_BRACKET_CLOSE_BYTE
                    || currentToken == Bytes.BRACE_CLOSE_BYTE) {
                validateStructureClose(currentToken);
                inObject = structureStack[--stackDepth];
                if (hasNext()) {
                    int next = buffer[currentIndex + 1] & 0xFF;
                    if (next != (SmileConstants.TOKEN_END_OBJECT & 0xFF)
                            && next != (SmileConstants.TOKEN_END_ARRAY & 0xFF)
                            && next != (SmileConstants.END_OF_CONTENT & 0xFF)) {
                        forcedEvent = Bytes.COMMA_BYTE;
                    } else {
                        forcedEvent = -1;
                    }
                }
            }
            keyExpected = inObject;
        }
        this.currentToken = currentToken;
        this.currentByte = currentByte;
        return currentToken;
    }

    @Override
    public byte currentByte() {
        return currentToken;
    }

    @Override
    public JsonValue readJsonValue() {
        return switch (currentToken) {
            case '{' -> readJsonObject();
            case '[' -> readJsonArray();
            case '"' -> readJsonString();
            case '1' -> readJsonNumber();
            case 't', 'f' -> JsonBoolean.create(readBoolean());
            case 'n' -> {
                checkNextAfterValue();
                yield JsonNull.instance();
            }
            default -> throw createException("Unexpected JSON value type: " + currentToken);
        };
    }

    @Override
    public JsonObject readJsonObject() {
        JsonObject object = super.readJsonObject();
        checkNextAfterValue();
        return object;
    }

    @Override
    public JsonArray readJsonArray() {
        JsonArray object = super.readJsonArray();
        checkNextAfterValue();
        return object;
    }

    @Override
    public JsonString readJsonString() {
        if (currentToken != '"') {
            throw createException("The value is not a string");
        }
        String shared = decodeSharedStringReference();
        if (shared != null) {
            if (keyExpected) {
                keyExpected = false;
                forcedEvent = Bytes.COLON_BYTE;
            } else {
                checkNextAfterValue();
            }
            return JsonString.create(shared);
        }
        int currentByte = this.currentByte;
        if (currentByte == SmileConstants.TOKEN_EMPTY_STRING) {
            if (keyExpected) {
                keyExpected = false;
                forcedEvent = Bytes.COLON_BYTE;
            } else {
                checkNextAfterValue();
            }
            return JsonString.create("");
        }
        int len = analyzeStringAndObtainLength();
        int currentIndex = this.currentIndex;
        if (currentIndex + len + stringOffset >= bufferLength) {
            throw createException("Unexpected end of the JSON. Needed " + len
                                          + " bytes but only " + (bufferLength - currentIndex + 1) + " remain");
        }
        JsonString jsonString = JsonString.create(decodeSmileString(buffer, currentIndex + 1, len, stringAscii));
        this.currentIndex += len + stringOffset;
        if (keyExpected) {
            keyExpected = false;
            forcedEvent = Bytes.COLON_BYTE;
            if (sharedKeysEnabled && shareable) {
                registerKey(jsonString, buffer, currentIndex + 1, len);
            }
        } else {
            if (sharedValuesEnabled && shareable) {
                registerValue(jsonString, buffer, currentIndex + 1, len);
            }
            checkNextAfterValue();
        }
        return jsonString;
    }

    @Override
    public JsonNumber readJsonNumber() {
        if (currentToken != '1') {
            throw createException("The value is not a numeric type");
        }
        JsonNumber number;
        int b = currentByte;
        if (b >= SmileConstants.VALUE_SMALL_INT_MIN && b <= SmileConstants.VALUE_SMALL_INT_MAX) {
            number = JsonNumber.create(zigzagDecodeInt(b & 0x1F));
        } else if (b == SmileConstants.TOKEN_INT32) {
            number = JsonNumber.create(decodeInt());
        } else if (b == SmileConstants.TOKEN_INT64) {
            number = JsonNumber.create(decodeLong());
        } else if (b == SmileConstants.TOKEN_BIG_INT) {
            number = JsonNumber.create(new BigDecimal(decodeBigInteger()));
        } else if (b == SmileConstants.TOKEN_BIG_DEC) {
            number = JsonNumber.create(decodeBigDecimal());
        } else if (b == SmileConstants.TOKEN_FLOAT32) {
            number = JsonNumber.create(decodeFloat());
        } else if (b == SmileConstants.TOKEN_FLOAT64) {
            number = JsonNumber.create(decodeDouble());
        } else {
            throw createException("Unsupported numeric value");
        }
        checkNextAfterValue();
        return number;
    }

    @Override
    public String readString() {
        if (checkNull()) {
            return null;
        } else if (currentToken != '"') {
            throw createException("The value is not a string");
        }
        String shared = decodeSharedStringReference();
        if (shared != null) {
            if (keyExpected) {
                keyExpected = false;
                forcedEvent = Bytes.COLON_BYTE;
            } else {
                checkNextAfterValue();
            }
            return shared;
        }
        int b = currentByte;
        if (b == SmileConstants.TOKEN_EMPTY_STRING) {
            if (keyExpected) {
                keyExpected = false;
                forcedEvent = Bytes.COLON_BYTE;
            } else {
                checkNextAfterValue();
            }
            return "";
        }
        int len = analyzeStringAndObtainLength();
        int currentIndex = this.currentIndex;
        if (currentIndex + len + stringOffset >= bufferLength) {
            throw createException("Unexpected end of the JSON. Needed " + len
                                          + " bytes but only " + (bufferLength - currentIndex + 1) + " remain");
        }
        String result = decodeSmileString(buffer, currentIndex + 1, len, stringAscii);
        this.currentIndex += len + stringOffset;
        if (keyExpected) {
            keyExpected = false;
            forcedEvent = Bytes.COLON_BYTE;
            if (sharedKeysEnabled && shareable) {
                registerKey(JsonString.create(result), buffer, currentIndex + 1, len);
            }
        } else {
            if (sharedValuesEnabled && shareable) {
                registerValue(JsonString.create(result), buffer, currentIndex + 1, len);
            }
            checkNextAfterValue();
        }
        return result;
    }

    @Override
    public int readStringAsHash() {
        if (currentToken != '"') {
            throw createException("Hash calculation is intended only for String values");
        }
        Integer shared = obtainSharedHashReference();
        if (shared != null) {
            if (keyExpected) {
                keyExpected = false;
                forcedEvent = Bytes.COLON_BYTE;
            } else {
                checkNextAfterValue();
            }
            return shared;
        }
        if (currentByte == SmileConstants.TOKEN_EMPTY_STRING) {
            if (keyExpected) {
                keyExpected = false;
                forcedEvent = Bytes.COLON_BYTE;
            } else {
                checkNextAfterValue();
            }
            return FNV_OFFSET_BASIS;
        }
        int start = this.currentIndex;
        int currentIndex = start;
        int len = analyzeStringAndObtainLength();
        if (currentIndex + len + stringOffset >= bufferLength) {
            throw createException("Unexpected end of the JSON. Needed " + len
                                          + " bytes but only " + (bufferLength - currentIndex + 1) + " remain");
        }

        // Compute FNV-1a hash of the string content using recommended offset basis and prime values.
        // This optimized loop scans the buffer directly without calling readNextByte() for each character.
        int fnv1aHash = FNV_OFFSET_BASIS;
        for (int i = 0; i < len; i++) {
            int b = buffer[++currentIndex] & 0xFF;
            fnv1aHash ^= b;
            fnv1aHash *= FNV_PRIME;
        }
        validateSmileString(buffer, start + 1, len, stringAscii);

        this.currentIndex = currentIndex + this.stringOffset;
        if (keyExpected) {
            keyExpected = false;
            forcedEvent = Bytes.COLON_BYTE;
            if (sharedKeysEnabled && shareable) {
                registerKey(new LazyHash(fnv1aHash), JsonString.create(decodeSmileString(buffer, start + 1, len, stringAscii)));
            }
        } else {
            if (sharedValuesEnabled && shareable) {
                registerValue(new LazyHash(fnv1aHash), JsonString.create(decodeSmileString(buffer, start + 1, len, stringAscii)));
            }
            checkNextAfterValue();
        }
        return fnv1aHash;
    }

    @Override
    public char readChar() {
        String value = readString();
        if (value == null || value.length() != 1) {
            throw createException("Expected only a single character, but got value " + value);
        }
        return value.charAt(0);
    }

    @Override
    public boolean readBoolean() {
        int t = buffer[currentIndex] & 0xFF;
        if (t == SmileConstants.TOKEN_TRUE) {
            checkNextAfterValue();
            return true;
        } else if (t == SmileConstants.TOKEN_FALSE) {
            checkNextAfterValue();
            return false;
        }
        throw createException("Current value is not a boolean", buffer[currentIndex]);
    }

    @Override
    public byte readByte() {
        if (currentToken != '1') {
            throw createException("The value is not a numeric type");
        }
        int b = currentByte;
        byte toReturn;
        if (b >= SmileConstants.VALUE_SMALL_INT_MIN && b <= SmileConstants.VALUE_SMALL_INT_MAX) {
            toReturn = (byte) zigzagDecodeInt(currentByte & 0x1F);
        } else if (b == SmileConstants.TOKEN_INT32) {
            int value = decodeInt();
            if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                throw createException("The number is too big for a byte number: " + value);
            }
            toReturn = (byte) value;
        } else if (b == SmileConstants.TOKEN_FLOAT32) {
            float value = decodeFloat();
            if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                throw createException("The number is too big for a byte number: " + value);
            }
            toReturn = (byte) value;
        } else if (b == SmileConstants.TOKEN_FLOAT64) {
            double value = decodeDouble();
            if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                throw createException("The number is too big for a byte number: " + value);
            }
            toReturn = (byte) value;
        } else if (b == SmileConstants.TOKEN_INT64) {
            long value = decodeLong();
            if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                throw createException("The number is too big for a byte number: " + value);
            }
            toReturn = (byte) value;
        } else if (b == SmileConstants.TOKEN_BIG_DEC) {
            BigDecimal value = decodeBigDecimal();
            if (value.compareTo(MAX_BYTE_BD) > 0
                    || value.compareTo(MIN_BYTE_BD) < 0) {
                throw createException("The number is too big for a byte number: " + value);
            }
            toReturn = value.byteValue();
        } else if (b == SmileConstants.TOKEN_BIG_INT) {
            BigInteger value = decodeBigInteger();
            if (value.compareTo(MAX_BYTE_BI) > 0
                    || value.compareTo(MIN_BYTE_BI) < 0) {
                throw createException("The number is too big for a byte number: " + value);
            }
            toReturn = value.byteValue();
        } else {
            throw createException("Unsupported numeric value");
        }
        checkNextAfterValue();
        return toReturn;
    }

    @Override
    public short readShort() {
        if (currentToken != '1') {
            throw createException("The value is not a numeric type");
        }
        int b = currentByte;
        short toReturn;
        if (b >= SmileConstants.VALUE_SMALL_INT_MIN && b <= SmileConstants.VALUE_SMALL_INT_MAX) {
            toReturn = (short) zigzagDecodeInt(currentByte & 0x1F);
        } else if (b == SmileConstants.TOKEN_INT32) {
            int value = decodeInt();
            if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                throw createException("The number is too big for a short number: " + value);
            }
            toReturn = (short) value;
        } else if (b == SmileConstants.TOKEN_FLOAT32) {
            float value = decodeFloat();
            if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                throw createException("The number is too big for a short number: " + value);
            }
            toReturn = (short) value;
        } else if (b == SmileConstants.TOKEN_FLOAT64) {
            double value = decodeDouble();
            if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                throw createException("The number is too big for a short number: " + value);
            }
            toReturn = (short) value;
        } else if (b == SmileConstants.TOKEN_INT64) {
            long value = decodeLong();
            if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                throw createException("The number is too big for a short number: " + value);
            }
            toReturn = (short) value;
        } else if (b == SmileConstants.TOKEN_BIG_DEC) {
            BigDecimal value = decodeBigDecimal();
            if (value.compareTo(MAX_SHORT_BD) > 0
                    || value.compareTo(MIN_SHORT_BD) < 0) {
                throw createException("The number is too big for a short number: " + value);
            }
            toReturn = value.shortValue();
        } else if (b == SmileConstants.TOKEN_BIG_INT) {
            BigInteger value = decodeBigInteger();
            if (value.compareTo(MAX_SHORT_BI) > 0
                    || value.compareTo(MIN_SHORT_BI) < 0) {
                throw createException("The number is too big for a short number: " + value);
            }
            toReturn = value.shortValue();
        } else {
            throw createException("Unsupported numeric value");
        }
        checkNextAfterValue();
        return toReturn;
    }

    @Override
    public int readInt() {
        if (currentToken != '1') {
            throw createException("The value is not a numeric type");
        }
        int b = currentByte;
        int toReturn;
        if (b >= SmileConstants.VALUE_SMALL_INT_MIN && b <= SmileConstants.VALUE_SMALL_INT_MAX) {
            toReturn = zigzagDecodeInt(currentByte & 0x1F);
        } else if (b == SmileConstants.TOKEN_INT32) {
            toReturn = decodeInt();
        } else if (b == SmileConstants.TOKEN_FLOAT32) {
            toReturn = (int) decodeFloat();
        } else if (b == SmileConstants.TOKEN_FLOAT64) {
            double value = decodeDouble();
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw createException("The number is too big for an integer number: " + value);
            }
            toReturn = (int) value;
        } else if (b == SmileConstants.TOKEN_INT64) {
            long value = decodeLong();
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw createException("The number is too big for an integer number: " + value);
            }
            toReturn = (int) value;
        } else if (b == SmileConstants.TOKEN_BIG_DEC) {
            BigDecimal value = decodeBigDecimal();
            if (value.compareTo(MAX_INT_BD) > 0
                    || value.compareTo(MIN_INT_BD) < 0) {
                throw createException("The number is too big for an int number: " + value);
            }
            toReturn = value.intValue();
        } else if (b == SmileConstants.TOKEN_BIG_INT) {
            BigInteger value = decodeBigInteger();
            if (value.compareTo(MAX_INT_BI) > 0
                    || value.compareTo(MIN_INT_BI) < 0) {
                throw createException("The number is too big for an int number: " + value);
            }
            toReturn = value.intValue();
        } else {
            throw createException("Unsupported numeric value");
        }
        checkNextAfterValue();
        return toReturn;
    }

    @Override
    public long readLong() {
        if (currentToken != '1') {
            throw createException("The value is not a numeric type");
        }
        int b = currentByte;
        long toReturn;
        if (b >= SmileConstants.VALUE_SMALL_INT_MIN && b <= SmileConstants.VALUE_SMALL_INT_MAX) {
            toReturn = zigzagDecodeLong(b & 0x1F);
        } else if (b == SmileConstants.TOKEN_INT32
                || b == SmileConstants.TOKEN_INT64) {
            toReturn = decodeLong();
        } else if (b == SmileConstants.TOKEN_FLOAT32) {
            toReturn = (long) decodeFloat();
        } else if (b == SmileConstants.TOKEN_FLOAT64) {
            toReturn = (long) decodeDouble();
        } else if (b == SmileConstants.TOKEN_BIG_DEC) {
            BigDecimal value = decodeBigDecimal();
            if (value.compareTo(MAX_LONG_BD) > 0
                    || value.compareTo(MIN_LONG_BD) < 0) {
                throw createException("The number is too big for a long number: " + value);
            }
            toReturn = value.longValue();
        } else if (b == SmileConstants.TOKEN_BIG_INT) {
            BigInteger value = decodeBigInteger();
            if (value.compareTo(MAX_LONG_BI) > 0
                    || value.compareTo(MIN_LONG_BI) < 0) {
                throw createException("The number is too big for a long number: " + value);
            }
            toReturn = value.longValue();
        } else {
            throw createException("Unsupported numeric value");
        }
        checkNextAfterValue();
        return toReturn;
    }

    @Override
    public float readFloat() {
        if (currentToken != '1') {
            throw createException("The value is not a numeric type");
        }
        int b = currentByte;
        float toReturn;
        if (b == SmileConstants.TOKEN_FLOAT32) {
            toReturn = decodeFloat();
        } else if (b == SmileConstants.TOKEN_FLOAT64) {
            double value = decodeDouble();
            float f = (float) value;
            if (Float.isInfinite(f)) {
                throw createException("The number is too big for a float number: " + value);
            }
            toReturn = f;
        } else if (b >= SmileConstants.VALUE_SMALL_INT_MIN && b <= SmileConstants.VALUE_SMALL_INT_MAX) {
            toReturn = zigzagDecodeInt(b & 0x1F);
        } else if (b == SmileConstants.TOKEN_INT32) {
            toReturn = decodeInt();
        } else if (b == SmileConstants.TOKEN_INT64) {
            long value = decodeLong();
            if ((long) (float) value != value) { //Check if rounding would happen
                throw createException("The number is too big for a float number: " + value);
            }
            toReturn = (float) value;
        } else if (b == SmileConstants.TOKEN_BIG_DEC) {
            BigDecimal value = decodeBigDecimal();
            toReturn = value.floatValue();
            if (Float.isInfinite(toReturn)) {
                throw createException("The number is too big for a float number: " + value);
            }
        } else if (b == SmileConstants.TOKEN_BIG_INT) {
            BigInteger value = decodeBigInteger();
            toReturn = value.floatValue();
            if (Float.isInfinite(toReturn)) {
                throw createException("The number is too big for a float number: " + value);
            }
        } else {
            throw createException("Unsupported numeric value");
        }
        checkNextAfterValue();
        return toReturn;
    }

    @Override
    public double readDouble() {
        if (currentToken != '1') {
            throw createException("The value is not a numeric type");
        }
        int b = currentByte;
        double toReturn;
        if (b == SmileConstants.TOKEN_FLOAT64) {
            toReturn = decodeDouble();
        } else if (b == SmileConstants.TOKEN_FLOAT32) {
            toReturn = decodeFloat();
        } else if (b >= SmileConstants.VALUE_SMALL_INT_MIN && b <= SmileConstants.VALUE_SMALL_INT_MAX) {
            toReturn = zigzagDecodeInt(b & 0x1F);
        } else if (b == SmileConstants.TOKEN_INT32
                || b == SmileConstants.TOKEN_INT64) {
            toReturn = decodeLong();
        } else if (b == SmileConstants.TOKEN_BIG_DEC) {
            BigDecimal value = decodeBigDecimal();
            toReturn = value.doubleValue();
            if (Double.isInfinite(toReturn)) {
                throw createException("The number is too big for a double number: " + value);
            }
        } else if (b == SmileConstants.TOKEN_BIG_INT) {
            BigInteger value = decodeBigInteger();
            toReturn = value.doubleValue();
            if (Double.isInfinite(toReturn)) {
                throw createException("The number is too big for a double number: " + value);
            }
        } else {
            throw createException("Unsupported numeric value");
        }
        checkNextAfterValue();
        return toReturn;
    }

    @Override
    public BigInteger readBigInteger() {
        if (currentToken != '1') {
            throw createException("The value has to be numeric");
        }
        int b = currentByte;
        BigInteger toReturn;
        if (b == SmileConstants.TOKEN_BIG_INT) {
            toReturn = decodeBigInteger();
        } else if (b == SmileConstants.TOKEN_INT32
                || b == SmileConstants.TOKEN_INT64) {
            long value = readUnsignedVInt();
            toReturn = BigInteger.valueOf(zigzagDecodeLong(value));
        } else if (b >= SmileConstants.VALUE_SMALL_INT_MIN && b <= SmileConstants.VALUE_SMALL_INT_MAX) {
            toReturn = BigInteger.valueOf(zigzagDecodeInt(b & 0x1F));
        } else if (b == SmileConstants.TOKEN_BIG_DEC) {
            toReturn = decodeBigDecimal().toBigInteger();
        } else if (b == SmileConstants.TOKEN_FLOAT64) {
            toReturn = BigInteger.valueOf((long) decodeDouble());
        } else if (b == SmileConstants.TOKEN_FLOAT32) {
            toReturn = BigInteger.valueOf((long) decodeFloat());
        } else {
            throw createException("Unsupported numeric value");
        }
        checkNextAfterValue();
        return toReturn;
    }

    @Override
    public BigDecimal readBigDecimal() {
        if (currentToken != '1') {
            throw createException("The value has to be numeric");
        }
        int b = currentByte;
        BigDecimal toReturn;
        if (b == SmileConstants.TOKEN_BIG_DEC) {
            toReturn = decodeBigDecimal();
        } else if (b == SmileConstants.TOKEN_FLOAT64) {
            toReturn = new BigDecimal(decodeDouble());
        } else if (b == SmileConstants.TOKEN_FLOAT32) {
            toReturn = new BigDecimal(decodeFloat());
        } else if (b >= SmileConstants.VALUE_SMALL_INT_MIN && b <= SmileConstants.VALUE_SMALL_INT_MAX) {
            toReturn = new BigDecimal(zigzagDecodeInt(b & 0x1F));
        } else if (b == SmileConstants.TOKEN_BIG_INT) {
            toReturn = new BigDecimal(decodeBigInteger());
        } else if (b == SmileConstants.TOKEN_INT32
                || b == SmileConstants.TOKEN_INT64) {
            toReturn = new BigDecimal(decodeLong());
        } else {
            throw createException("Unsupported numeric value");
        }
        checkNextAfterValue();
        return toReturn;
    }

    @Override
    public byte[] readBinary() {
        if (currentByte == (SmileConstants.TOKEN_BINARY_7BIT & 0xFF)) {
            int rawLen = (int) readUnsignedVInt();
            byte[] data = decode7Bit(rawLen, encodedLength7Bit(rawLen));
            checkNextAfterValue();
            return data;
        } else if (currentByte == (SmileConstants.TOKEN_BINARY_RAW & 0xFF)) {
            if (!rawBinaryEnabled) {
                throw createException("Raw binary not enabled in header");
            }
            int rawLen = (int) readUnsignedVInt();
            if (currentIndex + rawLen >= bufferLength) {
                throw createException("Unexpected end of the JSON");
            }
            byte[] bytes = new byte[rawLen];
            System.arraycopy(buffer, currentIndex + 1, bytes, 0, rawLen);
            currentIndex += rawLen;
            checkNextAfterValue();
            return bytes;
        }
        throw createException("Current token is not a binary value");
    }

    @Override
    public boolean checkNull() {
        if (currentByte == SmileConstants.TOKEN_NULL) {
            checkNextAfterValue();
            return true;
        }
        return false;
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
        case '1':
            skipNumber();
            break;
        case 't':
        case 'n':
        case 'f':
            checkNextAfterValue();
            break;
        case ',':
        case ':':
            //NOOP
            break;
        default:
            throw createException("Invalid JSON value to skip", currentByte());
        }
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
        if (mark > -1) {
            throw new IllegalStateException(
                    "Parser is already marked for replaying. "
                            + "Call clearMark() or resetToMark() before marking again.");
        }
        mark = currentIndex;
        markToken = currentToken;
        markByte = currentByte;
        markKeyExpected = keyExpected;
        markInObject = inObject;
        markDepth = stackDepth;
        markForcedEvent = forcedEvent;
        markEndOfContent = endOfContent;
        markSharedKeyStrings = Arrays.copyOf(sharedKeyStrings, sharedKeyStrings.length);
        markSharedValueStrings = Arrays.copyOf(sharedValueStrings, sharedValueStrings.length);
        markSharedKeyHashes = Arrays.copyOf(sharedKeyHashes, sharedKeyHashes.length);
        markSharedValueHashes = Arrays.copyOf(sharedValueHashes, sharedValueHashes.length);
        markNextSharedKeyIndex = nextSharedKeyIndex;
        markNextSharedValueIndex = nextSharedValueIndex;
    }

    @Override
    public void clearMark() {
        mark = -1;
        markToken = -1;
        markByte = -1;
        markKeyExpected = false;
        markInObject = false;
        markDepth = -1;
        markForcedEvent = -1;
        markEndOfContent = false;
        markSharedKeyStrings = EMPTY;
        markSharedValueStrings = EMPTY;
        markSharedKeyHashes = EMPTY_HASH;
        markSharedValueHashes = EMPTY_HASH;
        markNextSharedKeyIndex = 0;
        markNextSharedValueIndex = 0;
    }

    @Override
    public void resetToMark() {
        if (mark < 0) {
            throw new IllegalStateException(
                    "No mark has been set. Call mark() before resetToMark().");
        }
        currentIndex = mark;
        currentToken = markToken;
        currentByte = markByte;
        keyExpected = markKeyExpected;
        inObject = markInObject;
        stackDepth = markDepth;
        forcedEvent = markForcedEvent;
        endOfContent = markEndOfContent;
        sharedKeyStrings = Arrays.copyOf(markSharedKeyStrings, markSharedKeyStrings.length);
        sharedValueStrings = Arrays.copyOf(markSharedValueStrings, markSharedValueStrings.length);
        sharedKeyHashes = Arrays.copyOf(markSharedKeyHashes, markSharedKeyHashes.length);
        sharedValueHashes = Arrays.copyOf(markSharedValueHashes, markSharedValueHashes.length);
        nextSharedKeyIndex = markNextSharedKeyIndex;
        nextSharedValueIndex = markNextSharedValueIndex;
        clearMark();
    }

    /**
     * Initializes parser feature flags from the Smile header when present, or from
     * the headerless-format defaults otherwise.
     *
     * <pre>
     *   Byte 0: 0x3A (':')
     *   Byte 1: 0x29 (')')
     *   Byte 2: 0x0A (LF)
     *   Byte 3: feature flags
     *             bits 7-4 : version  (must be 0x0)
     *             bit  3   : reserved
     *             bit  2   : raw binary present (ignored here)
     *             bit  1   : shared value strings
     *             bit  0   : shared key names
     * </pre>
     */
    SmileParser initializeFromHeaderOrDefaults() {
        if (currentIndex >= bufferLength) {
            throw createException("Unexpected end of the binary JSON found");
        }

        int b0 = buffer[currentIndex] & 0xFF;
        if (b0 != (SmileConstants.HEADER_0 & 0xFF)) {
            initializeHeaderlessDefaults();
            currentIndex--;
            nextToken();
            return this;
        }

        if (currentIndex + 2 >= bufferLength) {
            throw createException("Unexpected end of Smile header");
        }

        int b1 = buffer[currentIndex + 1] & 0xFF;
        int b2 = buffer[currentIndex + 2] & 0xFF;
        if (b1 != (SmileConstants.HEADER_1 & 0xFF)
                || b2 != (SmileConstants.HEADER_2 & 0xFF)) {
            throw createException("Invalid Smile header: expected 0x3A 0x29 0x0A, got"
                                          + " 0x" + Integer.toHexString(b0)
                                          + " 0x" + Integer.toHexString(b1)
                                          + " 0x" + Integer.toHexString(b2));
        }

        if (currentIndex + 3 >= bufferLength) {
            throw createException("Unexpected end of Smile header");
        }

        currentIndex += 3;
        applyHeaderFeatures(buffer[currentIndex] & 0xFF);
        nextToken();
        return this;
    }

    private byte readNextByte() {
        if (++currentIndex == bufferLength) {
            throw createException("Unexpected end of the binary JSON found");
        }
        return buffer[currentIndex];
    }

    private BigInteger decodeBigInteger() {
        int rawLength = (int) readUnsignedVInt();
        int encodedLen = encodedLength7Bit(rawLength);
        byte[] decodedBytes = decode7Bit(rawLength, encodedLen);
        return new BigInteger(decodedBytes);
    }

    private BigDecimal decodeBigDecimal() {
        int scale = zigzagDecodeInt((int) readUnsignedVInt());
        int rawLength = (int) readUnsignedVInt();
        int encodedLen = encodedLength7Bit(rawLength);
        byte[] decodedBytes = decode7Bit(rawLength, encodedLen);
        return new BigDecimal(new BigInteger(decodedBytes), scale);
    }

    private int decodeInt() {
        return zigzagDecodeInt((int) readUnsignedVInt());
    }

    private long decodeLong() {
        return zigzagDecodeLong(readUnsignedVInt());
    }

    private void skipObject() {
        byte b = nextToken();
        if (b == '}') {
            checkNextAfterValue();
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
            checkNextAfterValue();
            return;
        }
        throw createException("Comma or the end of the object expected", b);
    }

    private void skipString() {
        String shared = decodeSharedStringReference();
        if (shared != null) {
            if (keyExpected) {
                keyExpected = false;
                forcedEvent = Bytes.COLON_BYTE;
            } else {
                checkNextAfterValue();
            }
            return;
        }
        int len = analyzeStringAndObtainLength();
        int start = this.currentIndex;
        int currentIndex = this.currentIndex + len + stringOffset;
        if (currentIndex >= bufferLength) {
            throw createException("Unexpected end of the JSON. Needed " + len
                                          + " bytes but only " + (bufferLength - this.currentIndex + 1) + " remain");
        }
        String decoded = null;
        if (keyExpected) {
            if (sharedKeysEnabled && shareable) {
                decoded = decodeSmileString(buffer, start + 1, len, stringAscii);
            } else {
                validateSmileString(buffer, start + 1, len, stringAscii);
            }
        } else if (sharedValuesEnabled && shareable) {
            decoded = decodeSmileString(buffer, start + 1, len, stringAscii);
        } else {
            validateSmileString(buffer, start + 1, len, stringAscii);
        }
        this.currentIndex = currentIndex;
        if (keyExpected) {
            keyExpected = false;
            forcedEvent = Bytes.COLON_BYTE;
            if (sharedKeysEnabled && shareable) {
                registerKey(new LazyHash(buffer, start + 1, len), JsonString.create(decoded));
            }
        } else {
            if (sharedValuesEnabled && shareable) {
                registerValue(new LazyHash(buffer, start + 1, len), JsonString.create(decoded));
            }
            checkNextAfterValue();
        }
    }

    private long readUnsignedVInt() {
        long value = 0;
        for (int i = currentIndex + 1; i < bufferLength; i++) {
            int b = buffer[i] & 0xFF;
            if ((b & 0x80) != 0) {
                if ((b & 0x40) != 0) {
                    throw createException("Invalid Smile VInt final byte: 0x" + Integer.toHexString(b));
                }
                // Final byte: MSB=1, bit 6=0 (spec), 6 data bits in LSBs.
                currentIndex = i;
                return (value << 6) | (b & 0x3F);
            }
            // Intermediate byte: MSB=0, 7 data bits; shift to make room.
            value = (value << 7) | (b & 0x7F);
        }
        throw createException("Unexpected end of the number");
    }

    /**
     * Decodes a ZigZag-encoded unsigned 32-bit value back to a signed integer.
     *
     * <pre>
     *   formula: (n >>> 1) ^ -(n & 1)
     *   0→0,  1→-1, 2→1,  3→-2,  30→15, 31→-16
     * </pre>
     */
    private static int zigzagDecodeInt(int n) {
        return ((n >>> 1)) ^ -(n & 1);
    }

    /**
     * Decodes a ZigZag-encoded unsigned 64-bit value back to a signed long.
     *
     * <pre>
     *   formula: (n >>> 1) ^ -(n & 1)
     * </pre>
     */
    private static long zigzagDecodeLong(long n) {
        return (n >>> 1) ^ -(n & 1L);
    }

    /**
     * Reads a 32-bit IEEE 754 float from 5 seven-bit bytes (big-endian).
     *
     * <p>Encoder split 32 bits as [4][7][7][7][7]. Reassembly:
     * <pre>
     *   bits = (b0 << 28) | (b1 << 21) | (b2 << 14) | (b3 << 7) | b4
     * </pre>
     * where {@code b0} carries the top 4 bits (mask {@code 0x0F}) and
     * {@code b1}-{@code b4} each carry 7 bits (mask {@code 0x7F}).
     */
    private float decodeFloat() {
        int b0 = readNextByte() & 0x0F;
        int b1 = readNextByte() & 0x7F;
        int b2 = readNextByte() & 0x7F;
        int b3 = readNextByte() & 0x7F;
        int b4 = readNextByte() & 0x7F;
        int bits = (b0 << 28) | (b1 << 21) | (b2 << 14) | (b3 << 7) | b4;
        return Float.intBitsToFloat(bits);
    }

    /**
     * Reads a 64-bit IEEE 754 double from 10 seven-bit bytes (big-endian).
     *
     * <p>Encoder split 64 bits as [1][7][7]...[7]. Reassembly:
     * <pre>
     *   bits = (b0 << 63) | (b1 << 56) | ... | b9
     * </pre>
     * where {@code b0} is the single sign bit (mask {@code 0x01}) and
     * {@code b1}-{@code b9} each carry 7 bits (mask {@code 0x7F}).
     */
    private double decodeDouble() {
        long b0 = readNextByte() & 0x01L;
        long b1 = readNextByte() & 0x7FL;
        long b2 = readNextByte() & 0x7FL;
        long b3 = readNextByte() & 0x7FL;
        long b4 = readNextByte() & 0x7FL;
        long b5 = readNextByte() & 0x7FL;
        long b6 = readNextByte() & 0x7FL;
        long b7 = readNextByte() & 0x7FL;
        long b8 = readNextByte() & 0x7FL;
        long b9 = readNextByte() & 0x7FL;
        long bits = (b0 << 63) | (b1 << 56) | (b2 << 49) | (b3 << 42)
                | (b4 << 35) | (b5 << 28) | (b6 << 21) | (b7 << 14)
                | (b8 << 7) | b9;
        return Double.longBitsToDouble(bits);
    }

    private void checkNextAfterValue() {
        if (hasNext()) {
            keyExpected = inObject;
            int b = buffer[currentIndex + 1] & 0xFF;
            if (b == (SmileConstants.TOKEN_END_OBJECT & 0xFF)
                    || b == (SmileConstants.TOKEN_END_ARRAY & 0xFF)) {
                forcedEvent = -1;
                return;
            } else if (b == (SmileConstants.END_OF_CONTENT & 0xFF)) {
                forcedEvent = -1;
                endOfContent = true;
                return;
            }
            forcedEvent = Bytes.COMMA_BYTE;
        }
    }

    private void initializeHeaderlessDefaults() {
        sharedKeysEnabled = true;
        sharedValuesEnabled = false;
        rawBinaryEnabled = false;
        sharedKeyStrings = new JsonString[SHARED_TABLE_SIZE_INIT];
        sharedKeyHashes = new LazyHash[SHARED_TABLE_SIZE_INIT];
    }

    private void applyHeaderFeatures(int headerFlags) {
        int version = (headerFlags & 0xF0) >> 4;
        if (version != 0) {
            throw createException("Unsupported Smile format version: " + version);
        }

        sharedKeysEnabled = (headerFlags & SmileConstants.HEADER_FEATURE_SHARED_KEYS) != 0;
        sharedValuesEnabled = (headerFlags & SmileConstants.HEADER_FEATURE_SHARED_VALUES) != 0;
        rawBinaryEnabled = (headerFlags & SmileConstants.HEADER_FEATURE_RAW_BINARY) != 0;

        if (sharedKeysEnabled) {
            sharedKeyStrings = new JsonString[SHARED_TABLE_SIZE_INIT];
            sharedKeyHashes = new LazyHash[SHARED_TABLE_SIZE_INIT];
        }
        if (sharedValuesEnabled) {
            sharedValueStrings = new JsonString[SHARED_TABLE_SIZE_INIT];
            sharedValueHashes = new LazyHash[SHARED_TABLE_SIZE_INIT];
        }
    }

    private byte translateToken(int rawToken) {
        if (keyExpected) {
            if (rawToken == (SmileConstants.TOKEN_END_OBJECT & 0xFF)) {
                return Bytes.BRACE_CLOSE_BYTE;
            }
            if (rawToken == (SmileConstants.TOKEN_EMPTY_STRING & 0xFF)
                    || rawToken == (SmileConstants.KEY_LONG_UNICODE & 0xFF)
                    || (rawToken >= SmileConstants.KEY_SHARED_LONG_MIN
                    && rawToken <= SmileConstants.KEY_SHARED_LONG_MAX)
                    || (rawToken >= SmileConstants.KEY_SHARED_SHORT_MIN
                    && rawToken <= SmileConstants.KEY_SHARED_SHORT_MAX)
                    || (rawToken >= SmileConstants.KEY_SHORT_ASCII_MIN
                    && rawToken <= SmileConstants.KEY_SHORT_ASCII_MAX)
                    || (rawToken >= SmileConstants.KEY_SHORT_UNICODE_MIN
                    && rawToken <= SmileConstants.KEY_SHORT_UNICODE_MAX)) {
                return Bytes.DOUBLE_QUOTE_BYTE;
            }
            return 0;
        }
        if (rawToken == (SmileConstants.TOKEN_END_OBJECT & 0xFF)) {
            return 0;
        }
        return TOKEN_LOOKUP[rawToken];
    }

    private void validateFeatureToken(int rawToken) {
        if (keyExpected) {
            if (!sharedKeysEnabled
                    && ((rawToken >= SmileConstants.KEY_SHARED_SHORT_MIN && rawToken <= SmileConstants.KEY_SHARED_SHORT_MAX)
                    || (rawToken >= SmileConstants.KEY_SHARED_LONG_MIN && rawToken <= SmileConstants.KEY_SHARED_LONG_MAX))) {
                throw createException("Shared key references are disabled by Smile header");
            }
            return;
        }

        if (!sharedValuesEnabled
                && ((rawToken >= SmileConstants.VALUE_SHARED_SHORT_MIN && rawToken <= SmileConstants.VALUE_SHARED_SHORT_MAX)
                || (rawToken >= SmileConstants.VALUE_SHARED_LONG_MIN && rawToken <= SmileConstants.VALUE_SHARED_LONG_MAX))) {
            throw createException("Shared value references are disabled by Smile header");
        }
    }

    private int analyzeStringAndObtainLength() {
        stringOffset = 0;
        stringAscii = false;
        shareable = false;
        if (keyExpected) {
            shareable = true;
            if (currentByte >= SmileConstants.KEY_SHORT_ASCII_MIN && currentByte <= SmileConstants.KEY_SHORT_ASCII_MAX) {
                // Short ASCII: len = (token & 0x3F) + 1
                stringAscii = true;
                return (currentByte & SmileConstants.KEY_STRING_LENGTH_MASK) + SmileConstants.KEY_SHORT_ASCII_LENGTH_ADD;
            } else if (currentByte >= SmileConstants.KEY_SHORT_UNICODE_MIN
                    && currentByte <= SmileConstants.KEY_SHORT_UNICODE_MAX) {
                // Short Unicode: len = (token & 0x3F) + 2
                return (currentByte & SmileConstants.KEY_STRING_LENGTH_MASK) + SmileConstants.KEY_SHORT_UNICODE_LENGTH_ADD;
            } else if (currentByte == SmileConstants.KEY_LONG_UNICODE) {
                stringOffset = 1;
                return findLongStringLength();
            } else {
                throw createException("Unexpected key-mode token: 0x" + Integer.toHexString(currentByte));
            }
        }
        if (currentByte == (SmileConstants.VALUE_LONG_ASCII & 0xFF)) {
            stringOffset = 1;
            stringAscii = true;
            return findLongStringLength();
        } else if (currentByte == (SmileConstants.VALUE_LONG_UNICODE & 0xFF)) {
            stringOffset = 1;
            return findLongStringLength();
        }
        int length;
        if (currentByte >= SmileConstants.VALUE_TINY_ASCII_MIN && currentByte <= SmileConstants.VALUE_TINY_ASCII_MAX) {
            stringAscii = true;
            length = (currentByte & SmileConstants.VALUE_STRING_LENGTH_MASK) + SmileConstants.VALUE_TINY_ASCII_LENGTH_ADD;
        } else if (currentByte >= SmileConstants.VALUE_SHORT_ASCII_MIN && currentByte <= SmileConstants.VALUE_SHORT_ASCII_MAX) {
            stringAscii = true;
            length = (currentByte & SmileConstants.VALUE_STRING_LENGTH_MASK) + SmileConstants.VALUE_SHORT_ASCII_LENGTH_ADD;
        } else if (currentByte >= SmileConstants.VALUE_TINY_UNICODE_MIN
                && currentByte <= SmileConstants.VALUE_TINY_UNICODE_MAX) {
            length = (currentByte & SmileConstants.VALUE_STRING_LENGTH_MASK) + SmileConstants.VALUE_TINY_UNICODE_LENGTH_ADD;
        } else if (currentByte >= SmileConstants.VALUE_SHORT_UNICODE_MIN
                && currentByte <= SmileConstants.VALUE_SHORT_UNICODE_MAX) {
            length = (currentByte & SmileConstants.VALUE_STRING_LENGTH_MASK) + SmileConstants.VALUE_SHORT_UNICODE_LENGTH_ADD;
        } else {
            throw createException("Unsupported string token: 0x" + Integer.toHexString(currentByte));
        }
        shareable = length <= SmileConstants.SHARED_STRING_VALUES_MAX_BYTES;
        return length;
    }

    private int findLongStringLength() {
        int currentIndex = this.currentIndex;
        for (int i = currentIndex + 1; i < bufferLength; i++) {
            byte val = buffer[i];
            if (val == SmileConstants.END_OF_STRING) {
                return i - currentIndex - 1;
            }
        }
        throw createException("Could not determine end of string");
    }

    private char[] readStringCharArray() {
        int readableBytes = analyzeStringAndObtainLength();
        char[] result = new char[readableBytes];
        if (stringAscii) {
            //we know the currently processing string has only ASCII chars
            int bufferPosition = currentIndex + 1;
            for (int i = 0; i < readableBytes; bufferPosition++, i++) {
                result[i] = (char) this.buffer[bufferPosition];
            }
            this.currentIndex += readableBytes + stringOffset;
            return result;
        }
        int bufferPosition = currentIndex + 1;
        for (int i = 0; i < readableBytes; bufferPosition++, i++) {
            byte b = this.buffer[bufferPosition];
            if (b == '\\') {
                bufferPosition = processEscapedSequence(result, bufferPosition, i);
            } else if ((b & 0x80) == 0) {
                result[i] = (char) b;
            } else if ((b & 0xE0) == 0xC0) {
                // 2-byte UTF-8 sequence: 110xxxxx 10yyyyyy -> U+0080 to U+07FF
                if (bufferPosition + 1 >= bufferLength) {
                    throw createException("Invalid UTF-8 value");
                }
                int c2 = buffer[++bufferPosition] & 0x3F; // Second byte must be 10yyyyyy
                int codePoint = ((b & 0x1F) << 6) | c2; // Assemble code point: xxxxx yyyyyy
                result[i] = (char) codePoint;
            } else if ((b & 0xF0) == 0xE0) {
                if (bufferPosition + 2 >= bufferLength) {
                    throw createException("Invalid UTF-8 value");
                }
                // 3-byte UTF-8 sequence: 1110xxxx 10yyyyyy 10zzzzzz -> U+0800 to U+FFFF
                int c2 = buffer[++bufferPosition] & 0x3F; // Second byte: 10yyyyyy
                int c3 = buffer[++bufferPosition] & 0x3F; // Third byte: 10zzzzzz
                int codePoint = ((b & 0x0F) << 12) | (c2 << 6) | c3; // Assemble: xxxx yyyyyy zzzzzz
                result[i] = (char) codePoint;
            } else if ((b & 0xF8) == 0xF0) {
                if (bufferPosition + 3 >= bufferLength) {
                    throw createException("Invalid UTF-8 value");
                }
                // 4-byte UTF-8 sequence: 11110www 10xxxxxx 10yyyyyy 10zzzzzz -> U+10000 to U+10FFFF
                int c2 = buffer[++bufferPosition] & 0x3F; // Second byte: 10xxxxxx
                int c3 = buffer[++bufferPosition] & 0x3F; // Third byte: 10yyyyyy
                int c4 = buffer[++bufferPosition] & 0x3F; // Fourth byte: 10zzzzzz
                int codePoint = ((b & 0x07) << 18) | (c2 << 12) | (c3 << 6) | c4; // Assemble: www xxxxxx yyyyyy zzzzzz
                if (codePoint >= 0x10000) {
                    // Code point requires UTF-16 surrogates
                    if (codePoint >= 0x110000) {
                        // Beyond valid Unicode range
                        throw createException("Invalid UTF-8 code point: " + Integer.toHexString(codePoint));
                    }
                    // Convert to UTF-16 surrogate pair
                    codePoint -= 0x10000; // Subtract U+10000 to get 20-bit value
                    result[i++] = (char) ((codePoint >> 10) + 0xD800); // High surrogate: U+D800 + high 10 bits
                    result[i] = (char) ((codePoint & 0x3FF) + 0xDC00); // Low surrogate: U+DC00 + low 10 bits
                } else {
                    // Code point fits in a single char (U+0000 to U+FFFF)
                    result[i] = (char) codePoint;
                }
            } else {
                // Invalid UTF-8 leading byte
                throw createException("Invalid UTF-8 byte", b);
            }
        }
        this.currentIndex += readableBytes + stringOffset;
        return result;
    }

    private Integer obtainSharedHashReference() {
        int token = currentByte;
        if (keyExpected) {
            if (token >= SmileConstants.KEY_SHARED_SHORT_MIN && token <= SmileConstants.KEY_SHARED_SHORT_MAX) {
                if (!sharedKeysEnabled) {
                    throw createException("Shared key references are disabled by Smile header");
                }
                int ref = token - SmileConstants.KEY_SHARED_SHORT_MIN;
                return resolveSharedHashReference(sharedKeyHashes, ref, nextSharedKeyIndex - 1, "key");
            }
            if (token >= SmileConstants.KEY_SHARED_LONG_MIN && token <= SmileConstants.KEY_SHARED_LONG_MAX) {
                if (!sharedKeysEnabled) {
                    throw createException("Shared key references are disabled by Smile header");
                }
                int low = readNextByte() & 0xFF;
                if (low == 0xFE || low == 0xFF) {
                    throw createException("Invalid long shared key reference low byte: 0x" + Integer.toHexString(low));
                }
                int ref = ((token & SmileConstants.LONG_SHARED_REFERENCE_PREFIX_MASK) << 8) | low;
                if (ref <= SmileConstants.KEY_SHARED_SHORT_MAX_INDEX) {
                    throw createException("Invalid long shared key reference index (must be >= 64): " + ref);
                }
                return resolveSharedHashReference(sharedKeyHashes, ref, nextSharedKeyIndex - 1, "key");
            }
            return null;
        } else if (token >= SmileConstants.VALUE_SHARED_SHORT_MIN && token <= SmileConstants.VALUE_SHARED_SHORT_MAX) {
            if (!sharedValuesEnabled) {
                throw createException("Shared value references are disabled by Smile header");
            }
            int ref = token - SmileConstants.VALUE_SHARED_SHORT_MIN;
            return resolveSharedHashReference(sharedValueHashes, ref, nextSharedValueIndex - 1, "value");
        } else if (token >= SmileConstants.VALUE_SHARED_LONG_MIN && token <= SmileConstants.VALUE_SHARED_LONG_MAX) {
            if (!sharedValuesEnabled) {
                throw createException("Shared value references are disabled by Smile header");
            }
            int low = readNextByte() & 0xFF;
            if (low == 0xFE || low == 0xFF) {
                throw createException("Invalid long shared value reference low byte: 0x" + Integer.toHexString(low));
            }
            int ref = ((token & SmileConstants.LONG_SHARED_REFERENCE_PREFIX_MASK) << 8) | low;
            if (ref <= SmileConstants.VALUE_SHARED_SHORT_MAX_INDEX) {
                throw createException("Invalid long shared value reference index (must be >= 31): " + ref);
            }
            return resolveSharedHashReference(sharedValueHashes, ref, nextSharedValueIndex - 1, "value");
        }
        return null;
    }

    private int resolveSharedHashReference(LazyHash[] table, int ref, int tableIndex, String type) {
        if (ref > tableIndex || ref < 0) {
            throw createException("Unresolved shared " + type + " reference index: " + ref);
        }
        return table[ref].hash();
    }

    private String decodeSharedStringReference() {
        int token = currentByte;
        if (keyExpected) {
            if (token >= SmileConstants.KEY_SHARED_SHORT_MIN && token <= SmileConstants.KEY_SHARED_SHORT_MAX) {
                if (!sharedKeysEnabled) {
                    throw createException("Shared key references are disabled by Smile header");
                }
                int ref = token - SmileConstants.KEY_SHARED_SHORT_MIN;
                return resolveSharedReference(sharedKeyStrings, ref, nextSharedKeyIndex - 1, "key");
            }
            if (token >= SmileConstants.KEY_SHARED_LONG_MIN && token <= SmileConstants.KEY_SHARED_LONG_MAX) {
                if (!sharedKeysEnabled) {
                    throw createException("Shared key references are disabled by Smile header");
                }
                int low = readNextByte() & 0xFF;
                if (low == 0xFE || low == 0xFF) {
                    throw createException("Invalid long shared key reference low byte: 0x" + Integer.toHexString(low));
                }
                int ref = ((token & SmileConstants.LONG_SHARED_REFERENCE_PREFIX_MASK) << 8) | low;
                if (ref <= SmileConstants.KEY_SHARED_SHORT_MAX_INDEX) {
                    throw createException("Invalid long shared key reference index (must be >= 64): " + ref);
                }
                return resolveSharedReference(sharedKeyStrings, ref, nextSharedKeyIndex - 1, "key");
            }
            return null;
        } else if (token >= SmileConstants.VALUE_SHARED_SHORT_MIN && token <= SmileConstants.VALUE_SHARED_SHORT_MAX) {
            if (!sharedValuesEnabled) {
                throw createException("Shared value references are disabled by Smile header");
            }
            int ref = token - SmileConstants.VALUE_SHARED_SHORT_MIN;
            return resolveSharedReference(sharedValueStrings, ref, nextSharedValueIndex - 1, "value");
        } else if (token >= SmileConstants.VALUE_SHARED_LONG_MIN && token <= SmileConstants.VALUE_SHARED_LONG_MAX) {
            if (!sharedValuesEnabled) {
                throw createException("Shared value references are disabled by Smile header");
            }
            int low = readNextByte() & 0xFF;
            if (low == 0xFE || low == 0xFF) {
                throw createException("Invalid long shared value reference low byte: 0x" + Integer.toHexString(low));
            }
            int ref = ((token & SmileConstants.LONG_SHARED_REFERENCE_PREFIX_MASK) << 8) | low;
            if (ref <= SmileConstants.VALUE_SHARED_SHORT_MAX_INDEX) {
                throw createException("Invalid long shared value reference index (must be >= 31): " + ref);
            }
            return resolveSharedReference(sharedValueStrings, ref, nextSharedValueIndex - 1, "value");
        }
        return null;
    }

    private String resolveSharedReference(JsonString[] table, int ref, int currentIndex, String type) {
        if (ref > currentIndex || ref < 0) {
            throw createException("Unresolved shared " + type + " reference index: " + ref);
        }
        return table[ref].value();
    }

    private void registerKey(JsonString value, byte[] buffer, int start, int length) {
        registerKey(new LazyHash(buffer, start, length), value);
    }

    private void registerKey(LazyHash hash, JsonString jsonString) {
        if (nextSharedKeyIndex == SHARED_TABLE_SIZE_MAX) {
            Arrays.fill(sharedKeyStrings, null);
            Arrays.fill(sharedKeyHashes, null);
            nextSharedKeyIndex = 0;
        }
        if (nextSharedKeyIndex == sharedKeyStrings.length) {
            int newSize = Math.min(sharedKeyStrings.length * 2, SHARED_TABLE_SIZE_MAX);
            JsonString[] newSharedValueStrings = new JsonString[newSize];
            System.arraycopy(sharedKeyStrings, 0, newSharedValueStrings, 0, sharedKeyStrings.length);
            sharedKeyStrings = newSharedValueStrings;
            LazyHash[] newSharedValueHash = new LazyHash[newSize];
            System.arraycopy(sharedKeyHashes, 0, newSharedValueHash, 0, sharedKeyHashes.length);
            sharedKeyHashes = newSharedValueHash;
        }
        if (isAllowedSharedIndex(nextSharedKeyIndex)) {
            sharedKeyStrings[nextSharedKeyIndex] = jsonString;
            sharedKeyHashes[nextSharedKeyIndex] = hash;
        }
        nextSharedKeyIndex++;
    }

    private void registerValue(JsonString value, byte[] buffer, int start, int length) {
        registerValue(new LazyHash(buffer, start, length), value);
    }

    private void registerValue(LazyHash hash, JsonString jsonString) {
        if (nextSharedValueIndex == SHARED_TABLE_SIZE_MAX) {
            Arrays.fill(sharedValueStrings, null);
            Arrays.fill(sharedValueHashes, null);
            nextSharedValueIndex = 0;
        }
        if (nextSharedValueIndex == sharedValueStrings.length) {
            int newSize = Math.min(sharedValueStrings.length * 2, SHARED_TABLE_SIZE_MAX);
            JsonString[] newSharedValueStrings = new JsonString[newSize];
            System.arraycopy(sharedValueStrings, 0, newSharedValueStrings, 0, sharedValueStrings.length);
            sharedValueStrings = newSharedValueStrings;
            LazyHash[] newSharedValueHash = new LazyHash[newSize];
            System.arraycopy(sharedValueHashes, 0, newSharedValueHash, 0, sharedValueHashes.length);
            sharedValueHashes = newSharedValueHash;
        }
        if (isAllowedSharedIndex(nextSharedValueIndex)) {
            sharedValueStrings[nextSharedValueIndex] = jsonString;
            sharedValueHashes[nextSharedValueIndex] = hash;
        }
        nextSharedValueIndex++;
    }

    private static boolean isAllowedSharedIndex(int index) {
        int low = index & 0xFF;
        return low != SmileConstants.SHARED_INDEX_FORBIDDEN_LOW_BYTE_1 && low != SmileConstants.SHARED_INDEX_FORBIDDEN_LOW_BYTE_2;
    }

    private static int fnv1aHashUtf8(String value) {
        int hash = FNV_OFFSET_BASIS;
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        for (byte aByte : bytes) {
            hash ^= aByte & 0xFF;
            hash *= FNV_PRIME;
        }
        return hash;
    }

    private int processEscapedSequence(char[] result, int currentIndex, int resultPosition) {
        byte b = buffer[++currentIndex];
        if (expectLowSurrogate && b != 'u') {
            throw createException("Low surrogate must follow the high surrogate.", b);
        }
        switch (b) {
        case '\\':
        case '"':
        case '/':
            result[resultPosition] = (char) b;
            return currentIndex;
        case 'b':
            result[resultPosition] = '\b';
            return currentIndex;
        case 't':
            result[resultPosition] = '\t';
            return currentIndex;
        case 'n':
            result[resultPosition] = '\n';
            return currentIndex;
        case 'f':
            result[resultPosition] = '\f';
            return currentIndex;
        case 'r':
            result[resultPosition] = '\r';
            return currentIndex;
        case 'u':
            char tmp = (char) (
                    (translateHex(buffer[++currentIndex], this) << 12)
                            + (translateHex(buffer[++currentIndex], this) << 8)
                            + (translateHex(buffer[++currentIndex], this) << 4)
                            + translateHex(buffer[++currentIndex], this));
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
            result[resultPosition] = tmp;
            return currentIndex;
        default:
            throw createException("Invalid escaped value", b);
        }
    }

    private char[] readNumberAsCharArray() {
        Number number;
        int b = currentByte;
        if (b >= SmileConstants.VALUE_SMALL_INT_MIN && b <= SmileConstants.VALUE_SMALL_INT_MAX) {
            number = zigzagDecodeInt(currentByte & 0x1F);
        } else if (b == SmileConstants.TOKEN_INT32
                || b == SmileConstants.TOKEN_INT64) {
            long value = readUnsignedVInt();
            number = zigzagDecodeLong(value);
        } else if (b == SmileConstants.TOKEN_FLOAT32) {
            number = decodeFloat();
        } else if (b == SmileConstants.TOKEN_FLOAT64) {
            number = decodeDouble();
        } else {
            throw createException("Unknown number type: " + b);
        }
        return number.toString().toCharArray();
    }

    private void skipNumber() {
        int b = currentByte;
        if (b == SmileConstants.TOKEN_INT32
                || b == SmileConstants.TOKEN_INT64) {
            for (int i = currentIndex + 1; i < bufferLength; i++) {
                int tokenByte = buffer[i] & 0xFF;
                if ((tokenByte & 0x80) != 0) {
                    if ((tokenByte & 0x40) != 0) {
                        throw createException("Invalid Smile VInt final byte: 0x" + Integer.toHexString(tokenByte));
                    }
                    // Final byte: MSB=1, bit 6=0 (spec), 6 data bits in LSBs.
                    currentIndex = i;
                    checkNextAfterValue();
                    return;
                }
            }
            throw createException("Unexpected end of the number");
        } else if (b == SmileConstants.TOKEN_FLOAT32) {
            currentIndex += 5;
        } else if (b == SmileConstants.TOKEN_FLOAT64) {
            currentIndex += 10;
        } else if (b == SmileConstants.TOKEN_BIG_INT) {
            int rawLength = (int) readUnsignedVInt();
            currentIndex += encodedLength7Bit(rawLength);
        } else if (b == SmileConstants.TOKEN_BIG_DEC) {
            readUnsignedVInt();
            int rawLength = (int) readUnsignedVInt();
            currentIndex += encodedLength7Bit(rawLength);
        }
        if (currentIndex >= bufferLength) {
            throw createException("Unexpected end of the number");
        }
        checkNextAfterValue();
    }

    private void skipArray() {
        byte b = nextToken();
        if (b == Bytes.SQUARE_BRACKET_CLOSE_BYTE) {
            checkNextAfterValue();
            return;
        }
        skip(); // Skip the first array value
        b = nextToken();
        while (b == Bytes.COMMA_BYTE) {
            nextToken();
            skip();
            b = nextToken();
        }

        if (b == Bytes.SQUARE_BRACKET_CLOSE_BYTE) {
            checkNextAfterValue();
            return;
        }
        throw createException("Unexpected encountered while skipping the array", b);
    }

    /**
     * Returns the number of 7-bit-encoded bytes required to represent
     * {@code rawLen} raw bytes: ceil(rawLen * 8 / 7).
     */
    private static int encodedLength7Bit(int rawLen) {
        return (rawLen * 8 + 6) / 7;
    }

    /**
     * Decodes Smile "safe binary" data back to raw bytes.
     *
     * @param rawLen    expected number of decoded bytes (from the VInt length prefix)
     */
    private byte[] decode7Bit(int rawLen, int encodedLen) {
        byte[] out = new byte[rawLen];
        int accumulator = 0;
        int bitsHeld = 0;
        int outputIndex = 0;
        int last = currentIndex + encodedLen;
        int lastByteBits = rawLen * 8 - (encodedLen - 1) * 7;
        int shift = 7 - lastByteBits;

        for (int i = currentIndex + 1; i < bufferLength && i <= last; i++) {
            int b = buffer[i] & 0x7F;
            if (i == last) {
                b <<= shift;
            }
            accumulator = (accumulator << 7) | b;
            bitsHeld += 7;
            if (bitsHeld >= 8) {
                bitsHeld -= 8;
                out[outputIndex++] = (byte) ((accumulator >> bitsHeld) & 0xFF);
            }
        }
        if (outputIndex == rawLen) {
            currentIndex += encodedLen;
            return out;
        }
        throw createException("Unexpected end of the JSON");
    }

    private void validateStructureClose(byte token) {
        if (stackDepth == 0) {
            throw createException("Unexpected structure close token: " + (char) token);
        }
        if (token == Bytes.BRACE_CLOSE_BYTE && !inObject) {
            throw createException("Unexpected end-object token while not in an object");
        }
        if (token == Bytes.SQUARE_BRACKET_CLOSE_BYTE && inObject) {
            throw createException("Unexpected end-array token while in an object");
        }
    }

    private void validateSmileString(byte[] source, int start, int length, boolean asciiToken) {
        if (asciiToken) {
            for (int i = 0; i < length; i++) {
                int b = source[start + i] & 0xFF;
                if ((b & 0x80) != 0) {
                    throw createException("Invalid non-ASCII byte in Smile ASCII string: 0x" + Integer.toHexString(b));
                }
            }
            return;
        }
        decodeSmileString(source, start, length, false);
    }

    private String decodeSmileString(byte[] source, int start, int length, boolean asciiToken) {
        if (asciiToken) {
            validateSmileString(source, start, length, true);
            return new String(source, start, length, StandardCharsets.US_ASCII);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(source, start, length))
                    .toString();
        } catch (CharacterCodingException e) {
            throw createException("Invalid UTF-8 sequence in Smile string", e);
        }
    }

    static final class LazyHash {
        private static final byte[] EMPTY = new byte[0];

        private final byte[] buffer;
        private final int start;
        private final int length;
        private int hash;
        private boolean resolved = false;

        LazyHash(byte[] buffer, int start, int length) {
            this.buffer = buffer;
            this.start = start;
            this.length = length;
        }

        LazyHash(int hash) {
            this.buffer = EMPTY;
            this.start = -1;
            this.length = -1;
            this.hash = hash;
            this.resolved = true;
        }

        int hash() {
            if (resolved) {
                return hash;
            }
            int fnv1aHash = FNV_OFFSET_BASIS;
            int bufferIndex = start;
            for (int i = 0; i < length; i++, bufferIndex++) {
                int b = buffer[bufferIndex] & 0xFF;
                fnv1aHash ^= b;
                fnv1aHash *= FNV_PRIME;
            }
            resolved = true;
            hash = fnv1aHash;
            return fnv1aHash;
        }

    }

}
