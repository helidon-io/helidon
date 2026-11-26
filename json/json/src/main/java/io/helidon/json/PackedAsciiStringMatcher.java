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

import java.util.Arrays;
import java.util.Objects;

import static io.helidon.json.JsonParserArray.FNV_OFFSET_BASIS;
import static io.helidon.json.JsonParserArray.FNV_PRIME;

/**
 * Matches short ASCII names using a prefix index or an FNV-1a hash.
 * Both paths verify the complete spelling through its closing quote.
 * An enclosing selector can also use the dictionary indexed by the first input word.
 */
final class PackedAsciiStringMatcher extends AsciiStringMatcher {
    private static final long QUOTES = 0x2222222222222222L;
    private static final long ONES = 0x0101010101010101L;
    private static final long HIGH_BITS = 0x8080808080808080L;
    private static final int MIN_NAMES = 16;
    private static final int MAX_NAMES = 64;
    private static final int MAX_PREFIX_FAN_OUT = 4;
    private static final int MAX_PLAIN_SCAN = 8 * Long.BYTES;
    private static final long UNKNOWN = Integer.toUnsignedLong(-1);
    private final Dictionary dictionary;
    private final PrefixIndex prefixIndex;

    private PackedAsciiStringMatcher(Dictionary dictionary, PrefixIndex prefixIndex) {
        super(dictionary);
        this.dictionary = dictionary;
        this.prefixIndex = prefixIndex;
    }

    static JsonStringMatcher create(String[] names) {
        Objects.requireNonNull(names);
        if (names.length < MIN_NAMES || names.length > MAX_NAMES) {
            return new RuntimeStringMatcher(names);
        }
        String[] registered = names.clone();
        Literal[] literals = new Literal[registered.length];
        boolean secondWord = false;
        int prefixWidth = Integer.BYTES;
        for (int i = 0; i < literals.length; i++) {
            String name = registered[i];
            if (name == null || name.length() >= 2 * Long.BYTES) {
                return new RuntimeStringMatcher(registered);
            }
            Literal literal = Literal.create(name);
            if (literal == null) {
                return new RuntimeStringMatcher(registered);
            }
            literals[i] = literal;
            prefixWidth = Math.min(prefixWidth, literal.length() + 1);
            secondWord |= literal.length() >= Long.BYTES;
        }
        if (!secondWord) {
            return new RuntimeStringMatcher(registered);
        }
        Dictionary dictionary = Dictionary.create(registered, literals);
        return dictionary == null ? new RuntimeStringMatcher(registered)
                : new PackedAsciiStringMatcher(dictionary, PrefixIndex.create(dictionary, prefixWidth, literals.length));
    }

    @Override
    long recognize(byte[] input, int start, int limit) {
        int available = limit - start;
        if (prefixIndex == null || available < Long.BYTES) {
            return recognizeHashed(input, start, limit);
        }
        long first = JsonTextParser.word(input, start);
        int slot = prefixIndex.slot(first);
        for (Entry entry = prefixIndex.entries[slot]; entry != null; entry = prefixIndex.next[entry.ordinal]) {
            Literal literal = entry.literal;
            int length = literal.length();
            // A registered name and its closing quote fit in two overlapping words.
            if (length < available && (first & literal.firstMask()) == literal.first()
                    && (length < Long.BYTES
                            || JsonTextParser.word(input, start + length + 1 - Long.BYTES) == literal.tail())) {
                return ((long) length << Integer.SIZE) | entry.ordinal;
            }
        }
        long second = available >= 2 * Long.BYTES ? JsonTextParser.word(input, start + Long.BYTES)
                : partialSecond(input, limit, available);
        return plainMiss(input, start, limit, first, second);
    }

    // An enclosing selector already examined this name. Use the buffered words to look up its exact spelling.
    long recognizeAfterSelection(byte[] input, int start, int limit) {
        int available = limit - start;
        if (available < Long.BYTES) {
            return -1;
        }
        long first = JsonTextParser.word(input, start);
        long second = available >= 2 * Long.BYTES ? JsonTextParser.word(input, start + Long.BYTES)
                : partialSecond(input, limit, available);
        long quoted = first ^ QUOTES;
        long ends = (quoted - ONES) & ~quoted & HIGH_BITS;
        // Keep bytes through the first quote. With no quote, the mask is all ones.
        long key = first & (ends ^ (ends - 1));
        for (Entry entry = dictionary.bucket(key); entry != null; entry = entry.nextPrefix) {
            Literal literal = entry.literal;
            if (key == literal.first() && (second & literal.secondMask()) == literal.second() && literal.length() < available) {
                return ((long) literal.length() << Integer.SIZE) | entry.ordinal;
            }
        }
        return plainMiss(input, start, limit, first, second);
    }

