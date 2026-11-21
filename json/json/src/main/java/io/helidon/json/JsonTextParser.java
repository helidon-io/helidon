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
        int position = quote + 1;
        int state = 0;
        while (limit - position >= Long.BYTES) {
            long word = (long) WORD.get(input, position);
            // Only the first marked byte matters. A borrow may also mark a later byte.
            long events = (word | (word - 0x2020202020202020L)
                    | ((word ^ 0x2222222222222222L) - ONES)
                    | ((word ^ 0x5c5c5c5c5c5c5c5cL) - ONES)) & HIGH_BITS;
            if (events != 0) {
                int shift = Long.numberOfTrailingZeros(events) & -Byte.SIZE;
                int end = position + shift / Byte.SIZE;
                if (input[end] == '"') {
                    stringPosition(end);
                    return state < 0 ? -1 : lexer.advance(state, word & ((1L << shift) - 1), 0, shift / Byte.SIZE);
                }
                break;
            }
            if (state >= 0) {
                state = lexer.advance(state, word, 0, Long.BYTES);
            }
            position += Long.BYTES;
        }
        stringPosition(position - 1);
        return readDecoded(lexer, state);
    }

    abstract void stringPosition(int position);

    abstract byte readNextByte();

    abstract char processEscapedSequence();

    abstract int readUtf8CodePoint(byte first);

    private int readDecoded(JsonStringLexer lexer, int state) {
        expectLowSurrogate = false;
        long low = 0;
        long high = 0;
        int shift = 0;
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
