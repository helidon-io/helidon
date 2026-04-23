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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import io.helidon.common.buffers.Bytes;
import io.helidon.json.JsonArray;
import io.helidon.json.JsonBoolean;
import io.helidon.json.JsonException;
import io.helidon.json.JsonNull;
import io.helidon.json.JsonNumber;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParserBase;
import io.helidon.json.JsonString;
import io.helidon.json.JsonValue;
import io.helidon.json.Parsers;

import static io.helidon.json.Parsers.translateHex;

/**
 * Streaming Smile-format parser that reads from an {@link InputStream} using an internal
 * sliding byte buffer.
 *
 * <p>The buffering strategy is adapted from {@code JsonParserStream}: unprocessed bytes are
 * compacted to the beginning of the buffer when more data is needed. The mark/reset facility
 * preserves bytes from the marked position so that the caller can backtrack.
 *
 * <p>All Smile decoding logic (token lookup, VInt / ZigZag / float / double / 7-bit-binary
 * decoding, shared-string tables, etc.) mirrors {@code SmileParser} exactly. The sole
 * structural difference is that every operation that would access {@code buffer[i]} directly
 * in the array-based parser instead goes through {@link #readNextByte()} or
 * {@link #ensureBytes(int)} so that the parser can transparently cross buffer boundaries.
 */
final class SmileInputStreamParser extends JsonParserBase {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    static final int DEFAULT_BUFFER_SIZE = 512;

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

    /**
     * Translates a raw Smile token byte into the JSON-like byte that the rest of the
     * parsing infrastructure (base class, skip helpers, etc.) uses to dispatch on.
     * Identical mapping to the one in {@code SmileParser}.
     */
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

    // -----------------------------------------------------------------------
    // Buffer / stream state
    // -----------------------------------------------------------------------

    private final int configuredBufferSize;
    private final InputStream inputStream;
    private byte[] buffer;
    /** Index of the last byte that was *consumed* (i.e. handed to the caller or used internally).
     *  Starts at {@code -1} so that the first {@link #readNextByte()} call returns {@code buffer[0]}. */
    private int currentIndex;
    private int bufferLength;
    private boolean finished;

    // -----------------------------------------------------------------------
    // Parser state  (mirrors SmileParser)
    // -----------------------------------------------------------------------

    private boolean inObject = false;
    private boolean keyExpected = false;
    private byte forcedEvent = -1;

    private final boolean[] structureStack = new boolean[64];
    private int stackDepth = 0;

    /** The JSON-like translation of the current Smile token (e.g. '{', '[', '"', '1', 't', …). */
    private byte currentToken;
    /** The raw Smile byte of the current token (needed for actual decoding). */
    private int currentByte;
    private boolean endOfContent = false;

    // String-parsing state (reset by analyzeStringAndObtainLength)
    private int stringOffset = 0;
    private boolean stringAscii = false;
    private boolean shareable = false;
    private boolean expectLowSurrogate = false;

    // -----------------------------------------------------------------------
    // Mark / reset state
    // -----------------------------------------------------------------------

    private int mark = -1;
    private boolean replayMarked = false;
    private byte markToken = -1;
    private int markRawByte = -1;
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

    // -----------------------------------------------------------------------
    // Shared-string tables  (mirrors SmileParser; bytes are always copied on
    // registration to prevent stale references after buffer compaction)
    // -----------------------------------------------------------------------

    private JsonString[] sharedKeyStrings = EMPTY;
    private JsonString[] sharedValueStrings = EMPTY;
    private LazyHash[] sharedKeyHashes = EMPTY_HASH;
    private LazyHash[] sharedValueHashes = EMPTY_HASH;
    private int nextSharedValueIndex;
    private int nextSharedKeyIndex;

    private boolean sharedValuesEnabled;
    private boolean sharedKeysEnabled = true;
    private boolean rawBinaryEnabled;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    SmileInputStreamParser(InputStream inputStream, int bufferSize) {
        this.configuredBufferSize = bufferSize;
        this.inputStream = inputStream;
        this.buffer = new byte[bufferSize];
        this.currentIndex = 0;
        try {
            int read = inputStream.read(buffer);
            this.bufferLength = (read == -1) ? 0 : read;
            this.finished = (read == -1);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read initial Smile data into buffer", e);
        }
    }

    // -----------------------------------------------------------------------
    // Header initialization (identical logic to SmileParser.initializeFromHeaderOrDefaults)
    // -----------------------------------------------------------------------

    /**
     * Reads and validates the 4-byte Smile header, extracts feature flags, and primes
     * the parser by calling {@link #nextToken()} so that the caller can immediately
     * inspect the first token.
     *
     * <pre>
     *   Byte 0: 0x3A (':')
     *   Byte 1: 0x29 (')')
     *   Byte 2: 0x0A (LF)
     *   Byte 3: feature flags
     *             bits 7-4 : version  (must be 0x0)
     *             bit  3   : reserved
     *             bit  2   : raw binary present
     *             bit  1   : shared value strings
     *             bit  0   : shared key names
     * </pre>
     */
    void initializeFromHeaderOrDefaults() {
        if (bufferLength == 0) {
            throw createException("Unexpected end of the binary JSON found");
        }

        int b0 = buffer[currentIndex] & 0xFF;
        if (b0 != (SmileConstants.HEADER_0 & 0xFF)) {
            initializeHeaderlessDefaults();
            currentIndex = -1;
            nextToken();
            return;
        }

        ensureHeaderBytes(3);
        if (bufferLength < 3) {
            throw createException("Unexpected end of Smile header");
        }

        int b1 = buffer[1] & 0xFF;
        int b2 = buffer[2] & 0xFF;
        if (b1 != (SmileConstants.HEADER_1 & 0xFF)
                || b2 != (SmileConstants.HEADER_2 & 0xFF)) {
            throw createException("Invalid Smile header: expected 0x3A 0x29 0x0A, got"
                                          + " 0x" + Integer.toHexString(b0)
                                          + " 0x" + Integer.toHexString(b1)
                                          + " 0x" + Integer.toHexString(b2));
        }

        ensureHeaderBytes(4);
        if (bufferLength < 4) {
            throw createException("Unexpected end of Smile header");
        }

        currentIndex = 3;
        applyHeaderFeatures(buffer[currentIndex] & 0xFF);
        nextToken();
    }

    // -----------------------------------------------------------------------
    // Core buffer / streaming primitives
    // -----------------------------------------------------------------------

    /**
     * Advances {@link #currentIndex} by one and returns the byte at the new position.
     * If the buffer is exhausted the parser fetches more data from the underlying stream
     * before advancing.
     *
     * @return the next raw byte from the Smile stream
     * @throws JsonException if the stream ends unexpectedly
     */
    private byte readNextByte() {
        if (currentIndex + 1 >= bufferLength) {
            if (finished) {
                throw createException("Unexpected end of the binary JSON found");
            }
            readMoreData();
        }
        return buffer[++currentIndex];
    }

