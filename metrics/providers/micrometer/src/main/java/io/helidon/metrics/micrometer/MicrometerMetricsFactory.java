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
package io.helidon.metrics.micrometer;

import java.util.Optional;
import java.util.function.ToDoubleFunction;

import io.helidon.common.LazyValue;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionStatisticsConfig;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.MetricsProgrammaticSettings;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;

/**
 * Implementation of the neutral Helidon metrics factory based on Micrometer.
 */
class MicrometerMetricsFactory implements MetricsFactory {

    static MicrometerMetricsFactory create(MetricsConfig metricsConfig) {
        return new MicrometerMetricsFactory(metricsConfig);
    }

    private final LazyValue<MeterRegistry> globalMeterRegistry;

    private MicrometerMetricsFactory(MetricsConfig metricsConfig) {
        globalMeterRegistry = LazyValue.create(() -> {
            ensurePrometheusRegistry(Metrics.globalRegistry, metricsConfig);
            return MMeterRegistry.create(Metrics.globalRegistry, metricsConfig);
        });
    }

    @Override
    public MeterRegistry createMeterRegistry(MetricsConfig metricsConfig) {
        return MMeterRegistry.create(metricsConfig);
    }

    @Override
    public MeterRegistry createMeterRegistry(Clock clock, MetricsConfig metricsConfig) {
        return MMeterRegistry.create(clock, metricsConfig);
    }

    @Override
    public MeterRegistry globalRegistry() {
        return globalMeterRegistry.get();
    }

    private static void ensurePrometheusRegistry(CompositeMeterRegistry compositeMeterRegistry,
                                                 MetricsConfig metricsConfig) {
        if (compositeMeterRegistry
                .getRegistries()
                .stream()
                .noneMatch(mr -> mr instanceof PrometheusMeterRegistry)) {
            compositeMeterRegistry.add(new PrometheusMeterRegistry(key -> metricsConfig.lookupConfig(key).orElse(null)));
        }
    }

    @Override
    public Clock clockSystem() {
        return new Clock() {

            private final io.micrometer.core.instrument.Clock delegate = io.micrometer.core.instrument.Clock.SYSTEM;

            @Override
            public long wallTime() {
                return delegate.wallTime();
            }

            @Override
            public long monotonicTime() {
                return delegate.monotonicTime();
            }

            @Override
            public <R> R unwrap(Class<? extends R> c) {
                return c.cast(delegate);
            }
        };
    }

    @Override
    public Counter.Builder counterBuilder(String name) {
        return MCounter.builder(name);
    }

    @Override
    public DistributionStatisticsConfig.Builder distributionStatisticsConfigBuilder() {
        return MDistributionStatisticsConfig.builder();
    }

    @Override
    public DistributionSummary.Builder distributionSummaryBuilder(String name,
                                                                  DistributionStatisticsConfig.Builder configBuilder) {
        return MDistributionSummary.builder(name, configBuilder);
    }

    @Override
    public <T> Gauge.Builder<T> gaugeBuilder(String name, T stateObject, ToDoubleFunction<T> fn) {
        return MGauge.builder(name, stateObject, fn);
    }

    @Override
    public Timer.Builder timerBuilder(String name) {
        return MTimer.builder(name);
    }

    @Override
    public Timer.Sample timerStart() {
        return MTimer.start();
    }

    @Override
    public Timer.Sample timerStart(MeterRegistry registry) {
        return MTimer.start(registry);
    }

    @Override
    public Timer.Sample timerStart(Clock clock) {
        return MTimer.start(clock);
    }

    @Override
    public Tag tagCreate(String key, String value) {
        return MTag.of(key, value);
    }

    @Override
    public HistogramSnapshot histogramSnapshotEmpty(long count, double total, double max) {
        return MHistogramSnapshot.create(io.micrometer.core.instrument.distribution.HistogramSnapshot.empty(count, total, max));
    }
// TODO remove commented code below when it's been transplanted

//    @Override
//    public Optional<?> scrape(MeterRegistry meterRegistry,
//                              MediaType mediaType,
//                              Iterable<String> scopeSelection,
//                              Iterable<String> meterNameSelection) {
//        if (mediaType.equals(MediaTypes.TEXT_PLAIN) || mediaType.equals(MediaTypes.APPLICATION_OPENMETRICS_TEXT)) {
//            var formatter =
//                    MicrometerPrometheusFormatter
//                            .builder(meterRegistry)
//                            .resultMediaType(mediaType)
//                            .scopeTagName(MetricsProgrammaticSettings.instance().scopeTagName())
//                            .scopeSelection(scopeSelection)
//                            .meterNameSelection(meterNameSelection)
//                            .build();
//
//            return formatter.filteredOutput();
//        } else if (mediaType.equals(MediaTypes.APPLICATION_JSON)) {
//            var formatter = JsonFormatter.builder(meterRegistry)
//                    .scopeTagName(MetricsProgrammaticSettings.instance().scopeTagName())
//                    .scopeSelection(scopeSelection)
//                    .meterNameSelection(meterNameSelection)
//                    .build();
//            return formatter.data(true);
//        }
//        throw new UnsupportedOperationException();
//    }
//
//    // TODO return something better
//    @Override
//    public Optional<?> scrapeMetadata(MeterRegistry meterRegistry,
//                                      MediaType mediaType,
//                                      Iterable<String> scopeSelection,
//                                      Iterable<String> meterNameSelection) {
//        return Optional.empty();
//    }
}
