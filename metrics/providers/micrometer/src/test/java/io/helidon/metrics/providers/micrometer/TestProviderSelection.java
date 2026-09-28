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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.metrics.api.FormatterContext;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MeterRegistryFormatter;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Timer;
import io.helidon.metrics.api.ValueAtPercentile;
import io.helidon.metrics.providers.helidon.HelidonMetricsFactoryProvider;
import io.helidon.metrics.providers.helidon.HelidonPrometheusFormatterProvider;
import io.helidon.metrics.spi.MeterRegistryFormatterProvider;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test(perMethod = true)
class TestProviderSelection {

    @Test
    void serviceRegistryUsesMicrometerWhenBothProvidersArePresent() {
        assertThat(Services.get(MetricsFactory.class), instanceOf(MicrometerMetricsFactory.class));
    }

    @Test
    void copiedTimerKeepsDefaultPercentiles() {
        assertCopiedTimerPercentiles(_ -> { }, List.of(0.5, 0.75, 0.95, 0.98, 0.99, 0.999));
    }

    @Test
    void copiedTimerKeepsCustomPercentiles() {
        assertCopiedTimerPercentiles(builder -> builder.percentiles(0.9, 0.99), List.of(0.9, 0.99));
    }

    @Test
    void copiedTimerKeepsDisabledPercentiles() {
        assertCopiedTimerPercentiles(builder -> builder.percentiles(new double[0]), List.of());
    }

    @Test
    void formatterProviderDeclinesHelidonRegistry() {
        MetricsFactory helidonFactory = new HelidonMetricsFactoryProvider().create(Config.empty(),
                                                                                   MetricsConfig.create(),
                                                                                   List.of());
        MeterRegistryFormatterProvider provider = new MicrometerPrometheusFormatterProvider();
        FormatterContext context = FormatterContext.builder()
                .mediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .metricsConfig(MetricsConfig.create())
                .build();

        try {
            assertThat("Micrometer formatter declines Helidon registry",
                       provider.formatter(context, helidonFactory.globalRegistry()),
                       is(Optional.empty()));
        } finally {
            helidonFactory.close();
        }
    }

    @Test
    void formatterProviderAcceptsMicrometerRegistry() {
        MetricsFactory micrometerFactory = Services.get(MetricsFactory.class);
        MeterRegistryFormatterProvider provider = new MicrometerPrometheusFormatterProvider();
        FormatterContext context = FormatterContext.builder()
                .mediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .metricsConfig(MetricsConfig.create())
                .build();

        Optional<MeterRegistryFormatter> formatter =
                provider.formatter(context, micrometerFactory.globalRegistry());

        assertThat("Micrometer formatter accepts Micrometer registry", formatter.isPresent(), is(true));
    }

    @Test
    @SuppressWarnings("removal")
    void formatterProviderRejectsNullContextAndDeprecatedApiValues() {
        MetricsFactory micrometerFactory = Services.get(MetricsFactory.class);
        MeterRegistryFormatterProvider provider = new MicrometerPrometheusFormatterProvider();
        MetricsConfig metricsConfig = MetricsConfig.create();
        MeterRegistry meterRegistry = micrometerFactory.globalRegistry();
        FormatterContext context = FormatterContext.builder()
                .mediaType(MediaTypes.APPLICATION_OPENMETRICS_TEXT)
                .metricsConfig(metricsConfig)
                .build();

        assertThrows(NullPointerException.class, () -> provider.formatter(null, meterRegistry));
        assertThrows(NullPointerException.class, () -> provider.formatter(context, null));

        assertThrows(NullPointerException.class,
                     () -> provider.formatter(null,
                                              metricsConfig,
                                              meterRegistry,
                                              Map.of(),
                                              List.of()));
        assertThrows(NullPointerException.class,
                     () -> provider.formatter(MediaTypes.APPLICATION_OPENMETRICS_TEXT,
                                              null,
                                              meterRegistry,
                                              Map.of(),
                                              List.of()));
        assertThrows(NullPointerException.class,
                     () -> provider.formatter(MediaTypes.APPLICATION_OPENMETRICS_TEXT,
                                              metricsConfig,
                                              null,
                                              Map.of(),
                                              List.of()));
        assertThrows(NullPointerException.class,
                     () -> provider.formatter(MediaTypes.APPLICATION_OPENMETRICS_TEXT,
                                              metricsConfig,
                                              meterRegistry,
                                              null,
                                              List.of()));
        assertThrows(NullPointerException.class,
                     () -> provider.formatter(MediaTypes.APPLICATION_OPENMETRICS_TEXT,
                                              metricsConfig,
                                              meterRegistry,
                                              Map.of(),
                                              null));
    }

    private static void assertCopiedTimerPercentiles(Consumer<Timer.Builder> configure, List<Double> expected) {
        MetricsFactory micrometerFactory = Services.get(MetricsFactory.class);
        MetricsConfig metricsConfig = MetricsConfig.create();
        MetricsFactory helidonFactory = new HelidonMetricsFactoryProvider().create(Config.empty(), metricsConfig, List.of());
        try {
            Timer.Builder foreignBuilder = micrometerFactory.timerBuilder("copied.timer");
            Timer.Builder nativeBuilder = helidonFactory.timerBuilder("native.timer");
            configure.accept(foreignBuilder);
            configure.accept(nativeBuilder);
            MeterRegistry registry = helidonFactory.globalRegistry();
            Timer copied = registry.getOrCreate(foreignBuilder);
            Timer nativeTimer = registry.getOrCreate(nativeBuilder);
            for (Timer timer : List.of(copied, nativeTimer)) {
                assertThat(timer.id().name() + " empty snapshot preserves configured percentiles",
                           StreamSupport.stream(timer.snapshot().percentileValues().spliterator(), false)
                                   .map(ValueAtPercentile::percentile).toList(),
                           is(expected));
                timer.record(2, TimeUnit.SECONDS);
                assertThat(timer.id().name() + " populated snapshot preserves configured percentiles",
                           StreamSupport.stream(timer.snapshot().percentileValues().spliterator(), false)
                                   .map(ValueAtPercentile::percentile).toList(),
                           is(expected));
                assertThat(timer.id().name() + " still counts observations", timer.count(), is(1L));
                assertThat(timer.id().name() + " still totals observations", timer.totalTime(TimeUnit.SECONDS), is(2D));
            }
            for (var mediaType : List.of(MediaTypes.TEXT_PLAIN, MediaTypes.APPLICATION_OPENMETRICS_TEXT)) {
                FormatterContext context = FormatterContext.builder()
                        .mediaType(mediaType)
                        .metricsConfig(metricsConfig)
                        .nameSelection(List.of("copied.timer"))
                        .build();
                var formatter = new HelidonPrometheusFormatterProvider().formatter(context, registry).orElseThrow();
                String output = (String) formatter.format().orElseThrow();
                assertThat(mediaType + " publishes exactly the configured quantiles",
                           output.lines().filter(line -> line.startsWith("copied_timer_seconds{")).toList(),
                           is(expected.stream().map(percentile -> "copied_timer_seconds{quantile=\"" + percentile + "\"} 2.0")
                                      .toList()));
                assertThat(mediaType + " publishes the observation count", output, containsString("copied_timer_seconds_count 1\n"));
                assertThat(mediaType + " publishes the total duration", output, containsString("copied_timer_seconds_sum 2.0\n"));
            }
        } finally {
            helidonFactory.close();
        }
    }
}
