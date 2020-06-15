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

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Timer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.metrics.HelidonMetricsMatcher.withinTolerance;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link HelidonTimer}.
 */
class HelidonTimerTest {
    private static final long[] SAMPLE_LONG_DATA = {0, 10, 20, 20, 20, 30, 30, 30, 30, 30, 40, 50, 50, 60, 70, 70, 70, 80, 90,
            90, 100, 110, 110, 120, 120, 120, 120, 130, 130, 130, 130, 140, 140, 150, 150, 170, 180, 180, 200, 200, 200,
            210, 220, 220, 220, 240, 240, 250, 250, 270, 270, 270, 270, 270, 270, 270, 280, 280, 290, 300, 310, 310,
            320, 320, 330, 330, 360, 360, 360, 360, 370, 380, 380, 380, 390, 400, 400, 410, 420, 420, 420, 430, 440,
            440, 440, 450, 450, 450, 460, 460, 460, 460, 470, 470, 470, 470, 470, 470, 480, 480, 490, 490, 500, 510,
            520, 520, 520, 530, 540, 540, 550, 560, 560, 570, 570, 590, 590, 600, 610, 610, 620, 620, 630, 640, 640,
            640, 650, 660, 660, 660, 670, 670, 680, 680, 700, 710, 710, 710, 710, 720, 720, 720, 720, 730, 730, 740,
            740, 740, 750, 750, 760, 760, 760, 770, 780, 780, 780, 800, 800, 810, 820, 820, 820, 830, 830, 840, 840,
            850, 870, 870, 880, 880, 880, 890, 890, 890, 890, 900, 910, 920, 920, 920, 930, 940, 950, 950, 950, 960,
            960, 960, 960, 970, 970, 970, 970, 980, 980, 980, 990, 990};

    private static HelidonTimer timer;
    private static HelidonTimer dataSetTimer;
    private static MetricID dataSetTimerID;
    private static TestClock timerClock = TestClock.create();
    private static Metadata meta;
    private static TestClock dataSetTimerClock = TestClock.create();

