/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;

import org.eclipse.microprofile.metrics.Histogram;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.MetricUnits;
import org.eclipse.microprofile.metrics.Snapshot;
import org.eclipse.microprofile.metrics.Tag;
import org.hamcrest.collection.IsMapContaining;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.metrics.HelidonMetricsMatcher.withinTolerance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
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

    private static final String EXPECTED_PROMETHEUS_OUTPUT = "# TYPE application_file_sizes_mean_bytes gauge\n"
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
            + "application_file_sizes_bytes{quantile=\"0.5\"} 48000\n"
            + "application_file_sizes_bytes{quantile=\"0.75\"} 75000\n"
            + "application_file_sizes_bytes{quantile=\"0.95\"} 96000\n"
            + "application_file_sizes_bytes{quantile=\"0.98\"} 98000\n"
            + "application_file_sizes_bytes{quantile=\"0.99\"} 98000\n"
            + "application_file_sizes_bytes{quantile=\"0.999\"} 99000\n";

    private static final Tag[] HISTO_INT_TAGS = new Tag[] {
            new Tag("tag1", "val1"),
            new Tag("tag2", "val2")};

    private static final Map<String, String> HISTO_INT_TAGS_AS_MAP =
            Arrays.stream(HISTO_INT_TAGS).collect(Collectors.toMap(Tag::getTagName, Tag::getTagValue));

    private static final Map<String, String> HISTO_INT_TAGS_AS_MAP_PROM =
            Arrays.stream(HISTO_INT_TAGS).collect(Collectors.toMap(Tag::getTagName, tag -> "\"" + tag.getTagValue() + "\""));

    // name{tag="val",tag="val"} where the braces and tags within are optional
    private static final Pattern PROMETHEUS_KEY_PATTERN = Pattern.compile("([^{]+)(?:\\{([^}]+)})?+");

    /**
     * Parses a {@code Stream| of text lines (presumably in Prometheus/OpenMetrics format) into a {@code Stream}
     * of {@code Map.Entry}, with the key the value name and the value a {@code Number}
     * representing the converted value.
     *
     * @param lines Prometheus-format text as a stream of lines
     * @return stream of name/value pairs
     */
    private static Stream<Map.Entry<String, Number>> parsePrometheusText(Stream<String> lines) {
        return lines
                .filter(line -> !line.startsWith("#") && line.length() > 0)
                .map(line -> {
                    final int space = line.indexOf(" ");
                    return new AbstractMap.SimpleImmutableEntry<String, Number>(
                            line.substring(0, space),
                            parseNumber(line.substring(space + 1)));
                });
    }

    private static Stream<Map.Entry<String, Number>> parsePrometheusText(String promText) {
        return parsePrometheusText(Arrays.stream(promText.split("\n")));
    }

    private static Map<String, Number> EXPECTED_PROMETHEUS_RESULTS;

    private static Metadata meta;
    private static HelidonHistogram histoInt;
    private static MetricID histoIntID;
    private static MetricID histoIntIDWithTags;
    private static HelidonHistogram delegatingHistoInt;
    private static HelidonHistogram histoLong;
    private static HelidonHistogram delegatingHistoLong;
    private static final NumberFormat NUMBER_FORMATTER = DecimalFormat.getNumberInstance();

    @BeforeAll
    static void initClass() {
        meta = Metadata.builder()
				.withName("file_sizes")
				.withDisplayName("theDisplayName")
				.withDescription("Users file size")
				.withType(MetricType.HISTOGRAM)
				.withUnit(MetricUnits.KILOBYTES)
				.build();

        histoInt = HelidonHistogram.create("application", meta);
        histoIntID = new MetricID("file_sizes");
        histoIntIDWithTags = new MetricID(histoIntID.getName(), HISTO_INT_TAGS);
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
        histoInt.jsonData(builder, new MetricID("file_sizes"));

        JsonObject result = builder.build();

        JsonObject metricData = result.getJsonObject("file_sizes");
        assertThat(metricData, notNullValue());
        assertThat(metricData.getJsonNumber("count").longValue(), is(200L));
        assertThat(metricData.getJsonNumber("min").longValue(), is(0L));
        assertThat(metricData.getJsonNumber("max").longValue(), is(99L));
        assertThat("mean", metricData.getJsonNumber("mean").doubleValue(), is(withinTolerance(50.6349)));
        assertThat("stddev", metricData.getJsonNumber("stddev").doubleValue(),  is(withinTolerance(29.4389)));
        assertThat("p50", metricData.getJsonNumber("p50").intValue(), is(withinTolerance(48)));
        assertThat("p75", metricData.getJsonNumber("p75").intValue(), is(withinTolerance(75)));
        assertThat("p95", metricData.getJsonNumber("p95").intValue(), is(withinTolerance(96)));
        assertThat("p98", metricData.getJsonNumber("p98").intValue(), is(withinTolerance(98)));
        assertThat("p99", metricData.getJsonNumber("p99").intValue(), is(withinTolerance(98)));
        assertThat("p999", metricData.getJsonNumber("p999").intValue(), is(withinTolerance(99)));
    }

    @Test
    void testJsonWithTags() {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        histoInt.jsonData(builder, new MetricID("file_sizes", HISTO_INT_TAGS));

        JsonObject result = builder.build();

        JsonObject metricData = result.getJsonObject("file_sizes");
        assertThat(metricData, notNullValue());

        checkJsonTreeForTags("file_sizes", metricData, HISTO_INT_TAGS_AS_MAP);
    }

    private static void checkJsonTreeForTags(String key, JsonValue jsonValue, Map<String, String> expectedTags) {
        if (jsonValue.getValueType() == JsonValue.ValueType.OBJECT) {
            jsonValue.asJsonObject()
                    .forEach((childKey, childValue) -> checkJsonTreeForTags(key + "." + childKey, childValue, expectedTags));
        } else {
            assertThat("Leaf JSON node with key " + key,
                    tagsFromJsonKey(key),
                    MetricsCustomMatchers.MapContains.all(HISTO_INT_TAGS_AS_MAP));
        }
    }

    private static Map<String, String> tagsFromJsonKey(String jsonKey) {
        return jsonKey.contains(";")
                ? tagsFromDelimitedString(jsonKey.substring(jsonKey.indexOf(';') + 1), ";")
                : Collections.emptyMap();
    }

    @Test
    void testPrometheus() throws IOException, ParseException {
        final StringBuilder sb = new StringBuilder();
        histoInt.prometheusData(sb, histoIntID, true);
        parsePrometheusText(new LineNumberReader(new StringReader(sb.toString())).lines())
                .forEach(entry -> assertThat("Unexpected value checking " + entry.getKey(),
                                    EXPECTED_PROMETHEUS_RESULTS.get(entry.getKey()),
                                    is(withinTolerance(entry.getValue()))));
    }

    @Test
    void testPrometheusWithTags() {
        final StringBuilder sb = new StringBuilder();
        histoInt.prometheusData(sb, histoIntIDWithTags, true);

        parsePrometheusText(new LineNumberReader(new StringReader(sb.toString())).lines())
                .forEach(entry -> assertThat("Missing tag labels for " + entry.getKey(),
                        tagsFromPrometheusKey(entry.getKey()),
                        MetricsCustomMatchers.MapContains.all(HISTO_INT_TAGS_AS_MAP_PROM)));
    }

    private static Map<String, String> tagsFromPrometheusKey(String promKey) {
        // Actual tags will exclude any possible "quantile" settings.
        List<Tag> result = new ArrayList<>();
        Matcher m = PROMETHEUS_KEY_PATTERN.matcher(promKey);
        if (!m.matches()) {
            fail("Could not parse Prometheus key for tags: " + promKey);
        }
        if (m.groupCount() <= 1) {
            return Collections.emptyMap();
        }

        String tagExprs = m.group(2);
        assertThat("Tag expressions from Prometheus key " + promKey, tagExprs, not(isEmptyOrNullString()));

        return tagsFromDelimitedString(m.group(2), ",");
    }

    private static Map<String, String> tagsFromDelimitedString(String delimitedString, String delimiter) {
        return Arrays.stream(delimitedString.split(delimiter))
                .filter(Predicate.not(tagExpr -> tagExpr.startsWith("quantile")))
                .map(tagExpr -> tagExpr.split("="))
                .peek(arr -> {
                    assertThat("Tag expression is name=value giving two segments", arr.length, is(2));
                })
                .collect(Collectors.toMap(arr -> arr[0], arr -> arr[1]));
    }

    @Test
    void testStatisticalValues() {
        testSnapshot(1, "integers", histoInt.getSnapshot(), 50.6, 29.4389);
        testSnapshot(1, "delegating integers", delegatingHistoInt.getSnapshot(), 50.6, 29.4389);
        testSnapshot(10, "longs", histoLong.getSnapshot(), 506.3, 294.389);
        testSnapshot(10, "delegating longs", delegatingHistoLong.getSnapshot(), 506.3, 294.389);
    }

    @Test
    void testNaNAvoidance() {
        Metadata metadata = Metadata.builder()
                .withName("long_idle_test")
                .withDisplayName("theDisplayName")
                .withDescription("Simulates a long idle period")
                .withType(MetricType.HISTOGRAM)
                .withUnit(MetricUnits.SECONDS)
                .build();
        TestClock testClock = TestClock.create();
        Histogram idleHistogram = HelidonHistogram.create("application", metadata, testClock);

        idleHistogram.update(100);

        for (int i = 1; i < 48; i++) {
            testClock.add(1, TimeUnit.HOURS);
            assertThat("Idle histogram failed after simulating " + i + " hours", idleHistogram.getSnapshot().getMean(),
                    not(equalTo(Double.NaN)));
        }
    }

    private void testSnapshot(int factor, String description, Snapshot snapshot, double mean, double stddev) {
        assertAll("Testing statistical values for " + description,
                  () -> assertThat("median", snapshot.getMedian(), is(withinTolerance(factor * 48))),
                  () -> assertThat("75th percentile", snapshot.get75thPercentile(), is(withinTolerance(factor * 75))),
                  () -> assertThat("95th percentile", snapshot.get95thPercentile(), is(withinTolerance(factor * 96))),
                  () -> assertThat("78th percentile", snapshot.get98thPercentile(), is(withinTolerance(factor * 98))),
                  () -> assertThat("99th percentile", snapshot.get99thPercentile(), is(withinTolerance(factor * 98))),
                  () -> assertThat("999th percentile", snapshot.get999thPercentile(), is(withinTolerance(factor * 99))),
                  () -> assertThat("mean", snapshot.getMean(), is(withinTolerance(mean))),
                  () -> assertThat("stddev", snapshot.getStdDev(), is(withinTolerance(stddev))),
                  () -> assertThat("min", snapshot.getMin(), is(0L)),
                  () -> assertThat("max", snapshot.getMax(), is(factor * 99L)),
                  () -> assertThat("size", snapshot.size(), is(200))
        );
    }
}
