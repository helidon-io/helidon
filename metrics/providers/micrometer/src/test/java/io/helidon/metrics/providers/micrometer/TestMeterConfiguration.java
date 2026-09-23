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

package io.helidon.metrics.providers.micrometer;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.api.Bucket;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.MeterConfig;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.api.ValueAtPercentile;
import io.helidon.metrics.spi.MeterBuilderCustomizer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestMeterConfiguration {

    @Test
    void yamlSettingsPreserveDefaultsAndDisableOnlySelectedMeasurements() {
        MetricsConfig config = MetricsConfig.create(Config.just(ConfigSources.classpath("meter-configuration.yaml"))
                                                           .get("metrics"));
        try (var fixture = new Fixture(config)) {
            Timer defaults = fixture.registry.getOrCreate(fixture.factory.timerBuilder("default.timer"));
            Timer aggregate = fixture.registry.getOrCreate(fixture.factory.timerBuilder("aggregate.timer"));
            Timer custom = fixture.registry.getOrCreate(fixture.factory.timerBuilder("custom.timer"));
            Timer disabled = fixture.registry.getOrCreate(fixture.factory.timerBuilder("disabled.timer"));
            Counter unrelated = fixture.registry.getOrCreate(fixture.factory.counterBuilder("unrelated.counter"));

            for (Timer timer : List.of(defaults, aggregate, custom, disabled)) {
                timer.record(2, TimeUnit.MILLISECONDS);
                timer.record(8, TimeUnit.MILLISECONDS);
            }
            unrelated.increment(3);

            assertThat(percentiles(defaults), contains(0.5, 0.75, 0.95, 0.98, 0.99, 0.999));
            assertThat(percentiles(aggregate), empty());
            assertThat(percentiles(custom), contains(0.5, 0.99));
            assertThat(buckets(custom).stream().map(bucket -> bucket.boundary(TimeUnit.MILLISECONDS)).toList(),
                       contains(5D, 10D));
            assertThat(buckets(custom).stream().map(Bucket::count).toList(), contains(1L, 2L));
            assertThat(aggregate.count(), is(2L));
            assertThat(aggregate.totalTime(TimeUnit.MILLISECONDS), is(10D));
            assertThat(aggregate.max(TimeUnit.MILLISECONDS), is(8D));
            assertThat(aggregate.mean(TimeUnit.MILLISECONDS), is(5D));
            assertThat(disabled.count(), is(0L));
            assertThat(unrelated.count(), is(3L));
            assertThat(fixture.registry.timer("disabled.timer", List.of()).isEmpty(), is(true));
            assertThat(fixture.nativeRegistry().find("disabled.timer").timer(), nullValue());
            assertThat(fixture.nativeRegistry().find("aggregate.timer").timer(), not(nullValue()));
        }
    }

    @Test
    void registrySettingsOverrideCustomizersAndExplicitBuilderPercentiles() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("configured")).percentiles(List.of(0.5, 0.99)))
                .addMeter(meter -> meter.namePattern(Pattern.compile("aggregate")).percentiles(List.of()))
                .addMeter(meter -> meter.namePattern(Pattern.compile("customized")))
                .build();
        MeterBuilderCustomizer customizer = builder -> {
            if (builder instanceof Timer.Builder timerBuilder) {
                timerBuilder.percentiles(0.2, 0.8);
            }
        };
        try (var fixture = new Fixture(config, List.of(customizer))) {
            Timer configured = fixture.registry.getOrCreate(fixture.factory.timerBuilder("configured")
                                                                    .percentiles(0.1, 0.9));
            Timer aggregate = fixture.registry.getOrCreate(fixture.factory.timerBuilder("aggregate")
                                                                   .percentiles(0.1, 0.9));
            Timer customized = fixture.registry.getOrCreate(fixture.factory.timerBuilder("customized")
                                                                    .percentiles(0.1, 0.9));

            assertThat(percentiles(configured), contains(0.5, 0.99));
            assertThat(percentiles(aggregate), empty());
            assertThat(percentiles(customized), contains(0.2, 0.8));
        }
    }

    @Test
    void absentPercentilesPreserveExplicitBuilderSettings() {
        MetricsConfig config = configBuilder().addMeter(meter -> meter.namePattern(Pattern.compile("timer"))).build();
        try (var fixture = new Fixture(config)) {
            Timer timer = fixture.registry.getOrCreate(fixture.factory.timerBuilder("timer").percentiles(0.25, 0.95));
            assertThat(percentiles(timer), contains(0.25, 0.95));
        }
    }

    @Test
    void emptyPercentilesPreserveExplicitHistogramBuckets() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("timer")).percentiles(List.of()))
                .build();
        try (var fixture = new Fixture(config)) {
            Timer timer = fixture.registry.getOrCreate(fixture.factory.timerBuilder("timer").buckets(Duration.ofMillis(5)));
            timer.record(2, TimeUnit.MILLISECONDS);

            assertThat(percentiles(timer), empty());
            assertThat(timer.snapshot().histogramCounts().iterator().hasNext(), is(true));
            assertThat(timer.count(), is(1L));
        }
    }

    @Test
    void configuredBucketsOverrideBuilderAndCustomizerWithoutChangingPercentiles() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("configured"))
                        .buckets(List.of(Duration.ofMillis(5), Duration.ofMillis(10))))
                .build();
        MeterBuilderCustomizer customizer = builder -> {
            if (builder instanceof Timer.Builder timerBuilder) {
                timerBuilder.buckets(Duration.ofMillis(3));
            }
        };
        try (var fixture = new Fixture(config, List.of(customizer))) {
            Timer timer = fixture.registry.getOrCreate(fixture.factory.timerBuilder("configured")
                                                               .buckets(Duration.ofMillis(1))
                                                               .percentiles(0.5, 0.99));
            timer.record(Duration.ofMillis(2));
            timer.record(Duration.ofMillis(8));
            timer.record(Duration.ofMillis(12));

            assertThat(buckets(timer).stream().map(bucket -> bucket.boundary(TimeUnit.MILLISECONDS)).toList(),
                       contains(5D, 10D));
            assertThat(buckets(timer).stream().map(Bucket::count).toList(), contains(1L, 2L));
            assertThat(timer.count(), is(3L));
            assertThat(percentiles(timer), contains(0.5, 0.99));
        }
    }

    @Test
    void absentBucketsPreserveBuilderAndEmptyBucketsClearOnlyExplicitBoundaries() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("preserved")))
                .addMeter(meter -> meter.namePattern(Pattern.compile("cleared")).buckets(List.of()))
                .addMeter(meter -> meter.namePattern(Pattern.compile("automatic")).buckets(List.of()))
                .build();
        try (var fixture = new Fixture(config)) {
            Timer preserved = fixture.registry.getOrCreate(fixture.factory.timerBuilder("preserved")
                                                                   .buckets(Duration.ofMillis(5)));
            Timer cleared = fixture.registry.getOrCreate(fixture.factory.timerBuilder("cleared")
                                                                 .buckets(Duration.ofMillis(5))
                                                                 .percentiles(0.5));
            Timer automatic = fixture.registry.getOrCreate(fixture.factory.timerBuilder("automatic")
                                                                   .buckets(Duration.ofMillis(200))
                                                                   .publishPercentileHistogram(true)
                                                                   .minimumExpectedValue(Duration.ofMillis(2))
                                                                   .maximumExpectedValue(Duration.ofMillis(20)));
            cleared.record(Duration.ofMillis(8));

            assertThat(buckets(preserved).stream().map(bucket -> bucket.boundary(TimeUnit.MILLISECONDS)).toList(),
                       contains(5D));
            assertThat(buckets(cleared), empty());
            assertThat(percentiles(cleared), contains(0.5));
            assertThat(cleared.count(), is(1L));
            assertThat(cleared.totalTime(TimeUnit.MILLISECONDS), is(8D));
            assertThat(buckets(automatic).getFirst().boundary(TimeUnit.MILLISECONDS), is(2D));
            assertThat(buckets(automatic).getLast().boundary(TimeUnit.MILLISECONDS), is(20D));
        }
    }

    @Test
    void expectedRangeOverridesBuilderAndCustomizerWithoutDiscardingMeasurements() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("timer"))
                        .minimumExpectedValue(Duration.ofMillis(2))
                        .maximumExpectedValue(Duration.ofMillis(20)))
                .build();
        MeterBuilderCustomizer customizer = builder -> {
            if (builder instanceof Timer.Builder timerBuilder) {
                timerBuilder.minimumExpectedValue(Duration.ofMillis(3)).maximumExpectedValue(Duration.ofMillis(50));
            }
        };
        try (var fixture = new Fixture(config, List.of(customizer))) {
            Timer timer = fixture.registry.getOrCreate(fixture.factory.timerBuilder("timer")
                                                               .publishPercentileHistogram(true)
                                                               .minimumExpectedValue(Duration.ofMillis(1))
                                                               .maximumExpectedValue(Duration.ofMillis(100)));
            timer.record(Duration.ofMillis(1));
            timer.record(Duration.ofMillis(10));
            timer.record(Duration.ofMillis(30));

            List<Bucket> histogram = buckets(timer);
            assertThat(histogram.getFirst().boundary(TimeUnit.MILLISECONDS), is(2D));
            assertThat(histogram.getLast().boundary(TimeUnit.MILLISECONDS), is(20D));
            assertThat(histogram.getFirst().count(), is(1L));
            assertThat(histogram.getLast().count(), is(2L));
            assertThat(timer.count(), is(3L));
            assertThat(timer.totalTime(TimeUnit.MILLISECONDS), is(41D));
        }
    }

    @Test
    void absentExpectedBoundsPreserveBuilderValues() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("both")))
                .addMeter(meter -> meter.namePattern(Pattern.compile("minimum"))
                        .minimumExpectedValue(Duration.ofMillis(2)))
                .addMeter(meter -> meter.namePattern(Pattern.compile("maximum"))
                        .maximumExpectedValue(Duration.ofMillis(20)))
                .build();
        try (var fixture = new Fixture(config)) {
            for (String name : List.of("both", "minimum", "maximum")) {
                Timer timer = fixture.registry.getOrCreate(fixture.factory.timerBuilder(name)
                                                                   .publishPercentileHistogram(true)
                                                                   .minimumExpectedValue(Duration.ofMillis(1))
                                                                   .maximumExpectedValue(Duration.ofMillis(100)));
                List<Bucket> histogram = buckets(timer);
                assertThat(name, histogram.getFirst().boundary(TimeUnit.MILLISECONDS), is(name.equals("minimum") ? 2D : 1D));
                assertThat(name, histogram.getLast().boundary(TimeUnit.MILLISECONDS), is(name.equals("maximum") ? 20D : 100D));
            }
        }
    }

    @Test
    void rejectsConfiguredBoundConflictingWithPreservedBuilderBound() {
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
            }
            assertThat(fixture.nativeRegistry().getMeters(), empty());
        }
    }

    @Test
    void histogramSettingsRejectNonTimersAndRespectDisablement() {
        List<MeterConfig> settings = List.of(
                MeterConfig.builder().namePattern(Pattern.compile("counter"))
                        .buckets(List.of(Duration.ofMillis(5))).build(),
                MeterConfig.builder().namePattern(Pattern.compile("counter"))
                        .minimumExpectedValue(Duration.ofMillis(2)).build(),
                MeterConfig.builder().namePattern(Pattern.compile("counter"))
                        .maximumExpectedValue(Duration.ofMillis(20)).build());
        for (MeterConfig meter : settings) {
            try (var fixture = new Fixture(configBuilder().addMeter(meter).build())) {
                var failure = assertThrows(IllegalArgumentException.class,
                                           () -> fixture.registry.getOrCreate(fixture.factory.counterBuilder("counter")));
                assertThat(failure.getMessage(), containsString("not a timer: counter"));
                assertThat(fixture.nativeRegistry().getMeters(), empty());
            }
            List<MetricsConfig> disabledConfigs = List.of(
                    configBuilder().enabled(false).addMeter(meter).build(),
                    configBuilder().addMeter(builder -> builder.from(meter).enabled(false)).build());
            for (MetricsConfig config : disabledConfigs) {
                try (var fixture = new Fixture(config)) {
                    Counter counter = fixture.registry.getOrCreate(fixture.factory.counterBuilder("counter"));
                    counter.increment();
                    assertThat(counter.count(), is(0L));
                    assertThat(fixture.nativeRegistry().getMeters(), empty());
                }
            }
        }
    }

    @Test
    void firstMatchingRuleDisablesFullNamesAcrossTagsWithoutAffectingSimilarNames() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("disabled|excluded")).enabled(false))
                .addMeter(meter -> meter.namePattern(Pattern.compile(".*")).enabled(true))
                .build();
        try (var fixture = new Fixture(config)) {
            for (String name : List.of("disabled", "excluded")) {
                assertThat(name, fixture.registry.isMeterEnabled(name), is(false));
                for (String role : List.of("client", "server")) {
                    assertThat(name + ": " + role,
                               fixture.registry.isMeterEnabled(name, Map.of("role", role)), is(false));
                    Timer disabled = fixture.registry.getOrCreate(fixture.factory.timerBuilder(name)
                            .tags(List.of(fixture.factory.tagCreate("role", role))));
                    disabled.record(1, TimeUnit.MILLISECONDS);
                    assertThat(name + ": " + role, disabled.count(), is(0L));
                }
                assertThat(name, fixture.nativeRegistry().find(name).meters(), empty());
            }
            for (String name : List.of("disabled.extra", "prefix.disabled")) {
                Timer unrelated = fixture.registry.getOrCreate(fixture.factory.timerBuilder(name));
                unrelated.record(1, TimeUnit.MILLISECONDS);
                assertThat(name, unrelated.count(), is(1L));
                assertThat(name, fixture.registry.isMeterEnabled(name), is(true));
            }
            assertThrows(NullPointerException.class, () -> fixture.registry.isMeterEnabled(null));
        }
    }

    @Test
    void patternAppliesTimerStatisticsAcrossNamesAndTags() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("(request|response)\\.duration"))
                        .percentiles(List.of(0.5, 0.99))
                        .buckets(List.of(Duration.ofMillis(5), Duration.ofMillis(10)))
                        .minimumExpectedValue(Duration.ofMillis(2))
                        .maximumExpectedValue(Duration.ofMillis(20)))
                .build();
        try (var fixture = new Fixture(config)) {
            for (String name : List.of("request.duration", "response.duration")) {
                for (String role : List.of("client", "server")) {
                    Timer timer = fixture.registry.getOrCreate(fixture.factory.timerBuilder(name)
                            .tags(List.of(fixture.factory.tagCreate("role", role)))
                            .publishPercentileHistogram(true));
                    timer.record(Duration.ofMillis(1));
                    timer.record(Duration.ofMillis(8));
                    timer.record(Duration.ofMillis(30));

                    String reason = name + ": " + role;
                    List<Bucket> histogram = buckets(timer);
                    assertThat(reason, fixture.registry.isMeterEnabled(name, Map.of("role", role)), is(true));
                    assertThat(reason, percentiles(timer), contains(0.5, 0.99));
                    assertThat(reason,
                               histogram.stream().map(bucket -> bucket.boundary(TimeUnit.MILLISECONDS)).toList(),
                               hasItems(5D, 10D));
                    assertThat(reason, histogram.getFirst().boundary(TimeUnit.MILLISECONDS), is(2D));
                    assertThat(reason, histogram.getLast().boundary(TimeUnit.MILLISECONDS), is(20D));
                    assertThat(reason, histogram.getFirst().count(), is(1L));
                    assertThat(reason, histogram.getLast().count(), is(2L));
                    assertThat(reason, timer.count(), is(3L));
                    assertThat(reason, timer.totalTime(TimeUnit.MILLISECONDS), is(39D));
                }
            }
        }
    }

    @Test
    void firstMatchingRuleSuppliesCompleteConfigurationWithoutMergingLaterRules() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("service\\..+")).percentiles(List.of(0.5)))
                .addMeter(meter -> meter.namePattern(Pattern.compile("service\\.request"))
                        .enabled(false)
                        .percentiles(List.of(0.99))
                        .buckets(List.of(Duration.ofMillis(7)))
                        .minimumExpectedValue(Duration.ofMillis(2))
                        .maximumExpectedValue(Duration.ofMillis(20)))
                .build();
        try (var fixture = new Fixture(config)) {
            for (String role : List.of("client", "server")) {
                assertThat(role, fixture.registry.isMeterEnabled("service.request", Map.of("role", role)), is(true));
                Timer timer = fixture.registry.getOrCreate(fixture.factory.timerBuilder("service.request")
                        .tags(List.of(fixture.factory.tagCreate("role", role)))
                        .buckets(Duration.ofMillis(5))
                        .publishPercentileHistogram(true)
                        .minimumExpectedValue(Duration.ofMillis(1))
                        .maximumExpectedValue(Duration.ofMillis(100)));
                timer.record(Duration.ofMillis(2));

                List<Bucket> histogram = buckets(timer);
                List<Double> boundaries = histogram.stream()
                        .map(bucket -> bucket.boundary(TimeUnit.MILLISECONDS))
                        .toList();
                assertThat(role, timer.count(), is(1L));
                assertThat(role, percentiles(timer), contains(0.5));
                assertThat(role, boundaries, hasItem(5D));
                assertThat(role, boundaries, not(hasItem(7D)));
                assertThat(role, histogram.getFirst().boundary(TimeUnit.MILLISECONDS), is(1D));
                assertThat(role, histogram.getLast().boundary(TimeUnit.MILLISECONDS), is(100D));
                assertThat(role,
                           fixture.nativeRegistry().find("service.request").tags("role", role).timer(), not(nullValue()));
            }
            assertThat(fixture.registry.isMeterEnabled("service.request"), is(true));
        }
    }

    @Test
    void separateRegistriesApplyTheirOwnSettings() {
        MetricsConfig firstConfig = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("timer")).percentiles(List.of()))
                .addMeter(meter -> meter.namePattern(Pattern.compile("disabled")).enabled(false))
                .build();
        MetricsConfig secondConfig = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("timer")).percentiles(List.of(0.9)))
                .build();
        try (var fixture = new Fixture(firstConfig)) {
            MeterRegistry second = fixture.factory.createMeterRegistry(secondConfig);
            Timer firstTimer = fixture.registry.getOrCreate(fixture.factory.timerBuilder("timer"));
            Timer secondTimer = second.getOrCreate(fixture.factory.timerBuilder("timer"));

            assertThat(percentiles(firstTimer), empty());
            assertThat(percentiles(secondTimer), contains(0.9));
            assertThat(fixture.registry.isMeterEnabled("disabled"), is(false));
            assertThat(second.isMeterEnabled("disabled"), is(true));
        }
    }

    @Test
    void globalDisableWinsOverEnabledMeterAndPercentileTypeCheck() {
        MetricsConfig config = configBuilder()
                .enabled(false)
                .addMeter(meter -> meter.namePattern(Pattern.compile("configured\\..*"))
                        .enabled(true).percentiles(List.of(0.9)))
                .build();
        try (var fixture = new Fixture(config)) {
            Counter counter = fixture.registry.getOrCreate(fixture.factory.counterBuilder("configured.counter"));
            Timer timer = fixture.registry.getOrCreate(fixture.factory.timerBuilder("unrelated"));
            counter.increment();
            timer.record(1, TimeUnit.MILLISECONDS);

            assertThat(counter.count(), is(0L));
            assertThat(timer.count(), is(0L));
            assertThat(fixture.registry.isMeterEnabled("configured.counter"), is(false));
            assertThat(fixture.registry.isMeterEnabled("configured.counter", Map.of()), is(false));
            assertThat(fixture.registry.isMeterEnabled("unrelated"), is(false));
            assertThat(fixture.nativeRegistry().getMeters(), empty());
        }
    }

    @Test
    void disabledMeterDoesNotApplyIncompatiblePercentileSettings() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("disabled"))
                        .enabled(false).percentiles(List.of(0.9)))
                .build();
        try (var fixture = new Fixture(config)) {
            Counter counter = fixture.registry.getOrCreate(fixture.factory.counterBuilder("disabled"));
            counter.increment();

            assertThat(counter.count(), is(0L));
            assertThat(fixture.nativeRegistry().getMeters(), empty());
        }
    }

    @Test
    void rejectsPercentilesForEnabledNonTimerBeforeRegistration() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("counter")).percentiles(List.of(0.9)))
                .build();
        try (var fixture = new Fixture(config)) {
            var failure = assertThrows(IllegalArgumentException.class,
                                       () -> fixture.registry.getOrCreate(fixture.factory.counterBuilder("counter")));
            assertThat(failure.getMessage(), containsString("not a timer: counter"));
            assertThat(fixture.nativeRegistry().getMeters(), empty());
        }
    }

    @Test
    void settingsApplyAfterAdaptingNeutralBuilder() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("timer")).percentiles(List.of()))
                .build();
        try (var fixture = new Fixture(config)) {
            Timer.Builder delegate = fixture.factory.timerBuilder("timer").percentiles(0.9);
            Timer.Builder neutralBuilder = (Timer.Builder) Proxy.newProxyInstance(Timer.Builder.class.getClassLoader(),
                    new Class<?>[] {Timer.Builder.class},
                    (_, method, args) -> method.invoke(delegate, args));
            Timer timer = fixture.registry.getOrCreate(neutralBuilder);
            timer.record(1, TimeUnit.MILLISECONDS);

            assertThat(percentiles(timer), empty());
            assertThat(timer.count(), is(1L));
        }
    }

    private static MetricsConfig.Builder configBuilder() {
        return MetricsConfig.builder().config(Config.empty()).warnOnMultipleRegistries(false);
    }

    private static List<Double> percentiles(Timer timer) {
        return StreamSupport.stream(timer.snapshot().percentileValues().spliterator(), false)
                .map(ValueAtPercentile::percentile)
                .toList();
    }

    private static List<Bucket> buckets(Timer timer) {
        return StreamSupport.stream(timer.snapshot().histogramCounts().spliterator(), false).toList();
    }

    private static final class Fixture implements AutoCloseable {
        private final MicrometerMetricsFactory factory;
        private final MeterRegistry registry;

        private Fixture(MetricsConfig config) {
            this(config, List.of());
        }

        private Fixture(MetricsConfig config, List<MeterBuilderCustomizer> customizers) {
            factory = MicrometerMetricsFactory.create(config,
                                                      List.of(),
                                                      customizers,
                                                      List.of(),
                                                      new NoOpSpanContextSupplierProvider());
            registry = factory.globalRegistry();
        }

        @Override
        public void close() {
            factory.close();
        }

        private io.micrometer.core.instrument.MeterRegistry nativeRegistry() {
            return registry.unwrap(io.micrometer.core.instrument.MeterRegistry.class);
        }
    }
}