    @BeforeAll
    static void initClass() {
        meta = new HelidonMetadata("response_time",
                            "Responses",
                            "Server response time for /index.html",
                            MetricType.TIMER,
                            MetricUnits.NANOSECONDS);

        dataSetTimer = HelidonTimer.create("application", meta, dataSetTimerClock);
        dataSetTimerID = new MetricID("response_time");

        for (long i : SAMPLE_LONG_DATA) {
            dataSetTimer.update(i, TimeUnit.NANOSECONDS);
        }

        timer = HelidonTimer.create("application", meta, timerClock);
        // now run the "load"
        int markSeconds = 30;

        for (int i = 0; i < markSeconds; i++) {
            timerClock.add(1, TimeUnit.SECONDS);
            timer.update(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void testRate() {
        assertThat(timer.getMeanRate(), is(1.0));
        assertThat(timer.getOneMinuteRate(), is(1.0));
        assertThat(timer.getFiveMinuteRate(), is(1.0));
        assertThat(timer.getFifteenMinuteRate(), is(1.0));

        timerClock.add(30, TimeUnit.SECONDS);

        assertThat(timer.getOneMinuteRate(), lessThan(1.0));
        assertThat(timer.getFiveMinuteRate(), lessThan(1.0));
        assertThat(timer.getFifteenMinuteRate(), lessThan(1.0));
    }

    @Test
    void testContextTime() {
        TestClock clock = TestClock.create();
        Timer timer = HelidonTimer.create("application", meta, clock);
        Timer.Context context = timer.time();

        clock.add(1, TimeUnit.SECONDS);

        long diff = context.stop();

        assertThat(diff, is(TimeUnit.SECONDS.toNanos(1)));
    }

    @Test
    void testCallableTiming() throws Exception {
        TestClock clock = TestClock.create();
        Timer timer = HelidonTimer.create("application", meta, clock);

        String result = timer.time(() -> {
            clock.add(1, TimeUnit.SECONDS);
            return "hello";
        });

        assertThat(timer.getMeanRate(), closeTo(1.0, 0.01));
        assertThat(timer.getCount(), is(1L));
        assertThat(result, is("hello"));
    }

    @Test
    void testRunnableTiming() {
        TestClock clock = TestClock.create();
        Timer timer = HelidonTimer.create("application", meta, clock);

        timer.time(() -> clock.add(1, TimeUnit.SECONDS));

        assertThat(timer.getMeanRate(), closeTo(1.0, 0.01));
        assertThat(timer.getCount(), is(1L));
    }

    @Test
    void testDataSet() {
        assertThat(dataSetTimer.getSnapshot().getValues(), is(SAMPLE_LONG_DATA));
    }

    @Test
    void testSnapshot() {
        Snapshot snapshot = dataSetTimer.getSnapshot();

        assertAll("Testing statistical values for snapshot",
                  () -> assertThat("median", snapshot.getMedian(), is(withinTolerance(480))),
                  () -> assertThat("75th percentile", snapshot.get75thPercentile(), is(withinTolerance(750))),
                  () -> assertThat("95th percentile", snapshot.get95thPercentile(), is(withinTolerance(960))),
                  () -> assertThat("78th percentile", snapshot.get98thPercentile(), is(withinTolerance(980))),
                  () -> assertThat("99th percentile", snapshot.get99thPercentile(), is(withinTolerance(980))),
                  () -> assertThat("999th percentile", snapshot.get999thPercentile(), is(withinTolerance(990))),
                  () -> assertThat("mean", snapshot.getMean(), is(withinTolerance(506.3))),
                  () -> assertThat("stddev", snapshot.getStdDev(), is(withinTolerance(294.3))),
                  () -> assertThat("min", snapshot.getMin(), Matchers.is(0L)),
                  () -> assertThat("max", snapshot.getMax(), Matchers.is(990L)),
                  () -> assertThat("size", snapshot.size(), Matchers.is(200))
        );
    }

    @Test
    void testJson() {
        dataSetTimerClock.addNanos(1, TimeUnit.SECONDS);
        dataSetTimerClock.setMillis(System.currentTimeMillis());

        JsonObjectBuilder builder = Json.createObjectBuilder();
        dataSetTimer.jsonData(builder, dataSetTimerID);

        JsonObject json = builder.build();
        JsonObject metricData = json.getJsonObject("response_time");

        assertThat(metricData, notNullValue());
        assertThat("count", metricData.getJsonNumber("count").longValue(), is(withinTolerance(200L)));
        assertThat("min", metricData.getJsonNumber("min").longValue(), is(withinTolerance(0L)));
        assertThat("max", metricData.getJsonNumber("max").longValue(), is(withinTolerance(990L)));
        assertThat("mean", metricData.getJsonNumber("mean").doubleValue(),  is(withinTolerance(506.349)));
        assertThat("stddev", metricData.getJsonNumber("stddev").doubleValue(),  is(withinTolerance(294.389)));
        assertThat(metricData.getJsonNumber("p50").intValue(), is(withinTolerance(480)));
        assertThat(metricData.getJsonNumber("p75").intValue(), is(withinTolerance(750)));
        assertThat(metricData.getJsonNumber("p95").intValue(), is(withinTolerance(960)));
        assertThat(metricData.getJsonNumber("p98").intValue(), is(withinTolerance(980)));
        assertThat(metricData.getJsonNumber("p99").intValue(), is(withinTolerance(980)));
        assertThat(metricData.getJsonNumber("p999").intValue(), is(withinTolerance(990)));
        assertThat(metricData.getJsonNumber("meanRate").intValue(), is(withinTolerance(200)));
        assertThat(metricData.getJsonNumber("oneMinRate").intValue(), is(0));
        assertThat(metricData.getJsonNumber("fiveMinRate").intValue(), is(0));
        assertThat(metricData.getJsonNumber("fifteenMinRate").intValue(), is(0));
    }

    @Test
    void testPrometheus() {
        final StringBuilder sb = new StringBuilder();
        dataSetTimer.prometheusData(sb, dataSetTimerID, true);
        final String prometheusData = sb.toString();
        assertThat(prometheusData, startsWith("# TYPE application_response_time_rate_per_second gauge\n"
                                                      + "application_response_time_rate_per_second 200.0\n"
                                                      + "# TYPE application_response_time_one_min_rate_per_second gauge\n"
                                                      + "application_response_time_one_min_rate_per_second 0.0\n"
                                                      + "# TYPE application_response_time_five_min_rate_per_second gauge\n"
                                                      + "application_response_time_five_min_rate_per_second 0.0\n"
                                                      + "# TYPE application_response_time_fifteen_min_rate_per_second gauge\n"
                                                      + "application_response_time_fifteen_min_rate_per_second 0.0\n"
                                                      + "# TYPE application_response_time_mean_seconds gauge\n"
                                                      + "application_response_time_mean_seconds "));
        assertThat(prometheusData, containsString("# TYPE application_response_time_max_seconds gauge\n"
                                                          + "application_response_time_max_seconds "));

        assertThat(prometheusData, containsString("# TYPE application_response_time_seconds summary\n"
                                                          + "# HELP application_response_time_seconds Server response time for "
                                                          + "/index.html\n"
                                                          + "application_response_time_seconds_count 200"));
    }
}
