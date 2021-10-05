/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.metrics;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Custom Hamcrest matchers used in metrics tests.
 */
class MetricsCustomMatchers {

    /**
     * A group of matchers dealing with map contents.
     */
    static abstract class MapContains extends TypeSafeMatcher<Map<?, ?>> {

        static All all(Map<?, ?> targetValues) {
            return new All(targetValues);
        }

        static None none(Map<?, ?> targetValues) {
            return new None(targetValues);
        }

        private final Map<?, ?> targetValues;
        private final String descriptionLabel;

        MapContains(Map<?, ?> targetValues, String descriptionLabel) {
            this.targetValues = targetValues;
            this.descriptionLabel = descriptionLabel;
        }

        protected Map<?, ?> targetValues() {
            return targetValues;
        }

        protected Predicate<Map.Entry<?, ?>> entryChecker(Map<?, ?> candidateMap) {
            return (Map.Entry<?, ?> expectedEntry) -> candidateMap.containsKey(expectedEntry.getKey())
                    && candidateMap.get(expectedEntry.getKey())
                    .equals(expectedEntry.getValue());
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("tags containing ")
                    .appendText(descriptionLabel)
                    .appendText(" of ")
                    .appendText(targetValues.toString());
        }

        /**
         * Matcher for checking that all key/value pairs in the target values appear in the candidate map.
         */
        static class All extends MapContains {

            All(Map<?, ?> targetValues) {
                super(targetValues, "all");
            }

            @Override
            protected boolean matchesSafely(Map<?, ?> candidateMap) {
                return targetValues().entrySet()
                        .stream()
                        .allMatch(entryChecker(candidateMap));
            }
        }

        static class None extends MapContains {

            None(Map<?, ?> targetValues) {
                super(targetValues, "none");
            }

            @Override
            protected boolean matchesSafely(Map<?, ?> candidateMap) {
                return targetValues().entrySet()
                        .stream()
                        .noneMatch(entryChecker(candidateMap));
            }
        }
    }
}
