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
package io.helidon.webserver.observe.metrics;


import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.json.JsonObject;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.ScopingConfig;
import io.helidon.metrics.api.SystemTagsManager;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class TestJsonFormatting {
    private static final String SCOPE_TAG_NAME = "the-scope";

    @Test
    void testRetrievingAll() {
        MetricsConfig metricsConfig = MetricsConfig.builder()
                .scoping(ScopingConfig.builder()
                                 .tagName(SCOPE_TAG_NAME)
                                 .defaultValue("application"))
                .build();

        MeterRegistry meterRegistry = MetricsFactory.getInstance().globalRegistry(metricsConfig);
        SystemTagsManager.instance(metricsConfig);

        Counter c = meterRegistry.getOrCreate(Counter.builder("c1"));
        assertThat("Initial counter value", c.count(), is(0L));
        c.increment();
        assertThat("After increment", c.count(), is(1L));

        Counter c1WithTag = meterRegistry.getOrCreate(Counter.builder("c1")
                                                              .tags(Set.of(Tag.create("t1", "v1"))));
        c1WithTag.increment(4L);

        Timer d = meterRegistry.getOrCreate(Timer.builder("t1"));
        d.record(3, TimeUnit.SECONDS);


        JsonFormatter formatter = JsonFormatter.builder(metricsConfig, meterRegistry)
                .scopeTagName(SCOPE_TAG_NAME)
                .build();

        JsonObject jsonOutput = checkAndCast(formatter.format());
        JsonObject app = jsonOutput.objectValue("application").orElseThrow();
        assertThat("Counter 1",
                   app.numberValue("c1;t1=v1").orElseThrow().intValue(),
                   is(4));
        assertThat("Counter 2",
                   app.numberValue("c1").orElseThrow().intValue(),
                   is(1));
        JsonObject timerJson = app.objectValue("t1").orElseThrow();
        assertThat("Timer", timerJson, notNullValue());
        assertThat("Timer count", timerJson.numberValue("count").orElseThrow().intValue(), is(1));
    }


    @Test
    void testRetrievingByName() {
        MetricsConfig metricsConfig = MetricsConfig.builder()
                .scoping(ScopingConfig.builder()
                                 .tagName(SCOPE_TAG_NAME)
                                 .defaultValue("application"))
                .build();

        MeterRegistry meterRegistry = MetricsFactory.getInstance().globalRegistry(metricsConfig);
        SystemTagsManager.instance(metricsConfig);

        Counter c = meterRegistry.getOrCreate(Counter.builder("c2"));
        assertThat("Initial counter value", c.count(), is(0L));
        c.increment();
        assertThat("After increment", c.count(), is(1L));

        Timer d = meterRegistry.getOrCreate(Timer.builder("t2"));
        d.record(7, TimeUnit.SECONDS);

        JsonFormatter formatter = JsonFormatter.builder(metricsConfig, meterRegistry)
                .meterNameSelection(Set.of("c2"))
                .build();

        JsonObject jsonOutput = checkAndCast(formatter.format());
        JsonObject app = jsonOutput.objectValue("application").orElseThrow();
        assertThat("Counter 2", app.numberValue("c2").orElseThrow().intValue(), is(1));

        assertThat("Timer", app.objectValue("t2").orElse(null), nullValue());

    }

    @Test
    void testTimerUnits() {
        // Prepare metrics config with no setting for the timer JSON output default. THe output should be in seconds.
        MetricsConfig metricsConfig = MetricsConfig.builder()
                .scoping(ScopingConfig.builder()
                                 .tagName(SCOPE_TAG_NAME)
                                 .defaultValue("application"))
                .build();

        MeterRegistry meterRegistry = MetricsFactory.getInstance().globalRegistry(metricsConfig);
        SystemTagsManager.instance(metricsConfig);

        Timer t = meterRegistry.getOrCreate(Timer.builder("timerWithMilliseconds")
                                                    .baseUnit("milliseconds"));
        t.record(Duration.ofMillis(256));

        JsonFormatter formatter = JsonFormatter.builder(metricsConfig, meterRegistry)
                .meterNameSelection(Set.of("timerWithMilliseconds"))
                .build();
        JsonObject jsonOutput = checkAndCast(formatter.format());
        JsonObject metadata = checkAndCast(formatter.formatMetadata());

        JsonObject app = jsonOutput.objectValue("application").orElseThrow();
        JsonObject timerJson = app.objectValue("timerWithMilliseconds").orElseThrow();
        assertThat("Timer", timerJson.numberValue("elapsedTime").orElseThrow().doubleValue(), is(0.256d));

        JsonObject metaApp = metadata.objectValue("application").orElseThrow();
        JsonObject metaTimerJson = metaApp.objectValue("timerWithMilliseconds").orElseThrow();

        // We did not set the default JSON output units in config, so it should be seconds even though the timer was set
        // to milliseconds.
        assertThat("Timer units metadata", metaTimerJson.stringValue("unit").orElseThrow(), is("SECONDS"));
    }

    @Test
    void testTimerUnitsWithConfigSetting() {
        // Prepare metrics config with no setting for the timer JSON output default. THe output should be in seconds.
        MetricsConfig metricsConfig = MetricsConfig.builder()
                .scoping(ScopingConfig.builder()
                                 .tagName(SCOPE_TAG_NAME)
                                 .defaultValue("application"))
                .jsonUnitsDefault(TimeUnit.MILLISECONDS)
                .build();

        MeterRegistry meterRegistry = MetricsFactory.getInstance().globalRegistry(metricsConfig);
        SystemTagsManager.instance(metricsConfig);

        Timer timerWithMicroSeconds = meterRegistry.getOrCreate(Timer.builder("timerWithMicroSeconds")
                                                    .baseUnit("microseconds"));
        timerWithMicroSeconds.record(Duration.ofMillis(3256));

        Timer timerWithNoUnits = meterRegistry.getOrCreate(Timer.builder("timerWithNoUnits"));
        timerWithNoUnits.record(Duration.ofMillis(128));

        JsonFormatter formatter = JsonFormatter.builder(metricsConfig, meterRegistry)
                .meterNameSelection(Set.of("timerWithMicroSeconds", "timerWithNoUnits"))
                .build();
        JsonObject jsonOutput = checkAndCast(formatter.format());
        JsonObject metadata = checkAndCast(formatter.formatMetadata());

        JsonObject app = jsonOutput.objectValue("application").orElseThrow();
        JsonObject timerWithMicroSecondsJson = app.objectValue("timerWithMicroSeconds").orElseThrow();
        JsonObject timerWithNoUnitsJson = app.objectValue("timerWithNoUnits").orElseThrow();

        JsonObject metadataApp = metadata.objectValue("application").orElseThrow();
        JsonObject metadataTimerWithMicroSecondsJson = metadataApp.objectValue("timerWithMicroSeconds").orElseThrow();
        JsonObject metadataTimerWithNoUnitsJson = metadataApp.objectValue("timerWithNoUnits").orElseThrow();

        assertThat("Timer with explicit microseconds units",
                   timerWithMicroSecondsJson.numberValue("elapsedTime").orElseThrow().doubleValue(),
                   is(3256000d));
        assertThat("Timer with no explicit units",
                   timerWithNoUnitsJson.numberValue("elapsedTime").orElseThrow().doubleValue(),
                   is(128d));

        assertThat("Timer with explicit microseconds metadata units",
                   metadataTimerWithMicroSecondsJson.stringValue("unit").orElseThrow(), is("MICROSECONDS"));
        assertThat("Timer with no explicit units metadata units",
                   metadataTimerWithNoUnitsJson.stringValue("unit").orElseThrow(), is("MILLISECONDS"));

    }



    private static JsonObject checkAndCast(Optional<Object> metricsOutput) {
        assertThat("Result", metricsOutput, OptionalMatcher.optionalPresent());
        assertThat("Result", metricsOutput.get(), is(instanceOf(JsonObject.class)));
        return (JsonObject) metricsOutput.get();
    }
}
