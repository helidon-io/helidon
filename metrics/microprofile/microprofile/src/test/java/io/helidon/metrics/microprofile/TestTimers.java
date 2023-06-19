/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.metrics.microprofile;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Timer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.metrics.microprofile.MetricsMatcher.withinTolerance;
import static org.junit.jupiter.api.Assertions.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TestTimers {

    static PrometheusMeterRegistry prometheusMeterRegistry;

    static MeterRegistry meterRegistry;

    static MpMetricRegistry mpMetricRegistry;

    @BeforeAll
    static void setup() {
        PrometheusConfig config = new PrometheusConfig() {
            @Override
            public String get(String s) {
                return null;
            }
        };

        prometheusMeterRegistry = new PrometheusMeterRegistry(config);
        meterRegistry = Metrics.globalRegistry.add(prometheusMeterRegistry);

        mpMetricRegistry = MpMetricRegistry.create("timerScope", meterRegistry);
    }

    @Test
    void testTimer() {
        Timer timer = mpMetricRegistry.timer("myTimer");
        timer.update(Duration.ofSeconds(2));
        try (Timer.Context context = timer.time()) {
            TimeUnit.SECONDS.sleep(3);
            // Don't explicitly stop the context; the try-with-resources will close it which stops the context.
            // Doing both adds an additional sample which skews the stats (and disturbs this test).
        } catch (InterruptedException ex) {
            fail("Thread interrupted while waiting for some time to pass");
        }

        assertThat("Count", timer.getCount(), is(2L));
        assertThat("Sum", timer.getElapsedTime().getSeconds(), is(withinTolerance(5)));

        Snapshot snapshot = timer.getSnapshot();
        assertThat("Mean", toMillis(snapshot.getMean()), is(withinTolerance(2500L, 0.01)));
        assertThat("Max", toMillis(snapshot.getMax()), is(withinTolerance(3000L, 0.01)));
        Snapshot.PercentileValue[] percentileValues = snapshot.percentileValues();

        double[] expectedPercents = {0.5, 0.75, 0.95, 0.98, 0.99, 0.999};
        double[] expectedMilliseconds =   {2000.0, 3000.0, 3000.0, 3000.0, 3000.0, 3000.0};

        for (int i = 0; i < percentileValues.length; i++ ) {
            assertThat("Percentile " + i + " %", percentileValues[i].getPercentile(), is(expectedPercents[i]));
            assertThat("Percentile " + i + " value",
                       toMillis(percentileValues[i].getValue()),
                       is(withinTolerance(expectedMilliseconds[i], 0.01)));
        }
    }

    private static long toMillis(double value) {
        return TimeUnit.NANOSECONDS.toMillis((long) value);
    }
}
