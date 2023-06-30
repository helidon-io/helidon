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

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.prometheus.client.CollectorRegistry;
import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.helidon.metrics.HelidonMetricsMatcher.withinTolerance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link HelidonHistogram}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HelidonHistogramTest{
     private static final int[]SAMPLE_INT_DATA = {0, 1, 2, 2, 2, 3, 3, 3, 3, 3, 4, 5, 5, 6, 7, 7, 7, 8, 9, 9, 10, 11, 11, 12, 12,
            12, 12, 13, 13, 13, 13, 14, 14, 15, 15, 17, 18, 18, 20, 20, 20, 21, 22, 22, 22, 24, 24, 25, 25, 27, 27, 27,
            27, 27, 27, 27, 28, 28, 29, 30, 31, 31, 32, 32, 33, 33, 36, 36, 36, 36, 37, 38, 38, 38, 39, 40, 40, 41, 42,
            42, 42, 43, 44, 44, 44, 45, 45, 45, 46, 46, 46, 46, 47, 47, 47, 47, 47, 47, 48, 48, 49, 49, 50, 51, 52, 52,
            52, 53, 54, 54, 55, 56, 56, 57, 57, 59, 59, 60, 61, 61, 62, 62, 63, 64, 64, 64, 65, 66, 66, 66, 67, 67, 68,
            68, 70, 71, 71, 71, 71, 72, 72, 72, 72, 73, 73, 74, 74, 74, 75, 75, 76, 76, 76, 77, 78, 78, 78, 80, 80, 81,
            82, 82, 82, 83, 83, 84, 84, 85, 87, 87, 88, 88, 88, 89, 89, 89, 89, 90, 91, 92, 92, 92, 93, 94, 95, 95, 95,
            96, 96, 96, 96, 97, 97, 97, 97, 98, 98, 98, 99, 99};

     private static final long[]SAMPLE_LONG_DATA = {0, 10, 20, 20, 20, 30, 30, 30, 30, 30, 40, 50, 50, 60, 70, 70, 70, 80, 90,
            90, 100, 110, 110, 120, 120, 120, 120, 130, 130, 130, 130, 140, 140, 150, 150, 170, 180, 180, 200, 200, 200,
            210, 220, 220, 220, 240, 240, 250, 250, 270, 270, 270, 270, 270, 270, 270, 280, 280, 290, 300, 310, 310,
            320, 320, 330, 330, 360, 360, 360, 360, 370, 380, 380, 380, 390, 400, 400, 410, 420, 420, 420, 430, 440,
            440, 440, 450, 450, 450, 460, 460, 460, 460, 470, 470, 470, 470, 470, 470, 480, 480, 490, 490, 500, 510,
            520, 520, 520, 530, 540, 540, 550, 560, 560, 570, 570, 590, 590, 600, 610, 610, 620, 620, 630, 640, 640,
            640, 650, 660, 660, 660, 670, 670, 680, 680, 700, 710, 710, 710, 710, 720, 720, 720, 720, 730, 730, 740,
            740, 740, 750, 750, 760, 760, 760, 770, 780, 780, 780, 800, 800, 810, 820, 820, 820, 830, 830, 840, 840,
            850, 870, 870, 880, 880, 880, 890, 890, 890, 890, 900, 910, 920, 920, 920, 930, 940, 950, 950, 950, 960,
            960, 960, 960, 970, 970, 970, 970, 980, 980, 980, 990, 990};

     private static final String EXPECTED_PROMETHEUS_OUTPUT="# TYPE application_file_sizes_mean_bytes gauge\n"
            + "application_file_sizes_mean_bytes 50634.99999999998\n"
            + "# TYPE application_file_sizes_max_bytes gauge\n"
            + "application_file_sizes_max_bytes 99000\n"
            + "# TYPE application_file_sizes_min_bytes gauge\n"
            + "application_file_sizes_min_bytes 0\n"
            + "# TYPE application_file_sizes_stddev_bytes gauge\n"
            + "application_file_sizes_stddev_bytes 29438.949964290514\n"
            + "# TYPE application_file_sizes_bytes summary\n"
            + "# HELP application_file_sizes_bytes Users file size\n"
            + "application_file_sizes_bytes_count 200\n"
            + "application_file_sizes_bytes_sum 10127\n"
            + "application_file_sizes_bytes{quantile=\"0.5\"} 48000\n"
            + "application_file_sizes_bytes{quantile=\"0.75\"} 75000\n"
            + "application_file_sizes_bytes{quantile=\"0.95\"} 96000\n"
            + "application_file_sizes_bytes{quantile=\"0.98\"} 98000\n"
            + "application_file_sizes_bytes{quantile=\"0.99\"} 98000\n"
            + "application_file_sizes_bytes{quantile=\"0.999\"} 99000\n";

     private static final Tag[]HISTO_INT_TAGS = new Tag[] {
            new Tag("tag1", "val1"),
            new Tag("tag2", "val2")};

     private static final Map<String, String>HISTO_INT_TAGS_AS_MAP =
            Arrays.stream(HISTO_INT_TAGS).collect(Collectors.toMap(Tag::getTagName, Tag::getTagValue));

     private static final Map<String, String>HISTO_INT_TAGS_AS_MAP_PROM =
            Arrays.stream(HISTO_INT_TAGS).collect(Collectors.toMap(Tag::getTagName, tag -> "\"" + tag.getTagValue() + "\""));

     // name{tag="val",tag="val"} where the braces and tags within are optional
     private static final Pattern PROMETHEUS_KEY_PATTERN=Pattern.compile("([^{]+)(?:\\{([^}]+)})?+");

     private static final long SAMPLE_INT_SUM = Arrays.stream(SAMPLE_INT_DATA).sum();

     /**
      * Parses a {@code Stream| of text lines (presumably in Prometheus/OpenMetrics format) into a {@code Stream}
      * of {@code Map.Entry}, with the key the value name and the value a {@code Number}
      * representing the converted value.
      *
      * @param lines Prometheus-format text as a stream of lines
      * @return stream of name/value pairs
      */
     private static Stream<Map.Entry<String, Number>>parsePrometheusText(Stream<String> lines) {
     return lines
             .filter(line -> !line.startsWith("#") && line.length() > 0)
             .map(line -> {
                 final int space = line.indexOf(" ");
                 return new AbstractMap.SimpleImmutableEntry<String, Number>(
                         line.substring(0, space),
                         parseNumber(line.substring(space + 1)));
             });
     }

     private static Stream<Map.Entry<String, Number>>parsePrometheusText(String promText) {
        return parsePrometheusText(Arrays.stream(promText.split("\n")));
    }

    private static Map<String, Number> EXPECTED_PROMETHEUS_RESULTS;

    private static Metadata meta;
    private static HelidonHistogram histoInt;
    private static HelidonHistogram histoLong;
    private static final NumberFormat NUMBER_FORMATTER = DecimalFormat.getNumberInstance();

    private static MockClock clock;
    private static MeterRegistry meterRegistry;

    @BeforeAll
    static void initClass() {

        meta = Metadata.builder()
				.withName("file_sizes")
				.withDescription("Users file size")
				.withUnit(MetricUnits.KILOBYTES)
				.build();
        clock = new MockClock();
        meterRegistry =  new PrometheusMeterRegistry(key -> null,
                                                       new CollectorRegistry(),
                                                       clock);

        histoInt = HelidonHistogram.create(meterRegistry,"application", meta, HISTO_INT_TAGS);
        histoLong = HelidonHistogram.create(meterRegistry, "application", meta);

        for (int dato : SAMPLE_INT_DATA) {
            histoInt.update(dato);
        }

        for (long dato : SAMPLE_LONG_DATA) {
            histoLong.update(dato);
        }

        EXPECTED_PROMETHEUS_RESULTS = parsePrometheusText(EXPECTED_PROMETHEUS_OUTPUT)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    // Deals with the exception so this is usable from inside a stream.
    private static Number parseNumber(String value) {
        try {
            return NUMBER_FORMATTER.parse(value);
        } catch (ParseException ex) {
            fail("Unable to parse value as Number", ex);
            return null; // never executed - the preceding fail will see to that
        }
    }

    @Test
    void testCounts() {
        assertAll("All counts must be 200",
                  () -> assertThat(histoInt.getCount(), is(200L)),
                  () -> assertThat(histoLong.getCount(), is(200L))
        );
    }

    // TODO see what other value check we might test
    @Disabled
    @Test
    void testDataSet() {
//        assertAll("For our sample size, all data must be available",
//                  () -> assertThat(histoInt.getSnapshot().getValues(),
//                                   is(Arrays.stream(SAMPLE_INT_DATA).asLongStream().toArray())),
//                  () -> assertThat(delegatingHistoInt.getSnapshot().getValues(),
//                                   is(Arrays.stream(SAMPLE_INT_DATA).asLongStream().toArray())),
//                  () -> assertThat(histoLong.getSnapshot().getValues(), is(SAMPLE_LONG_DATA)),
//                  () -> assertThat(delegatingHistoLong.getSnapshot().getValues(), is(SAMPLE_LONG_DATA)),
//                  () -> assertThat(histoInt.getSum(), is(equalTo(SAMPLE_INT_SUM)))
//        );

    }

    @Test
    void testStatisticalValues() {
        testSnapshot(1, "integers", histoInt.getSnapshot(), 50.6, 29.4389);
        testSnapshot(10, "longs", histoLong.getSnapshot(), 506.3, 294.389);
    }

    @Test
    @Order(Integer.MAX_VALUE)
    void testNaNAvoidance() {
        Metadata metadata = Metadata.builder()
                .withName("long_idle_test")
                .withDescription("Simulates a long idle period")
                .withUnit(MetricUnits.SECONDS)
                .build();
        Histogram idleHistogram = HelidonHistogram.create(meterRegistry, "application", metadata);

        idleHistogram.update(100);

        for (int i = 1; i < 48; i++) {
            clock.add(1, TimeUnit.HOURS);
            assertThat("Idle histogram failed after simulating " + i + " hours", idleHistogram.getSnapshot().getMean(),
                    not(equalTo(Double.NaN)));
        }
    }

    private void testSnapshot(int factor, String description, Snapshot snapshot, double mean, double stddev) {
        assertAll("Testing statistical values for " + description,
                  () -> assertThat("mean", snapshot.getMean(), is(withinTolerance(mean))),
                  () -> assertThat("max", snapshot.getMax(), is(factor * 99D)),
                  () -> assertThat("size", snapshot.size(), is(200L))
        );
    }
}
