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
import java.util.stream.StreamSupport;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.api.Counter;
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
                .addMeter(meter -> meter.name("configured").percentiles(List.of(0.5, 0.99)))
                .addMeter(meter -> meter.name("aggregate").percentiles(List.of()))
                .addMeter(meter -> meter.name("customized"))
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
        MetricsConfig config = configBuilder().addMeter(meter -> meter.name("timer")).build();
        try (var fixture = new Fixture(config)) {
            Timer timer = fixture.registry.getOrCreate(fixture.factory.timerBuilder("timer").percentiles(0.25, 0.95));
            assertThat(percentiles(timer), contains(0.25, 0.95));
        }
    }

    @Test
    void emptyPercentilesPreserveExplicitHistogramBuckets() {
        MetricsConfig config = configBuilder().addMeter(meter -> meter.name("timer").percentiles(List.of())).build();
        try (var fixture = new Fixture(config)) {
            Timer timer = fixture.registry.getOrCreate(fixture.factory.timerBuilder("timer").buckets(Duration.ofMillis(5)));
            timer.record(2, TimeUnit.MILLISECONDS);

            assertThat(percentiles(timer), empty());
            assertThat(timer.snapshot().histogramCounts().iterator().hasNext(), is(true));
            assertThat(timer.count(), is(1L));
        }
    }

    @Test
    void exactNameDisablementAppliesAcrossTagsWithoutAffectingSimilarNames() {
        MetricsConfig config = configBuilder().addMeter(meter -> meter.name("disabled").enabled(false)).build();
        try (var fixture = new Fixture(config)) {
            for (String role : List.of("client", "server")) {
                assertThat(fixture.registry.isMeterEnabled("disabled", Map.of("role", role)), is(false));
                Timer disabled = fixture.registry.getOrCreate(fixture.factory.timerBuilder("disabled")
                                                                      .tags(List.of(fixture.factory.tagCreate("role", role))));
                disabled.record(1, TimeUnit.MILLISECONDS);
                assertThat(disabled.count(), is(0L));
            }
            Timer unrelated = fixture.registry.getOrCreate(fixture.factory.timerBuilder("disabled.extra"));
            unrelated.record(1, TimeUnit.MILLISECONDS);
            assertThat(unrelated.count(), is(1L));
            assertThat(fixture.registry.isMeterEnabled("disabled"), is(false));
            assertThat(fixture.registry.isMeterEnabled("disabled.extra"), is(true));
            assertThat(fixture.nativeRegistry().find("disabled").meters(), empty());
            assertThrows(NullPointerException.class, () -> fixture.registry.isMeterEnabled(null));
        }
    }

    @Test
    void separateRegistriesApplyTheirOwnSettings() {
        MetricsConfig firstConfig = configBuilder()
                .addMeter(meter -> meter.name("timer").percentiles(List.of()))
                .addMeter(meter -> meter.name("disabled").enabled(false))
                .build();
        MetricsConfig secondConfig = configBuilder()
                .addMeter(meter -> meter.name("timer").percentiles(List.of(0.9)))
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
                .addMeter(meter -> meter.name("configured").enabled(true).percentiles(List.of(0.9)))
                .build();
        try (var fixture = new Fixture(config)) {
            Counter counter = fixture.registry.getOrCreate(fixture.factory.counterBuilder("configured"));
            Timer timer = fixture.registry.getOrCreate(fixture.factory.timerBuilder("unrelated"));
            counter.increment();
            timer.record(1, TimeUnit.MILLISECONDS);

            assertThat(counter.count(), is(0L));
            assertThat(timer.count(), is(0L));
            assertThat(fixture.registry.isMeterEnabled("configured"), is(false));
            assertThat(fixture.registry.isMeterEnabled("configured", Map.of()), is(false));
            assertThat(fixture.nativeRegistry().getMeters(), empty());
        }
    }

    @Test
    void disabledMeterDoesNotApplyIncompatiblePercentileSettings() {
        MetricsConfig config = configBuilder()
                .addMeter(meter -> meter.name("disabled").enabled(false).percentiles(List.of(0.9)))
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
                .addMeter(meter -> meter.name("counter").percentiles(List.of(0.9)))
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
        MetricsConfig config = configBuilder().addMeter(meter -> meter.name("timer").percentiles(List.of())).build();
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
