/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Services;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

class TestPrometheusFormatting {

    private static final String TEST_TAG_NAME = "this-scope";

    /*
    Only OpenMetrics format, not the Prometheus exposition format, has the trailing EOF which (for example) the Prometheus
    server expects to see when it probes targets. That's why, below, when we format the output, we specify OpenMetrics as the
    media type.
     */
    private static final String OPENMETRICS_EOF = "# EOF\n";
    private static MetricsFactory metricsFactory;
    private static MeterRegistry meterRegistry;

    private static MetricsConfig metricsConfig;

    @BeforeAll
    static void prep() {
        metricsConfig = MetricsConfig.create();
        metricsFactory = Services.get(MetricsFactory.class);
        meterRegistry = metricsFactory.createMeterRegistry(metricsConfig);
    }

    @AfterAll
    static void cleanUp() {
        meterRegistry.close();
    }

    @Test
    void testCustomRegistryDoesNotUseMicrometerGlobalRegistry() {
        assertThat(meterRegistry.unwrap(io.micrometer.core.instrument.MeterRegistry.class),
                   not(sameInstance(Metrics.globalRegistry)));
    }

    @Test
    void testServiceRegistryOwnedRegistryDoesNotUseMicrometerGlobalRegistry() {
        MeterRegistry globalRegistry = Services.get(MeterRegistry.class);

        assertThat(globalRegistry.unwrap(io.micrometer.core.instrument.MeterRegistry.class),
                   not(sameInstance(Metrics.globalRegistry)));
        assertThat(globalRegistry.unwrap(io.micrometer.core.instrument.MeterRegistry.class),
                   not(sameInstance(meterRegistry.unwrap(io.micrometer.core.instrument.MeterRegistry.class))));
    }

    @Test
    void testCustomRegistryOwnsPublishers() {
        MetricsConfig customConfig = MetricsConfig.builder(metricsConfig)
                .warnOnMultipleRegistries(false)
                .build();
        MeterRegistry customRegistry = metricsFactory.createMeterRegistry(customConfig);
        CompositeMeterRegistry delegate = (CompositeMeterRegistry) customRegistry
                .unwrap(io.micrometer.core.instrument.MeterRegistry.class);
        Set<io.micrometer.core.instrument.MeterRegistry> publishers = delegate.getRegistries();

        assertThat("Publisher count", publishers.size(), is(1));
        customRegistry.close();
        publishers.forEach(publisher -> assertThat("Publisher is closed", publisher.isClosed(), is(true)));
    }

    @Test
    void testRetrievingAll() {
        Counter c = meterRegistry.getOrCreate(metricsFactory.counterBuilder("c1")
                                                       .addTag(metricsFactory.tagCreate(TEST_TAG_NAME, "app")));
        assertThat("Initial counter value", c.count(), Matchers.is(0L));
        c.increment();
        assertThat("After increment", c.count(), Matchers.is(1L));

        Timer d = meterRegistry.getOrCreate(metricsFactory.timerBuilder("t1")
                                                    .addTag(metricsFactory.tagCreate(TEST_TAG_NAME, "other")));
        d.record(3, TimeUnit.SECONDS);

        Timer e = meterRegistry.getOrCreate(metricsFactory.timerBuilder("t1-1")
                                                    .addTag(metricsFactory.tagCreate(TEST_TAG_NAME, "app")));
        e.record(2, TimeUnit.SECONDS);

        MicrometerPrometheusFormatter formatter = MicrometerPrometheusFormatter.builder(meterRegistry)
                .resultMediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .build();
        Optional<Object> outputOpt = formatter.format();

        assertThat("Formatted output",
                   checkAndCast(outputOpt),
                   allOf(
                           containsString(scopeExpr("c1_total",
                                                  "this_scope",
                                                  "app",
                                                  "1.0")),
                         containsString(scopeExpr("t1_seconds_count",
                                                  "this_scope",
                                                  "other",
                                                  "1.0")),
                         containsString(scopeExpr("t1_seconds_sum",
                                                  "this_scope",
                                                  "other",
                                                  "3.0")),
                         containsString(scopeExpr("t1_1_seconds_count",
                                                  "this_scope",
                                                  "app",
                                                  "1.0")),
                         endsWith(OPENMETRICS_EOF)));

    }

