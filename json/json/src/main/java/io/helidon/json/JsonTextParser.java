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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Native text parsers share the string lexer while retaining ownership of their input cursors.
 */
abstract class JsonTextParser extends JsonParserBase {
    private static final VarHandle WORD = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final long ONES = 0x0101010101010101L;
    private static final long HIGH_BITS = 0x8080808080808080L;

    private boolean expectLowSurrogate;

    static long word(byte[] input, int position) {
        return (long) WORD.get(input, position);
    }

    static long events(long word) {
        // Only the first marked byte matters. A borrow may also mark a later byte.
        return (word | (word - 0x2020202020202020L)
                | ((word ^ 0x2222222222222222L) - ONES)
                | ((word ^ 0x5c5c5c5c5c5c5c5cL) - ONES)) & HIGH_BITS;
    }

    final boolean expectsLowSurrogate() {
        return expectLowSurrogate;
    }

    final void expectLowSurrogate(boolean value) {
        expectLowSurrogate = value;
    }

    final int readString(JsonStringLexer lexer, byte[] input, int quote, int limit) {
        Objects.requireNonNull(lexer);
        if (input[quote] != '"') {
            throw createException("Start of a string expected", input[quote]);
        }
        // A public selector may decline a known name. Only an owned dictionary can establish a definitive miss.
        if (lexer instanceof PackedAsciiStringLexer complete) {
            int start = quote + 1;
            long hit = complete.recognizeAfterSelection(input, start, limit);
            if (hit >= 0) {
                stringPosition(start + (int) (hit >>> Integer.SIZE));
                return (int) hit;
            }
            lexer = complete.decoded();
        }
        int position = quote + 1;
        int state = 0;
        while (limit - position >= Long.BYTES) {
            long word = (long) WORD.get(input, position);
            long events = events(word);
            if (events != 0) {
                int shift = Long.numberOfTrailingZeros(events) & -Byte.SIZE;
                int end = position + shift / Byte.SIZE;
                byte event = input[end];
                if (event == '"') {
                    stringPosition(end);
                    return state < 0 ? -1 : lexer.advance(state, word & ((1L << shift) - 1), 0, shift / Byte.SIZE);
                }
                if (event == '\\' || event < 0) {
                    return readBuffered(lexer, input, end, limit, state, word & ((1L << shift) - 1), shift);
                }
                break;
            }
            if (state >= 0) {
                state = lexer.advance(state, word, 0, Long.BYTES);
            }
            position += Long.BYTES;
        }
        stringPosition(position - 1);
        return readDecoded(lexer, state, 0, 0, 0);
    }

    final int nextProperty(JsonStringLexer lexer, byte[] input, int position, int limit) {
        Objects.requireNonNull(lexer);
        int remaining = limit - position;
        if (remaining > 1 && input[position + 1] == '}') {
            stringPosition(position + 1);
            return END_OBJECT;
        }
        if (remaining > 2 && input[position + 1] == ',' && input[position + 2] == '"') {
            return readProperty(lexer, input, position + 2, limit);
        }
        return Parsers.nextProperty(this, lexer);
    }

    final int readProperty(JsonStringLexer lexer, byte[] input, int quote, int limit) {
        Objects.requireNonNull(lexer);
        if (input[quote] != '"') {
            throw createException("Expected a quoted property name", input[quote]);
        }
        if (lexer instanceof AsciiStringLexer raw) {
            int start = quote + 1;
            long hit = raw.recognize(input, start, limit);
            if (hit >= 0) {
                int end = start + (int) (hit >>> Integer.SIZE);
                if (limit - end > 2 && input[end + 1] == ':' && !JsonParserArray.WHITESPACE_CHARS[input[end + 2] & 0xff]) {
                    stringPosition(end + 2);
                } else {
                    stringPosition(end);
                    Parsers.propertyValue(this);
                }
                return (int) hit;
            }
            lexer = raw.decoded();
        }
        int ordinal = readString(lexer, input, quote, limit);
        Parsers.propertyValue(this);
        return Math.max(-1, ordinal);
    }

    abstract void stringPosition(int position);

    abstract byte readNextByte();

    abstract char processEscapedSequence();

    abstract int readUtf8CodePoint(byte first);

    // Each successive group of three little-endian bytes must contain a UTF-8 lead and two continuations.
    // E0 and ED have additional second-byte bounds and stay with the single-code-point decoder.
    private static long threeBytePair(long word) {
        if ((word & 0x0000c0c0f0c0c0f0L) != 0x00008080e08080e0L) {
            return -1;
        }
        int first = (int) word & 0xff;
        int second = (int) (word >>> 24) & 0xff;
        if (first == 0xe0 || first == 0xed || second == 0xe0 || second == 0xed) {
            return -1;
        }
        // Keep the two decoded UTF-16 code units 24 bits apart, as in the input word.
        return ((word & 0x0f00000fL) << 12) | (((word >>> 8) & 0x3f00003fL) << 6) | ((word >>> 16) & 0x3f00003fL);
    }

