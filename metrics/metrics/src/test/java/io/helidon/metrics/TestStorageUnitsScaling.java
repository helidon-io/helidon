/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.commons.JUnitException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Prometheus output should always express storage quantities in bytes. This test makes sure that the output is correct and
 * has the correctly-scaled value for each of the units in the storage family.
 */
public class TestStorageUnitsScaling {

    private static final int EXPECTED_VALUE = 656;

    @ParameterizedTest
    @MethodSource("scalingValues")
    void checkGaugeScaling(String units, long expectedValue) {
        Metadata gaugeMetadata = Metadata.builder()
                .withName("myGauge")
                .withType(MetricType.GAUGE)
                .withUnit(units)
                .build();

        Gauge<Integer> myGauge = () -> EXPECTED_VALUE;

        HelidonGauge<Integer> hGauge = HelidonGauge.create("application", gaugeMetadata, myGauge);
        MetricID gaugeMetricID = new MetricID(gaugeMetadata.getName());

        StringBuilder sb = new StringBuilder();
        hGauge.prometheusData(sb, gaugeMetricID, true, false);

        Pattern pattern = Pattern.compile("# TYPE application_myGauge_bytes gauge\n"
                                + "# HELP application_myGauge_bytes \n"
                                + "application_myGauge_bytes (\\S*)");

        Matcher matcher = pattern.matcher(sb.toString());

        if (!matcher.find()) {
            throw new JUnitException("Unable to match Prometheus output " + sb + " to expected pattern " + pattern);
        }
        assertThat("Scaled " + EXPECTED_VALUE + " " + units, Long.parseLong(matcher.group(1)), is(expectedValue));
    }

    private static Arguments[] scalingValues() {
        long expected = EXPECTED_VALUE;
        return new Arguments[] {
                Arguments.arguments(MetricUnits.BYTES, expected),
                Arguments.arguments(MetricUnits.KILOBYTES, expected * 1000),
                Arguments.arguments(MetricUnits.MEGABYTES, expected * 1000 * 1000),
                Arguments.arguments(MetricUnits.GIGABYTES, expected * 1000 * 1000 * 1000),

                Arguments.arguments(MetricUnits.BITS, expected / 8),
                Arguments.arguments(MetricUnits.KILOBITS, expected / 8 * 1000),
                Arguments.arguments(MetricUnits.MEGABITS, expected / 8 * 1000 * 1000),
                Arguments.arguments(MetricUnits.GIGABITS, expected / 8 * 1000 * 1000 * 1000),

                Arguments.arguments(MetricUnits.KIBIBITS, expected / 8 * 1024),
                Arguments.arguments(MetricUnits.MEBIBITS, expected / 8 * 1024 * 1024),
                Arguments.arguments(MetricUnits.GIBIBITS, expected / 8 * 1024 * 1024 * 1024)
        };
    }
}