    private static long plainMiss(byte[] input, int start, int limit, long first, long second) {
        long word = first;
        long events = JsonTextParser.events(word);
        int offset = 0;
        if (events == 0) {
            word = second;
            events = JsonTextParser.events(word);
            offset = Long.BYTES;
        }
        if (events != 0) {
            int shift = Long.numberOfTrailingZeros(events) & -Byte.SIZE;
            return (byte) (word >>> shift) == '"' ? ((long) (offset + shift / Byte.SIZE) << Integer.SIZE) | UNKNOWN : -1;
        }
        return plainMiss(input, start, start + 2 * Long.BYTES, limit);
    }

    private static long plainMiss(byte[] input, int start, int pos, int limit) {
        // A validated 16-byte prefix is longer than every registered name.
        int scanLimit = start + Math.min(limit - start, MAX_PLAIN_SCAN);
        while (scanLimit - pos >= Long.BYTES) {
            long word = JsonTextParser.word(input, pos);
            long events = JsonTextParser.events(word);
            if (events != 0) {
                int end = pos + Long.numberOfTrailingZeros(events) / Byte.SIZE;
                return input[end] == '"' ? ((long) (end - start) << Integer.SIZE) | UNKNOWN : -1;
            }
            pos += Long.BYTES;
        }
        while (pos < scanLimit) {
            byte value = input[pos];
            if (value == '"') {
                return ((long) (pos - start) << Integer.SIZE) | UNKNOWN;
            }
            if (value < 0x20 || value == '\\') {
                break;
            }
            pos++;
        }
        return -1;
    }

    private long recognizeHashed(byte[] input, int start, int limit) {
        int available = limit - start;
        int shortLimit = start + Math.min(available, 2 * Long.BYTES);
        int hash = FNV_OFFSET_BASIS;
        int position = start;
        scan: {
            while (shortLimit - position >= Integer.BYTES) {
                byte value = input[position++];
                if (value == '"') {
                    break scan;
                }
                if (value < 0x20 || value == '\\') {
                    return -1;
                }
                hash = (hash ^ value) * FNV_PRIME;
                value = input[position++];
                if (value == '"') {
                    break scan;
                }
                if (value < 0x20 || value == '\\') {
                    return -1;
                }
                hash = (hash ^ value) * FNV_PRIME;
                value = input[position++];
                if (value == '"') {
                    break scan;
                }
                if (value < 0x20 || value == '\\') {
                    return -1;
                }
                hash = (hash ^ value) * FNV_PRIME;
                value = input[position++];
                if (value == '"') {
                    break scan;
                }
                if (value < 0x20 || value == '\\') {
                    return -1;
                }
                hash = (hash ^ value) * FNV_PRIME;
            }
            while (position < shortLimit) {
                byte value = input[position++];
                if (value == '"') {
                    break scan;
                }
                if (value < 0x20 || value == '\\') {
                    return -1;
                }
                hash = (hash ^ value) * FNV_PRIME;
            }
            return position < limit ? plainMiss(input, start, position, limit) : -1;
        }
        int length = position - start - 1;
        int ordinal = dictionary.match(hash, input, start, available, length);
        return ((long) length << Integer.SIZE) | Integer.toUnsignedLong(ordinal);
    }

    // Every registered name, including its closing quote, supplies all bytes used to select a bucket.
    private record PrefixIndex(int mask, int shift, Entry[] entries, Entry[] next) {
        private static final int MIX = 0x9e3779b9;

        private static PrefixIndex create(Dictionary dictionary, int width, int nameCount) {
            int slots = dictionary.hashBuckets.length * 2;
            int mask = -1 >>> ((Integer.BYTES - width) * Byte.SIZE);
            var index = new PrefixIndex(mask, Integer.SIZE - Integer.numberOfTrailingZeros(slots),
                                        new Entry[slots], new Entry[nameCount]);
            byte[] depths = new byte[slots];
            int covered = 0;
            for (Entry head : dictionary.hashBuckets) {
                for (Entry entry = head; entry != null; entry = entry.nextHash) {
                    int slot = index.slot(entry.literal.first());
                    int depth = ++depths[slot];
                    if (depth == 1) {
                        covered++;
                    } else if (depth > MAX_PREFIX_FAN_OUT) {
                        return null;
                    }
                    index.next[entry.ordinal] = index.entries[slot];
                    index.entries[slot] = entry;
                }
            }
            // Use FNV for alphabets with too many shared or colliding prefixes.
            return covered * 4 >= nameCount * 3 ? index : null;
        }

