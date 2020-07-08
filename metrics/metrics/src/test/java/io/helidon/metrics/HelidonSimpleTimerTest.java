/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.metrics;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

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

        clock.add(1, TimeUnit.SECONDS);

        long diff = context.stop();

        assertThat(diff, is(TimeUnit.SECONDS.toNanos(1)));
    }

    @Test
    void testCallableTiming() throws Exception {
        TestClock clock = TestClock.create();
        SimpleTimer timer = HelidonSimpleTimer.create("application", meta, clock);

        String result = timer.time(() -> {
            clock.add(1, TimeUnit.SECONDS);
            return "hello";
        });

        assertThat(timer.getCount(), is(1L));
        assertThat(timer.getElapsedTime(), is(Duration.ofSeconds(1)));
        assertThat(result, is("hello"));
    }

    @Test
    void testRunnableTiming() {
        TestClock clock = TestClock.create();
        SimpleTimer timer = HelidonSimpleTimer.create("application", meta, clock);

        timer.time(() -> clock.add(1, TimeUnit.SECONDS));

        assertThat(timer.getCount(), CoreMatchers.is(1L));
        assertThat(timer.getElapsedTime(), is(Duration.ofSeconds(1)));
    }

    @Test
    void testJson() {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        dataSetTimer.jsonData(builder, dataSetTimerID);

        JsonObject json = builder.build();
        JsonObject metricData = json.getJsonObject("response_time");

        assertThat(metricData, notNullValue());
        assertThat("total", metricData.getJsonNumber("total").longValue(), is(200L));
    }
}
