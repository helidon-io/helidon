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
package io.helidon.metrics;

import java.time.Duration;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.microprofile.metrics.MetricUnits;
import org.junit.platform.commons.JUnitException;

class TestUtils {
    static long secondsToMetricUnits(String metricUnits, Duration duration) {
        return switch (metricUnits) {
            case MetricUnits.SECONDS -> duration.toSeconds();
            case MetricUnits.NANOSECONDS -> duration.toNanos();
            case MetricUnits.MICROSECONDS -> duration.toNanos() / 1000;
            case MetricUnits.MILLISECONDS -> duration.toMillis();
            default -> throw new IllegalArgumentException("Unrecognized metric units value " + metricUnits);
        };
    }

    /**
     * Locates and extracts a numeric value preceded on the same line by the specified label within a larger string.
     *
     * @param wholeString the entire String to be searched
     * @param label the identifying label preceding the value to be parsed
     * @return the double of the matched value
     */
    static <T extends Number> T valueAfterLabel(String wholeString, String label, Function<String, T> parser) {
        Pattern pattern = Pattern.compile("^" + Pattern.quote(label) + "\\s*(\\S*)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(wholeString);
        if (!matcher.find()) {
            throw new JUnitException("Unable to find value with label " + label + " in string " + wholeString);
        }
        String valueText = matcher.group(1);
        return parser.apply(valueText);
    }
}
