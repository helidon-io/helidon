/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Snapshot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link HelidonHistogram}.
 */
class HelidonHistogramTest {
    private static final int[] SAMPLE_INT_DATA = {0, 1, 2, 2, 2, 3, 3, 3, 3, 3, 4, 5, 5, 6, 7, 7, 7, 8, 9, 9, 10, 11, 11, 12, 12,
            12, 12, 13, 13, 13, 13, 14, 14, 15, 15, 17, 18, 18, 20, 20, 20, 21, 22, 22, 22, 24, 24, 25, 25, 27, 27, 27,
            27, 27, 27, 27, 28, 28, 29, 30, 31, 31, 32, 32, 33, 33, 36, 36, 36, 36, 37, 38, 38, 38, 39, 40, 40, 41, 42,
            42, 42, 43, 44, 44, 44, 45, 45, 45, 46, 46, 46, 46, 47, 47, 47, 47, 47, 47, 48, 48, 49, 49, 50, 51, 52, 52,
            52, 53, 54, 54, 55, 56, 56, 57, 57, 59, 59, 60, 61, 61, 62, 62, 63, 64, 64, 64, 65, 66, 66, 66, 67, 67, 68,
            68, 70, 71, 71, 71, 71, 72, 72, 72, 72, 73, 73, 74, 74, 74, 75, 75, 76, 76, 76, 77, 78, 78, 78, 80, 80, 81,
            82, 82, 82, 83, 83, 84, 84, 85, 87, 87, 88, 88, 88, 89, 89, 89, 89, 90, 91, 92, 92, 92, 93, 94, 95, 95, 95,
            96, 96, 96, 96, 97, 97, 97, 97, 98, 98, 98, 99, 99};

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

    private static final String EXPECTED_PROMETHEUS_OUTPUT = "# TYPE application:file_sizes_mean_bytes gauge\n"
            + "application:file_sizes_mean_bytes 50634.99999999998\n"
            + "# TYPE application:file_sizes_max_bytes gauge\n"
            + "application:file_sizes_max_bytes 99000\n"
            + "# TYPE application:file_sizes_min_bytes gauge\n"
            + "application:file_sizes_min_bytes 0\n"
            + "# TYPE application:file_sizes_stddev_bytes gauge\n"
            + "application:file_sizes_stddev_bytes 29438.949964290514\n"
            + "# TYPE application:file_sizes_bytes summary\n"
            + "# HELP application:file_sizes_bytes Users file size\n"
            + "application:file_sizes_bytes_count 200\n"
            + "application:file_sizes_bytes{quantile=\"0.5\"} 48000\n"
            + "application:file_sizes_bytes{quantile=\"0.75\"} 75000\n"
            + "application:file_sizes_bytes{quantile=\"0.95\"} 96000\n"
            + "application:file_sizes_bytes{quantile=\"0.98\"} 98000\n"
            + "application:file_sizes_bytes{quantile=\"0.99\"} 98000\n"
            + "application:file_sizes_bytes{quantile=\"0.999\"} 99000\n";

    private static Metadata meta;
    private static HelidonHistogram histoInt;
    private static HelidonHistogram delegatingHistoInt;
    private static HelidonHistogram histoLong;
    private static HelidonHistogram delegatingHistoLong;

    @BeforeAll
    static void initClass() {
        meta = new Metadata("file_sizes",
                            "theDisplayName",
                            "Users file size",
                            MetricType.HISTOGRAM,
                            MetricUnits.KILOBYTES);

        histoInt = HelidonHistogram.create("application", meta);
        delegatingHistoInt = HelidonHistogram.create("application", meta, HelidonHistogram.create("ignored", meta));
        histoLong = HelidonHistogram.create("application", meta);
        delegatingHistoLong = HelidonHistogram.create("application", meta, HelidonHistogram.create("ignored", meta));

        long now = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());

        for (int dato : SAMPLE_INT_DATA) {
            histoInt.getDelegate().update(dato, now);
            delegatingHistoInt.getDelegate().update(dato, now);
        }

