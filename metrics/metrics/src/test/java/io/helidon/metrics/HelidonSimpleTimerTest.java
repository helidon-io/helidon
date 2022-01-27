/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class HelidonSimpleTimerTest {

    private static final long SECONDS_TO_RUN = 30;

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

    private static HelidonSimpleTimer timer;
    private static HelidonSimpleTimer dataSetTimer;
    private static MetricID dataSetTimerID;
    private static TestClock timerClock = TestClock.create();
    private static Metadata meta;
    private static TestClock dataSetTimerClock = TestClock.create();
    private static boolean dataSetTimerClockAdvanced = false;

    @BeforeAll
    static void initClass() {
        meta = Metadata.builder()
                .withName("response_time")
                .withDisplayName("Responses")
                .withDescription("Server response time for /index.html")
                .withType(MetricType.SIMPLE_TIMER)
                .withUnit(MetricUnits.SECONDS)
                .build();

        dataSetTimer = HelidonSimpleTimer.create("application", meta, dataSetTimerClock);
        dataSetTimerID = new MetricID("response_time");

        for (long i : SAMPLE_LONG_DATA) {
            dataSetTimer.update(Duration.ofNanos(i));
        }

        timer = HelidonSimpleTimer.create("application", meta, timerClock);

        for (int i = 0; i < SECONDS_TO_RUN; i++) {
            timerClock.add(1, TimeUnit.SECONDS);
            timer.update(Duration.ofSeconds(1));
        }
    }

    @Test
    void testCount() {
        assertThat("Incorrect count from timer", timer.getCount(), is(SECONDS_TO_RUN));
        assertThat("Incorrect elapsed time from timer", timer.getElapsedTime(), is(Duration.ofSeconds(SECONDS_TO_RUN)));

        timerClock.add(30, TimeUnit.SECONDS);

        assertThat("Incorrect count after additional 30 seconds", timer.getCount(), is(SECONDS_TO_RUN));
        assertThat("Incorrect elapsed time after additional 30 seconds", timer.getElapsedTime(),
                is(Duration.ofSeconds(SECONDS_TO_RUN)));
    }

    @Test
    void testContextTime() {
        TestClock clock = TestClock.create();
        SimpleTimer timer = HelidonSimpleTimer.create("application", meta, clock);
        SimpleTimer.Context context = timer.time();

        clock.add(3, TimeUnit.SECONDS);

        long diff = context.stop();

        // Wait until next minute for reported min and max to change.
        clock.add(1, TimeUnit.MINUTES);

        long toSeconds = TimeUnit.SECONDS.toNanos(3);
        assertThat(diff, is(toSeconds));
        checkMinAndMaxDurations(timer, toSeconds, toSeconds);
    }

    @Test
    void testCallableTiming() throws Exception {
        TestClock clock = TestClock.create();
        SimpleTimer timer = HelidonSimpleTimer.create("application", meta, clock);

        String result = timer.time(() -> {
            clock.add(20, TimeUnit.MILLISECONDS);
            return "hello";
        });

        assertThat("Min immediately after first update", timer.getMinTimeDuration(), is(nullValue()));
        assertThat("Max immediately after first update", timer.getMaxTimeDuration(), is(nullValue()));

        // Trigger updates to min and max in previous complete minute.
        clock.add(1, TimeUnit.MINUTES);

        long toMillis = TimeUnit.MILLISECONDS.toNanos(20);
        assertThat("Timer count", timer.getCount(), is(1L));
        assertThat(timer.getElapsedTime(), is(Duration.ofMillis(20)));
        assertThat(result, is("hello"));
        checkMinAndMaxDurations(timer, toMillis, toMillis);
    }

    @Test
    void testRunnableTiming() {
        TestClock clock = TestClock.create();
        SimpleTimer timer = HelidonSimpleTimer.create("application", meta, clock);

        timer.time(() -> clock.add(1, TimeUnit.SECONDS));

        clock.add(1, TimeUnit.MINUTES);

        long toSeconds = TimeUnit.SECONDS.toNanos(1);
        assertThat(timer.getCount(), is(1L));
        assertThat(timer.getElapsedTime(), is(Duration.ofSeconds(1)));
        checkMinAndMaxDurations(timer, toSeconds, toSeconds);
    }

    @Test
    void testJson() {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        ensureDataSetTimerClockAdvanced();
        dataSetTimer.jsonData(builder, dataSetTimerID);

        JsonObject json = builder.build();
        JsonObject metricData = json.getJsonObject("response_time");

        assertThat(metricData, notNullValue());
        assertThat("count", metricData.getJsonNumber("count").longValue(), is(200L));
        assertThat("elapsedTime", metricData.getJsonNumber("elapsedTime"), notNullValue());
        assertThat("maxTimeDuration", metricData.getJsonNumber("maxTimeDuration").longValue(), is(0L));
        assertThat("minTimeDuration", metricData.getJsonNumber("minTimeDuration").longValue(), is(0L));

        // Because the batch of test data does not give a non-zero min or max, do a separate test to check the min and max.
        TestClock clock = TestClock.create();
        HelidonSimpleTimer simpleTimer = HelidonSimpleTimer.create("application", meta, clock);

        simpleTimer.update(Duration.ofSeconds(4));
        simpleTimer.update(Duration.ofSeconds(3));

        clock.add(1, TimeUnit.MINUTES);
        builder = Json.createObjectBuilder();
        simpleTimer.jsonData(builder, dataSetTimerID);

        json = builder.build();
        metricData = json.getJsonObject("response_time");

        assertThat(metricData, notNullValue());
        assertThat("count", metricData.getJsonNumber("count").longValue(), is(2L));
        assertThat("elapsedTime", metricData.getJsonNumber("elapsedTime").doubleValue(), is(7.0D));
        assertThat("maxTimeDuration", metricData.getJsonNumber("maxTimeDuration").longValue(), is(4L));
        assertThat("minTimeDuration", metricData.getJsonNumber("minTimeDuration").longValue(), is(3L));


    }

    @ParameterizedTest
    @ValueSource(strings = {MetricUnits.SECONDS, MetricUnits.NANOSECONDS, MetricUnits.MILLISECONDS, MetricUnits.MICROSECONDS})
    void testJsonNonDefaultUnits(String metricUnits) {
        Metadata metadataWithUnits = Metadata.builder(meta)
                .withUnit(metricUnits)
                .build();
        TestClock clock = TestClock.create();
        HelidonSimpleTimer simpleTimer = HelidonSimpleTimer.create("application", metadataWithUnits, clock);

        Duration longInterval = Duration.ofSeconds(4);
        Duration shortInterval = Duration.ofSeconds(3);
        Duration overallInterval = Duration.of(longInterval.toNanos() + shortInterval.toNanos(), ChronoUnit.NANOS);

        simpleTimer.update(longInterval);
        simpleTimer.update(shortInterval);
        clock.add(1, TimeUnit.MINUTES);
        JsonObjectBuilder builder = Json.createObjectBuilder();
        simpleTimer.jsonData(builder, new MetricID("simpleTimerWithExplicitUnits"));
        JsonObject json = builder.build();

        JsonObject metricData = json.getJsonObject("simpleTimerWithExplicitUnits");
        assertThat(metricData, notNullValue());
        assertThat("elapsedTime",
                   metricData.getJsonNumber("elapsedTime").longValue(),
                   is(TestUtils.secondsToMetricUnits(metricUnits, overallInterval)));
        assertThat("maxTimeDuration",
                   metricData.getJsonNumber("maxTimeDuration").longValue(),
                   is(TestUtils.secondsToMetricUnits(metricUnits, longInterval)));
        assertThat("maxTimeDuration",
                   metricData.getJsonNumber("minTimeDuration").longValue(),
                   is(TestUtils.secondsToMetricUnits(metricUnits, shortInterval)));
    }

    @Test
    void testPrometheus() {
        StringBuilder sb = new StringBuilder();
        ensureDataSetTimerClockAdvanced();
        dataSetTimer.prometheusData(sb, dataSetTimerID, true);
        String prometheusData = sb.toString();
        assertThat(prometheusData,
                   startsWith("""
                                      # TYPE application_response_time_total counter
                                      # HELP application_response_time_total Server response time for /index.html
                                      application_response_time_total 200
                                      # TYPE application_response_time_elapsedTime_seconds gauge
                                      application_response_time_elapsedTime_seconds 1.0127E-4
                                      # TYPE application_response_time_maxTimeDuration_seconds gauge
                                      application_response_time_maxTimeDuration_seconds 0
                                      # TYPE application_response_time_minTimeDuration_seconds gauge
                                      application_response_time_minTimeDuration_seconds 0
                                      """));

        // Because the batch of test data does not give non-zero min and max, do a separate test to check those.
        TestClock clock = TestClock.create();
        HelidonSimpleTimer simpleTimer = HelidonSimpleTimer.create("application", meta, clock);

        simpleTimer.update(Duration.ofSeconds(4));
        simpleTimer.update(Duration.ofSeconds(3));

        clock.add(1, TimeUnit.MINUTES);
        sb = new StringBuilder();
        simpleTimer.prometheusData(sb, dataSetTimerID, true);
        prometheusData = sb.toString();
        assertThat(prometheusData,
                   startsWith("""
                                      # TYPE application_response_time_total counter
                                      # HELP application_response_time_total Server response time for /index.html
                                      application_response_time_total 2
                                      # TYPE application_response_time_elapsedTime_seconds gauge
                                      application_response_time_elapsedTime_seconds 7.0
                                      # TYPE application_response_time_maxTimeDuration_seconds gauge
                                      application_response_time_maxTimeDuration_seconds 4
                                      # TYPE application_response_time_minTimeDuration_seconds gauge
                                      application_response_time_minTimeDuration_seconds 3
                                      """));
    }

    @Test
    void testNoUpdatesJson() {
        TestClock clock = TestClock.create();
        HelidonSimpleTimer simpleTimer = HelidonSimpleTimer.create("application", meta, clock);

        JsonObjectBuilder builder = Json.createObjectBuilder();
        simpleTimer.jsonData(builder, dataSetTimerID);

        JsonObject json = builder.build();
        JsonObject metricData = json.getJsonObject("response_time");

        assertThat(metricData, notNullValue());
        assertThat("count", metricData.getJsonNumber("count").longValue(), is(0L));
        assertThat("elapsedTime", metricData.getJsonNumber("elapsedTime").doubleValue(), is(0.0D));
        assertThat("maxTimeDuration", metricData.get("maxTimeDuration").getValueType(), is(JsonValue.ValueType.NULL));
        assertThat("minTimeDuration", metricData.get("minTimeDuration").getValueType(), is(JsonValue.ValueType.NULL));
    }

    @Test
    void testDataSetTimerDurations() {
        ensureDataSetTimerClockAdvanced();
        checkMinAndMaxDurations(dataSetTimer, 0L, 990L);
    }

    @Test
    void testIdleSimpleTimerMinAndMaxDurations() {
        TestClock clock = TestClock.create();
        SimpleTimer timer = HelidonSimpleTimer.create("application", meta, clock);

        assertThat("Min duration", timer.getMinTimeDuration(), is(nullValue()));
        assertThat("Max duration", timer.getMaxTimeDuration(), is(nullValue()));
    }

    private void checkMinAndMaxDurations(SimpleTimer simpleTimer, long minNanos, long maxNanos) {
        assertThat("Min duration", simpleTimer.getMinTimeDuration(), is(Duration.ofNanos(minNanos)));
        assertThat("Max duration", simpleTimer.getMaxTimeDuration(), is(Duration.ofNanos(maxNanos)));
    }

    private static void ensureDataSetTimerClockAdvanced() {
        if (!dataSetTimerClockAdvanced) {
            dataSetTimerClockAdvanced = true;
            dataSetTimerClock.add(1, TimeUnit.MINUTES);
        }
    }
}
