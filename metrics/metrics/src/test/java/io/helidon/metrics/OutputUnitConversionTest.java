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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.JUnitException;

import static io.helidon.metrics.HelidonMetricsMatcher.withinTolerance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public class OutputUnitConversionTest {

    private static final String TIMER_NAME = "myTimer";
    private static MetricID TIMER_METRIC_ID;

    private static final String SIMPLE_TIMER_NAME = "mySimpleTimer";
    private static MetricID SIMPLE_TIMER_METRIC_ID;

    private static final int TIMER_UPDATE_INCREMENT_MICRO_SECONDS = 120; // microseconds

    private HelidonTimer prepTimer() {
        TIMER_METRIC_ID = new MetricID(TIMER_NAME);
        TestClock clock = TestClock.create();
        HelidonTimer result = HelidonTimer.create("application", Metadata.builder()
                                                          .withName(TIMER_NAME)
                                                          .withUnit(MetricUnits.MILLISECONDS)
                                                          .withType(MetricType.TIMER)
                                                          .build(),
                                                  clock);
        clock.addNanos(1, TimeUnit.MICROSECONDS);
        result.update(TIMER_UPDATE_INCREMENT_MICRO_SECONDS, TimeUnit.MICROSECONDS);

        // Advance the clock so most-recent-whole-minute stats have meaning.
        clock.addNanos(1, TimeUnit.MINUTES);
        return result;
    }

    private HelidonSimpleTimer prepSimpleTimer() {
        SIMPLE_TIMER_METRIC_ID = new MetricID(SIMPLE_TIMER_NAME);
        TestClock clock = TestClock.create();
        HelidonSimpleTimer result = HelidonSimpleTimer.create("application", Metadata.builder()
                                                                      .withName(SIMPLE_TIMER_NAME)
                                                                      .withUnit(MetricUnits.MILLISECONDS)
                                                                      .withType(MetricType.SIMPLE_TIMER)
                                                                      .build(),
                                                              clock);
        clock.addNanos(1, TimeUnit.MICROSECONDS);
        result.update(Duration.of(TIMER_UPDATE_INCREMENT_MICRO_SECONDS, ChronoUnit.MICROS));

        // Advance the clock.
        clock.addNanos(1, TimeUnit.MINUTES);
        return result;
    }

    @Test
    void testPrometheusTimerConversion() {

        StringBuilder prometheusSB = new StringBuilder();
        HelidonTimer hTimer = prepTimer();
        hTimer.prometheusData(prometheusSB, TIMER_METRIC_ID, false, false);
        double expectedValue = 0.000120D;
        // The Prometheus exposition format always represents time in seconds.
        for (String suffix : new String[] {"_mean_seconds",
                "_seconds{quantile=\"0.5\"}",
                "_seconds{quantile=\"0.75\"}",
                "_seconds{quantile=\"0.95\"}",
                "_seconds{quantile=\"0.98\"}",
                "_seconds{quantile=\"0.99\"}",
                "_seconds{quantile=\"0.999\"}"
        }) {
            String label = "application_" + TIMER_NAME + suffix;
            double v = valueAfterLabel(prometheusSB.toString(), label);
            assertThat("Prometheus data for " + label, v, is(expectedValue));
        }
    }

    @Test
    void testPrometheusSimpleTimerConversion() {
        StringBuilder prometheusSB = new StringBuilder();
        HelidonSimpleTimer hSimpleTimer = prepSimpleTimer();
        hSimpleTimer.prometheusData(prometheusSB, SIMPLE_TIMER_METRIC_ID, false, false);
        // We updated the simple timer by 120 microseconds. Although the simple timer units were set to ms, Prometheus output
        // always is in seconds (for times).
        Duration expectedElapsedTime = Duration.of(TIMER_UPDATE_INCREMENT_MICRO_SECONDS, ChronoUnit.MICROS);
        double expectedElapsedTimeInSeconds = expectedElapsedTime.toNanos() / 1000.0 / 1000.0 / 1000.0;
        String label = "application_" + SIMPLE_TIMER_NAME + "_elapsedTime_seconds";
        double v = valueAfterLabel(prometheusSB.toString(), label);
        assertThat("SimpleTimer Prometheths elapsed time", v, is(withinTolerance(expectedElapsedTimeInSeconds)));
    }

    @Test
    void testTimerJsonOutput() {

        HelidonTimer hTimer = prepTimer();
        JsonObjectBuilder builder = Json.createObjectBuilder();
        hTimer.jsonData(builder, TIMER_METRIC_ID);

        JsonObject json = builder.build()
                .getJsonObject(TIMER_NAME);
        assertThat("Metric JSON object", json, notNullValue());

        // We updated timer by 120 microseconds. The timer units are ms, so the reported values should be 0.120 because
        // JSON output honors the units in the metric's metadata.
        double expectedValue = TIMER_UPDATE_INCREMENT_MICRO_SECONDS / 1000.0;
        for (String suffix : new String[] {"mean", "p50", "p75", "p95", "p98", "p99", "p999"}) {
            JsonNumber number = json.getJsonNumber(suffix);
            assertThat("JsonNumber for data item " + suffix, number, notNullValue());
            assertThat("JSON value for " + suffix, number.doubleValue(), is(expectedValue));
        }
    }

    @Test
    void testSimpleTimerJsonOutput() {
        HelidonSimpleTimer hSimpleTimer = prepSimpleTimer();
        JsonObjectBuilder builder = Json.createObjectBuilder();
        hSimpleTimer.jsonData(builder, SIMPLE_TIMER_METRIC_ID);

        JsonObject json = builder.build()
                .getJsonObject(SIMPLE_TIMER_NAME);
        assertThat("Metric JSON object", json, notNullValue());

        // We updated the simple timer by 120 microseconds. The simple timer units were set to ms, so the reported value should
        // be 0.120 because JSON output honors the units in the metric's metadata.
        Duration expectedElapsedTime = Duration.of(TIMER_UPDATE_INCREMENT_MICRO_SECONDS, ChronoUnit.MICROS);
        double expectedElapsedTimeInMillis = expectedElapsedTime.toNanos() / 1000.0 / 1000.0;
        assertThat("SimpleTimer elapsed time", hSimpleTimer.getElapsedTime(), is(expectedElapsedTime));
        JsonNumber number = json.getJsonNumber("elapsedTime");
        assertThat("JsonNumber for elapsedTime", number, notNullValue());
        assertThat("JSON value for elapsedTime", number.doubleValue(), is(withinTolerance(expectedElapsedTimeInMillis)));
    }

    private double valueAfterLabel(String wholeString, String label) {
        Pattern pattern = Pattern.compile("^" + Pattern.quote(label) + "\\s*(\\S*)$", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(wholeString);
        if (!matcher.find()) {
            throw new JUnitException("Unable to find value with label " + label + " in string " + wholeString);
        }
        String valueText = matcher.group(1);
        return Double.parseDouble(valueText);
    }
}