    private int readBuffered(JsonStringLexer lexer, byte[] input, int position, int limit, int state, long low, int shift) {
        long high = 0;
        while (position < limit) {
            byte b = input[position];
            if (b == '"') {
                stringPosition(position);
                return state < 0 ? -1 : lexer.advance(state, low, high, shift / Byte.SIZE);
            }
            if (b == '\\') {
                if (limit - position < 6 || input[position + 1] != 'u') {
                    break;
                }
                int unit = Parsers.hexPair(input[position + 4], input[position + 5]);
                int upper = input[position + 2] == '0' && input[position + 3] == '0'
                        ? 0 : Parsers.hexPair(input[position + 2], input[position + 3]);
                // The existing decoder validates surrogate pairs, including pairs crossing a refill.
                if ((unit | upper) < 0 || (upper & 0xf8) == 0xd8) {
                    break;
                }
                low |= (long) unit << shift;
                high |= (long) upper << shift;
                shift += Byte.SIZE;
                position += 6;
            } else if (b < 0) {
                long pair = shift <= Long.SIZE - 2 * Byte.SIZE && (b & 0xf0) == 0xe0 && limit - position >= Long.BYTES
                        ? threeBytePair(word(input, position)) : -1;
                if (pair >= 0) {
                    // Append the low and high bytes of both UTF-16 units to their respective lexer words.
                    low |= ((pair & 0xff) | ((pair >>> 16) & 0xff00)) << shift;
                    high |= (((pair >>> 8) & 0xff) | ((pair >>> 24) & 0xff00)) << shift;
                    shift += 2 * Byte.SIZE;
                    position += 6;
                } else {
                    int unit;
                    if ((b & 0xe0) == 0xc0 && limit - position >= 2) {
                        stringPosition(position + 1);
                        unit = Parsers.decodeUtf8TwoByte(b, input[position + 1], this);
                        position += 2;
                    } else if ((b & 0xf0) == 0xe0 && limit - position >= 3) {
                        stringPosition(position + 2);
                        unit = Parsers.decodeUtf8ThreeByte(b, input[position + 1], input[position + 2], this);
                        position += 3;
                    } else {
                        break;
                    }
                    low |= (unit & 0xffL) << shift;
                    high |= (long) (unit >>> Byte.SIZE) << shift;
                    shift += Byte.SIZE;
                }
            } else if (b < 0x20) {
                break;
            } else if (limit - position >= Long.BYTES) {
                long word = word(input, position);
                int run = Long.numberOfTrailingZeros(events(word)) / Byte.SIZE;
                int take = Math.min(Long.BYTES - shift / Byte.SIZE, run);
                long mask = -1L >>> ((Long.BYTES - take) * Byte.SIZE);
                low |= (word & mask) << shift;
                shift += take * Byte.SIZE;
                position += take;
            } else {
                low |= (long) b << shift;
                shift += Byte.SIZE;
                position++;
            }
            if (shift == Long.SIZE) {
                if (state >= 0) {
                    state = lexer.advance(state, low, high, Long.BYTES);
                }
                low = 0;
                high = 0;
                shift = 0;
            }
        }
        stringPosition(position - 1);
        return readDecoded(lexer, state, low, high, shift);
    }

    private int readDecoded(JsonStringLexer lexer, int state, long low, long high, int shift) {
        expectLowSurrogate = false;
        int remainingUnit = -1;
        while (true) {
            int unit;
            if (remainingUnit >= 0) {
                unit = remainingUnit;
                remainingUnit = -1;
            } else {
                if (!hasNext()) {
                    throw createException("Incomplete JSON string");
                }
                byte b = readNextByte();
                if (b == '\\') {
                    unit = processEscapedSequence();
                } else if (expectLowSurrogate) {
                    throw createException("Low surrogate must follow the high surrogate.", b);
                } else if (b == '"') {
                    return state < 0 ? -1 : lexer.advance(state, low, high, shift / Byte.SIZE);
                } else if (b < 0) {
                    unit = readUtf8CodePoint(b);
                    if (Character.isSupplementaryCodePoint(unit)) {
                        remainingUnit = Character.lowSurrogate(unit);
                        unit = Character.highSurrogate(unit);
                    }
                } else if (b < 0x20) {
                    throw createException("Unescaped control character not allowed in string", b);
                } else {
                    unit = b;
                }
            }
            if (state >= 0) {
                low |= (unit & 0xffL) << shift;
                high |= (long) (unit >>> Byte.SIZE) << shift;
                shift += Byte.SIZE;
                if (shift == Long.SIZE) {
                    state = lexer.advance(state, low, high, Long.BYTES);
                    low = 0;
                    high = 0;
                    shift = 0;
                }
            }
        }
    }
}
