/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.config.testing;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyIterable;

/**
 * Simple framework for checking case-sensitive or case-insensitive behavior of containers such as headers and parameters.
 */
public class CaseSensitivityTester {
    private static final String VLOWER = "lower";
    private static final String VMIXED = "Mixed";
    private static final String VUPPER = "UPPER";
    private static final String VLIST = "LiSt";
    private static final List<String> LIST_VALUES = List.of("v1", "v2");


    private static final Map<String, List<String>> TEST_MAP = Map.of(
            VLOWER, List.of("lower-value"),
            VMIXED, List.of("Mixed-value"),
            VUPPER, List.of("UPPER_value"),
            VLIST, LIST_VALUES);

    // Maps each key to its changed values we use to expect matches in headers and no matches elsewhere.
    private static final Map<String, List<String>> CHANGED_KEYS = Map.of(
            VLOWER, List.of(VLOWER.toUpperCase(Locale.ROOT), "Lower"),
            VMIXED, List.of(VMIXED.toLowerCase(Locale.ROOT), VMIXED.toUpperCase(Locale.ROOT), "mIxEd"),
            VUPPER, List.of(VUPPER.toLowerCase(Locale.ROOT), "Upper", "upper"),
            VLIST, List.of(VLIST.toLowerCase(Locale.ROOT), VLIST.toUpperCase(Locale.ROOT), "lIsT"));

    private CaseSensitivityTester() {
    }

    /**
     *
     * @return test map containing data to populate containers under test
     */
    public static Map<String, List<String>> testMap() {
        return TEST_MAP;
    }

    /**
     * Performs case-sensitive and case-insensitive testing of the supplied data,
     * using strict case matching: changing the case of a valid key should return no results.
     *
     * @param dataStructure data structure to be tested
     * @param valueGetter function which accepts a key and returns an {@code Iterable<String>}
     * @param <T> type of the data structure
     */
    public static <T> void testStrict(T dataStructure,
                                      BiFunction<T, String, Iterable<String>> valueGetter) {
        // With strict treatment, using case-altered keys should return an empty iterable.
        test(dataStructure, valueGetter, true);
    }

    /**
     * Performs case-sensitive and case-insensitive testing of the supplied data,
     * using lenient case matching: changing the case of a valid key should return the same results as
     * when using the valid key.
     *
     * @param dataStructure data structure to be tested
     * @param valueGetter function which accepts a key and returns an {@code Iterable<String>}
     * @param <T> type of the data structure
     */
    public static <T> void testLenient(T dataStructure,
                                       BiFunction<T, String, Iterable<String>> valueGetter) {
        // With lenient treatment, using case-altered keys should return the same values as the unaltered key would.
        test(dataStructure, valueGetter, false);
    }

    /**
     * Makes sure that the data structure (headers, parameters, etc.) responds with the correct values when using the exact
     * keys and also responds with either values or nothing when using case-altered keys, whichever is correct for the data
     * structure. (The test data is set up so that case-altered keys are not actually present in the data structure.)
     *
     * @param dataStructure the data structure containing key/list-of-values pairs
     * @param valueGetter function which accepts a key and returns the corresponding list of values
     * @param isStrict whether the check should be strict (vs. lenient)
     * @param <T> type of the data structure being queried (inferred from the arguments)
     */
    private static <T> void test(T dataStructure,
                                 BiFunction<T, String, Iterable<String>> valueGetter,
                                 boolean isStrict) {
        // Use the correct keys which should always return the expected value from the data structure.
        for (Map.Entry<String, List<String>> entry : TEST_MAP.entrySet()) {
            String key = entry.getKey();
            String[] expectedValue = TEST_MAP.get(key).toArray(new String[0]);
            assertThat("Values found with key as-is '" + key + "'",
                       valueGetter.apply(dataStructure, key),
                       containsInAnyOrder(expectedValue));
        }

        // Use case-altered keys and check the returned values against the provided matcher, because that varies depending
        // on which data structure we query.
        for (Map.Entry<String, List<String>> changedKeyEntry : CHANGED_KEYS.entrySet()) {
            String validKey = changedKeyEntry.getKey();
            for (String changedKey : changedKeyEntry.getValue()) {
                assertThat("Values found with key '" + validKey + "' altered to '" + changedKey + "'",
                           valueGetter.apply(dataStructure, changedKey),
                           isStrict ? emptyIterable() : containsInAnyOrder(TEST_MAP.get(validKey).toArray(new String[0])));
            }
        }
    }
}