    @Test
    void testRetrievingByName() {
        Counter c = meterRegistry.getOrCreate(metricsFactory.counterBuilder("c2")
                                                       .addTag(metricsFactory.tagCreate(TEST_TAG_NAME, "app")));
        assertThat("Initial counter value", c.count(), Matchers.is(0L));
        c.increment();
        assertThat("After increment", c.count(), Matchers.is(1L));

        Timer d = meterRegistry.getOrCreate(metricsFactory.timerBuilder("t2")
                                                    .addTag(metricsFactory.tagCreate(TEST_TAG_NAME, "app")));
        d.record(7, TimeUnit.SECONDS);

        MicrometerPrometheusFormatter formatter = MicrometerPrometheusFormatter.builder(meterRegistry)
                .resultMediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .meterNameSelection(Set.of("c2"))
                .build();
        Optional<Object> outputOpt = formatter.format();

        assertThat("Formatted output",
                   checkAndCast(outputOpt),
                   allOf(containsString(scopeExpr("c2_total",
                                                  "this_scope",
                                                  "app",
                                                  "1.0")),
                         not(containsString(scopeExpr("t2_seconds_count",
                                                      "this_scope",
                                                      "app",
                                                      "1.0"))),
                         not(containsString(scopeExpr("t2_seconds_sum",
                                                      "this_scope",
                                                      "app", "7.0"))),
                         endsWith(OPENMETRICS_EOF)));

    }

    @Test
    void testRetrievingByTag() {

        Counter c = meterRegistry.getOrCreate(metricsFactory.counterBuilder("c3")
                                                       .addTag(metricsFactory.tagCreate(TEST_TAG_NAME, "app")));
        assertThat("Initial counter value", c.count(), is(0L));
        c.increment();
        assertThat("After increment", c.count(), is(1L));

        Timer d = meterRegistry.getOrCreate(metricsFactory.timerBuilder("t3")
                                                    .addTag(metricsFactory.tagCreate(TEST_TAG_NAME, "other-scope")));
        d.record(7, TimeUnit.SECONDS);

        Timer e = meterRegistry.getOrCreate(metricsFactory.timerBuilder("t3-1")
                                                    .addTag(metricsFactory.tagCreate(TEST_TAG_NAME, "app")));
        e.record(2, TimeUnit.SECONDS);

        MeterRegistryFormatter formatter = new MicrometerPrometheusFormatterProvider()
                .formatter(MediaTypes.APPLICATION_OPENMETRICS_TEXT,
                           metricsConfig,
                           meterRegistry,
                           Map.<String, Collection<String>>of(TEST_TAG_NAME, Set.of("app")),
                           Set.of())
                .orElseThrow();

        Optional<Object> outputOpt = formatter.format();

        assertThat("Formatted output",
                   checkAndCast(outputOpt),
                   allOf(containsString(scopeExpr("c3_total",
                                                  "this_scope",
                                                  "app",
                                                  "1.0")),
                         not(containsString(scopeExpr("t3_seconds_count",
                                                      "this_scope",
                                                      "other-scope",
                                                      "1.0"))),
                         not(containsString(scopeExpr("t3_seconds_sum",
                                                      "this_scope",
                                                      "other-scope",
                                                      "3.0"))),
                         containsString(scopeExpr("t3_1_seconds_count",
                                                  "this_scope",
                                                  "app",
                                                  "1.0")),
                         endsWith(OPENMETRICS_EOF)));
    }

    @Test
    void testMeterNameWithColon() {
        Counter withColon = meterRegistry.getOrCreate(metricsFactory.counterBuilder("c:withColon")
                                                               .addTag(metricsFactory.tagCreate(TEST_TAG_NAME, "app")));
        withColon.increment();

        Counter withoutColon = meterRegistry.getOrCreate(metricsFactory.counterBuilder("cWithoutColon")
                                                                  .addTag(metricsFactory.tagCreate(TEST_TAG_NAME, "app")));
        withoutColon.increment(2L);

        Counter withQuestionMark = meterRegistry.getOrCreate(metricsFactory.counterBuilder("c?withQuestionMark")
                                                                      .addTag(metricsFactory.tagCreate(TEST_TAG_NAME,
                                                                                                      "app")));
        withQuestionMark.increment();

        MicrometerPrometheusFormatter formatter = MicrometerPrometheusFormatter.builder(meterRegistry)
                .resultMediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .tagSelection(Map.of(TEST_TAG_NAME, Set.of("app")))
                .build();

        Optional<Object> outputOpt = formatter.format();

        assertThat("Formatted output",
                   checkAndCast(outputOpt),
                   allOf(containsString(scopeExpr("c:withColon_total",
                                                  "this_scope",
                                                  "app",
                                                  "1.0")),
                         containsString(scopeExpr("cWithoutColon_total",
                                                  "this_scope",
                                                  "app",
                                                  "2.0")),
                         containsString(scopeExpr("c_withQuestionMark_total",
                                                  "this_scope",
                                                  "app",
                                                  "1.0")),
                         endsWith(OPENMETRICS_EOF)));
    }

