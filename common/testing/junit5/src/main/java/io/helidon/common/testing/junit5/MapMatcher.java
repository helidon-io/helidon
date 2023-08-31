/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.common.testing.junit5;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

/**
 * Hamcrest matchers for {@link java.util.Map}.
 */
public final class MapMatcher {
    private MapMatcher() {
    }

    /**
     * A matcher for an {@link java.util.Map} that performs a deep equality.
     * <p>
     * Usage example:
     * <pre>
     *     assertThat(actualMap, isMapEqualTo(expectedMap));
     * </pre>
     *
     * This method targets trees implemented using {@link java.util.Map} where values of type {@link java.util.Map}
     * are considered tree nodes, and values with other types are considered leaf nodes.
     * <p>
     * The deep-equality is performed by diffing a flat string representation of each map. If the diff yields no differences,
     * the maps are considered deeply equal.
     * <p>
     * The entries are compared using strings, both keys and leaf nodes must implement {@link Object#toString()}.
     *
     * @param expected expected map
     * @param <K> type of the map keys
     * @param <V> type of the map values
     * @return matcher validating the {@link java.util.Map} is deeply equal
     */
    public static <K, V> Matcher<Map<K, V>> mapEqualTo(Map<K, V> expected) {
        return new DiffMatcher<>(expected);
    }

    private static final class DiffMatcher<K, V> extends TypeSafeMatcher<Map<K, V>> {

        private final Map<K, V> expected;
        private volatile Map<K, V> actual;
        private volatile List<Diff> diffs;

        private DiffMatcher(Map<K, V> expected) {
            this.expected = expected;
        }

        @Override
        protected boolean matchesSafely(Map<K, V> actual) {
            this.actual = actual;
            this.diffs = diffs(expected, actual);
            return diffs.isEmpty();
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("deep map equality");
        }

        @Override
        protected void describeMismatchSafely(Map<K, V> item, Description mismatchDescription) {
            List<Diff> diffs = actual == item ? this.diffs : diffs(expected, item);
            mismatchDescription.appendText("found differences" + System.lineSeparator())
                    .appendText(String.join(System.lineSeparator(), diffs.stream().map(Diff::toString).toList()));
        }

        private static List<Diff> diffs(Map<?, ?> left, Map<?, ?> right) {
            List<Diff> diffs = new ArrayList<>();
            Iterator<Map.Entry<String, String>> leftEntries = flattenEntries(left, "").iterator();
            Iterator<Map.Entry<String, String>> rightEntries = flattenEntries(right, "").iterator();
            while (true) {
                boolean hasLeft = leftEntries.hasNext();
                boolean hasRight = rightEntries.hasNext();
                if (hasLeft && hasRight) {
                    Map.Entry<String, String> leftEntry = leftEntries.next();
                    Map.Entry<String, String> rightEntry = rightEntries.next();
                    if (!leftEntry.equals(rightEntry)) {
                        diffs.add(new Diff(leftEntry, rightEntry));
                    }
                } else if (hasLeft) {
                    diffs.add(new Diff(leftEntries.next(), null));
                } else if (hasRight) {
                    diffs.add(new Diff(null, rightEntries.next()));
                } else {
                    return diffs;
                }
            }
        }

        private static List<Map.Entry<String, String>> flattenEntries(Map<?, ?> map, String prefix) {
            List<Map.Entry<String, String>> result = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getValue() instanceof Map<?, ?> node) {
                    result.addAll(flattenEntries(node, prefix + entry.getKey() + "."));
                } else {
                    result.add(Map.entry(prefix + entry.getKey(), entry.getValue().toString()));
                }
            }
            result.sort(Map.Entry.comparingByKey());
            return result;
        }

        private record Diff(Map.Entry<String, String> left, Map.Entry<String, String> right) {

            @Override
            public String toString() {
                if (left == null && right != null) {
                    return "ADDED   >> " + right;
                }
                if (left != null && right == null) {
                    return "REMOVED << " + left;
                }
                if (left != null) {
                    return "ADDED   >> " + left + System.lineSeparator() + "REMOVED << " + right;
                }
                return "?";
            }
        }
    }
}
