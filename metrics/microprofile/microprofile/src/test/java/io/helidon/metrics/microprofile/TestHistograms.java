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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Snapshot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.metrics.microprofile.MetricsMatcher.withinTolerance;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


public class TestHistograms {

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
        meterRegistry = Metrics.globalRegistry;

        mpMetricRegistry = MpMetricRegistry.create("histoScope", meterRegistry);
    }

    @Test
    void testHistogram() {
        Histogram histogram = mpMetricRegistry.histogram("myHisto");
        histogram.update(4);
        histogram.update(24);
        assertThat("Count", histogram.getCount(), is(2L));
        assertThat("Sum", histogram.getSum(), is(28L));
        Snapshot snapshot = histogram.getSnapshot();
        assertThat("Mean", snapshot.getMean(), is(14.0D));
        assertThat("Max", snapshot.getMax(), is(24.0D));
        Snapshot.PercentileValue[] percentileValues = snapshot.percentileValues();

        double[] expectedPercents = {0.5, 0.75, 0.95, 0.98, 0.99, 0.999};
        double[] expectedValues = {4.0, 24.0, 24.0, 24.0, 24.0, 24.0};

        for (int i = 0; i < percentileValues.length; i++ ) {
            assertThat("Percentile " + i + " %", percentileValues[i].getPercentile(), is(expectedPercents[i]));
            assertThat("Percentile " + i + " value", percentileValues[i].getValue(), is(withinTolerance(expectedValues[i])));
        }
    }
}