        for (long dato : SAMPLE_LONG_DATA) {
            histoLong.getDelegate().update(dato, now);
            delegatingHistoLong.getDelegate().update(dato, now);
        }
    }

    @Test
    void testCounts() {
        assertAll("All counts must be 200",
                  () -> assertThat(histoInt.getCount(), is(200L)),
                  () -> assertThat(histoLong.getCount(), is(200L)),
                  () -> assertThat(delegatingHistoInt.getCount(), is(200L)),
                  () -> assertThat(delegatingHistoLong.getCount(), is(200L))
        );
    }

    @Test
    void testDataSet() {
        assertAll("For our sample size, all data must be available",
                  () -> assertThat(histoInt.getSnapshot().getValues(),
                                   is(Arrays.stream(SAMPLE_INT_DATA).asLongStream().toArray())),
                  () -> assertThat(delegatingHistoInt.getSnapshot().getValues(),
                                   is(Arrays.stream(SAMPLE_INT_DATA).asLongStream().toArray())),
                  () -> assertThat(histoLong.getSnapshot().getValues(), is(SAMPLE_LONG_DATA)),
                  () -> assertThat(delegatingHistoLong.getSnapshot().getValues(), is(SAMPLE_LONG_DATA))
        );

    }

    @Test
    void testJson() {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        histoInt.jsonData(builder);

        JsonObject result = builder.build();

        JsonObject metricData = result.getJsonObject("file_sizes");
        assertThat(metricData, notNullValue());
        assertThat(metricData.getJsonNumber("count").longValue(), is(200L));
        assertThat(metricData.getJsonNumber("min").longValue(), is(0L));
        assertThat(metricData.getJsonNumber("max").longValue(), is(99L));
        withTolerance("mean", metricData.getJsonNumber("mean").doubleValue(), 50.6349);
        withTolerance("stddev", metricData.getJsonNumber("stddev").doubleValue(), 29.4389);
        assertThat(metricData.getJsonNumber("p50").intValue(), is(48));
        assertThat(metricData.getJsonNumber("p75").intValue(), is(75));
        assertThat(metricData.getJsonNumber("p95").intValue(), is(96));
        assertThat(metricData.getJsonNumber("p98").intValue(), is(98));
        assertThat(metricData.getJsonNumber("p99").intValue(), is(98));
        assertThat(metricData.getJsonNumber("p999").intValue(), is(99));
    }

    @Test
    void testPrometheus() {
        assertThat(histoInt.prometheusData(), is(EXPECTED_PROMETHEUS_OUTPUT));
    }

    @Test
    void testStatisticalValues() {
        testSnapshot(1, "integers", histoInt.getSnapshot(), 50.6, 29.4389);
        testSnapshot(1, "delegating integers", delegatingHistoInt.getSnapshot(), 50.6, 29.4389);
        testSnapshot(10, "longs", histoLong.getSnapshot(), 506.3, 294.389);
        testSnapshot(10, "delegating longs", delegatingHistoLong.getSnapshot(), 506.3, 294.389);
    }

    private void testSnapshot(int factor, String description, Snapshot snapshot, double mean, double stddev) {
        assertAll("Testing statistical values for " + description,
                  () -> withTolerance("median", snapshot.getMedian(), factor * 48),
                  () -> withTolerance("75th percentile", snapshot.get75thPercentile(), factor * 75),
                  () -> withTolerance("95th percentile", snapshot.get95thPercentile(), factor * 96),
                  () -> withTolerance("78th percentile", snapshot.get98thPercentile(), factor * 98),
                  () -> withTolerance("99th percentile", snapshot.get99thPercentile(), factor * 98),
                  () -> withTolerance("999th percentile", snapshot.get999thPercentile(), factor * 99),
                  () -> withTolerance("mean", snapshot.getMean(), mean),
                  () -> withTolerance("stddev", snapshot.getStdDev(), stddev),
                  () -> assertThat("min", snapshot.getMin(), is(0L)),
                  () -> assertThat("max", snapshot.getMax(), is(factor * 99L)),
                  () -> assertThat("size", snapshot.size(), is(200))
        );
    }

    private void withTolerance(String field, double actual, double expectedValue) {
        double min = expectedValue * 0.999;
        double max = expectedValue * 1.001;

        if ((actual < min) || (actual > max)) {
            fail(field + ": expected: <" + expectedValue + ">, but actual value was: <" + actual + ">");
        }
    }
}