    @Test
    void testMeterNameWithSpecialChars() {
        Counter counterWithDashes = meterRegistry.getOrCreate(metricsFactory.counterBuilder("counter-with-dashes")
                                                                       .addTag(metricsFactory.tagCreate(TEST_TAG_NAME,
                                                                                                       "app")));
        counterWithDashes.increment(3L);

        Counter counterWithUmlauts = meterRegistry.getOrCreate(metricsFactory.counterBuilder("counter-with-umlaut-äöü")
                                                                        .addTag(metricsFactory.tagCreate(TEST_TAG_NAME,
                                                                                                        "app")));
        counterWithUmlauts.increment(4L);
        var formatter =  MicrometerPrometheusFormatter.builder(meterRegistry)
                .resultMediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .build();

        Optional<Object> output = formatter.format();
        assertThat("With dashes",
                   checkAndCast(output),
                   allOf(
                       containsString(scopeExpr("counter_with_dashes_total",
                                                "this_scope",
                                                "app",
                                                "3.0")),
                       containsString(scopeExpr("counter_with_umlaut_____total",
                                                "this_scope",
                                                "app",
                                                "4.0"))));


    }

    @Test
    void testSelectiveByName() {
        Counter counter = meterRegistry.getOrCreate(metricsFactory.counterBuilder("counterByName")
                                                             .addTag(metricsFactory.tagCreate(TEST_TAG_NAME, "app")));
        counter.increment();

        var formatter =  MicrometerPrometheusFormatter.builder(meterRegistry)
                .resultMediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .meterNameSelection(Set.of("counterByName"))
                .build();

        Optional<Object> output = formatter.format();
        assertThat("Selective by name",
                   checkAndCast(output),
                   containsString(scopeExpr("counterByName_total",
                                            "this_scope",
                                            "app",
                                            "1.0")));
    }

    @Test
    void testSelectiveByNameAndTag() {
        Counter counter = meterRegistry.getOrCreate(metricsFactory.counterBuilder("counterByNameAndScope")
                                                             .addTag(metricsFactory.tagCreate(TEST_TAG_NAME, "app")));
        counter.increment(6L);

        var formatter =  MicrometerPrometheusFormatter.builder(meterRegistry)
                .resultMediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .meterNameSelection(Set.of("counterByNameAndScope"))
                .tagSelection(Map.of(TEST_TAG_NAME, Set.of("app")))
                .build();

        Optional<Object> output = formatter.format();
        assertThat("Selective by name and scope",
                   checkAndCast(output),
                   containsString(scopeExpr("counterByNameAndScope_total",
                                            "this_scope",
                                            "app",
                                            "6.0")));
    }

    @Test
    void testEachSelectedTagMustMatch() {
        Counter matching = meterRegistry.getOrCreate(metricsFactory.counterBuilder("sameNameByTags")
                                                              .addTag(metricsFactory.tagCreate(TEST_TAG_NAME, "app"))
                                                              .addTag(metricsFactory.tagCreate("kind", "selected")));
        Counter wrongKind = meterRegistry.getOrCreate(metricsFactory.counterBuilder("sameNameByTags")
                                                               .addTag(metricsFactory.tagCreate(TEST_TAG_NAME, "app"))
                                                               .addTag(metricsFactory.tagCreate("kind", "other")));
        Counter wrongScope = meterRegistry.getOrCreate(metricsFactory.counterBuilder("sameNameByTags")
                                                                .addTag(metricsFactory.tagCreate(TEST_TAG_NAME, "other"))
                                                                .addTag(metricsFactory.tagCreate("kind", "selected")));
        matching.increment();
        wrongKind.increment(2L);
        wrongScope.increment(3L);

        var formatter = MicrometerPrometheusFormatter.builder(meterRegistry)
                .resultMediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .meterNameSelection(Set.of("sameNameByTags"))
                .tagSelection(Map.of(TEST_TAG_NAME, Set.of("app"),
                                     "kind", Set.of("selected")))
                .build();

        assertThat("Every selected tag must match",
                   checkAndCast(formatter.format()),
                   allOf(containsString("sameNameByTags_total{kind=\"selected\",this_scope=\"app\"} 1.0"),
                         not(containsString("kind=\"other\"")),
                         not(containsString("this_scope=\"other\""))));
    }

