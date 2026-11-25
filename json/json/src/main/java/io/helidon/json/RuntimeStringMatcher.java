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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

final class RuntimeStringMatcher implements JsonStringMatcher {
    private final int[] offsets;
    private final Word[] edges;
    private final int[] targets;

    RuntimeStringMatcher(String[] names) {
        Objects.requireNonNull(names);
        List<Map<Word, Integer>> states = new ArrayList<>();
        states.add(new TreeMap<>());
        int edgeCount = 0;
        for (int ordinal = 0; ordinal < names.length; ordinal++) {
            String name = Objects.requireNonNull(names[ordinal]);
            int position = 0;
            Map<Word, Integer> state = states.get(0);
            while (true) {
                Word word = Word.at(name, position);
                if (word.length < Long.BYTES) {
                    if (state.putIfAbsent(word, ordinal) != null) {
                        throw new IllegalArgumentException("Duplicate string in matcher: " + name);
                    }
                    edgeCount++;
                    break;
                }
                Integer next = state.get(word);
                if (next == null) {
                    next = states.size();
                    state.put(word, next);
                    states.add(new TreeMap<>());
                    edgeCount++;
                }
                state = states.get(next);
                position += Long.BYTES;
            }
        }
        offsets = new int[states.size() + 1];
        edges = new Word[edgeCount];
        targets = new int[edgeCount];
        int edge = 0;
        for (int i = 0; i < states.size(); i++) {
            offsets[i] = edge;
            for (var entry : states.get(i).entrySet()) {
                edges[edge] = entry.getKey();
                targets[edge++] = entry.getValue();
            }
        }
        offsets[states.size()] = edge;
    }

    @Override
    public int advance(int state, long low, long high, int length) {
        int from = offsets[state];
        int to = offsets[state + 1];
        while (from < to) {
            int mid = (from + to) >>> 1;
            int comparison = edges[mid].compare(length, low, high);
            if (comparison < 0) {
                from = mid + 1;
            } else if (comparison > 0) {
                to = mid;
            } else {
                return targets[mid];
            }
        }
        return -1;
    }

    private record Word(int length, long low, long high) implements Comparable<Word> {
        @Override
        public int compareTo(Word other) {
            return compare(other.length, other.low, other.high);
        }

        private static Word at(String value, int position) {
            int length = Math.min(Long.BYTES, value.length() - position);
            long low = 0;
            long high = 0;
            for (int i = 0; i < length; i++) {
                char unit = value.charAt(position + i);
                low |= (unit & 0xffL) << (i * Byte.SIZE);
                high |= (long) (unit >>> Byte.SIZE) << (i * Byte.SIZE);
            }
            return new Word(length, low, high);
        }

        private int compare(int otherLength, long otherLow, long otherHigh) {
            int result = Integer.compare(length, otherLength);
            if (result == 0) {
                result = Long.compare(low, otherLow);
            }
            return result != 0 ? result : Long.compare(high, otherHigh);
        }
    }
}
