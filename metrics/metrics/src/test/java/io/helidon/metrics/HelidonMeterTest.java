/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.metrics.HelidonMetricsMatcher.withinTolerance;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link HelidonMeter}.
 */
class HelidonMeterTest {
    private static final String EXPECTED_PROMETHEUS_START = "# TYPE application_requests_total counter\n"
            + "# HELP application_requests_total Tracks the number of requests to the server\n"
            + "application_requests_total 1000\n"
            + "# TYPE application_requests_rate_per_second gauge\n"
            + "application_requests_rate_per_second ";
    private static HelidonMeter meter;
    private static MetricID meterID;

    @BeforeAll
    static void initClass() throws InterruptedException {
        Metadata meta = Metadata.builder()
				.withName("requests")
				.withDisplayName("Requests")
				.withDescription("Tracks the number of requests to the server")
				.withType(MetricType.METERED)
				.withUnit(MetricUnits.PER_SECOND)
				.build();

        LongAdder nanoTime = new LongAdder();
        LongAdder milliTime = new LongAdder();
        milliTime.add(System.currentTimeMillis());

        Clock myClock = new Clock() {
            @Override
            public long nanoTick() {
                return nanoTime.sum();
            }

            @Override
            public long milliTime() {
                return milliTime.sum();
            }
        };
        meter = HelidonMeter.create("application", meta, myClock);
        meterID = new MetricID("requests");

        // now run the "load"
        int count = 100;
        int markSeconds = 10;

        for (int i = 0; i < markSeconds; i++) {
            nanoTime.add(TimeUnit.SECONDS.toNanos(1));
            milliTime.add(TimeUnit.SECONDS.toNanos(1));
            meter.mark(count);
        }
    }

    @Test
    void testCount() {
        assertThat(meter.getCount(), CoreMatchers.is(1000L));
    }

    @Test
    void testMeanRate() {
        assertThat("mean rate", meter.getMeanRate(),  is(withinTolerance(100)));
    }

    @Test
    void testOneMinuteRate() {
        assertThat("one minute rate", meter.getOneMinuteRate(),  is(withinTolerance(100)));
    }

    @Test
    void testFiveMinuteRate() {
        assertThat("five minute rate", meter.getFiveMinuteRate(),  is(withinTolerance(100)));
    }

    @Test
    void testFifteenMinuteRate() {
        assertThat("fifteen minute rate", meter.getFifteenMinuteRate(),  is(withinTolerance(100)));
    }

    @Test
    void testJson() {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        meter.jsonData(builder, new MetricID("requests"));

        JsonObject result = builder.build();

        JsonObject metricData = result.getJsonObject("requests");
        assertThat(metricData, notNullValue());
        assertThat(metricData.getInt("count"), is(1000));
        assertThat(metricData.getJsonNumber("meanRate").doubleValue(), closeTo(100, 0.1));
        assertThat(metricData.getJsonNumber("oneMinRate").doubleValue(), closeTo(100, 0.1));
        assertThat(metricData.getJsonNumber("fiveMinRate").doubleValue(), closeTo(100, 0.1));
        assertThat(metricData.getJsonNumber("fifteenMinRate").doubleValue(), closeTo(100, 0.1));

    }

    @Test
    void testPrometheus() {
        final StringBuilder sb = new StringBuilder();
        meter.prometheusData(sb, meterID, true);
        String data = sb.toString();

        assertThat(data, startsWith(EXPECTED_PROMETHEUS_START));
        assertThat(data, containsString("# TYPE application_requests_one_min_rate_per_second gauge\n"
                                                + "application_requests_one_min_rate_per_second "));

        assertThat(data, containsString("# TYPE application_requests_five_min_rate_per_second gauge\n"
                                                + "application_requests_five_min_rate_per_second "));

        assertThat(data, containsString("# TYPE application_requests_fifteen_min_rate_per_second gauge\n"
                                                + "application_requests_fifteen_min_rate_per_second "));

    }
}
