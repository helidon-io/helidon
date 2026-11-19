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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.helidon.common.Api;

/**
 * An immutable lookup of decoded JSON strings, suitable for repeated property-name or discriminator matching.
 * Strings are identified by their zero-based position in the lookup. Hash collisions never identify a different string.
 * A lookup can be shared by multiple parsers and threads.
 *
 * @see JsonParser#readString(JsonStringLookup)
 */
@Api.Preview
public final class JsonStringLookup {

    private static final VarHandle LONG_VIEW = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);
    private static final VarHandle INT_VIEW = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);
    private static final Candidate[] EMPTY_CANDIDATES = new Candidate[0];

    private final Map<String, Integer> strings;
    private final Candidate[] buckets = new Candidate[128];
    private final int emptyIndex;
    private final int offset;
    private final boolean shortNames;
    private final Candidate shortCandidates;
    private final DecodedTrie decodedTrie;
    private final Candidate[] utf8Only;

    private JsonStringLookup(String[] values) {
        Map<String, Integer> strings = new HashMap<>();
        char[][] decodedNames = new char[values.length][];
        byte[][] names = new byte[values.length][];
        ArrayList<Candidate> utf8Only = null;
        int shortest = Integer.MAX_VALUE;
        int longest = 0;
        int nameCount = 0;
        for (int index = 0; index < values.length; index++) {
            String value = Objects.requireNonNull(values[index]);
            if (strings.putIfAbsent(value, index) != null) {
                throw new IllegalArgumentException("Duplicate string in lookup: " + value);
            }
            char[] chars = value.toCharArray();
            decodedNames[index] = chars;
            boolean raw = true;
            boolean wellFormed = true;
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c < 0x20 || c == '"' || c == '\\') {
                    raw = false;
                }
                if (Character.isHighSurrogate(c)) {
                    if (i + 1 == value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                        wellFormed = false;
                        break;
                    }
                    i++;
                } else if (Character.isLowSurrogate(c)) {
                    wellFormed = false;
                    break;
                }
            }
            if (!wellFormed) {
                // Unpaired surrogates have no valid UTF-8 representation.
                continue;
            }
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            if (!raw) {
                if (utf8Only == null) {
                    utf8Only = new ArrayList<>();
                }
                utf8Only.add(new Candidate(bytes, index, null));
                continue;
            }
            if (bytes.length != 0) {
                names[index] = bytes;
                nameCount++;
                shortest = Math.min(shortest, bytes.length);
                longest = Math.max(longest, bytes.length);
            }
        }
        this.strings = Map.copyOf(strings);
        this.emptyIndex = strings.getOrDefault("", -1);
        int offset = 0;
        long bestCost = Long.MAX_VALUE;
        int[] counts = new int[128];
        // Charge shorter names for both probe misses, the fallback chain and an allowance for the fallback dispatch.
        if (shortest != Integer.MAX_VALUE) {
            for (int position = 0; position < longest; position++) {
                Arrays.fill(counts, 0);
                long cost = 0;
                int shorterCount = 0;
                int largestBucket = 0;
                for (byte[] bytes : names) {
                    if (bytes != null) {
                        if (bytes.length <= position) {
                            shorterCount++;
                        } else {
                            int bucketSize = ++counts[bytes[position] & 0x7f];
                            cost += 2L * bucketSize - 1;
                            largestBucket = Math.max(largestBucket, bucketSize);
                        }
                    }
                }
                long fallbackCost = (long) shorterCount * (shorterCount + 4L * largestBucket + 2);
                cost += fallbackCost;
                if (cost < bestCost) {
                    bestCost = cost;
                    offset = position;
                }
                // At later positions the shorter-name count cannot decrease, so this lower bound cannot improve.
                if (bestCost <= nameCount - shorterCount + (long) shorterCount * (shorterCount + 6L)) {
                    break;
                }
            }
        }
        this.offset = offset;
        boolean shortNames = false;
        Candidate shortCandidates = null;
        for (int index = 0; index < names.length; index++) {
            byte[] bytes = names[index];
            if (bytes != null) {
                if (bytes.length <= offset) {
                    shortCandidates = new Candidate(bytes, index, shortCandidates);
                } else {
                    int initial = bytes[offset] & 0x7f;
                    buckets[initial] = new Candidate(bytes, index, buckets[initial]);
                    shortNames |= bytes.length < Integer.BYTES;
                }
            }
        }
        this.shortNames = shortNames;
        this.shortCandidates = shortCandidates;
        this.decodedTrie = new DecodedTrie(decodedNames);
        this.utf8Only = utf8Only == null ? EMPTY_CANDIDATES : utf8Only.toArray(Candidate[]::new);
    }

    /**
     * Create a lookup for the supplied strings, in their supplied order.
     *
     * @param values distinct, non-null decoded strings
     * @return immutable lookup
     * @throws IllegalArgumentException if a string occurs more than once
     */
    public static JsonStringLookup create(String... values) {
        return new JsonStringLookup(Objects.requireNonNull(values));
    }

    static boolean matchesAscii(int word, byte[] buffer, int start) {
        return (int) INT_VIEW.get(buffer, start) == word;
    }

    static boolean matchesEscapedAscii(int value, byte[] buffer, int start) {
        return (int) INT_VIEW.get(buffer, start) == 0x3030755c
                && buffer[start + 4] == '0' + (value >>> 4)
                && buffer[start + 5] >= '0'
                && (buffer[start + 5] | 0x20) == "0123456789abcdef".charAt(value & 0xf);
    }

    /**
     * Find a decoded string in this lookup.
     *
     * @param value decoded string
     * @return zero-based position supplied to {@link #create(String...)}, or {@code -1} if absent
     */
    public int match(String value) {
        return strings.getOrDefault(Objects.requireNonNull(value), -1);
    }

    /**
     * Find a string encoded as UTF-8 in the supplied byte range.
     * The range contains the decoded value's bytes, without JSON quotes or escape processing.
     * The input is neither modified nor retained. Malformed UTF-8 cannot match a lookup entry;
     * entries containing unpaired UTF-16 surrogates have no UTF-8 representation and cannot match.
     *
     * @param buffer UTF-8 bytes
     * @param start first byte of the value
     * @param length number of bytes in the value
     * @return zero-based position supplied to {@link #create(String...)}, or {@code -1} if absent
     * @throws IndexOutOfBoundsException if the range is outside the buffer
     */
    public int matchUtf8(byte[] buffer, int start, int length) {
        Objects.requireNonNull(buffer);
        Objects.checkFromIndexSize(start, length, buffer.length);
        if (length == 0) {
            return emptyIndex;
        }
        int end = start + length;
        Candidate first = length > offset ? buckets[buffer[start + offset] & 0x7f] : shortCandidates;
        for (Candidate candidate = first; candidate != null; candidate = candidate.next) {
            if (candidate.length == length && Arrays.equals(buffer, start, end, candidate.bytes, 0, length)) {
                return candidate.index;
            }
        }
        for (Candidate candidate : utf8Only) {
            if (candidate.length == length && Arrays.equals(buffer, start, end, candidate.bytes, 0, length)) {
                return candidate.index;
            }
        }
        return -1;
    }

    DecodedTrie decodedTrie() {
        return decodedTrie;
    }

    // The upper word is the closing quote position; the lower word is the matched index.
    // A negative result requests the parser's decoding path without having consumed any input.
    long match(byte[] buffer, int start, int limit) {
        if (limit - start >= Long.BYTES && limit - start > offset) {
            // Packed candidates include the closing quote, so a matching prefix cannot identify a longer string.
            long word = (long) LONG_VIEW.get(buffer, start);
            for (Candidate candidate = buckets[buffer[start + offset] & 0x7f]; candidate != null; candidate = candidate.next) {
                if (candidate.length < Long.BYTES && (word & candidate.mask) == candidate.word) {
                    return ((long) (start + candidate.length) << 32) | candidate.index;
                }
                if (candidate.length >= Long.BYTES && candidate.length < 2 * Long.BYTES
                        && word == candidate.word && limit - start >= 2 * Long.BYTES
                        && ((long) LONG_VIEW.get(buffer, start + Long.BYTES) & candidate.mask) == candidate.tail) {
                    return ((long) (start + candidate.length) << 32) | candidate.index;
                }
            }
        } else if (shortNames && limit - start >= Integer.BYTES) {
            // An indexed name shorter than four bytes guarantees that four bytes also cover the selector.
            int word = (int) INT_VIEW.get(buffer, start);
            for (Candidate candidate = buckets[buffer[start + offset] & 0x7f]; candidate != null; candidate = candidate.next) {
                if (candidate.length < Integer.BYTES && (word & (int) candidate.mask) == (int) candidate.word) {
                    return ((long) (start + candidate.length) << 32) | candidate.index;
                }
            }
        }
        return matchBytes(buffer, start, limit);
    }

    private long matchShortCandidates(byte[] buffer, int start, int limit) {
        int available = limit - start;
        long word = available >= Long.BYTES ? (long) LONG_VIEW.get(buffer, start) : 0;
        int shortWord = available >= Integer.BYTES && available < Long.BYTES ? (int) INT_VIEW.get(buffer, start) : 0;
        for (Candidate candidate = shortCandidates; candidate != null; candidate = candidate.next) {
            if (available >= Long.BYTES && candidate.length < Long.BYTES) {
                if ((word & candidate.mask) == candidate.word) {
                    return ((long) (start + candidate.length) << 32) | candidate.index;
                }
                continue;
            }
            if (available >= 2 * Long.BYTES && candidate.length < 2 * Long.BYTES) {
                if (word == candidate.word
                        && ((long) LONG_VIEW.get(buffer, start + Long.BYTES) & candidate.mask) == candidate.tail) {
                    return ((long) (start + candidate.length) << 32) | candidate.index;
                }
                continue;
            }
            if (available >= Integer.BYTES && candidate.length < Integer.BYTES) {
                if ((shortWord & (int) candidate.mask) == (int) candidate.word) {
                    return ((long) (start + candidate.length) << 32) | candidate.index;
                }
                continue;
            }
            if (candidate.length < available) {
                int end = start + candidate.length;
                if (buffer[end] == '"' && Arrays.equals(buffer, start, end, candidate.bytes, 0, candidate.length)) {
                    return ((long) end << 32) | candidate.index;
                }
            }
        }
        return Long.MIN_VALUE;
    }

    private long matchBytes(byte[] buffer, int start, int limit) {
        if (start == limit) {
            return Long.MIN_VALUE;
        }
        byte initial = buffer[start];
        if (initial == '"') {
            return ((long) start << 32) | (emptyIndex & 0xffff_ffffL);
        }
        if (limit - start > offset) {
            for (Candidate candidate = buckets[buffer[start + offset] & 0x7f]; candidate != null; candidate = candidate.next) {
                byte[] expected = candidate.bytes;
                if (expected.length < limit - start) {
                    int end = start + expected.length;
                    if (buffer[end] == '"' && Arrays.equals(buffer, start, end, expected, 0, expected.length)) {
                        return ((long) end << 32) | candidate.index;
                    }
                } else if (Arrays.equals(buffer, start, limit, expected, 0, limit - start)) {
                    // A complete prefix contains no closing quote; let the parser continue across the buffer boundary.
                    return Long.MIN_VALUE;
                }
            }
        }
        if (shortCandidates != null) {
            long match = matchShortCandidates(buffer, start, limit);
            if (match >= 0) {
                return match;
            }
        }
        // Unknown names still need to be consumed and validated in full.
        for (int end = start; end < limit; end++) {
            byte b = buffer[end];
            if (b == '"') {
                return ((long) end << 32) | 0xffff_ffffL;
            }
            if (b < 0x20 || b == '\\') {
                return Long.MIN_VALUE;
            }
        }
        return Long.MIN_VALUE;
    }

    static final class DecodedTrie {
        private final long[] decodedNodes;
        private final int[] decodedIndexes;
        private final char[] branchLabels;
        private final int[] branchTargets;
        private final int[] branchOffsets;

        private DecodedTrie(char[][] values) {
            char[][] sorted = values.clone();
            Arrays.sort(sorted, Arrays::compare);
            int[] indexes = new int[values.length];
            for (int index = 0; index < values.length; index++) {
                indexes[Arrays.binarySearch(sorted, values[index], Arrays::compare)] = index;
            }
            int[] prefixes = new int[values.length];
            int nodeCount = 1;
            int maxLength = 0;
            for (int index = 0; index < sorted.length; index++) {
                char[] value = sorted[index];
                int prefix = index == 0 ? 0 : Arrays.mismatch(sorted[index - 1], value);
                prefixes[index] = prefix;
                nodeCount = Math.addExact(nodeCount, value.length - prefix);
                maxLength = Math.max(maxLength, value.length);
            }
            this.decodedNodes = new long[nodeCount];
            this.decodedIndexes = new int[nodeCount];
            Arrays.fill(decodedIndexes, -1);
            int[] path = new int[maxLength + 1];
            int state = 0;
            int branchCount = 0;
            int edgeCount = 0;
            // Sorted suffixes emit preorder states; the upper word temporarily counts each node's children.
            for (int index = 0; index < sorted.length; index++) {
                char[] value = sorted[index];
                int parent = path[prefixes[index]];
                for (int depth = prefixes[index]; depth < value.length; depth++) {
                    long node = decodedNodes[parent];
                    int children = (int) (node >>> 32);
                    if (children == 0) {
                        node = value[depth] + 1;
                    } else if (children == 1) {
                        branchCount++;
                        edgeCount += 2;
                    } else {
                        edgeCount++;
                    }
                    decodedNodes[parent] = node + (1L << 32);
                    parent = ++state;
                    path[depth + 1] = parent;
                }
                decodedIndexes[parent] = indexes[index];
            }
            this.branchLabels = new char[edgeCount];
            this.branchTargets = new int[edgeCount];
            this.branchOffsets = new int[branchCount + 1];
            int branch = 0;
            int edge = 0;
            for (state = 0; state < nodeCount; state++) {
                long node = decodedNodes[state];
                int children = (int) (node >>> 32);
                if (children > 1) {
                    branchOffsets[branch] = edge;
                    // The upper word now holds this branch's next edge slot while its children are emitted.
                    decodedNodes[state] = ((long) edge << 32) | (~branch++ & 0xffff_ffffL);
                    edge += children;
                } else {
                    decodedNodes[state] = node & 0xffff_ffffL;
                }
            }
            branchOffsets[branchCount] = edge;
            state = 0;
            for (int index = 0; index < sorted.length; index++) {
                char[] value = sorted[index];
                int parent = path[prefixes[index]];
                for (int depth = prefixes[index]; depth < value.length; depth++) {
                    long node = decodedNodes[parent];
                    if ((int) node < 0) {
                        edge = (int) (node >>> 32);
                        branchLabels[edge] = value[depth];
                        branchTargets[edge] = state + 1;
                        decodedNodes[parent] = node + (1L << 32);
                    }
                    parent = ++state;
                    path[depth + 1] = parent;
                }
            }
            for (state = 0; state < nodeCount; state++) {
                int asciiWord = 0;
                for (int i = 0; i < Integer.BYTES; i++) {
                    int transition = (int) decodedNodes[state + i];
                    int c = transition - 1;
                    if (c < 0x20 || c >= 0x80 || c == '"' || c == '\\') {
                        asciiWord = 0;
                        break;
                    }
                    asciiWord |= c << (i * Byte.SIZE);
                }
                // Upper word: four raw ASCII singleton edges, or zero. Lower word: transition descriptor.
                decodedNodes[state] = ((long) asciiWord << 32) | (decodedNodes[state] & 0xffff_ffffL);
            }
        }

        long node(int state) {
            return decodedNodes[state];
        }

        // State zero is the root; negative states remain unmatched until the parser finishes validation.
        int advance(int state, int transition, char value) {
            if (state < 0) {
                return -1;
            }
            if (transition >= 0) {
                return transition == value + 1 ? state + 1 : -1;
            }
            return advanceBranch(~transition, value);
        }

        int match(int state) {
            return state < 0 ? -1 : decodedIndexes[state];
        }

        private int advanceBranch(int branch, char value) {
            int index = Arrays.binarySearch(branchLabels, branchOffsets[branch], branchOffsets[branch + 1], value);
            return index < 0 ? -1 : branchTargets[index];
        }
    }

    private static final class Candidate {
        private final byte[] bytes;
        private final int index;
        private final int length;
        private final Candidate next;
        private final long word;
        private final long mask;
        private final long tail;

        private Candidate(byte[] bytes, int index, Candidate next) {
            this.bytes = bytes;
            this.index = index;
            this.length = bytes.length;
            this.next = next;
            if (bytes.length < Long.BYTES) {
                long word = (long) '"' << (bytes.length * Byte.SIZE);
                for (int i = 0; i < bytes.length; i++) {
                    word |= (bytes[i] & 0xffL) << (i * Byte.SIZE);
                }
                this.word = word;
                this.mask = -1L >>> ((Long.BYTES - bytes.length - 1) * Byte.SIZE);
                this.tail = 0;
            } else if (bytes.length < 2 * Long.BYTES) {
                this.word = (long) LONG_VIEW.get(bytes, 0);
                long tail = (long) '"' << ((bytes.length - Long.BYTES) * Byte.SIZE);
                for (int i = Long.BYTES; i < bytes.length; i++) {
                    tail |= (bytes[i] & 0xffL) << ((i - Long.BYTES) * Byte.SIZE);
                }
                this.tail = tail;
                this.mask = -1L >>> ((2 * Long.BYTES - bytes.length - 1) * Byte.SIZE);
            } else {
                this.word = 0;
                this.mask = 0;
                this.tail = 0;
            }
        }
    }
}