    @Test
    void testTagOnlySelectionDoesNotExpandHighCardinalityMeterNames() {
        MetricsConfig customConfig = MetricsConfig.builder(metricsConfig)
                .warnOnMultipleRegistries(false)
                .build();
        MeterRegistry customRegistry = metricsFactory.createMeterRegistry(customConfig);
        CompositeMeterRegistry delegate = (CompositeMeterRegistry) customRegistry
                .unwrap(io.micrometer.core.instrument.MeterRegistry.class);
        io.micrometer.core.instrument.MeterRegistry originalPublisher = delegate.getRegistries().iterator().next();
        delegate.remove(originalPublisher);
        originalPublisher.close();

        var trackingRegistry = new TrackingPrometheusMeterRegistry();
        delegate.add(trackingRegistry);
        try {
            int cardinality = 256;
            for (int i = 0; i < cardinality; i++) {
                String tagValue = i == cardinality - 1 ? "selected" : "unselected";
                String cardinalityValue = Integer.toString(i);
                Counter counter = customRegistry.getOrCreate(metricsFactory.counterBuilder("highCardinality")
                                                                      .addTag(metricsFactory.tagCreate(TEST_TAG_NAME,
                                                                                                      tagValue))
                                                                      .addTag(metricsFactory.tagCreate("cardinality",
                                                                                                      cardinalityValue)));
                counter.increment(i + 1);
            }

            trackingRegistry.resetMeterEnumerationCount();
            var formatter = MicrometerPrometheusFormatter.builder(customRegistry)
                    .resultMediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                    .tagSelection(Map.of(TEST_TAG_NAME, Set.of("selected")))
                    .build();

            String output = checkAndCast(formatter.format());
            assertThat("Tag-only selection bypasses meter name expansion",
                       trackingRegistry.meterEnumerationCount(),
                       is(0));
            assertThat("Only the matching high-cardinality sample is formatted",
                       output,
                       allOf(containsString("highCardinality_total{cardinality=\"255\",this_scope=\"selected\"} 256.0"),
                             not(containsString("this_scope=\"unselected\""))));
        } finally {
            customRegistry.close();
        }
    }

    @Test
    void testSelectNonExistentTagValue() {
        Counter counter = meterRegistry.getOrCreate(metricsFactory.counterBuilder("counterByBadScope")
                                                             .addTag(metricsFactory.tagCreate(TEST_TAG_NAME, "app")));
        counter.increment(7L);

        var formatter =  MicrometerPrometheusFormatter.builder(meterRegistry)
                .resultMediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .tagSelection(Map.of(TEST_TAG_NAME, Set.of("missing")))
                .build();

        Optional<Object> output = formatter.format();
        assertThat("Selective by non-existent tag value",
                   output,
                   OptionalMatcher.optionalEmpty());
    }

    @Test
    @SuppressWarnings("removal")
    void testScopeSelectionIsIgnored() {
        Counter counter = meterRegistry.getOrCreate(metricsFactory.counterBuilder("counterByIgnoredScope")
                                                             .scope("explicit"));
        counter.increment(8L);

        var formatter = MicrometerPrometheusFormatter.builder(meterRegistry)
                .resultMediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .meterNameSelection(Set.of("counterByIgnoredScope"))
                .scopeSelection(Set.of("missing"))
                .scopeTagName(TEST_TAG_NAME)
                .build();

        assertThat("Scope selection is ignored",
                   checkAndCast(formatter.format()),
                   containsString("counterByIgnoredScope_total 8.0"));

        MeterRegistryFormatter providerFormatter = new MicrometerPrometheusFormatterProvider()
                .formatter(MediaTypes.APPLICATION_OPENMETRICS_TEXT,
                           metricsConfig,
                           meterRegistry,
                           Optional.of(TEST_TAG_NAME),
                           Set.of("missing"),
                           Set.of("counterByIgnoredScope"))
                .orElseThrow();
        assertThat("Provider scope selection is ignored",
                   checkAndCast(providerFormatter.format()),
                   containsString("counterByIgnoredScope_total 8.0"));
    }

    private static String scopeExpr(String meterName, String key, String value, String suffix) {
        return meterName + "{" + key + "=\"" + value + "\"} " + suffix;
    }

    private static String checkAndCast(Optional<Object> outputOpt) {
        assertThat("Formatted output", outputOpt, OptionalMatcher.optionalPresent());
        assertThat("Formatted output", outputOpt.get(), is(instanceOf(String.class)));

        return (String) outputOpt.get();
    }

    private static class TrackingPrometheusMeterRegistry extends PrometheusMeterRegistry {

        private int meterEnumerationCount;

        private TrackingPrometheusMeterRegistry() {
            super(PrometheusConfig.DEFAULT);
        }

        @Override
        public List<Meter> getMeters() {
            meterEnumerationCount++;
            return super.getMeters();
        }

        private int meterEnumerationCount() {
            return meterEnumerationCount;
        }

        private void resetMeterEnumerationCount() {
            meterEnumerationCount = 0;
        }
    }
}
