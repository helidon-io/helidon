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
package io.helidon.metrics.api;

import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * No-op implementation of the {@link io.helidon.metrics.api.spi.MetricFactory} interface.
 */
class NoOpMetricsFactory implements MetricsFactory {

    private final MeterRegistry meterRegistry = new NoOpMeterRegistry();

    static NoOpMetricsFactory create() {
        return new NoOpMetricsFactory();
    }

    @Override
    public MeterRegistry globalRegistry() {
        return meterRegistry;
    }

    @Override
    public Meter.Id idOf(String name) {
        return idOf(name, Set.of());
    }

    @Override
    public Meter.Id idOf(String name, Iterable<Tag> tags) {
        return NoOpMeter.Id.create(name, tags);
    }

    @Override
    public Tag tagOf(String key, String value) {
        return new NoOpTag(key, value);
    }

    @Override
    public Counter.Builder counterBuilder(String name) {
        return NoOpMeter.Counter.builder(name);
    }

    @Override
    public DistributionSummary.Builder distributionSummaryBuilder(String name, DistributionStatisticsConfig.Builder configBuilder) {
        // TODO
        return null;
    }

    @Override
    public <T> Gauge.Builder<T> gaugeBuilder(String name, T stateObject, ToDoubleFunction<T> fn) {
        return NoOpMeter.Gauge.builder(name, stateObject, fn);
    }

    @Override
    public Timer.Builder timerBuilder(String name) {
        return NoOpMeter.Timer.builder(name);
    }

    @Override
    public HistogramSupport.Builder histogramSupportBuilder() {
        // TODO
        return null;
    }

    @Override
    public DistributionStatisticsConfig.Builder distributionStatisticsConfigBuilder() {
        // TODO
        return null;
    }

    // TODO fix remaining null returns
    @Override
    public HistogramSnapshot histogramSnapshotEmpty(long count, double total, double max) {
        return null;
    }

    @Override
    public Timer.Sample timerStart() {
        return null;
    }

    @Override
    public Timer.Sample timerStart(MeterRegistry registry) {
        return null;
    }

    @Override
    public Timer.Sample timerStart(Clock clock) {
        return null;
    }
}
