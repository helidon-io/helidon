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

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.helidon.config.Config;
import io.helidon.metrics.api.Bucket;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterConfig;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.api.ValueAtPercentile;
import io.helidon.metrics.spi.MeterBuilderCustomizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestHelidonMeterConfiguration {

    static Stream<Arguments> nonTimerSettings() {
        return Stream.of(
                Arguments.of("percentiles", meterBuilder().percentiles(List.of(0.9)).build()),
                Arguments.of("empty percentiles", meterBuilder().percentiles(List.of()).build()),
                Arguments.of("buckets", meterBuilder().buckets(List.of(Duration.ofMillis(5))).build()),
                Arguments.of("empty buckets", meterBuilder().buckets(List.of()).build()),
                Arguments.of("minimum", meterBuilder().minimumExpectedValue(Duration.ofMillis(2)).build()),
                Arguments.of("maximum", meterBuilder().maximumExpectedValue(Duration.ofMillis(20)).build()));
    }

    @Test
    void registrySettingsOverrideBuilderAndCustomizer() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("timer"))
                        .percentiles(List.of(0.5, 0.99))
                        .buckets(List.of(Duration.ofMillis(5), Duration.ofMillis(10)))
                        .minimumExpectedValue(Duration.ofMillis(2))
                        .maximumExpectedValue(Duration.ofMillis(20)))
                .build();
        MeterBuilderCustomizer customizer = builder -> {
            if (builder instanceof Timer.Builder timerBuilder) {
                timerBuilder.percentiles(0.2, 0.8)
                        .buckets(Duration.ofMillis(3))
                        .minimumExpectedValue(Duration.ofMillis(3))
                        .maximumExpectedValue(Duration.ofMillis(50));
            }
        };
        try (var fixture = new Fixture(config, List.of(customizer))) {
            Timer timer = fixture.registry.getOrCreate(fixture.factory.timerBuilder("timer")
                    .percentiles(0.1, 0.9)
                    .buckets(Duration.ofMillis(1))
                    .publishPercentileHistogram(true)
                    .minimumExpectedValue(Duration.ofMillis(1))
                    .maximumExpectedValue(Duration.ofMillis(100)));
            timer.record(Duration.ofMillis(1));
            timer.record(Duration.ofMillis(8));
            timer.record(Duration.ofMillis(30));

            assertThat(percentiles(timer), contains(0.5, 0.99));
            List<Bucket> histogram = buckets(timer);
            assertThat(boundaries(timer), hasItem(5D));
            assertThat(boundaries(timer), hasItem(10D));
            assertThat(boundaries(timer), not(hasItem(3D)));
            assertThat(histogram.getFirst().boundary(TimeUnit.MILLISECONDS), is(2D));
            assertThat(histogram.getLast().boundary(TimeUnit.MILLISECONDS), is(20D));
            assertThat(histogram.getFirst().count(), is(1L));
            assertThat(histogram.getLast().count(), is(2L));
            assertThat(timer.count(), is(3L));
            assertThat(timer.totalTime(TimeUnit.MILLISECONDS), is(39D));
            assertThat(timer.max(TimeUnit.MILLISECONDS), is(30D));
        }
    }

    @Test
    void absentSettingsPreserveBuilderAndCustomizer() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("timer")))
                .build();
        try (var fixture = new Fixture(config)) {
            Timer timer = fixture.registry.getOrCreate(fixture.factory.timerBuilder("timer")
                    .percentiles(0.25, 0.95).buckets(Duration.ofMillis(5))
                    .publishPercentileHistogram(true)
                    .minimumExpectedValue(Duration.ofMillis(1))
                    .maximumExpectedValue(Duration.ofMillis(100)));
            assertThat(percentiles(timer), contains(0.25, 0.95));
            assertThat(boundaries(timer), hasItem(5D));
            assertThat(boundaries(timer).getFirst(), is(1D));
            assertThat(boundaries(timer).getLast(), is(100D));
        }
        MeterBuilderCustomizer customizer = builder -> {
            if (builder instanceof Timer.Builder timerBuilder) {
                timerBuilder.percentiles(0.2).buckets(Duration.ofMillis(3));
            }
        };
        try (var fixture = new Fixture(config, List.of(customizer))) {
            Timer timer = fixture.registry.getOrCreate(fixture.factory.timerBuilder("timer")
                    .percentiles(0.9).buckets(Duration.ofMillis(5)));
            assertThat(percentiles(timer), contains(0.2));
            assertThat(boundaries(timer), contains(3D));
        }
    }

    @Test
    void emptyPercentilesPreserveBucketsAndAggregateMeasurements() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("timer|automatic")).percentiles(List.of()))
                .build();
        try (var fixture = new Fixture(config)) {
            Timer timer = fixture.registry.getOrCreate(fixture.factory.timerBuilder("timer")
                    .percentiles(0.9).buckets(Duration.ofMillis(5), Duration.ofMillis(10)));
            timer.record(Duration.ofMillis(2));
            timer.record(Duration.ofMillis(8));
            Timer automatic = fixture.registry.getOrCreate(fixture.factory.timerBuilder("automatic")
                    .publishPercentileHistogram(true)
                    .minimumExpectedValue(Duration.ofMillis(2))
                    .maximumExpectedValue(Duration.ofMillis(20)));

            assertThat(percentiles(timer), empty());
            assertThat(boundaries(timer), contains(5D, 10D));
            assertThat(buckets(timer).stream().map(Bucket::count).toList(), contains(1L, 2L));
            assertThat(timer.count(), is(2L));
            assertThat(timer.totalTime(TimeUnit.MILLISECONDS), is(10D));
            assertThat(timer.mean(TimeUnit.MILLISECONDS), is(5D));
            assertThat(timer.max(TimeUnit.MILLISECONDS), is(8D));
            assertThat(percentiles(automatic), empty());
            assertThat(boundaries(automatic).getFirst(), is(2D));
            assertThat(boundaries(automatic).getLast(), is(20D));
        }
    }

    @Test
    void emptyBucketsPreservePercentilesAndAutomaticHistogram() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("cleared|automatic")).buckets(List.of()))
                .build();
        try (var fixture = new Fixture(config)) {
            Timer cleared = fixture.registry.getOrCreate(fixture.factory.timerBuilder("cleared")
                    .percentiles(0.5).buckets(Duration.ofMillis(5)));
            cleared.record(Duration.ofMillis(8));
            Timer automatic = fixture.registry.getOrCreate(fixture.factory.timerBuilder("automatic")
                    .percentiles(0.9).buckets(Duration.ofMillis(200))
                    .publishPercentileHistogram(true)
                    .minimumExpectedValue(Duration.ofMillis(2))
                    .maximumExpectedValue(Duration.ofMillis(20)));

            assertThat(buckets(cleared), empty());
            assertThat(percentiles(cleared), contains(0.5));
            assertThat(cleared.count(), is(1L));
            assertThat(cleared.totalTime(TimeUnit.MILLISECONDS), is(8D));
            assertThat(percentiles(automatic), contains(0.9));
            assertThat(boundaries(automatic).getFirst(), is(2D));
            assertThat(boundaries(automatic).getLast(), is(20D));
            assertThat(boundaries(automatic), not(hasItem(200D)));
        }
    }

    @Test
    void configuredBoundCombinesWithPreservedBuilderBound() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("minimum"))
                        .minimumExpectedValue(Duration.ofMillis(2)))
                .addMeter(meter -> meter.namePattern(Pattern.compile("maximum"))
                        .maximumExpectedValue(Duration.ofMillis(20)))
                .build();
        try (var fixture = new Fixture(config)) {
            for (String name : List.of("minimum", "maximum")) {
                Timer timer = fixture.registry.getOrCreate(fixture.factory.timerBuilder(name)
                        .publishPercentileHistogram(true)
                        .minimumExpectedValue(Duration.ofMillis(1))
                        .maximumExpectedValue(Duration.ofMillis(100)));
                assertThat(name, boundaries(timer).getFirst(), is(name.equals("minimum") ? 2D : 1D));
                assertThat(name, boundaries(timer).getLast(), is(name.equals("maximum") ? 20D : 100D));
            }
        }
    }

    @Test
    void conflictingConfiguredAndPreservedBoundsRejectBeforeRegistration() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("minimum"))
                        .minimumExpectedValue(Duration.ofMillis(20)))
                .addMeter(meter -> meter.namePattern(Pattern.compile("maximum"))
                        .maximumExpectedValue(Duration.ofMillis(2)))
                .build();
        try (var fixture = new Fixture(config)) {
            for (String name : List.of("minimum", "maximum")) {
                var failure = assertThrows(IllegalArgumentException.class,
                        () -> fixture.registry.getOrCreate(fixture.factory.timerBuilder(name)
                                .minimumExpectedValue(Duration.ofMillis(5))
                                .maximumExpectedValue(Duration.ofMillis(10))));
                assertThat(failure.getMessage(), containsString("minimum-expected-value"));
                assertThat(failure.getMessage(), containsString("maximum-expected-value"));
                assertThat(failure.getMessage(), containsString(name));
                assertThat(name, fixture.registry.meters(), empty());
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonTimerSettings")
    void enabledNonTimerRejectsBeforeRegistration(String option, MeterConfig meter) {
        try (var fixture = new Fixture(configBuilder().addMeter(meter).build())) {
            var failure = assertThrows(IllegalArgumentException.class,
                    () -> fixture.registry.getOrCreate(fixture.factory.counterBuilder("counter")));
            assertThat(option, failure.getMessage(), containsString("not a timer: counter"));
            assertThat(option, fixture.registry.meters(), empty());
            assertThat(option, fixture.registry.counter("counter", List.of()).isEmpty(), is(true));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonTimerSettings")
    void disabledNonTimerSkipsValidation(String option, MeterConfig meter) {
        List<MetricsConfig> configs = List.of(
                configBuilder().enabled(false).addMeter(meter).build(),
                configBuilder().addMeter(builder -> builder.from(meter).enabled(false)).build());
        for (MetricsConfig config : configs) {
            try (var fixture = new Fixture(config)) {
                Counter counter = fixture.registry.getOrCreate(fixture.factory.counterBuilder("counter"));
                counter.increment();
                assertThat(option + ", globally enabled=" + config.enabled(), counter.count(), is(0L));
                assertThat(option, fixture.registry.meters(), empty());
            }
        }
    }

    @Test
    void firstMatchingRuleDoesNotMergeLaterRulesAndAppliesAcrossTags() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("service\\..+")).percentiles(List.of(0.5)))
                .addMeter(meter -> meter.namePattern(Pattern.compile("service\\.request"))
                        .enabled(false).percentiles(List.of(0.99)).buckets(List.of(Duration.ofMillis(7)))
                        .minimumExpectedValue(Duration.ofMillis(2)).maximumExpectedValue(Duration.ofMillis(20)))
                .addMeter(meter -> meter.namePattern(Pattern.compile("disabled")).enabled(false))
                .addMeter(meter -> meter.namePattern(Pattern.compile(".*")).enabled(true))
                .build();
        try (var fixture = new Fixture(config)) {
            for (String role : List.of("client", "server")) {
                var tags = List.of(fixture.factory.tagCreate("role", role));
                Timer timer = fixture.registry.getOrCreate(fixture.factory.timerBuilder("service.request")
                        .tags(tags).buckets(Duration.ofMillis(5)).publishPercentileHistogram(true)
                        .minimumExpectedValue(Duration.ofMillis(1)).maximumExpectedValue(Duration.ofMillis(100)));
                timer.record(Duration.ofMillis(2));

                assertThat(role, fixture.registry.isMeterEnabled("service.request", Map.of("role", role)), is(true));
                assertThat(role, percentiles(timer), contains(0.5));
                assertThat(role, boundaries(timer), hasItem(5D));
                assertThat(role, boundaries(timer), not(hasItem(7D)));
                assertThat(role, boundaries(timer).getFirst(), is(1D));
                assertThat(role, boundaries(timer).getLast(), is(100D));
                assertThat(role, timer.count(), is(1L));
                assertThat(role, fixture.registry.timer("service.request", tags).isPresent(), is(true));

                Timer disabled = fixture.registry.getOrCreate(fixture.factory.timerBuilder("disabled").tags(tags));
                disabled.record(Duration.ofMillis(2));
                assertThat(role, disabled.count(), is(0L));
                assertThat(role, fixture.registry.timer("disabled", tags).isEmpty(), is(true));
            }
            Timer unrelated = fixture.registry.getOrCreate(fixture.factory.timerBuilder("prefix.disabled"));
            unrelated.record(Duration.ofMillis(2));
            assertThat(unrelated.count(), is(1L));
        }
    }

    @Test
    void settingsApplyAfterAdaptingNeutralBuilder() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("timer"))
                        .percentiles(List.of()).buckets(List.of(Duration.ofMillis(5)))
                        .minimumExpectedValue(Duration.ofMillis(2)).maximumExpectedValue(Duration.ofMillis(20)))
                .build();
        try (var fixture = new Fixture(config)) {
            Timer.Builder delegate = fixture.factory.timerBuilder("timer")
                    .percentiles(0.9).buckets(Duration.ofMillis(3)).publishPercentileHistogram(true)
                    .minimumExpectedValue(Duration.ofMillis(1)).maximumExpectedValue(Duration.ofMillis(100));
            Timer.Builder neutral = (Timer.Builder) Proxy.newProxyInstance(Timer.Builder.class.getClassLoader(),
                    new Class<?>[] {Timer.Builder.class}, (_, method, args) -> method.invoke(delegate, args));
            Timer timer = fixture.registry.getOrCreate(neutral);
            timer.record(Duration.ofMillis(4));

            assertThat(percentiles(timer), empty());
            assertThat(boundaries(timer), hasItem(5D));
            assertThat(boundaries(timer), not(hasItem(3D)));
            assertThat(boundaries(timer).getFirst(), is(2D));
            assertThat(boundaries(timer).getLast(), is(20D));
            assertThat(timer.count(), is(1L));
        }
    }

    @Test
    void registriesApplyTheirOwnConfiguration() {
        MetricsConfig firstConfig = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("timer")).percentiles(List.of()))
                .addMeter(meter -> meter.namePattern(Pattern.compile("disabled")).enabled(false))
                .build();
        MetricsConfig secondConfig = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("timer")).percentiles(List.of(0.9)))
                .build();
        try (var fixture = new Fixture(firstConfig)) {
            MeterRegistry second = fixture.factory.createMeterRegistry(secondConfig);
            Timer first = fixture.registry.getOrCreate(fixture.factory.timerBuilder("timer"));
            Timer other = second.getOrCreate(fixture.factory.timerBuilder("timer"));
            first.record(Duration.ofMillis(2));
            other.record(Duration.ofMillis(3));

            assertThat(percentiles(first), empty());
            assertThat(percentiles(other), contains(0.9));
            assertThat(first.totalTime(TimeUnit.MILLISECONDS), is(2D));
            assertThat(other.totalTime(TimeUnit.MILLISECONDS), is(3D));
            Counter disabled = fixture.registry.getOrCreate(fixture.factory.counterBuilder("disabled"));
            Counter enabled = second.getOrCreate(fixture.factory.counterBuilder("disabled"));
            disabled.increment();
            enabled.increment();
            assertThat(disabled.count(), is(0L));
            assertThat(enabled.count(), is(1L));
        }
    }

    @Test
    void nameOnlyEnablementUsesFirstMatchingFullNameRule() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("enabled")).enabled(true))
                .addMeter(meter -> meter.namePattern(Pattern.compile("enabled|disabled")).enabled(false))
                .addMeter(meter -> meter.namePattern(Pattern.compile("disabled")).enabled(true))
                .build();
        try (var fixture = new Fixture(config)) {
            assertThat("The first enabled rule wins", fixture.registry.isMeterEnabled("enabled"), is(true));
            assertThat("The first disabled rule wins", fixture.registry.isMeterEnabled("disabled"), is(false));
            assertThat("Rules match the entire name", fixture.registry.isMeterEnabled("prefix.disabled"), is(true));
            assertThat("Unmatched names retain default enablement", fixture.registry.isMeterEnabled("unrelated"), is(true));
            assertThrows(NullPointerException.class, () -> fixture.registry.isMeterEnabled(null));
        }
    }

    @Test
    void nameOnlyEnablementHonorsGlobalDisableBeforeMeterRules() {
        MetricsConfig config = configBuilder().enabled(false)
                .addMeter(meter -> meter.namePattern(Pattern.compile("enabled")).enabled(true))
                .build();
        try (var fixture = new Fixture(config)) {
            assertThat("An enabled rule cannot override global disablement",
                       fixture.registry.isMeterEnabled("enabled"), is(false));
            assertThat("Global disablement includes unmatched names",
                       fixture.registry.isMeterEnabled("unrelated"), is(false));
            assertThrows(NullPointerException.class, () -> fixture.registry.isMeterEnabled(null));
        }
    }

    private static MetricsConfig.Builder configBuilder() {
        return MetricsConfig.builder().config(Config.empty()).warnOnMultipleRegistries(false);
    }

    private static MeterConfig.Builder meterBuilder() {
        return MeterConfig.builder().namePattern(Pattern.compile("counter"));
    }

    private static List<Double> percentiles(Timer timer) {
        return StreamSupport.stream(timer.snapshot().percentileValues().spliterator(), false)
                .map(ValueAtPercentile::percentile).toList();
    }

    private static List<Bucket> buckets(Timer timer) {
        return StreamSupport.stream(timer.snapshot().histogramCounts().spliterator(), false).toList();
    }

    private static List<Double> boundaries(Timer timer) {
        return buckets(timer).stream().map(bucket -> bucket.boundary(TimeUnit.MILLISECONDS)).toList();
    }

    private static final class Fixture implements AutoCloseable {
        private final HelidonMetricsFactory factory;
        private final MeterRegistry registry;

        private Fixture(MetricsConfig config) {
            this(config, List.of());
        }

        private Fixture(MetricsConfig config, List<MeterBuilderCustomizer> customizers) {
            factory = HelidonMetricsFactory.create(builder -> builder.metricsConfig(config)
                    .meterBuilderCustomizers(customizers));
            registry = factory.globalRegistry();
        }

        @Override
        public void close() {
            factory.close();
        }
    }
}
