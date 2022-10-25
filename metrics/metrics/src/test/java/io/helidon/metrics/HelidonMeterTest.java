/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.metrics.HelidonMetricsMatcher.withinTolerance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Unit test for {@link HelidonMeter}.
 */
class HelidonMeterTest {
    private static HelidonMeter meter;
    private static MetricID meterID;

    @BeforeAll
    static void initClass() {
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
}
