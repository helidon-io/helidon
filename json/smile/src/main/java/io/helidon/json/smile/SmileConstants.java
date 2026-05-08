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

/**
 * Smile format constants used by parser and generator implementations.
 */
class SmileConstants {

    // -------------------------------------------------------------------------
    // Header constants  (spec §"High-level format")
    // -------------------------------------------------------------------------

    /** Byte 0 of the 4-byte Smile header: ASCII ':'. */
    static final byte HEADER_0 = 0x3A;
    /** Byte 1 of the 4-byte Smile header: ASCII ')'. */
    static final byte HEADER_1 = 0x29;
    /** Byte 2 of the 4-byte Smile header: ASCII LF '\n'. */
    static final byte HEADER_2 = 0x0A;
    /** Header feature bit 0: shared key names. */
    static final byte HEADER_FEATURE_SHARED_KEYS = 0x01;
    /** Header feature bit 1: shared string values. */
    static final byte HEADER_FEATURE_SHARED_VALUES = 0x02;
    /** Header feature bit 2: raw binary data may be present. */
    static final byte HEADER_FEATURE_RAW_BINARY = 0x04;


    /** Header byte sequence length. */
    static final int HEADER_LENGTH = 4;

    /** Value-mode short/tiny string maximum shareable UTF-8 byte length. */
    static final int SHARED_STRING_VALUES_MAX_BYTES = 64;

    /** Short shared value references use indexes 0-30. */
    static final int VALUE_SHARED_SHORT_MAX_INDEX = 30;

    /** Short shared key references use indexes 0-63. */
    static final int KEY_SHARED_SHORT_MAX_INDEX = 63;


    // -------------------------------------------------------------------------
    // Structural token bytes  (spec §"Token class: Misc")
    // -------------------------------------------------------------------------

    static final byte TOKEN_START_ARRAY = (byte) 0xF8;
    static final byte TOKEN_END_ARRAY = (byte) 0xF9;
    static final byte TOKEN_START_OBJECT = (byte) 0xFA;
    /**
     * END_OBJECT is the {@code 0xFB} byte; in Smile it appears in <em>key mode</em>
     * (i.e., where the next token would otherwise be a property-name token).
     */
    static final byte TOKEN_END_OBJECT = (byte) 0xFB;

    // -------------------------------------------------------------------------
    // Simple literal token bytes  (spec §"Simple literals, numbers")
    // -------------------------------------------------------------------------

    static final byte TOKEN_EMPTY_STRING = 0x20;
    static final byte TOKEN_NULL = 0x21;
    static final byte TOKEN_FALSE = 0x22;
    static final byte TOKEN_TRUE = 0x23;

    // -------------------------------------------------------------------------
    // Numeric token prefix bytes
    // -------------------------------------------------------------------------

    static final byte TOKEN_INT32 = 0x24;
    static final byte TOKEN_INT64 = 0x25;
    static final byte TOKEN_BIG_INT = 0x26;
    static final byte TOKEN_FLOAT32 = 0x28;
    static final byte TOKEN_FLOAT64 = 0x29;
    static final byte TOKEN_BIG_DEC = 0x2A;

    // -------------------------------------------------------------------------
    // Value-mode string tokens and ranges
    // -------------------------------------------------------------------------

    /** Value-mode Tiny ASCII prefix: lengths 1-32. Token = prefix | (len - 1). */
    static final int VALUE_TINY_ASCII_PREFIX = 0x40;
    static final int VALUE_TINY_ASCII_MIN = 0x40;
    static final int VALUE_TINY_ASCII_MAX = 0x5F;
    static final int VALUE_STRING_LENGTH_MASK = 0x1F;
    static final int VALUE_TINY_ASCII_LENGTH_ADD = 1;

    /** Value-mode Short ASCII prefix: lengths 33-64. Token = prefix | (len - 33). */
    static final int VALUE_SHORT_ASCII_PREFIX = 0x60;
    static final int VALUE_SHORT_ASCII_MIN = 0x60;
    static final int VALUE_SHORT_ASCII_MAX = 0x7F;
    static final int VALUE_SHORT_ASCII_LENGTH_ADD = 33;

