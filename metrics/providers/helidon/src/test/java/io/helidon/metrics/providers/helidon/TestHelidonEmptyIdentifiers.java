/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.metrics.providers.helidon;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.FormatterContext;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Tag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestHelidonEmptyIdentifiers {

    @ParameterizedTest
    @ValueSource(strings = {"counter", "functional-counter", "summary", "timer", "gauge-supplier", "gauge-function"})
    void rejectsEmptyNameForEveryMeterType(String meterType) {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        try {
            assertAll(meterType + " must reject an empty name regardless of units",
                      () -> assertThrows(IllegalArgumentException.class,
                                         () -> emptyNameBuilder(factory, meterType), "unitless meter"),
                      () -> assertThrows(IllegalArgumentException.class,
                                         () -> emptyNameBuilder(factory, meterType).baseUnit(Meter.BaseUnits.BYTES),
                                         "a unit cannot substitute for a name"));
        } finally {
            factory.close();
        }
    }

    @Test
    void rejectsEmptyTagKeyButPreservesEmptyValue() {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        try {
            assertThrows(IllegalArgumentException.class, () -> factory.tagCreate("", "value"),
                         "a tag must have a key");
            Tag tag = factory.tagCreate("empty", "");
            assertThat("a legitimate empty value is preserved", tag.value(), is(""));
            assertThat("the tag retains its nonempty key", tag.key(), is("empty"));
        } finally {
            factory.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsImportedEmptyTagKeyBeforeRegistration(boolean useTags) {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        try {
            MeterRegistry registry = factory.createMeterRegistry(MetricsConfig.create());
            int initialCount = registry.meters().size();
            assertThrows(IllegalArgumentException.class, () -> {
                Counter.Builder builder = factory.counterBuilder("invalid.tags");
                Tag tag = new ForeignTag("", "value");
                registry.getOrCreate(useTags ? builder.tags(List.of(tag)) : builder.addTag(tag));
            }, "foreign tags need the same validation as native tags");
            assertThat("a rejected meter must not enter the registry", registry.meters().size(), is(initialCount));
        } finally {
            factory.close();
        }
    }

    @Test
    void rejectsForeignBuildersWithEmptyIdentifiersBeforeRegistration() {
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        try {
            MeterRegistry registry = factory.createMeterRegistry(MetricsConfig.create());
            int initialCount = registry.meters().size();
            assertAll("foreign builder conversion validates identifiers",
                      () -> assertThrows(IllegalArgumentException.class,
                                         () -> registry.getOrCreate(new ForeignCounterBuilder("", Map.of())),
                                         "foreign empty name"),
                      () -> assertThrows(IllegalArgumentException.class,
                                         () -> registry.getOrCreate(new ForeignCounterBuilder("foreign", Map.of("", "value"))),
                                         "foreign empty tag key"));
            assertThat("neither invalid foreign meter is registered", registry.meters().size(), is(initialCount));
        } finally {
            factory.close();
        }
    }

    @Test
    void rejectsConfiguredEmptyGlobalTagKeyAtRegistryConstruction() {
        MetricsConfig invalid = MetricsConfig.builder().tags(List.of(new ForeignTag("", "value"))).build();
        HelidonMetricsFactory factory = HelidonMetricsFactory.builder().metricsConfig(invalid).build();
        try {
            assertAll("configured tags must be validated before any meter is added",
                      () -> assertThrows(IllegalArgumentException.class, () -> factory.createMeterRegistry(invalid),
                                         "local registry with empty global tag key"),
                      () -> assertThrows(IllegalArgumentException.class, factory::globalRegistry,
                                         "global registry with empty global tag key"));
        } finally {
            factory.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectedIdentifiersLeaveHealthyMetersUsableAndScrapable(boolean openMetrics) {
        MetricsConfig config = MetricsConfig.builder().tags(List.of(new ForeignTag("global", ""))).build();
        HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        try {
            MeterRegistry registry = factory.createMeterRegistry(config);
            Counter healthy = registry.getOrCreate(factory.counterBuilder("9healthy.counter")
                                                         .addTag(factory.tagCreate("9empty.value", "")));
            healthy.increment(2);
            int initialCount = registry.meters().size();
            assertAll("bad identifiers must not poison subsequent scrapes",
                      () -> assertThrows(IllegalArgumentException.class,
                                         () -> registry.getOrCreate(factory.counterBuilder(""))),
                      () -> assertThrows(IllegalArgumentException.class,
                                         () -> registry.getOrCreate(factory.counterBuilder("bad.tag")
                                                                            .addTag(new ForeignTag("", "value")))),
                      () -> assertThrows(IllegalArgumentException.class,
                                         () -> factory.createMeterRegistry(MetricsConfig.builder()
                                                                                   .tags(List.of(new ForeignTag("", "value")))
                                                                                   .build())));

            assertThat("registry membership survives rejection", registry.meters().size(), is(initialCount));
            assertThat("the healthy meter retains its identity",
                       registry.meter(Counter.class, "9healthy.counter", List.of(factory.tagCreate("9empty.value", "")))
                               .orElseThrow(),
                       sameInstance(healthy));
            healthy.increment(3);
            assertThat("the healthy counter remains usable", healthy.count(), is(5L));

            FormatterContext context = FormatterContext.builder()
                    .mediaType(openMetrics ? MediaTypes.APPLICATION_OPENMETRICS_TEXT : MediaTypes.TEXT_PLAIN)
                    .metricsConfig(config)
                    .build();
            String output = (String) new HelidonPrometheusFormatterProvider().formatter(context, registry)
                    .orElseThrow().format().orElseThrow();
            assertThat("both formats retain empty values and normalize nonempty identifiers", output,
                       allOf(containsString("_healthy_counter_total{_empty_value=\"\",global=\"\"} 5.0\n"),
                             not(containsString("bad_tag")), not(containsString("{=\""))));
        } finally {
            factory.close();
        }
    }

    private static Meter.Builder<?, ?> emptyNameBuilder(HelidonMetricsFactory factory, String meterType) {
        return switch (meterType) {
            case "counter" -> factory.counterBuilder("");
            case "functional-counter" -> factory.functionalCounterBuilder("", 1L, value -> value);
            case "summary" -> factory.distributionSummaryBuilder("", factory.distributionStatisticsConfigBuilder());
            case "timer" -> factory.timerBuilder("");
            case "gauge-supplier" -> factory.gaugeBuilder("", () -> 1L);
            case "gauge-function" -> factory.gaugeBuilder("", 1L, Long::doubleValue);
            default -> throw new IllegalArgumentException("Unknown meter type: " + meterType);
        };
    }

    private record ForeignTag(String key, String value) implements Tag {
        @Override
        public <T> T unwrap(Class<? extends T> type) {
            return type.cast(this);
        }
    }

    private record ForeignCounterBuilder(String name, Map<String, String> tags) implements Counter.Builder {
        @Override
        public Counter.Builder tags(Iterable<Tag> tags) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Counter.Builder addTag(Tag tag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Counter.Builder description(String description) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Counter.Builder baseUnit(String baseUnit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<String> description() {
            return Optional.empty();
        }

        @Override
        public Optional<String> baseUnit() {
            return Optional.of(Meter.BaseUnits.BYTES);
        }

        @Override
        public <T> T unwrap(Class<? extends T> type) {
            return type.cast(this);
        }
    }
}