    /**
     * Ensures that at least {@code count} bytes are available <em>after</em>
     * {@link #currentIndex} (i.e. {@code buffer[currentIndex+1 .. currentIndex+count]}
     * are all valid).
     *
     * <p>First the buffer is compacted (unneeded prefix bytes are discarded) and, if
     * necessary, grown so that it can physically hold {@code count} bytes past
     * {@code currentIndex}. Then the stream is read in a loop until the required number
     * of bytes have actually arrived.
     *
     * @param count number of bytes required after the current position
     */
    private void ensureBytes(int count) {
        if (currentIndex + count < bufferLength) {
            return; // already satisfied
        } else if (finished) {
            throw createException("Unexpected end of the binary JSON found");
        }
        try {
            // 1. Compact: shift unneeded prefix out so the required bytes can fit.
            int preserveFrom;
            if (replayMarked && mark >= 0 && mark <= currentIndex) {
                preserveFrom = mark;
            } else {
                preserveFrom = currentIndex;
            }

            int shift = preserveFrom;
            int kept = bufferLength - preserveFrom;

            if (shift > 0 && kept > 0) {
                System.arraycopy(buffer, preserveFrom, buffer, 0, kept);
            }
            bufferLength = kept;
            currentIndex -= shift;
            if (mark >= 0) {
                mark = Math.max(0, mark - shift);
            }

            // 2. Grow the backing array if it still cannot hold currentIndex + count + 1 bytes.
            int required = currentIndex + count + 1;
            if (required > bufferLength) {
                // Round up to the nearest configuredBufferSize multiple to avoid many small grows.
                int newSize = buffer.length;
                while (newSize < required) {
                    newSize += configuredBufferSize;
                }
                byte[] newBuffer = new byte[newSize];
                System.arraycopy(buffer, 0, newBuffer, 0, kept);
                buffer = newBuffer;
            }

            // 3. Read from the stream until we have enough bytes or EOF.
            int lastRead = inputStream.read(buffer, kept, buffer.length - kept);
            if (lastRead == -1) {
                finished = true;
                throw createException("Unexpected end of the binary JSON found");
            }
            bufferLength = kept + lastRead;
        } catch (IOException e) {
            throw new JsonException("Failed to read more Smile data from stream", e);
        }
    }

    /**
     * Checks whether at least one more byte is available after the current position.
     * Fetches data from the stream when the buffer is exhausted.
     */
    @Override
    public boolean hasNext() {
        if (endOfContent) {
            return false;
        }
        if (currentIndex + 1 >= bufferLength && !finished) {
            readMoreData();
        }
        return currentIndex + 1 < bufferLength;
    }

