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
import java.util.Arrays;
import java.util.Objects;

abstract class AsciiStringLexer implements JsonStringLexer {
    private static final int MAX_SINGLE_WORD_NAMES = 12;

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
        Literal[] literals = new Literal[names.length];
        boolean singleWord = names.length >= 2 && names.length <= MAX_SINGLE_WORD_NAMES;
        for (int i = 0; i < names.length; i++) {
            Literal literal = Literal.create(Objects.requireNonNull(names[i]));
            literals[i] = literal;
            singleWord = singleWord && literal != null && literal.length < Long.BYTES;
        }
        return singleWord ? new SingleWord(decoded, selector, literals) : new Multiple(decoded, selector, literals);
    }

    static JsonStringLexer withUtf8(JsonStringLexer decoded, Utf8Selector selector, int offset, String[] names) {
        Objects.requireNonNull(decoded);
        Objects.requireNonNull(selector);
        Objects.requireNonNull(names);
        if (offset < 0) {
            throw new IllegalArgumentException("Negative UTF-8 selector offset: " + offset);
        }
        return new Utf8(decoded, selector, offset, names);
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

    // The first word includes the entire literal and its closing quote. The selector can inspect both input words.
    private static final class SingleWord extends AsciiStringLexer {
        private final AsciiSelector selector;
        private final Literal[] literals;

        private SingleWord(JsonStringLexer decoded, AsciiSelector selector, Literal[] literals) {
            super(decoded);
            this.selector = selector;
            this.literals = literals;
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
                if ((first & literal.firstMask) == literal.first) {
                    return ((long) literal.length << Integer.SIZE) | ordinal;
                }
            }
            return -1;
        }
    }

    private static final class Multiple extends AsciiStringLexer {
        private final AsciiSelector selector;
        private final Literal[] literals;

        private Multiple(JsonStringLexer decoded, AsciiSelector selector, Literal[] literals) {
            super(decoded);
            this.selector = selector;
            this.literals = literals;
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

    private static final class Utf8 extends AsciiStringLexer {
        private final Utf8Selector selector;
        private final int offset;
        private final Literal[] literals;
        private final byte[][] longNames;

        private Utf8(JsonStringLexer decoded, Utf8Selector selector, int offset, String[] names) {
            super(decoded);
            this.selector = selector;
            this.offset = offset;
            this.literals = new Literal[names.length];
            byte[][] extended = null;
            for (int i = 0; i < names.length; i++) {
                String name = Objects.requireNonNull(names[i]);
                if (name.codePoints().anyMatch(c -> c < 0x20 || c == '"' || c == '\\'
                        || c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE)) {
                    continue;
                }
                byte[] spelling = name.getBytes(StandardCharsets.UTF_8);
                if (spelling.length < 3 * Long.BYTES) {
                    literals[i] = Literal.create(name, spelling);
                } else {
                    if (extended == null) {
                        extended = new byte[names.length][];
                    }
                    extended[i] = spelling;
                }
            }
            longNames = extended;
        }

        @Override
        long recognize(byte[] input, int start, int limit) {
            int available = limit - start;
            if (offset > available - Long.BYTES) {
                return -1;
            }
            int window = start + offset;
            long first = JsonTextParser.word(input, window);
            long second = limit - window >= 2 * Long.BYTES ? JsonTextParser.word(input, window + Long.BYTES)
                    : partialSecond(input, limit, limit - window);
            int ordinal = selector.select(first, second);
            if (ordinal >= 0 && ordinal < literals.length) {
                Literal literal = literals[ordinal];
                if (literal != null && literal.matchesSingle(input, start, available)) {
                    return ((long) literal.length << Integer.SIZE) | ordinal;
                }
                if (literal == null && longNames != null) {
                    byte[] spelling = longNames[ordinal];
                    if (spelling != null && spelling.length < available && input[start + spelling.length] == '"'
                            && Arrays.equals(spelling, 0, spelling.length, input, start, start + spelling.length)) {
                        return ((long) spelling.length << Integer.SIZE) | ordinal;
                    }
                }
            }
            return -1;
        }
    }

    // Each successful comparison establishes both valid JSON spelling and its exact boundary.
    private record Literal(long first, long firstMask, long second, long secondMask, long tail, int length) {
        private static Literal create(String name) {
            return create(name, null);
        }

        private static Literal create(String name, byte[] bytes) {
            int length = bytes == null ? name.length() : bytes.length;
            if (length >= 3 * Long.BYTES) {
                return null;
            }
            long first = 0;
            long second = 0;
            long tail = 0;
            for (int i = 0; i <= length; i++) {
                int c = '"';
                if (i < length) {
                    c = bytes == null ? name.charAt(i) : bytes[i] & 0xff;
                    if (bytes == null && (c < 0x20 || c >= 0x80 || c == '"' || c == '\\')) {
                        return null;
                    }
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
