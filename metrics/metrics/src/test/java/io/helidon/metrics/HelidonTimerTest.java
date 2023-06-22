/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.CollectorRegistry;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Timer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.helidon.metrics.HelidonMetricsMatcher.withinTolerance;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertAll;

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
    private static MockClock timerClock = new MockClock();
    private static Metadata meta;

    private static MeterRegistry meterRegistry = new PrometheusMeterRegistry((key) -> null, new CollectorRegistry(), timerClock);


    private static final long DATA_SET_ELAPSED_TIME = Arrays.stream(SAMPLE_LONG_DATA).sum();

    @BeforeAll
    static void initClass() {
        meta = Metadata.builder()
				.withName("response_time")
				.withDescription("Server response time for /index.html")
				.withUnit(MetricUnits.NANOSECONDS)
				.build();

        dataSetTimer = HelidonTimer.create(meterRegistry, "application", meta);

        for (long i : SAMPLE_LONG_DATA) {
            dataSetTimer.update(Duration.ofNanos(i));
        }

        timer = HelidonTimer.create(meterRegistry, "application", meta);
        // now run the "load"
        int markSeconds = 30;
        timerClock.add(markSeconds, TimeUnit.SECONDS);
    }

    @BeforeEach
    void clear() {
        meterRegistry.clear();
    }

    @Test
    void testContextTime() {
        Timer timer = HelidonTimer.create(meterRegistry, "application", meta);
        Timer.Context context = timer.time();

        timerClock.add(2, TimeUnit.SECONDS);

        long diff = context.stop();

        assertThat(nanosToMillis(diff), closeTo((double) TimeUnit.SECONDS.toMillis(2), 200.0));
        assertThat("Elapsed time", (double) timer.getElapsedTime().toMillis(), closeTo(Duration.ofSeconds(2L).toMillis(), 200.0));
    }

    @Test
    void testCallableTiming() throws Exception {
        Timer timer = HelidonTimer.create(meterRegistry, "application", meta);

        timer.update(Duration.ofSeconds(2L));
        String result = timer.time(() -> {
            timerClock.add(1, TimeUnit.SECONDS);
            return "hello";
        });

        assertThat(nanosToMillis(timer.getSnapshot().getMean()), closeTo(1500.0, 100.0));
        assertThat(timer.getCount(), is(2L));
        assertThat(result, is("hello"));
        assertThat("Elapsed time", (double) timer.getElapsedTime().toMillis(), closeTo(3000.0, 125.0));
    }

   @Test
    void testRunnableTiming() throws Exception {
        Timer timer = HelidonTimer.create(meterRegistry, "application", meta);

        timer.time(() -> timerClock.add(1, TimeUnit.SECONDS));

        assertThat(nanosToMillis(timer.getSnapshot().getMean()), closeTo(1000.0, 100.0));
        assertThat(timer.getCount(), is(1L));
        assertThat("Elapsed time", timer.getElapsedTime(), is(equalTo(Duration.ofSeconds(1L))));
    }

    @Test
    @Disabled
    // TODO re-work to check percentiles
    void testDataSet() {
//        assertThat(dataSetTimer.getSnapshot().getValues(), is(SAMPLE_LONG_DATA));
//        assertThat("Elapsed time", dataSetTimer.getElapsedTime(), is(equalTo(Duration.ofNanos(DATA_SET_ELAPSED_TIME))));
    }

    @Test
    void testSnapshot() {
        Snapshot snapshot = dataSetTimer.getSnapshot();

        assertAll("Testing statistical values for snapshot",
                  () -> assertThat("mean", snapshot.getMean(), is(withinTolerance(506.3))),
                  () -> assertThat("max", snapshot.getMax(), is(990D)),
                  () -> assertThat("size", snapshot.size(), is(200L))
        );
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1000.0 / 1000.0;
    }

    private static double nanosToMillis(double nanos) {
        return nanos / 1000.0 / 1000.0;
    }


}