        private int slot(long first) {
            return (((int) first & mask) * MIX) >>> shift;
        }
    }

    private record Entry(Literal literal, int hash, int ordinal, int prefixState, Entry nextPrefix, Entry nextHash) {
    }

    private static final class Dictionary implements JsonStringMatcher {
        private static final long MIX = 0x9e3779b97f4a7c15L;
        private final int shift;
        private final Entry[] entries;
        private final long[] prefixes;
        private final Entry[] hashBuckets;

        private Dictionary(int shift, Entry[] entries, long[] prefixes, Entry[] hashBuckets) {
            this.shift = shift;
            this.entries = entries;
            this.prefixes = prefixes;
            this.hashBuckets = hashBuckets;
        }

        @Override
        public int advance(int state, long low, long high, int length) {
            long prefix = prefixes[state];
            if (high != 0 || length < 0 || length > Long.BYTES) {
                return -1;
            }
            if (length == Long.BYTES) {
                if (state == 0) {
                    for (Entry entry = bucket(low); entry != null; entry = entry.nextPrefix) {
                        if (entry.literal.first() == low && entry.prefixState != 0) {
                            return entry.prefixState;
                        }
                    }
                }
                return -1;
            }
            int shift = length * Byte.SIZE;
            if ((low >>> shift) != 0) {
                return -1;
            }
            long last = low | (0x22L << shift);
            long first = state == 0 ? last : prefix;
            long second = state == 0 ? 0 : last;
            for (Entry entry = bucket(first); entry != null; entry = entry.nextPrefix) {
                if (entry.literal.first() == first && entry.literal.second() == second) {
                    return entry.ordinal;
                }
            }
            return -1;
        }

        private static Dictionary create(String[] names, Literal[] literals) {
            int slots = Integer.highestOneBit(2 * literals.length - 1) << 1;
            int shift = Long.SIZE - Integer.numberOfTrailingZeros(slots);
            Entry[] entries = new Entry[slots];
            Entry[] hashBuckets = new Entry[slots];
            long[] prefixes = new long[literals.length + 1];
            int prefixCount = 0;
            for (int ordinal = 0; ordinal < literals.length; ordinal++) {
                Literal literal = literals[ordinal];
                int slot = index(literal.first(), shift);
                Entry head = entries[slot];
                int state = 0;
                int shared = 1;
                for (Entry entry = head; entry != null; entry = entry.nextPrefix) {
                    if (entry.literal.first() == literal.first()) {
                        if (entry.literal.second() == literal.second()) {
                            throw new IllegalArgumentException("Duplicate string in matcher: " + names[ordinal]);
                        }
                        if (++shared > MAX_PREFIX_FAN_OUT) {
                            return null;
                        }
                        state = entry.prefixState;
                    }
                }
                if (state == 0 && literal.length() >= Long.BYTES) {
                    state = ++prefixCount;
                    prefixes[state] = literal.first();
                }
                int hash = JsonParserArray.fnv1aHashUtf8(names[ordinal]);
                int hashSlot = (hash ^ (hash >>> 16)) & (slots - 1);
                Entry entry = new Entry(literal, hash, ordinal, state, head, hashBuckets[hashSlot]);
                entries[slot] = entry;
                hashBuckets[hashSlot] = entry;
            }
            return new Dictionary(shift, entries, Arrays.copyOf(prefixes, prefixCount + 1), hashBuckets);
        }

        private static int index(long word, int shift) {
            return (int) (((word ^ (word >>> 33)) * MIX) >>> shift);
        }

        private int match(int hash, byte[] input, int start, int available, int length) {
            int slot = (hash ^ (hash >>> 16)) & (hashBuckets.length - 1);
            for (Entry entry = hashBuckets[slot]; entry != null; entry = entry.nextHash) {
                Literal literal = entry.literal;
                if (entry.hash == hash && literal.length() == length && literal.matchesSingle(input, start, available)) {
                    return entry.ordinal;
                }
            }
            return -1;
        }

        private Entry bucket(long first) {
            return entries[index(first, shift)];
        }
    }
}
