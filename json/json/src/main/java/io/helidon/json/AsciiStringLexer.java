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

import java.util.Objects;

abstract class AsciiStringLexer implements JsonStringLexer {
    private final JsonStringLexer decoded;

    private AsciiStringLexer(JsonStringLexer decoded) {
        this.decoded = decoded;
    }

    static AsciiStringLexer create(JsonStringLexer decoded, AsciiSelector selector, String[] names) {
        Objects.requireNonNull(decoded);
        Objects.requireNonNull(selector);
        Objects.requireNonNull(names);
        if (names.length == 1) {
            Literal literal = Literal.create(Objects.requireNonNull(names[0]));
            if (literal != null) {
                return new Single(decoded, literal);
            }
        }
        return new Multiple(decoded, selector, names);
    }

    @Override
    public final int advance(int state, long low, long high, int length) {
        return decoded.advance(state, low, high, length);
    }

    final JsonStringLexer decoded() {
        return decoded;
    }

    abstract long recognize(byte[] input, int start, int limit);

    private static long partialSecond(byte[] input, int limit, int available) {
        if (available == Long.BYTES) {
            return 0;
        }
        // The last bounded word overlaps the first. Discard the overlap and leave the rest zero.
        return JsonTextParser.word(input, limit - Long.BYTES) >>> ((2 * Long.BYTES - available) * Byte.SIZE);
    }

    private static final class Single extends AsciiStringLexer {
        private final Literal literal;

        private Single(JsonStringLexer decoded, Literal literal) {
            super(decoded);
            this.literal = literal;
        }

        @Override
        long recognize(byte[] input, int start, int limit) {
            return literal.matchesSingle(input, start, limit - start) ? (long) literal.length << Integer.SIZE : -1;
        }
    }

    private static final class Multiple extends AsciiStringLexer {
        private final AsciiSelector selector;
        private final Literal[] literals;

        private Multiple(JsonStringLexer decoded, AsciiSelector selector, String[] names) {
            super(decoded);
            this.selector = selector;
            literals = new Literal[names.length];
            for (int i = 0; i < names.length; i++) {
                literals[i] = Literal.create(Objects.requireNonNull(names[i]));
            }
        }

        @Override
        long recognize(byte[] input, int start, int limit) {
            int available = limit - start;
            if (available < Long.BYTES) {
                return -1;
            }
            long first = JsonTextParser.word(input, start);
            long second = available >= 2 * Long.BYTES ? JsonTextParser.word(input, start + Long.BYTES)
                    : partialSecond(input, limit, available);
            int ordinal = selector.select(first, second);
            if (ordinal >= 0 && ordinal < literals.length) {
                Literal literal = literals[ordinal];
                if (literal != null && literal.length < available
                        && (first & literal.firstMask) == literal.first && (second & literal.secondMask) == literal.second
                        && (literal.length < 2 * Long.BYTES
                                || JsonTextParser.word(input, start + literal.length - Long.BYTES + 1) == literal.tail)) {
                    return ((long) literal.length << Integer.SIZE) | ordinal;
                }
            }
            return -1;
        }
    }

    // Each successful comparison establishes both valid JSON spelling and its exact boundary.
    private record Literal(long first, long firstMask, long second, long secondMask, long tail, int length) {
        private static Literal create(String name) {
            int length = name.length();
            if (length >= 3 * Long.BYTES) {
                return null;
            }
            long first = 0;
            long second = 0;
            long tail = 0;
            for (int i = 0; i <= length; i++) {
                char c = i == length ? '"' : name.charAt(i);
                if (i < length && (c < 0x20 || c >= 0x80 || c == '"' || c == '\\')) {
                    return null;
                }
                if (i < Long.BYTES) {
                    first |= (long) c << (i * Byte.SIZE);
                } else if (i < 2 * Long.BYTES) {
                    second |= (long) c << ((i - Long.BYTES) * Byte.SIZE);
                }
                if (length >= Long.BYTES && i > length - Long.BYTES) {
                    tail |= (long) c << ((i - (length - Long.BYTES + 1)) * Byte.SIZE);
                }
            }
            return new Literal(first, mask(Math.min(length + 1, Long.BYTES)), second,
                               mask(Math.clamp(length + 1 - Long.BYTES, 0, Long.BYTES)), tail, length);
        }

        private static long mask(int bytes) {
            return bytes == 0 ? 0 : -1L >>> ((Long.BYTES - bytes) * Byte.SIZE);
        }

        private boolean matchesSingle(byte[] input, int start, int available) {
            if (length >= available) {
                return false;
            }
            if (length < Long.BYTES) {
                if (available >= Long.BYTES) {
                    return (JsonTextParser.word(input, start) & firstMask) == first;
                }
                for (int i = 0; i <= length; i++) {
                    if (input[start + i] != (byte) (first >>> (i * Byte.SIZE))) {
                        return false;
                    }
                }
                return true;
            }
            // The final word includes the closing quote and can overlap the first two.
            return JsonTextParser.word(input, start) == first
                    && (length < 2 * Long.BYTES || JsonTextParser.word(input, start + Long.BYTES) == second)
                    && JsonTextParser.word(input, start + length - Long.BYTES + 1) == tail;
        }
    }
}
