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

import io.helidon.common.Api;

/**
 * Matches complete decoded JSON strings against registered names. The result is the matching ordinal,
 * or a negative value for an unknown string. Generated converters can implement the state transitions
 * directly, starting at state zero. A matcher can be shared across parser instances.
 *
 * @see JsonParser#readString(JsonStringMatcher)
 */
@Api.Preview
@FunctionalInterface
public interface JsonStringMatcher {

    /**
     * Build a matcher for names supplied at runtime.
     *
     * @param names distinct decoded names in ordinal order
     * @return immutable matcher
     * @throws IllegalArgumentException if a name is repeated
     */
    static JsonStringMatcher create(String... names) {
        return PackedAsciiStringMatcher.create(names);
    }

    /**
     * Add a generated ASCII decision tree to a matcher. A selected name is verified against its complete
     * literal and its closing quote before the parser advances. Other input is recognized by the supplied
     * decoded transitions. The order of the names must agree with their decoded ordinals.
     *
     * @param decoded matcher for decoded input
     * @param selector generated selector for short unescaped ASCII names
     * @param names registered names in ordinal order
     * @return matcher with an ASCII fast path
     */
    static JsonStringMatcher withAscii(JsonStringMatcher decoded, AsciiSelector selector, String... names) {
        return AsciiStringMatcher.create(decoded, selector, names);
    }

    /**
     * Add a decision tree for unescaped UTF-8 spellings. The parser verifies the entire encoded
     * name and its closing quote before accepting the selection. Other spellings use the decoded transitions.
     *
     * @param decoded matcher for decoded input
     * @param selector possible ordinal for a bounded two-word window
     * @param offset non-negative byte offset of the selector window relative to the beginning of the name
     * @param names registered names in ordinal order
     * @return matcher with a raw UTF-8 fast path
     * @throws IllegalArgumentException if the offset is negative
     */
    static JsonStringMatcher withUtf8(JsonStringMatcher decoded, Utf8Selector selector, int offset, String... names) {
        return AsciiStringMatcher.withUtf8(decoded, selector, offset, names);
    }

    /**
     * Advance over a block of decoded UTF-16 code units. Byte {@code i} of each word contains the low
     * or high byte of code unit {@code i}, starting with the least significant byte. Unused bytes are zero.
     * A block of eight units continues the string. A shorter block ends it, including an empty block
     * after an exact multiple of eight.
     *
     * @param state current state, initially zero
     * @param low low bytes of the code units
     * @param high high bytes of the code units
     * @param length number of code units, from zero to eight
     * @return next state for a full block, or the recognized ordinal for a final block, or {@code -1} on a miss
     */
    int advance(int state, long low, long high, int length);

    /**
     * Recognize an already decoded string.
     *
     * @param value decoded string
     * @return matching ordinal, or {@code -1}
     */
    default int match(String value) {
        Objects.requireNonNull(value);
        int state = 0;
        int position = 0;
        while (state >= 0) {
            int length = Math.min(Long.BYTES, value.length() - position);
            long low = 0;
            long high = 0;
            for (int i = 0; i < length; i++) {
                char unit = value.charAt(position++);
                low |= (unit & 0xffL) << (i * Byte.SIZE);
                high |= (long) (unit >>> Byte.SIZE) << (i * Byte.SIZE);
            }
            state = advance(state, low, high, length);
            if (length < Long.BYTES) {
                return state;
            }
        }
        return -1;
    }

    /**
     * Select a possible spelling in a bounded raw JSON input window. The window starts after the opening
     * quote, can contain subsequent tokens, and is padded with zeroes at the input limit.
     */
    @FunctionalInterface
    interface AsciiSelector extends Utf8Selector {
        @Override
        int select(long first, long second);
    }

    /**
     * Select a possible literal using a raw UTF-8 window. Bytes following the logical input are zero.
     */
    @FunctionalInterface
    interface Utf8Selector {
        /**
         * Select a possible name using the two little-endian words in the window.
         *
         * @param first first eight input bytes
         * @param second next eight input bytes
         * @return candidate ordinal or {@code -1}
         */
        int select(long first, long second);
    }
}