    /**
     * Compacts the buffer by preserving bytes starting from the mark (when active) or
     * from {@link #currentIndex}, then reads more data from the stream into the freed
     * space. Expands the buffer array if the remaining / needed data does not fit.
     */
    private void readMoreData() {
        try {
            // Determine the earliest position we still need to keep.
            int preserveFrom;
            if (replayMarked && mark >= 0 && mark <= currentIndex) {
                preserveFrom = mark;
            } else {
                preserveFrom = currentIndex;
            }

            int shift = preserveFrom;
            int kept = bufferLength - preserveFrom;

            if (shift > 0 && kept > 0) {
                System.arraycopy(buffer, preserveFrom, buffer, 0, kept);
            }

            // Adjust tracked indices.
            currentIndex -= shift;
            if (mark >= 0) {
                mark = Math.max(0, mark - shift);
            }

            // Expand the backing array if there is no room to read into.
            if (kept >= buffer.length) {
                byte[] newBuffer = new byte[buffer.length + configuredBufferSize];
                System.arraycopy(buffer, 0, newBuffer, 0, kept);
                buffer = newBuffer;
            }

            int lastRead = inputStream.read(buffer, kept, buffer.length - kept);
            if (lastRead == -1) {
                finished = true;
                bufferLength = kept;
            } else {
                bufferLength = kept + lastRead;
                finished = false;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read more Smile data from stream", e);
        }
    }

    // -----------------------------------------------------------------------
    // Token navigation  (mirrors SmileParser.nextToken / currentByte)
    // -----------------------------------------------------------------------

    @Override
    public byte nextToken() {
        // Return a previously scheduled synthetic event (comma / colon) without
        // consuming a real byte.
        byte toThrow = forcedEvent;
        if (toThrow != -1) {
            this.forcedEvent = -1;
            this.currentToken = toThrow;
            this.currentByte = toThrow & 0xFF;
            return toThrow;
        }
        if (endOfContent) {
            throw createException("End of content encountered. No more data to be parsed");
        }

        byte b = readNextByte();
        int rawByte = b & 0xFF;
        validateFeatureToken(rawByte);
        byte translatedToken = translateToken(rawByte);
        if (translatedToken == 0) {
            throw createException("Invalid Smile token 0x" + Integer.toHexString(rawByte)
                                          + (keyExpected ? " in key position" : ""));
        }

        // Maintain structure stack and key/value alternation just as SmileParser does.
        if (rawByte >= (SmileConstants.TOKEN_START_ARRAY & 0xFF)
                && rawByte <= (SmileConstants.TOKEN_END_OBJECT & 0xFF)) {
            if (translatedToken == Bytes.BRACE_OPEN_BYTE) {
                structureStack[++stackDepth] = true;
                inObject = true;
            } else if (translatedToken == Bytes.SQUARE_BRACKET_OPEN_BYTE) {
                structureStack[++stackDepth] = false;
                inObject = false;
            } else if (translatedToken == Bytes.SQUARE_BRACKET_CLOSE_BYTE
                    || translatedToken == Bytes.BRACE_CLOSE_BYTE) {
                validateStructureClose(translatedToken);
                inObject = structureStack[--stackDepth];
                // If there is more content after the closing bracket, signal a comma so
                // that the base-class object/array readers see the expected separator.
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

        this.currentToken = translatedToken;
        this.currentByte = rawByte;
        return translatedToken;
    }

    @Override
    public byte currentByte() {
        return currentToken;
    }

    // -----------------------------------------------------------------------
    // High-level value readers
    // -----------------------------------------------------------------------

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
        JsonArray array = super.readJsonArray();
        checkNextAfterValue();
        return array;
    }

    // -----------------------------------------------------------------------
    // String reading
    // -----------------------------------------------------------------------

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
        int rawByte = this.currentByte;
        if (rawByte == (SmileConstants.TOKEN_EMPTY_STRING & 0xFF)) {
            if (keyExpected) {
                keyExpected = false;
                forcedEvent = Bytes.COLON_BYTE;
            } else {
                checkNextAfterValue();
            }
            return JsonString.create("");
        }

        int len = analyzeStringAndObtainLength();
        if (stringOffset == 0) {
            // Short string: data may not yet be fully in the buffer.
            ensureBytes(len);
        }
        int startIndex = this.currentIndex;

        byte[] bytes = new byte[len];
        System.arraycopy(buffer, startIndex + 1, bytes, 0, len);
        JsonString jsonString = JsonString.create(decodeSmileString(bytes, 0, bytes.length, stringAscii));
        this.currentIndex += len + stringOffset;

        if (keyExpected) {
            keyExpected = false;
            forcedEvent = Bytes.COLON_BYTE;
            if (sharedKeysEnabled && shareable) {
                registerKey(new LazyHash(bytes, 0, bytes.length), jsonString);
            }
        } else {
            if (sharedValuesEnabled && shareable) {
                registerValue(new LazyHash(bytes, 0, bytes.length), jsonString);
            }
            checkNextAfterValue();
        }
        return jsonString;
    }

    @Override
    public String readString() {
        if (checkNull()) {
            return null;
        }
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
            return shared;
        }
        int rawByte = this.currentByte;
        if (rawByte == (SmileConstants.TOKEN_EMPTY_STRING & 0xFF)) {
            if (keyExpected) {
                keyExpected = false;
                forcedEvent = Bytes.COLON_BYTE;
            } else {
                checkNextAfterValue();
            }
            return "";
        }

        int len = analyzeStringAndObtainLength();
        if (stringOffset == 0) {
            ensureBytes(len);
        }
        int startIndex = this.currentIndex;

        String result = decodeSmileString(buffer, startIndex + 1, len, stringAscii);
        this.currentIndex += len + stringOffset;

        if (keyExpected) {
            keyExpected = false;
            forcedEvent = Bytes.COLON_BYTE;
            if (sharedKeysEnabled && shareable) {
                // buffer[startIndex+1..startIndex+len] is still valid: compaction only
                // happens inside readMoreData(), which is not called here.
                int fnv1aHash = FNV_OFFSET_BASIS;
                for (int i = 1; i <= len; i++) {
                    fnv1aHash ^= buffer[startIndex + i] & 0xFF;
                    fnv1aHash *= FNV_PRIME;
                }
                registerKey(new LazyHash(fnv1aHash), JsonString.create(result));
            }
        } else {
            if (sharedValuesEnabled && shareable) {
                int fnv1aHash = FNV_OFFSET_BASIS;
                for (int i = 1; i <= len; i++) {
                    fnv1aHash ^= buffer[startIndex + i] & 0xFF;
                    fnv1aHash *= FNV_PRIME;
                }
                registerValue(new LazyHash(fnv1aHash), JsonString.create(result));
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
        int rawByte = this.currentByte;
        if (rawByte == (SmileConstants.TOKEN_EMPTY_STRING & 0xFF)) {
            if (keyExpected) {
                keyExpected = false;
                forcedEvent = Bytes.COLON_BYTE;
            } else {
                checkNextAfterValue();
            }
            // FNV-1a hash of the empty string is the offset basis.
            return FNV_OFFSET_BASIS;
        }

        int len = analyzeStringAndObtainLength();
        if (stringOffset == 0) {
            ensureBytes(len);
        }
        int startIndex = this.currentIndex;

        // Compute FNV-1a hash directly over the string bytes in the buffer.
        int fnv1aHash = FNV_OFFSET_BASIS;
        for (int i = 1; i <= len; i++) {
            fnv1aHash ^= buffer[startIndex + i] & 0xFF;
            fnv1aHash *= FNV_PRIME;
        }
        validateSmileString(buffer, startIndex + 1, len, stringAscii);

        this.currentIndex += len + stringOffset;

        if (keyExpected) {
            keyExpected = false;
            forcedEvent = Bytes.COLON_BYTE;
            if (sharedKeysEnabled && shareable) {
                byte[] bytes = new byte[len];
                System.arraycopy(buffer, startIndex + 1, bytes, 0, len);
                registerKey(new LazyHash(fnv1aHash), JsonString.create(decodeSmileString(bytes, 0, bytes.length, stringAscii)));
            }
        } else {
            if (sharedValuesEnabled && shareable) {
                byte[] bytes = new byte[len];
                System.arraycopy(buffer, startIndex + 1, bytes, 0, len);
                registerValue(new LazyHash(fnv1aHash), JsonString.create(decodeSmileString(bytes, 0, bytes.length, stringAscii)));
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

    // -----------------------------------------------------------------------
    // Boolean
    // -----------------------------------------------------------------------

    @Override
    public boolean readBoolean() {
        int t = this.currentByte;
        if (t == (SmileConstants.TOKEN_TRUE & 0xFF)) {
            checkNextAfterValue();
            return true;
        } else if (t == (SmileConstants.TOKEN_FALSE & 0xFF)) {
            checkNextAfterValue();
            return false;
        }
        throw createException("Current value is not a boolean");
    }

    // -----------------------------------------------------------------------
    // Numeric reads
    // -----------------------------------------------------------------------

    @Override
    public JsonNumber readJsonNumber() {
        if (currentToken != '1') {
            throw createException("The value is not a numeric type");
        }
        JsonNumber number;
        int b = currentByte;
        if (b >= (SmileConstants.VALUE_SMALL_INT_MIN & 0xFF) && b <= (SmileConstants.VALUE_SMALL_INT_MAX & 0xFF)) {
            number = JsonNumber.create(zigzagDecodeInt(b & 0x1F));
        } else if (b == (SmileConstants.TOKEN_INT32 & 0xFF)) {
            number = JsonNumber.create(decodeInt());
        } else if (b == (SmileConstants.TOKEN_INT64 & 0xFF)) {
            number = JsonNumber.create(decodeLong());
        } else if (b == (SmileConstants.TOKEN_BIG_INT & 0xFF)) {
            number = JsonNumber.create(new BigDecimal(decodeBigInteger()));
        } else if (b == (SmileConstants.TOKEN_BIG_DEC & 0xFF)) {
            number = JsonNumber.create(decodeBigDecimal());
        } else if (b == (SmileConstants.TOKEN_FLOAT32 & 0xFF)) {
            number = JsonNumber.create(decodeFloat());
        } else if (b == (SmileConstants.TOKEN_FLOAT64 & 0xFF)) {
            number = JsonNumber.create(decodeDouble());
        } else {
            throw createException("Unsupported numeric value");
        }
        checkNextAfterValue();
        return number;
    }

    @Override
    public byte readByte() {
        if (currentToken != '1') {
            throw createException("The value is not a numeric type");
        }
        int b = currentByte;
        byte toReturn;
        if (b >= (SmileConstants.VALUE_SMALL_INT_MIN & 0xFF) && b <= (SmileConstants.VALUE_SMALL_INT_MAX & 0xFF)) {
            toReturn = (byte) zigzagDecodeInt(b & 0x1F);
        } else if (b == (SmileConstants.TOKEN_INT32 & 0xFF)) {
            int value = decodeInt();
            if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                throw createException("The number is too big for a byte: " + value);
            }
            toReturn = (byte) value;
        } else if (b == (SmileConstants.TOKEN_INT64 & 0xFF)) {
            long value = decodeLong();
            if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                throw createException("The number is too big for a byte: " + value);
            }
            toReturn = (byte) value;
        } else if (b == (SmileConstants.TOKEN_FLOAT32 & 0xFF)) {
            float value = decodeFloat();
            if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                throw createException("The number is too big for a byte: " + value);
            }
            toReturn = (byte) value;
        } else if (b == (SmileConstants.TOKEN_FLOAT64 & 0xFF)) {
            double value = decodeDouble();
            if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
                throw createException("The number is too big for a byte: " + value);
            }
            toReturn = (byte) value;
        } else if (b == (SmileConstants.TOKEN_BIG_DEC & 0xFF)) {
            BigDecimal value = decodeBigDecimal();
            if (value.compareTo(MAX_BYTE_BD) > 0 || value.compareTo(MIN_BYTE_BD) < 0) {
                throw createException("The number is too big for a byte: " + value);
            }
            toReturn = value.byteValue();
        } else if (b == (SmileConstants.TOKEN_BIG_INT & 0xFF)) {
            BigInteger value = decodeBigInteger();
            if (value.compareTo(MAX_BYTE_BI) > 0 || value.compareTo(MIN_BYTE_BI) < 0) {
                throw createException("The number is too big for a byte: " + value);
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
        if (b >= (SmileConstants.VALUE_SMALL_INT_MIN & 0xFF) && b <= (SmileConstants.VALUE_SMALL_INT_MAX & 0xFF)) {
            toReturn = (short) zigzagDecodeInt(b & 0x1F);
        } else if (b == (SmileConstants.TOKEN_INT32 & 0xFF)) {
            int value = decodeInt();
            if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                throw createException("The number is too big for a short: " + value);
            }
            toReturn = (short) value;
        } else if (b == (SmileConstants.TOKEN_INT64 & 0xFF)) {
            long value = decodeLong();
            if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                throw createException("The number is too big for a short: " + value);
            }
            toReturn = (short) value;
        } else if (b == (SmileConstants.TOKEN_FLOAT32 & 0xFF)) {
            float value = decodeFloat();
            if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                throw createException("The number is too big for a short: " + value);
            }
            toReturn = (short) value;
        } else if (b == (SmileConstants.TOKEN_FLOAT64 & 0xFF)) {
            double value = decodeDouble();
            if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                throw createException("The number is too big for a short: " + value);
            }
            toReturn = (short) value;
        } else if (b == (SmileConstants.TOKEN_BIG_DEC & 0xFF)) {
            BigDecimal value = decodeBigDecimal();
            if (value.compareTo(MAX_SHORT_BD) > 0 || value.compareTo(MIN_SHORT_BD) < 0) {
                throw createException("The number is too big for a short: " + value);
            }
            toReturn = value.shortValue();
        } else if (b == (SmileConstants.TOKEN_BIG_INT & 0xFF)) {
            BigInteger value = decodeBigInteger();
            if (value.compareTo(MAX_SHORT_BI) > 0 || value.compareTo(MIN_SHORT_BI) < 0) {
                throw createException("The number is too big for a short: " + value);
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
        if (b >= (SmileConstants.VALUE_SMALL_INT_MIN & 0xFF) && b <= (SmileConstants.VALUE_SMALL_INT_MAX & 0xFF)) {
            toReturn = zigzagDecodeInt(b & 0x1F);
        } else if (b == (SmileConstants.TOKEN_INT32 & 0xFF)) {
            toReturn = decodeInt();
        } else if (b == (SmileConstants.TOKEN_INT64 & 0xFF)) {
            long value = decodeLong();
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw createException("The number is too big for an int: " + value);
            }
            toReturn = (int) value;
        } else if (b == (SmileConstants.TOKEN_FLOAT32 & 0xFF)) {
            toReturn = (int) decodeFloat();
        } else if (b == (SmileConstants.TOKEN_FLOAT64 & 0xFF)) {
            double value = decodeDouble();
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw createException("The number is too big for an int: " + value);
            }
            toReturn = (int) value;
        } else if (b == (SmileConstants.TOKEN_BIG_DEC & 0xFF)) {
            BigDecimal value = decodeBigDecimal();
            if (value.compareTo(MAX_INT_BD) > 0 || value.compareTo(MIN_INT_BD) < 0) {
                throw createException("The number is too big for an int: " + value);
            }
            toReturn = value.intValue();
        } else if (b == (SmileConstants.TOKEN_BIG_INT & 0xFF)) {
            BigInteger value = decodeBigInteger();
            if (value.compareTo(MAX_INT_BI) > 0 || value.compareTo(MIN_INT_BI) < 0) {
                throw createException("The number is too big for an int: " + value);
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
        if (b >= (SmileConstants.VALUE_SMALL_INT_MIN & 0xFF) && b <= (SmileConstants.VALUE_SMALL_INT_MAX & 0xFF)) {
            toReturn = zigzagDecodeLong(b & 0x1F);
        } else if (b == (SmileConstants.TOKEN_INT32 & 0xFF) || b == (SmileConstants.TOKEN_INT64 & 0xFF)) {
            toReturn = decodeLong();
        } else if (b == (SmileConstants.TOKEN_FLOAT32 & 0xFF)) {
            toReturn = (long) decodeFloat();
        } else if (b == (SmileConstants.TOKEN_FLOAT64 & 0xFF)) {
            toReturn = (long) decodeDouble();
        } else if (b == (SmileConstants.TOKEN_BIG_DEC & 0xFF)) {
            BigDecimal value = decodeBigDecimal();
            if (value.compareTo(MAX_LONG_BD) > 0 || value.compareTo(MIN_LONG_BD) < 0) {
                throw createException("The number is too big for a long: " + value);
            }
            toReturn = value.longValue();
        } else if (b == (SmileConstants.TOKEN_BIG_INT & 0xFF)) {
            BigInteger value = decodeBigInteger();
            if (value.compareTo(MAX_LONG_BI) > 0 || value.compareTo(MIN_LONG_BI) < 0) {
                throw createException("The number is too big for a long: " + value);
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
        if (b == (SmileConstants.TOKEN_FLOAT32 & 0xFF)) {
            toReturn = decodeFloat();
        } else if (b == (SmileConstants.TOKEN_FLOAT64 & 0xFF)) {
            double value = decodeDouble();
            float f = (float) value;
            if (Float.isInfinite(f)) {
                throw createException("The number is too big for a float: " + value);
            }
            toReturn = f;
        } else if (b >= (SmileConstants.VALUE_SMALL_INT_MIN & 0xFF) && b <= (SmileConstants.VALUE_SMALL_INT_MAX & 0xFF)) {
            toReturn = zigzagDecodeInt(b & 0x1F);
        } else if (b == (SmileConstants.TOKEN_INT32 & 0xFF)) {
            toReturn = decodeInt();
        } else if (b == (SmileConstants.TOKEN_INT64 & 0xFF)) {
            long value = decodeLong();
            if ((long) (float) value != value) {
                throw createException("The number loses precision when converted to float: " + value);
            }
            toReturn = (float) value;
        } else if (b == (SmileConstants.TOKEN_BIG_DEC & 0xFF)) {
            BigDecimal value = decodeBigDecimal();
            toReturn = value.floatValue();
            if (Float.isInfinite(toReturn)) {
                throw createException("The number is too big for a float: " + value);
            }
        } else if (b == (SmileConstants.TOKEN_BIG_INT & 0xFF)) {
            BigInteger value = decodeBigInteger();
            toReturn = value.floatValue();
            if (Float.isInfinite(toReturn)) {
                throw createException("The number is too big for a float: " + value);
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
        if (b == (SmileConstants.TOKEN_FLOAT64 & 0xFF)) {
            toReturn = decodeDouble();
        } else if (b == (SmileConstants.TOKEN_FLOAT32 & 0xFF)) {
            toReturn = decodeFloat();
        } else if (b >= (SmileConstants.VALUE_SMALL_INT_MIN & 0xFF) && b <= (SmileConstants.VALUE_SMALL_INT_MAX & 0xFF)) {
            toReturn = zigzagDecodeInt(b & 0x1F);
        } else if (b == (SmileConstants.TOKEN_INT32 & 0xFF) || b == (SmileConstants.TOKEN_INT64 & 0xFF)) {
            toReturn = decodeLong();
        } else if (b == (SmileConstants.TOKEN_BIG_DEC & 0xFF)) {
            BigDecimal value = decodeBigDecimal();
            toReturn = value.doubleValue();
            if (Double.isInfinite(toReturn)) {
                throw createException("The number is too big for a double: " + value);
            }
        } else if (b == (SmileConstants.TOKEN_BIG_INT & 0xFF)) {
            BigInteger value = decodeBigInteger();
            toReturn = value.doubleValue();
            if (Double.isInfinite(toReturn)) {
                throw createException("The number is too big for a double: " + value);
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
        if (b == (SmileConstants.TOKEN_BIG_INT & 0xFF)) {
            toReturn = decodeBigInteger();
        } else if (b == (SmileConstants.TOKEN_INT32 & 0xFF) || b == (SmileConstants.TOKEN_INT64 & 0xFF)) {
            toReturn = BigInteger.valueOf(zigzagDecodeLong(readUnsignedVInt()));
        } else if (b >= (SmileConstants.VALUE_SMALL_INT_MIN & 0xFF) && b <= (SmileConstants.VALUE_SMALL_INT_MAX & 0xFF)) {
            toReturn = BigInteger.valueOf(zigzagDecodeInt(b & 0x1F));
        } else if (b == (SmileConstants.TOKEN_BIG_DEC & 0xFF)) {
            toReturn = decodeBigDecimal().toBigInteger();
        } else if (b == (SmileConstants.TOKEN_FLOAT64 & 0xFF)) {
            toReturn = BigInteger.valueOf((long) decodeDouble());
        } else if (b == (SmileConstants.TOKEN_FLOAT32 & 0xFF)) {
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
        if (b == (SmileConstants.TOKEN_BIG_DEC & 0xFF)) {
            toReturn = decodeBigDecimal();
        } else if (b == (SmileConstants.TOKEN_FLOAT64 & 0xFF)) {
            toReturn = new BigDecimal(decodeDouble());
        } else if (b == (SmileConstants.TOKEN_FLOAT32 & 0xFF)) {
            toReturn = new BigDecimal(decodeFloat());
        } else if (b >= (SmileConstants.VALUE_SMALL_INT_MIN & 0xFF) && b <= (SmileConstants.VALUE_SMALL_INT_MAX & 0xFF)) {
            toReturn = new BigDecimal(zigzagDecodeInt(b & 0x1F));
        } else if (b == (SmileConstants.TOKEN_BIG_INT & 0xFF)) {
            toReturn = new BigDecimal(decodeBigInteger());
        } else if (b == (SmileConstants.TOKEN_INT32 & 0xFF) || b == (SmileConstants.TOKEN_INT64 & 0xFF)) {
            toReturn = new BigDecimal(decodeLong());
        } else {
            throw createException("Unsupported numeric value");
        }
        checkNextAfterValue();
        return toReturn;
    }

    // -----------------------------------------------------------------------
    // Binary
    // -----------------------------------------------------------------------

    @Override
    public byte[] readBinary() {
        int rawByte = this.currentByte;
        if (rawByte == (SmileConstants.TOKEN_BINARY_7BIT & 0xFF)) {
            int rawLen = (int) readUnsignedVInt();
            byte[] data = decode7Bit(rawLen, encodedLength7Bit(rawLen));
            checkNextAfterValue();
            return data;
        } else if (rawByte == (SmileConstants.TOKEN_BINARY_RAW & 0xFF)) {
            if (!rawBinaryEnabled) {
                throw createException("Raw binary not enabled in Smile header");
            }
            int rawLen = (int) readUnsignedVInt();
            ensureBytes(rawLen);
            byte[] bytes = new byte[rawLen];
            System.arraycopy(buffer, currentIndex + 1, bytes, 0, rawLen);
            currentIndex += rawLen;
            checkNextAfterValue();
            return bytes;
        }
        throw createException("Current token is not a binary value");
    }

    // -----------------------------------------------------------------------
    // Null check
    // -----------------------------------------------------------------------

    @Override
    public boolean checkNull() {
        if (this.currentByte == (SmileConstants.TOKEN_NULL & 0xFF)) {
            checkNextAfterValue();
            return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Skip
    // -----------------------------------------------------------------------

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
            // Synthetic events — nothing to consume.
            break;
        default:
            throw createException("Invalid JSON value to skip", currentByte());
        }
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
        throw createException("Comma or end of object expected", b);
    }

    private void skipArray() {
        byte b = nextToken();
        if (b == ']') {
            checkNextAfterValue();
            return;
        }
        skip();
        b = nextToken();
        while (b == Bytes.COMMA_BYTE) {
            nextToken();
            skip();
            b = nextToken();
        }
        if (b == ']') {
            checkNextAfterValue();
            return;
        }
        throw createException("Unexpected token encountered while skipping array", b);
    }

    /**
     * Skips a string value at the current position, handling shared references, empty
     * strings, short inline strings and long (terminated) strings. Registers the string
     * in the shared-name / shared-value table when applicable — consistent with
     * {@code SmileParser.skipString()}.
     */
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
        int rawByte = this.currentByte;
        if (rawByte == (SmileConstants.TOKEN_EMPTY_STRING & 0xFF)) {
            if (keyExpected) {
                keyExpected = false;
                forcedEvent = Bytes.COLON_BYTE;
            } else {
                checkNextAfterValue();
            }
            return;
        }

        int len = analyzeStringAndObtainLength();
        if (stringOffset == 0) {
            ensureBytes(len);
        }
        int startIndex = this.currentIndex;
        String decoded = null;
        if (keyExpected) {
            if (sharedKeysEnabled && shareable) {
                decoded = decodeSmileString(buffer, startIndex + 1, len, stringAscii);
            } else {
                validateSmileString(buffer, startIndex + 1, len, stringAscii);
            }
        } else if (sharedValuesEnabled && shareable) {
            decoded = decodeSmileString(buffer, startIndex + 1, len, stringAscii);
        } else {
            validateSmileString(buffer, startIndex + 1, len, stringAscii);
        }
        this.currentIndex += len + stringOffset;

        if (keyExpected) {
            keyExpected = false;
            forcedEvent = Bytes.COLON_BYTE;
            if (sharedKeysEnabled && shareable) {
                int fnv1aHash = FNV_OFFSET_BASIS;
                for (int i = 1; i <= len; i++) {
                    fnv1aHash ^= buffer[startIndex + i] & 0xFF;
                    fnv1aHash *= FNV_PRIME;
                }
                registerKey(new LazyHash(fnv1aHash), JsonString.create(decoded));
            }
        } else {
            if (sharedValuesEnabled && shareable) {
                int fnv1aHash = FNV_OFFSET_BASIS;
                for (int i = 1; i <= len; i++) {
                    fnv1aHash ^= buffer[startIndex + i] & 0xFF;
                    fnv1aHash *= FNV_PRIME;
                }
                registerValue(new LazyHash(fnv1aHash), JsonString.create(decoded));
            }
            checkNextAfterValue();
        }
    }

    /**
     * Skips a numeric value at the current position.
     * Uses {@link #readNextByte()} for VInt scanning so that buffer boundaries are
     * handled transparently.
     */
    private void skipNumber() {
        int b = currentByte;
        if (b == (SmileConstants.TOKEN_INT32 & 0xFF) || b == (SmileConstants.TOKEN_INT64 & 0xFF)) {
            // VInt: consume bytes until the terminal byte (MSB == 1) is found.
            int next;
            do {
                next = readNextByte() & 0xFF;
            } while ((next & 0x80) == 0);
            if ((next & 0x40) != 0) {
                throw createException("Invalid Smile VInt final byte: 0x" + Integer.toHexString(next));
            }
        } else if (b == (SmileConstants.TOKEN_FLOAT32 & 0xFF)) {
            // 5 seven-bit bytes.
            for (int i = 0; i < 5; i++) {
                readNextByte();
            }
        } else if (b == (SmileConstants.TOKEN_FLOAT64 & 0xFF)) {
            // 10 seven-bit bytes.
            for (int i = 0; i < 10; i++) {
                readNextByte();
            }
        } else if (b == (SmileConstants.TOKEN_BIG_INT & 0xFF)) {
            int rawLength = (int) readUnsignedVInt();
            int encodedLen = encodedLength7Bit(rawLength);
            for (int i = 0; i < encodedLen; i++) {
                readNextByte();
            }
        } else if (b == (SmileConstants.TOKEN_BIG_DEC & 0xFF)) {
            readUnsignedVInt();                         // scale
            int rawLength = (int) readUnsignedVInt();
            int encodedLen = encodedLength7Bit(rawLength);
            for (int i = 0; i < encodedLen; i++) {
                readNextByte();
            }
        }
        // Small-int tokens are single bytes — nothing extra to skip.
        checkNextAfterValue();
    }

    // -----------------------------------------------------------------------
    // Mark / reset
    // -----------------------------------------------------------------------

    @Override
    public void mark() {
        if (replayMarked) {
            throw new IllegalStateException(
                    "Parser is already marked for replaying. "
                            + "Call clearMark() or resetToMark() before marking again.");
        }
        replayMarked = true;
        mark = currentIndex;
        markToken = currentToken;
        markRawByte = currentByte;
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
        replayMarked = false;
        mark = -1;
        markToken = -1;
        markRawByte = -1;
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
        if (!replayMarked) {
            throw new IllegalStateException(
                    "No mark has been set. Call mark() before resetToMark().");
        }
        finished = false;           // allow continued reading after reset
        currentIndex = mark;
        currentToken = markToken;
        currentByte = markRawByte;
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

    // -----------------------------------------------------------------------
    // Exception factory
    // -----------------------------------------------------------------------

    @Override
    public JsonException createException(String message) {
        return new JsonException(message + " (stream position ~" + currentIndex + ")");
    }

    public JsonException createException(String message, java.lang.Exception e) {
        return new JsonException(message + " (stream position ~" + currentIndex + ")", e);
    }

    // -----------------------------------------------------------------------
    // Internal helpers — post-value bookkeeping
    // -----------------------------------------------------------------------

    /**
     * After a value has been fully consumed, inspects the next byte to decide whether
     * a synthetic comma event should be scheduled (mirrors {@code SmileParser.checkNextAfterValue}).
     */
    private void checkNextAfterValue() {
        if (hasNext()) {
            keyExpected = inObject;
            int next = buffer[currentIndex + 1] & 0xFF;
            if (next == (SmileConstants.TOKEN_END_OBJECT & 0xFF)
                    || next == (SmileConstants.TOKEN_END_ARRAY & 0xFF)) {
                forcedEvent = -1;
                return;
            }
            if (next == (SmileConstants.END_OF_CONTENT & 0xFF)) {
                forcedEvent = -1;
                endOfContent = true;
                return;
            }
            forcedEvent = Bytes.COMMA_BYTE;
        }
    }

    private void ensureHeaderBytes(int totalBytes) {
        while (bufferLength < totalBytes && !finished) {
            readMoreData();
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

    // -----------------------------------------------------------------------
    // String-length analysis
    // -----------------------------------------------------------------------

    /**
     * Determines the byte-length of the string at the current position and sets
     * {@link #stringAscii}, {@link #shareable} and {@link #stringOffset} as
     * side-effects — identical contract to {@code SmileParser.analyzeStringAndObtainLength()}.
     *
     * <p>For <em>long</em> strings the method also ensures that the entire content
     * (including the {@code END_OF_STRING} terminator) is resident in the buffer
     * before returning.
     */
    private int analyzeStringAndObtainLength() {
        stringOffset = 0;
        stringAscii = false;
        shareable = false;

        if (keyExpected) {
            shareable = true;
            if (currentByte >= (SmileConstants.KEY_SHORT_ASCII_MIN & 0xFF)
                    && currentByte <= (SmileConstants.KEY_SHORT_ASCII_MAX & 0xFF)) {
                stringAscii = true;
                return (currentByte & SmileConstants.KEY_STRING_LENGTH_MASK)
                        + SmileConstants.KEY_SHORT_ASCII_LENGTH_ADD;
            } else if (currentByte >= (SmileConstants.KEY_SHORT_UNICODE_MIN & 0xFF)
                    && currentByte <= (SmileConstants.KEY_SHORT_UNICODE_MAX & 0xFF)) {
                return (currentByte & SmileConstants.KEY_STRING_LENGTH_MASK)
                        + SmileConstants.KEY_SHORT_UNICODE_LENGTH_ADD;
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
        if (currentByte >= (SmileConstants.VALUE_TINY_ASCII_MIN & 0xFF)
                && currentByte <= (SmileConstants.VALUE_TINY_ASCII_MAX & 0xFF)) {
            stringAscii = true;
            length = (currentByte & SmileConstants.VALUE_STRING_LENGTH_MASK)
                    + SmileConstants.VALUE_TINY_ASCII_LENGTH_ADD;
        } else if (currentByte >= (SmileConstants.VALUE_SHORT_ASCII_MIN & 0xFF)
                && currentByte <= (SmileConstants.VALUE_SHORT_ASCII_MAX & 0xFF)) {
            stringAscii = true;
            length = (currentByte & SmileConstants.VALUE_STRING_LENGTH_MASK)
                    + SmileConstants.VALUE_SHORT_ASCII_LENGTH_ADD;
        } else if (currentByte >= (SmileConstants.VALUE_TINY_UNICODE_MIN & 0xFF)
                && currentByte <= (SmileConstants.VALUE_TINY_UNICODE_MAX & 0xFF)) {
            length = (currentByte & SmileConstants.VALUE_STRING_LENGTH_MASK)
                    + SmileConstants.VALUE_TINY_UNICODE_LENGTH_ADD;
        } else if (currentByte >= (SmileConstants.VALUE_SHORT_UNICODE_MIN & 0xFF)
                && currentByte <= (SmileConstants.VALUE_SHORT_UNICODE_MAX & 0xFF)) {
            length = (currentByte & SmileConstants.VALUE_STRING_LENGTH_MASK)
                    + SmileConstants.VALUE_SHORT_UNICODE_LENGTH_ADD;
        } else {
            throw createException("Unsupported string token: 0x" + Integer.toHexString(currentByte));
        }
        shareable = length <= SmileConstants.SHARED_STRING_VALUES_MAX_BYTES;
        return length;
    }

    /**
     * Scans forward from {@code currentIndex + 1} in search of an
     * {@code END_OF_STRING} (0xFC) terminator, loading additional data from
     * the stream as needed and keeping all scanned bytes resident in the buffer.
     *
     * <p>When this method returns, {@code buffer[currentIndex + 1 .. currentIndex + len]}
     * contains the complete string bytes and
     * {@code buffer[currentIndex + len + 1]} is the terminator byte.
     *
     * @return number of string bytes (excluding the terminator)
     */
    private int findLongStringLength() {
        int startIndex = this.currentIndex;   // token byte position
        int i = startIndex + 1;      // first candidate content byte

        while (true) {
            // Scan the bytes currently in the buffer.
            while (i < bufferLength) {
                if ((buffer[i] & 0xFF) == (SmileConstants.END_OF_STRING & 0xFF)) {
                    return i - startIndex - 1;
                }
                i++;
            }
            if (finished) {
                throw createException("Could not find END_OF_STRING terminator: stream ended prematurely");
            }
            // Compact the buffer, keeping everything from startIndex onward.
            int shift = startIndex;
            int kept = bufferLength - startIndex;
            if (shift > 0 && kept > 0) {
                System.arraycopy(buffer, startIndex, buffer, 0, kept);
            }
            this.currentIndex -= shift;
            if (mark >= 0) {
                mark = Math.max(0, mark - shift);
            }
            i -= shift;
            startIndex = 0;

            if (kept >= buffer.length) {
                byte[] newBuffer = new byte[buffer.length + configuredBufferSize];
                System.arraycopy(buffer, 0, newBuffer, 0, kept);
                buffer = newBuffer;
            }

            try {
                int lastRead = inputStream.read(buffer, kept, buffer.length - kept);
                if (lastRead == -1) {
                    finished = true;
                    bufferLength = kept;
                } else {
                    bufferLength = kept + lastRead;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Char-array reading  (used by readChar)
    // -----------------------------------------------------------------------

    /**
     * Returns the decoded characters of the current string value as a {@code char[]}.
     * For short strings {@link #ensureBytes(int)} is called first so that all content
     * bytes are guaranteed to be in the buffer before the loop starts.
     */
    private char[] readStringCharArray() {
        int readableBytes = analyzeStringAndObtainLength();
        if (stringOffset == 0) {
            ensureBytes(readableBytes);
        }
        char[] result = new char[readableBytes];
        if (stringAscii) {
            int bufferPosition = currentIndex + 1;
            for (int i = 0; i < readableBytes; bufferPosition++, i++) {
                result[i] = (char) buffer[bufferPosition];
            }
            this.currentIndex += readableBytes + stringOffset;
            return result;
        }
        int bufferPosition = currentIndex + 1;
        for (int i = 0; i < readableBytes; bufferPosition++, i++) {
            byte b = buffer[bufferPosition];
            if (b == '\\') {
                bufferPosition = processEscapedSequence(result, bufferPosition, i);
            } else if ((b & 0x80) == 0) {
                result[i] = (char) b;
            } else if ((b & 0xE0) == 0xC0) {
                // 2-byte UTF-8
                int c2 = buffer[++bufferPosition] & 0x3F;
                result[i] = (char) (((b & 0x1F) << 6) | c2);
            } else if ((b & 0xF0) == 0xE0) {
                // 3-byte UTF-8
                int c2 = buffer[++bufferPosition] & 0x3F;
                int c3 = buffer[++bufferPosition] & 0x3F;
                result[i] = (char) (((b & 0x0F) << 12) | (c2 << 6) | c3);
            } else if ((b & 0xF8) == 0xF0) {
                // 4-byte UTF-8 → surrogate pair
                int c2 = buffer[++bufferPosition] & 0x3F;
                int c3 = buffer[++bufferPosition] & 0x3F;
                int c4 = buffer[++bufferPosition] & 0x3F;
                int codePoint = ((b & 0x07) << 18) | (c2 << 12) | (c3 << 6) | c4;
                if (codePoint >= 0x10000) {
                    if (codePoint >= 0x110000) {
                        throw createException("Invalid UTF-8 code point: 0x" + Integer.toHexString(codePoint));
                    }
                    codePoint -= 0x10000;
                    result[i++] = (char) ((codePoint >> 10) + 0xD800);
                    result[i] = (char) ((codePoint & 0x3FF) + 0xDC00);
                } else {
                    result[i] = (char) codePoint;
                }
            } else {
                throw createException("Invalid UTF-8 byte", b);
            }
        }
        this.currentIndex += readableBytes + stringOffset;
        return result;
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

    // -----------------------------------------------------------------------
    // Shared-string table lookups
    // -----------------------------------------------------------------------

    /**
     * If the current raw token is a shared-string reference, resolves it and returns
     * the stored string. Returns {@code null} when the current token is not a reference.
     * Mirrors {@code SmileParser.decodeSharedStringReference()}.
     */
    private String decodeSharedStringReference() {
        int token = currentByte;
        if (keyExpected) {
            if (token >= (SmileConstants.KEY_SHARED_SHORT_MIN & 0xFF)
                    && token <= (SmileConstants.KEY_SHARED_SHORT_MAX & 0xFF)) {
                if (!sharedKeysEnabled) {
                    throw createException("Shared key references are disabled by Smile header");
                }
                int ref = token - (SmileConstants.KEY_SHARED_SHORT_MIN & 0xFF);
                return resolveSharedReference(sharedKeyStrings, ref, nextSharedKeyIndex - 1, "key");
            }
            if (token >= (SmileConstants.KEY_SHARED_LONG_MIN & 0xFF)
                    && token <= (SmileConstants.KEY_SHARED_LONG_MAX & 0xFF)) {
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
        }
        if (token >= (SmileConstants.VALUE_SHARED_SHORT_MIN & 0xFF)
                && token <= (SmileConstants.VALUE_SHARED_SHORT_MAX & 0xFF)) {
            if (!sharedValuesEnabled) {
                throw createException("Shared value references are disabled by Smile header");
            }
            int ref = token - (SmileConstants.VALUE_SHARED_SHORT_MIN & 0xFF);
            return resolveSharedReference(sharedValueStrings, ref, nextSharedValueIndex - 1, "value");
        }
        if (token >= (SmileConstants.VALUE_SHARED_LONG_MIN & 0xFF)
                && token <= (SmileConstants.VALUE_SHARED_LONG_MAX & 0xFF)) {
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

    private String resolveSharedReference(JsonString[] table, int ref, int maxIndex, String type) {
        if (ref < 0 || ref > maxIndex) {
            throw createException("Unresolved shared " + type + " reference index: " + ref);
        }
        return table[ref].value();
    }

    /**
     * Like {@link #decodeSharedStringReference()} but returns the pre-computed hash
     * instead of the string value. Returns {@code null} when not a reference.
     * Mirrors {@code SmileParser.obtainSharedHashReference()}.
     */
    private Integer obtainSharedHashReference() {
        int token = currentByte;
        if (keyExpected) {
            if (token >= (SmileConstants.KEY_SHARED_SHORT_MIN & 0xFF)
                    && token <= (SmileConstants.KEY_SHARED_SHORT_MAX & 0xFF)) {
                if (!sharedKeysEnabled) {
                    throw createException("Shared key references are disabled by Smile header");
                }
                int ref = token - (SmileConstants.KEY_SHARED_SHORT_MIN & 0xFF);
                return resolveSharedHashReference(sharedKeyHashes, ref, nextSharedKeyIndex - 1, "key");
            }
            if (token >= (SmileConstants.KEY_SHARED_LONG_MIN & 0xFF)
                    && token <= (SmileConstants.KEY_SHARED_LONG_MAX & 0xFF)) {
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
        }
        if (token >= (SmileConstants.VALUE_SHARED_SHORT_MIN & 0xFF)
                && token <= (SmileConstants.VALUE_SHARED_SHORT_MAX & 0xFF)) {
            if (!sharedValuesEnabled) {
                throw createException("Shared value references are disabled by Smile header");
            }
            int ref = token - (SmileConstants.VALUE_SHARED_SHORT_MIN & 0xFF);
            return resolveSharedHashReference(sharedValueHashes, ref, nextSharedValueIndex - 1, "value");
        }
        if (token >= (SmileConstants.VALUE_SHARED_LONG_MIN & 0xFF)
                && token <= (SmileConstants.VALUE_SHARED_LONG_MAX & 0xFF)) {
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

    private int resolveSharedHashReference(LazyHash[] table, int ref, int maxIndex, String type) {
        if (ref < 0 || ref > maxIndex) {
            throw createException("Unresolved shared " + type + " reference index: " + ref);
        }
        return table[ref].hash();
    }

    // -----------------------------------------------------------------------
    // Shared-string table registration
    // -----------------------------------------------------------------------

    private void registerKey(LazyHash hash, JsonString jsonString) {
        if (nextSharedKeyIndex == SHARED_TABLE_SIZE_MAX) {
            Arrays.fill(sharedKeyStrings, null);
            Arrays.fill(sharedKeyHashes, null);
            nextSharedKeyIndex = 0;
        }
        if (nextSharedKeyIndex == sharedKeyStrings.length) {
            int newSize = Math.min(sharedKeyStrings.length * 2, SHARED_TABLE_SIZE_MAX);
            JsonString[] ns = new JsonString[newSize];
            System.arraycopy(sharedKeyStrings, 0, ns, 0, sharedKeyStrings.length);
            sharedKeyStrings = ns;
            LazyHash[] nh = new LazyHash[newSize];
            System.arraycopy(sharedKeyHashes, 0, nh, 0, sharedKeyHashes.length);
            sharedKeyHashes = nh;
        }
        if (isAllowedSharedIndex(nextSharedKeyIndex)) {
            sharedKeyStrings[nextSharedKeyIndex] = jsonString;
            sharedKeyHashes[nextSharedKeyIndex] = hash;
        }
        nextSharedKeyIndex++;
    }

    private void registerValue(LazyHash hash, JsonString jsonString) {
        if (nextSharedValueIndex == SHARED_TABLE_SIZE_MAX) {
            Arrays.fill(sharedValueStrings, null);
            Arrays.fill(sharedValueHashes, null);
            nextSharedValueIndex = 0;
        }
        if (nextSharedValueIndex == sharedValueStrings.length) {
            int newSize = Math.min(sharedValueStrings.length * 2, SHARED_TABLE_SIZE_MAX);
            JsonString[] ns = new JsonString[newSize];
            System.arraycopy(sharedValueStrings, 0, ns, 0, sharedValueStrings.length);
            sharedValueStrings = ns;
            LazyHash[] nh = new LazyHash[newSize];
            System.arraycopy(sharedValueHashes, 0, nh, 0, sharedValueHashes.length);
            sharedValueHashes = nh;
        }
        if (isAllowedSharedIndex(nextSharedValueIndex)) {
            sharedValueStrings[nextSharedValueIndex] = jsonString;
            sharedValueHashes[nextSharedValueIndex] = hash;
        }
        nextSharedValueIndex++;
    }

    private static boolean isAllowedSharedIndex(int index) {
        int low = index & 0xFF;
        return low != SmileConstants.SHARED_INDEX_FORBIDDEN_LOW_BYTE_1
                && low != SmileConstants.SHARED_INDEX_FORBIDDEN_LOW_BYTE_2;
    }

    // -----------------------------------------------------------------------
    // Numeric decoders  (identical to SmileParser, adapted to use readNextByte)
    // -----------------------------------------------------------------------

    /**
     * Reads a variable-length unsigned integer (Smile VInt).
     *
     * <p>Each intermediate byte contributes 7 data bits (MSB == 0). The terminal
     * byte has MSB == 1 and contributes 6 data bits.
     *
     * <p>Uses {@link #readNextByte()} so that buffer boundaries are crossed
     * transparently.
     */
    private long readUnsignedVInt() {
        long value = 0;
        while (true) {
            int b = readNextByte() & 0xFF;
            if ((b & 0x80) != 0) {
                if ((b & 0x40) != 0) {
                    throw createException("Invalid Smile VInt final byte: 0x" + Integer.toHexString(b));
                }
                // Terminal byte: MSB=1, 6 data bits.
                return (value << 6) | (b & 0x3F);
            }
            // Intermediate byte: MSB=0, 7 data bits.
            value = (value << 7) | (b & 0x7F);
        }
    }

    /**
     * Decodes a ZigZag-encoded unsigned 32-bit value back to a signed integer.
     * Formula: {@code (n >>> 1) ^ -(n & 1)}.
     */
    private static int zigzagDecodeInt(int n) {
        return (n >>> 1) ^ -(n & 1);
    }

    /**
     * Decodes a ZigZag-encoded unsigned 64-bit value back to a signed long.
     * Formula: {@code (n >>> 1) ^ -(n & 1L)}.
     */
    private static long zigzagDecodeLong(long n) {
        return (n >>> 1) ^ -(n & 1L);
    }

    private int decodeInt() {
        return zigzagDecodeInt((int) readUnsignedVInt());
    }

    private long decodeLong() {
        return zigzagDecodeLong(readUnsignedVInt());
    }

    private BigInteger decodeBigInteger() {
        int rawLength = (int) readUnsignedVInt();
        int encodedLen = encodedLength7Bit(rawLength);
        return new BigInteger(decode7Bit(rawLength, encodedLen));
    }

    private BigDecimal decodeBigDecimal() {
        int scale = zigzagDecodeInt((int) readUnsignedVInt());
        int rawLength = (int) readUnsignedVInt();
        int encodedLen = encodedLength7Bit(rawLength);
        return new BigDecimal(new BigInteger(decode7Bit(rawLength, encodedLen)), scale);
    }

    /**
     * Reads a 32-bit IEEE 754 float from 5 seven-bit bytes (big-endian Smile encoding).
     *
     * <p>Bit layout: {@code [4][7][7][7][7]}.
     * Reassembly: {@code bits = (b0 << 28) | (b1 << 21) | (b2 << 14) | (b3 << 7) | b4}.
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
     * Reads a 64-bit IEEE 754 double from 10 seven-bit bytes (big-endian Smile encoding).
     *
     * <p>Bit layout: {@code [1][7][7][7][7][7][7][7][7][7]}.
     * Reassembly: {@code bits = (b0 << 63) | (b1 << 56) | … | b9}.
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

    /**
     * Returns the number of 7-bit-encoded bytes required to represent {@code rawLen}
     * raw bytes: {@code ceil(rawLen * 8 / 7)}.
     */
    private static int encodedLength7Bit(int rawLen) {
        return (rawLen * 8 + 6) / 7;
    }

    /**
     * Decodes Smile "safe binary" (7-bit-per-byte) data back to raw bytes.
     * Reads {@code encodedLen} bytes via {@link #readNextByte()}, which handles
     * buffer boundaries transparently.
     *
     * @param rawLen     expected number of decoded bytes
     * @param encodedLen number of 7-bit-encoded source bytes
     */
    private byte[] decode7Bit(int rawLen, int encodedLen) {
        byte[] out = new byte[rawLen];
        int accumulator = 0;
        int bitsHeld = 0;
        int outputIndex = 0;
        int lastByteBits = rawLen * 8 - (encodedLen - 1) * 7;
        int shift = 7 - lastByteBits;

        for (int i = 0; i < encodedLen; i++) {
            int b = readNextByte() & 0x7F;
            if (i == encodedLen - 1) {
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
            return out;
        }
        throw createException("Unexpected end of 7-bit-encoded binary data");
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

    // -----------------------------------------------------------------------
    // LazyHash  (identical to SmileParser.LazyHash)
    // -----------------------------------------------------------------------

    /**
     * Holds either a direct reference to the byte range from which an FNV-1a hash
     * should be computed lazily, or an already-resolved hash value.
     *
     * <p>In the streaming parser the byte array passed to the constructor is always
     * a dedicated copy (never a slice of the rolling buffer), so the reference remains
     * valid indefinitely.
     */
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

        /** Constructs a pre-resolved hash (no further buffer access needed). */
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
                fnv1aHash ^= buffer[bufferIndex] & 0xFF;
                fnv1aHash *= FNV_PRIME;
            }
            resolved = true;
            hash = fnv1aHash;
            return fnv1aHash;
        }
    }
}
