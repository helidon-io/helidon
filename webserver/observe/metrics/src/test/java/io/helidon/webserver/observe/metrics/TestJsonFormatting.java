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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.json.JsonObject;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestJsonFormatting {

    @Test
    void testRetrievingAll() {
        MetricsConfig metricsConfig = MetricsConfig.create();

        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        MeterRegistry meterRegistry = metricsFactory.createMeterRegistry(metricsConfig);
        try {
            Counter c = meterRegistry.getOrCreate(metricsFactory.counterBuilder("c1"));
            assertThat("Initial counter value", c.count(), is(0L));
            c.increment();
            assertThat("After increment", c.count(), is(1L));

            Counter c1WithTag = meterRegistry.getOrCreate(metricsFactory.counterBuilder("c1")
                                                                  .tags(Set.of(metricsFactory.tagCreate("t1", "v1"))));
            c1WithTag.increment(4L);

            Timer d = meterRegistry.getOrCreate(metricsFactory.timerBuilder("t1"));
            d.record(3, TimeUnit.SECONDS);


            JsonFormatter formatter = JsonFormatter.builder(metricsConfig, meterRegistry).build();

            JsonObject jsonOutput = checkAndCast(formatter.format());
            assertThat("Counter 1",
                       jsonOutput.numberValue("c1;t1=v1").orElseThrow().intValue(),
                       is(4));
            assertThat("Counter 2",
                       jsonOutput.numberValue("c1").orElseThrow().intValue(),
                       is(1));
            JsonObject timerJson = jsonOutput.objectValue("t1").orElseThrow();
            assertThat("Timer", timerJson, notNullValue());
            assertThat("Timer count", timerJson.numberValue("count").orElseThrow().intValue(), is(1));
        } finally {
            meterRegistry.close();
        }
    }


    @Test
    void testRetrievingByName() {
        MetricsConfig metricsConfig = MetricsConfig.create();

        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        MeterRegistry meterRegistry = metricsFactory.createMeterRegistry(metricsConfig);
        try {
            Counter c = meterRegistry.getOrCreate(metricsFactory.counterBuilder("c2"));
            assertThat("Initial counter value", c.count(), is(0L));
            c.increment();
            assertThat("After increment", c.count(), is(1L));

            Timer d = meterRegistry.getOrCreate(metricsFactory.timerBuilder("t2"));
            d.record(7, TimeUnit.SECONDS);

            JsonFormatter formatter = JsonFormatter.builder(metricsConfig, meterRegistry)
                    .meterNameSelection(Set.of("c2"))
                    .build();

            JsonObject jsonOutput = checkAndCast(formatter.format());
            assertThat("Counter 2", jsonOutput.numberValue("c2").orElseThrow().intValue(), is(1));

            assertThat("Timer", jsonOutput.objectValue("t2").orElse(null), nullValue());
        } finally {
            meterRegistry.close();
        }

    }

    @Test
    void testTimerUnits() {
        // Prepare metrics config with no setting for the timer JSON output default. The output should be in seconds.
        MetricsConfig metricsConfig = MetricsConfig.create();

        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        MeterRegistry meterRegistry = metricsFactory.createMeterRegistry(metricsConfig);
        try {
            Timer t = meterRegistry.getOrCreate(metricsFactory.timerBuilder("timerWithMilliseconds")
                                                        .baseUnit("milliseconds"));
            t.record(Duration.ofMillis(256));

            JsonFormatter formatter = JsonFormatter.builder(metricsConfig, meterRegistry)
                    .meterNameSelection(Set.of("timerWithMilliseconds"))
                    .build();
            JsonObject jsonOutput = checkAndCast(formatter.format());
            JsonObject metadata = checkAndCast(formatter.formatMetadata());

            JsonObject timerJson = jsonOutput.objectValue("timerWithMilliseconds").orElseThrow();
            assertThat("Timer", timerJson.numberValue("elapsedTime").orElseThrow().doubleValue(), is(0.256d));

            JsonObject metaTimerJson = metadata.objectValue("timerWithMilliseconds").orElseThrow();

            // We did not set the default JSON output units in config, so it should be seconds even though the timer was set
            // to milliseconds.
            assertThat("Timer units metadata", metaTimerJson.stringValue("unit").orElseThrow(), is("SECONDS"));
        } finally {
            meterRegistry.close();
        }
    }

    @Test
    void testTimerUnitsWithConfigSetting() {
        // Prepare metrics config with an explicit timer JSON output default.
        MetricsConfig metricsConfig = MetricsConfig.builder()
                .jsonUnitsDefault(TimeUnit.MILLISECONDS)
                .build();

        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        MeterRegistry meterRegistry = metricsFactory.createMeterRegistry(metricsConfig);
        try {
            Timer timerWithMicroSeconds = meterRegistry.getOrCreate(metricsFactory.timerBuilder("timerWithMicroSeconds")
                                                        .baseUnit("microseconds"));
            timerWithMicroSeconds.record(Duration.ofMillis(3256));

            Timer timerWithNoUnits = meterRegistry.getOrCreate(metricsFactory.timerBuilder("timerWithNoUnits"));
            timerWithNoUnits.record(Duration.ofMillis(128));

            JsonFormatter formatter = JsonFormatter.builder(metricsConfig, meterRegistry)
                    .meterNameSelection(Set.of("timerWithMicroSeconds", "timerWithNoUnits"))
                    .build();
            JsonObject jsonOutput = checkAndCast(formatter.format());
            JsonObject metadata = checkAndCast(formatter.formatMetadata());

            JsonObject timerWithMicroSecondsJson = jsonOutput.objectValue("timerWithMicroSeconds").orElseThrow();
            JsonObject timerWithNoUnitsJson = jsonOutput.objectValue("timerWithNoUnits").orElseThrow();

            JsonObject metadataTimerWithMicroSecondsJson = metadata.objectValue("timerWithMicroSeconds").orElseThrow();
            JsonObject metadataTimerWithNoUnitsJson = metadata.objectValue("timerWithNoUnits").orElseThrow();

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
        } finally {
            meterRegistry.close();
        }

    }

    @Test
    void testRetrievingByTags() {
        MetricsConfig metricsConfig = MetricsConfig.create();
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        MeterRegistry meterRegistry = metricsFactory.createMeterRegistry(metricsConfig);
        try {
            Counter red = meterRegistry.getOrCreate(metricsFactory.counterBuilder("cars")
                                                            .addTag(metricsFactory.tagCreate("color", "red")));
            red.increment(3);
            Counter blue = meterRegistry.getOrCreate(metricsFactory.counterBuilder("cars")
                                                             .addTag(metricsFactory.tagCreate("color", "blue")));
            blue.increment(5);
            meterRegistry.getOrCreate(metricsFactory.counterBuilder("trucks")).increment();

            MeterRegistryFormatter formatter = new JsonMeterRegistryFormatterProvider()
                    .formatter(MediaTypes.APPLICATION_JSON,
                               metricsConfig,
                               meterRegistry,
                               Map.of("color", List.of("red")),
                               Set.of())
                    .orElseThrow();

            JsonObject jsonOutput = checkAndCast(formatter.format());
            assertThat("Selected tagged counter",
                       jsonOutput.numberValue("cars;color=red").orElseThrow().intValue(),
                       is(3));
            assertThat("Unselected tagged counter", jsonOutput.value("cars;color=blue").orElse(null), nullValue());
            assertThat("Meter missing selected tag", jsonOutput.value("trucks").orElse(null), nullValue());
        } finally {
            meterRegistry.close();
        }
    }

    @Test
    void testScopeLikeTagsRemainOrdinaryTags() {
        MetricsConfig metricsConfig = MetricsConfig.create();
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        MeterRegistry meterRegistry = metricsFactory.createMeterRegistry(metricsConfig);
        try {
            meterRegistry.getOrCreate(metricsFactory.counterBuilder("scope-tagged")
                                              .addTag(metricsFactory.tagCreate("scope", "custom")))
                    .increment();
            meterRegistry.getOrCreate(metricsFactory.counterBuilder("mp-scope-tagged")
                                              .addTag(metricsFactory.tagCreate("mp_scope", "vendor")))
                    .increment();

            JsonFormatter formatter = JsonFormatter.builder(metricsConfig, meterRegistry).build();
            JsonObject jsonOutput = checkAndCast(formatter.format());

            assertThat("Literal scope tag",
                       jsonOutput.numberValue("scope-tagged;scope=custom").orElseThrow().intValue(),
                       is(1));
            assertThat("Literal MP scope tag",
                       jsonOutput.numberValue("mp-scope-tagged;mp_scope=vendor").orElseThrow().intValue(),
                       is(1));
        } finally {
            meterRegistry.close();
        }
    }

    @Test
    @SuppressWarnings("removal")
    void testLegacyScopeSelectionIsIgnored() {
        MetricsConfig metricsConfig = MetricsConfig.create();
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        MeterRegistry meterRegistry = metricsFactory.createMeterRegistry(metricsConfig);
        try {
            meterRegistry.getOrCreate(metricsFactory.counterBuilder("counter")).increment();

            MeterRegistryFormatter formatter = new JsonMeterRegistryFormatterProvider()
                    .formatter(MediaTypes.APPLICATION_JSON,
                               metricsConfig,
                               meterRegistry,
                               Optional.of("scope"),
                               Set.of("does-not-exist"),
                               Set.of())
                    .orElseThrow();

            JsonObject jsonOutput = checkAndCast(formatter.format());
            assertThat("Scope selection is ignored", jsonOutput.numberValue("counter").orElseThrow().intValue(), is(1));
        } finally {
            meterRegistry.close();
        }
    }

    @Test
    void testProviderRejectsNullArguments() {
        var provider = new JsonMeterRegistryFormatterProvider();
        MetricsConfig metricsConfig = MetricsConfig.create();
        MeterRegistry meterRegistry = Services.get(MeterRegistry.class);
        Map<String, Collection<String>> tagSelection = Map.of();
        Iterable<String> nameSelection = Set.of();

        assertThat("Valid non-matching media type",
                   provider.formatter(MediaTypes.TEXT_PLAIN,
                                      metricsConfig,
                                      meterRegistry,
                                      tagSelection,
                                      nameSelection),
                   OptionalMatcher.optionalEmpty());

        assertAll("Generic formatter arguments",
                  () -> assertThrows(NullPointerException.class,
                                     () -> provider.formatter(null,
                                                              metricsConfig,
                                                              meterRegistry,
                                                              tagSelection,
                                                              nameSelection)),
                  () -> assertThrows(NullPointerException.class,
                                     () -> provider.formatter(MediaTypes.TEXT_PLAIN,
                                                              null,
                                                              meterRegistry,
                                                              tagSelection,
                                                              nameSelection)),
                  () -> assertThrows(NullPointerException.class,
                                     () -> provider.formatter(MediaTypes.TEXT_PLAIN,
                                                              metricsConfig,
                                                              null,
                                                              tagSelection,
                                                              nameSelection)),
                  () -> assertThrows(NullPointerException.class,
                                     () -> provider.formatter(MediaTypes.TEXT_PLAIN,
                                                              metricsConfig,
                                                              meterRegistry,
                                                              null,
                                                              nameSelection)),
                  () -> assertThrows(NullPointerException.class,
                                     () -> provider.formatter(MediaTypes.TEXT_PLAIN,
                                                              metricsConfig,
                                                              meterRegistry,
                                                              tagSelection,
                                                              null)));
    }

    @Test
    @SuppressWarnings("removal")
    void testDeprecatedProviderRejectsNullArguments() {
        var provider = new JsonMeterRegistryFormatterProvider();
        MetricsConfig metricsConfig = MetricsConfig.create();
        MeterRegistry meterRegistry = Services.get(MeterRegistry.class);
        Optional<String> scopeTagName = Optional.empty();
        Iterable<String> scopeSelection = Set.of();
        Iterable<String> nameSelection = Set.of();

        assertAll("Deprecated formatter arguments",
                  () -> assertThrows(NullPointerException.class,
                                     () -> provider.formatter(null,
                                                              metricsConfig,
                                                              meterRegistry,
                                                              scopeTagName,
                                                              scopeSelection,
                                                              nameSelection)),
                  () -> assertThrows(NullPointerException.class,
                                     () -> provider.formatter(MediaTypes.TEXT_PLAIN,
                                                              null,
                                                              meterRegistry,
                                                              scopeTagName,
                                                              scopeSelection,
                                                              nameSelection)),
                  () -> assertThrows(NullPointerException.class,
                                     () -> provider.formatter(MediaTypes.TEXT_PLAIN,
                                                              metricsConfig,
                                                              null,
                                                              scopeTagName,
                                                              scopeSelection,
                                                              nameSelection)),
                  () -> assertThrows(NullPointerException.class,
                                     () -> provider.formatter(MediaTypes.TEXT_PLAIN,
                                                              metricsConfig,
                                                              meterRegistry,
                                                              null,
                                                              scopeSelection,
                                                              nameSelection)),
                  () -> assertThrows(NullPointerException.class,
                                     () -> provider.formatter(MediaTypes.TEXT_PLAIN,
                                                              metricsConfig,
                                                              meterRegistry,
                                                              scopeTagName,
                                                              null,
                                                              nameSelection)),
                  () -> assertThrows(NullPointerException.class,
                                     () -> provider.formatter(MediaTypes.TEXT_PLAIN,
                                                              metricsConfig,
                                                              meterRegistry,
                                                              scopeTagName,
                                                              scopeSelection,
                                                              null)));
    }

    private static JsonObject checkAndCast(Optional<Object> metricsOutput) {
        assertThat("Result", metricsOutput, OptionalMatcher.optionalPresent());
        assertThat("Result", metricsOutput.get(), is(instanceOf(JsonObject.class)));
        return (JsonObject) metricsOutput.get();
    }

}
