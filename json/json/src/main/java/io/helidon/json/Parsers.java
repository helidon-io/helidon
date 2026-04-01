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

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Utility methods shared by JSON parser implementations.
 */
public final class Parsers {

    //Lookup table for converting hexadecimal characters to their numeric values
    private static final int[] HEX_DIGITS = new int[256];

    static {
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

    private Parsers() {
    }

    /**
     * Translates a hexadecimal byte into its numeric value.
     *
     * @param b the hexadecimal byte to translate
     * @param parser the parser used to create an exception when the byte is invalid
     * @return the numeric value of the hexadecimal digit
     */
    public static int translateHex(byte b, JsonParser parser) {
        int val = HEX_DIGITS[b & 0xFF];
        if (val == -1) {
            throw parser.createException("Invalid hex digit found", b);
        }
        return val;
    }

    /**
     * Converts a byte value into a printable representation for diagnostics.
     *
     * @param c the byte value to convert
     * @return a printable representation of the byte
     */
    public static String toPrintableForm(byte c) {
        return toPrintableForm((char) c);
    }

    /**
     * Converts a character value into a printable representation for diagnostics.
     *
     * @param c the character value to convert
     * @return a printable representation of the character
     */
    public static String toPrintableForm(char c) {
        if (Character.isDigit(c) || Character.isAlphabetic(c)) {
            return "'" + c + "'";
        }
        return "0x" + hex(c);
    }

    private static String hex(char c) {
        String hexString = Integer.toHexString(c);
        if (hexString.length() == 1) {
            return "0" + hexString;
        }
        return hexString;
    }

    static int translateHex(byte b) {
        int val = HEX_DIGITS[b & 0xFF];
        if (val == -1) {
            throw new JsonException("Invalid hex digit found");
        }
        return val;
    }

    static boolean isControlCharacter(byte b) {
        return b >= 0 && b < 0x20;
    }

    static int requireUtf8Continuation(byte b, JsonParser parser) {
        int value = b & 0xFF;
        if ((value & 0xC0) != 0x80) {
            throw parser.createException("Invalid UTF-8 continuation byte", b);
        }
        return value & 0x3F;
    }

    static int decodeUtf8TwoByte(byte firstByte, byte secondByte, JsonParser parser) {
        int codePoint = ((firstByte & 0x1F) << 6)
                | requireUtf8Continuation(secondByte, parser);
        if (codePoint < 0x80) {
            throw parser.createException("Overlong UTF-8 sequence", firstByte);
        }
        return codePoint;
    }

    static int decodeUtf8ThreeByte(byte firstByte, byte secondByte, byte thirdByte, JsonParser parser) {
        int codePoint = ((firstByte & 0x0F) << 12)
                | (requireUtf8Continuation(secondByte, parser) << 6)
                | requireUtf8Continuation(thirdByte, parser);
        if (codePoint < 0x800) {
            throw parser.createException("Overlong UTF-8 sequence", firstByte);
        }
        if (Character.isSurrogate((char) codePoint)) {
            throw parser.createException("UTF-8 surrogate code points are not allowed", firstByte);
        }
        return codePoint;
    }

    static int decodeUtf8FourByte(byte firstByte, byte secondByte, byte thirdByte, byte fourthByte, JsonParser parser) {
        int codePoint = ((firstByte & 0x07) << 18)
                | (requireUtf8Continuation(secondByte, parser) << 12)
                | (requireUtf8Continuation(thirdByte, parser) << 6)
                | requireUtf8Continuation(fourthByte, parser);
        if (codePoint < 0x10000) {
            throw parser.createException("Overlong UTF-8 sequence", firstByte);
        }
        if (codePoint > 0x10FFFF) {
            throw parser.createException("Invalid UTF-8 code point", firstByte);
        }
        return codePoint;
    }

    static String decodeJsonString(byte[] buffer, int start, int length) {
        int end = start + length;
        for (int i = start; i < end; i++) {
            byte b = buffer[i];
            if (b == '\\') {
                return decodeEscapedJsonString(buffer, start, end);
            }
            if (isControlCharacter(b)) {
                throw new JsonException("Unescaped control character in JSON string");
            }
        }
        return decodeUtf8Strict(buffer, start, length);
    }

    private static String decodeEscapedJsonString(byte[] buffer, int start, int end) {
        StringBuilder builder = new StringBuilder(end - start);
        int segmentStart = start;
        boolean expectLowSurrogate = false;

        for (int i = start; i < end; i++) {
            byte current = buffer[i];
            if (current == '\\') {
                if (segmentStart < i) {
                    if (expectLowSurrogate) {
                        throw new JsonException("Low surrogate must follow the high surrogate.");
                    }
                    builder.append(decodeUtf8Strict(buffer, segmentStart, i - segmentStart));
                }
                if (++i == end) {
                    throw new JsonException("Incomplete escaped JSON string");
                }
                byte escaped = buffer[i];
                if (expectLowSurrogate && escaped != 'u') {
                    throw new JsonException("Low surrogate must follow the high surrogate.");
                }
                char decoded = switch (escaped) {
                    case '\\', '"', '/' -> (char) escaped;
                    case 'b' -> '\b';
                    case 'f' -> '\f';
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'u' -> {
                        if (i + 4 >= end) {
                            throw new JsonException("Incomplete escaped JSON string");
                        }
                        char ch = (char) (
                                (translateHex(buffer[++i]) << 12)
                                        + (translateHex(buffer[++i]) << 8)
                                        + (translateHex(buffer[++i]) << 4)
                                        + translateHex(buffer[++i]));
                        if (Character.isHighSurrogate(ch)) {
                            if (expectLowSurrogate) {
                                throw new JsonException("A high surrogate must always be followed by a low surrogate");
                            }
                            expectLowSurrogate = true;
                        } else if (Character.isLowSurrogate(ch)) {
                            if (!expectLowSurrogate) {
                                throw new JsonException("A low surrogate must always follow a high surrogate");
                            }
                            expectLowSurrogate = false;
                        } else if (expectLowSurrogate) {
                            throw new JsonException("Low surrogate must follow the high surrogate.");
                        }
                        yield ch;
                    }
                    default -> throw new JsonException("Invalid escaped value");
                };
                builder.append(decoded);
                segmentStart = i + 1;
                continue;
            }
            if (isControlCharacter(current)) {
                throw new JsonException("Unescaped control character in JSON string");
            }
        }

        if (segmentStart < end) {
            if (expectLowSurrogate) {
                throw new JsonException("Low surrogate must follow the high surrogate.");
            }
            builder.append(decodeUtf8Strict(buffer, segmentStart, end - segmentStart));
        } else if (expectLowSurrogate) {
            throw new JsonException("Low surrogate must follow the high surrogate.");
        }

        return builder.toString();
    }

    private static String decodeUtf8Strict(byte[] buffer, int start, int length) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(buffer, start, length))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new JsonException("Invalid UTF-8 sequence in JSON string", e);
        }
    }

}