    /** Value-mode Tiny Unicode prefix: byte-lengths 2-33. Token = prefix | (byteLen - 2). */
    static final int VALUE_TINY_UNICODE_PREFIX = 0x80;
    static final int VALUE_TINY_UNICODE_MIN = 0x80;
    static final int VALUE_TINY_UNICODE_MAX = 0x9F;
    static final int VALUE_TINY_UNICODE_LENGTH_ADD = 2;

    /** Value-mode Short Unicode prefix: byte-lengths 34-65. Token = prefix | (byteLen - 34). */
    static final int VALUE_SHORT_UNICODE_PREFIX = 0xA0;
    static final int VALUE_SHORT_UNICODE_MIN = 0xA0;
    static final int VALUE_SHORT_UNICODE_MAX = 0xBF;
    static final int VALUE_SHORT_UNICODE_LENGTH_ADD = 34;

    /** Long (variable-length) ASCII text: token 0xE0, followed by raw bytes + 0xFC. */
    static final byte VALUE_LONG_ASCII = (byte) 0xE0;
    /** Long (variable-length) Unicode text: token 0xE4, followed by raw bytes + 0xFC. */
    static final byte VALUE_LONG_UNICODE = (byte) 0xE4;

    // -------------------------------------------------------------------------
    // Shared value reference tokens
    // -------------------------------------------------------------------------

    static final int VALUE_SHARED_SHORT_MIN = 0x01;
    static final int VALUE_SHARED_SHORT_MAX = 0x1F;
    static final int VALUE_SHARED_LONG_MIN = 0xEC;
    static final int VALUE_SHARED_LONG_MAX = 0xEF;
    static final int LONG_SHARED_REFERENCE_PREFIX_MASK = 0x03;

    // -------------------------------------------------------------------------
    // Small integers
    // -------------------------------------------------------------------------

    static final int VALUE_SMALL_INT_MIN = 0xC0;
    static final int VALUE_SMALL_INT_MAX = 0xDF;

    // -------------------------------------------------------------------------
    // Key-mode name tokens and ranges
    // -------------------------------------------------------------------------

    /** Key-mode Long Unicode token (0x34): raw bytes followed by end-of-string marker. */
    static final byte KEY_LONG_UNICODE = 0x34;

    /** Key-mode Short shared references: names 0-63. */
    static final int KEY_SHARED_SHORT_MIN = 0x40;
    static final int KEY_SHARED_SHORT_MAX = 0x7F;

    /** Key-mode Short ASCII: byte-lengths 1-64. Token = 0x80 | (len - 1). */
    static final int KEY_SHORT_ASCII_PREFIX = 0x80;
    static final int KEY_SHORT_ASCII_MIN = 0x80;
    static final int KEY_SHORT_ASCII_MAX = 0xBF;
    static final int KEY_STRING_LENGTH_MASK = 0x3F;
    static final int KEY_SHORT_ASCII_LENGTH_ADD = 1;

    /** Key-mode Short Unicode: byte-lengths 2-57. Token = 0xC0 | (byteLen - 2). */
    static final int KEY_SHORT_UNICODE_PREFIX = 0xC0;
    static final int KEY_SHORT_UNICODE_MIN = 0xC0;
    static final int KEY_SHORT_UNICODE_MAX = 0xF7;
    static final int KEY_SHORT_UNICODE_LENGTH_ADD = 2;

    /** Key-mode Long shared references: 2-byte token, indexes 64-1023. */
    static final int KEY_SHARED_LONG_MIN = 0x30;
    static final int KEY_SHARED_LONG_MAX = 0x33;

    /** End-of-string marker used for variable-length long strings. */
    static final byte END_OF_STRING = (byte) 0xFC;

    static final int SHARED_INDEX_FORBIDDEN_LOW_BYTE_1 = 0xFE;
    static final int SHARED_INDEX_FORBIDDEN_LOW_BYTE_2 = 0xFF;

    /** Binary tokens. */
    static final byte TOKEN_BINARY_7BIT = (byte) 0xE8;
    static final byte TOKEN_BINARY_RAW = (byte) 0xFD;

    /** Optional end-of-content marker. */
    static final byte END_OF_CONTENT = (byte) 0xFF;

    private SmileConstants() {
    }

}
