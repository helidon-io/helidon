/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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
package io.helidon.microprofile.metrics;

import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

/**
 * Hamcrest Matcher for Metrics-related value checking.
 * <p>
 * Includes:
 * <ul>
 * <li>{@link #withinTolerance(Number)} for checking within a <em>configured</em> range (tolerance) either side of an expected
 * value</li>
 * <li>{@link #withinTolerance(Number, double)} for checking within a <em>specified</em> range</li>
 * </ul>
 */
class MetricsMatcher {

    static final Double VARIANCE = Double.valueOf(System.getProperty("helidon.histogram.tolerance", "0.001"));

    static TypeSafeMatcher<Number> withinTolerance(final Number expected) {
        return withinTolerance(expected, VARIANCE);
    }

    static TypeSafeMatcher<Number> withinTolerance(final Number expected, double variance) {
        return new TypeSafeMatcher<>() {

            private final double v = variance;

            @Override
            protected boolean matchesSafely(Number item) {
                return Math.abs(expected.doubleValue() - item.doubleValue()) <= expected.doubleValue() * v;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("withinTolerance expected value in range [")
                        .appendValue(expected.doubleValue() * (1.0 - v))
                        .appendText(", ")
                        .appendValue(expected.doubleValue() * (1.0 + v))
                        .appendText("]");
            }
        };
    }
}
