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
package io.helidon.metrics.providers.micrometer;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.ScopingConfig;
import io.helidon.metrics.api.SystemTagsManager;
import io.helidon.metrics.api.Timer;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class TestPrometheusFormatting {

    private static final String SCOPE_TAG_NAME = "this-scope";

    /*
    Only OpenMetrics format, not the Prometheus exposition format, has the trailing EOF which (for example) the Prometheus
    server expects to see when it probes targets. That's why, below, when we format the output, we specify OpenMetrics as the
    media type.
     */
    private static final String OPENMETRICS_EOF = "# EOF\n";
    private static MeterRegistry meterRegistry;

    private static MetricsConfig metricsConfig;

    @BeforeAll
    static void prep() {
        MetricsConfig.Builder metricsConfigBuilder = MetricsConfig.builder()
                .scoping(ScopingConfig.builder()
                                 .tagName(SCOPE_TAG_NAME)
                                 .defaultValue("app"));

        metricsConfig = metricsConfigBuilder.build();
        meterRegistry = MetricsFactory.getInstance().globalRegistry(metricsConfig);
    }

    /**
     * Sets the system tags according to what this test expects.
     * <p>
     *     When a metrics factory is obtained via MetricsFactory.getInstance(metricsConfig), that config object initializes
     *     the system tags manager. This happens after the @BeforeAll method runs. So re-assert the values we want for the
     *     test here. We would only need to do it once, not before each test, but it's low cost esp. in a test environment.
     *
     * </p>
     */
    @BeforeEach
    void setUpSystemTags() {
        SystemTagsManager.instance(metricsConfig);
    }

    @Test
    void testRetrievingAll() {
        Counter c = meterRegistry.getOrCreate(Counter.builder("c1"));
        assertThat("Initial counter value", c.count(), Matchers.is(0L));
        c.increment();
        assertThat("After increment", c.count(), Matchers.is(1L));

        Timer d = meterRegistry.getOrCreate(Timer.builder("t1")
                                                    .scope("other"));
        d.record(3, TimeUnit.SECONDS);

        Timer e = meterRegistry.getOrCreate(Timer.builder("t1-1"));
        e.record(2, TimeUnit.SECONDS);

        MicrometerPrometheusFormatter formatter = MicrometerPrometheusFormatter.builder(meterRegistry)
                .resultMediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .scopeTagName(SCOPE_TAG_NAME)
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
        Counter c = meterRegistry.getOrCreate(Counter.builder("c2"));
        assertThat("Initial counter value", c.count(), Matchers.is(0L));
        c.increment();
        assertThat("After increment", c.count(), Matchers.is(1L));

        Timer d = meterRegistry.getOrCreate(Timer.builder("t2"));
        d.record(7, TimeUnit.SECONDS);

        MicrometerPrometheusFormatter formatter = MicrometerPrometheusFormatter.builder(meterRegistry)
                .resultMediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .scopeTagName(SCOPE_TAG_NAME)
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
    void testRetrievingByScope() {

        Counter c = meterRegistry.getOrCreate(Counter.builder("c3"));
        assertThat("Initial counter value", c.count(), is(0L));
        c.increment();
        assertThat("After increment", c.count(), is(1L));

        Timer d = meterRegistry.getOrCreate(Timer.builder("t3")
                                                    .scope("other-scope"));
        d.record(7, TimeUnit.SECONDS);

        Timer e = meterRegistry.getOrCreate(Timer.builder("t3-1"));
        e.record(2, TimeUnit.SECONDS);

        MicrometerPrometheusFormatter formatter = MicrometerPrometheusFormatter.builder(meterRegistry)
                .resultMediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .scopeTagName(SCOPE_TAG_NAME)
                .scopeSelection(Set.of("app"))
                .build();

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

    private static String scopeExpr(String meterName, String key, String value, String suffix) {
        return meterName + "{" + key + "=\"" + value + "\"} " + suffix;
    }

    private static String checkAndCast(Optional<Object> outputOpt) {
        assertThat("Formatted output", outputOpt, OptionalMatcher.optionalPresent());
        assertThat("Formatted output", outputOpt.get(), is(instanceOf(String.class)));

        return (String) outputOpt.get();
    }
}
