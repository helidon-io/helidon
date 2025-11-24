/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.metrics.providers.micrometer;

import java.util.Arrays;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.Timer;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Retained for ad hoc simple performance testing of Prometheus formatting. To get a rough timing, uncomment the @Disabled
 * annotation below, run the test, and look in the test's output file in target/surefire to see the timings.
 * This test does not attempt to detect any failure condition; it simply runs the code to capture timing.
 */
class TestPrometheusPerf {

    @Disabled
    @Test
    void testPerf() {
        int loops = 40;

        // Warmup
        registerAndFormat(10);

        // Timed test
        double[] results = registerAndFormat(loops);

        double totalElapsedMillis = 0.0;
        for (double result : results) {
            totalElapsedMillis += result;
        }

        System.err.println("Durations:" + Arrays.toString(results));
        System.err.println("Mean: " + totalElapsedMillis / (double) loops);
    }

    private static double[] registerAndFormat(int loops) {

        double[] result = new double[loops];

        for (int loop = 0; loop < loops; loop++) {
            MeterRegistry meterRegistry = Metrics.globalRegistry();
            meterRegistry.close();

            Random random = new Random();
            for (int i = 0; i < 400; i++) {
                Counter c = meterRegistry.getOrCreate(Counter.builder("ctr" + i));
                c.increment();

                Timer t = meterRegistry.getOrCreate(Timer.builder("tmr" + i));
                t.record(123, TimeUnit.MILLISECONDS);

                DistributionSummary ds = meterRegistry.getOrCreate(DistributionSummary.builder("dist" + i));
                ds.record(random.nextDouble());
            }

            long start = System.nanoTime();
            MicrometerPrometheusFormatter formatter = MicrometerPrometheusFormatter.builder(meterRegistry)
                    .resultMediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                    .build();
            Optional<Object> outputOpt = formatter.format();
            result[loop] = (System.nanoTime() - start) / 1000.0 / 1000.0;
            meterRegistry.close();
            System.err.println(outputOpt.toString());
        }

        return result;
    }
}
