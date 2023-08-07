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

import java.util.function.ToDoubleFunction;

import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionStatisticsConfig;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.HistogramSnapshot;
import io.helidon.metrics.api.HistogramSupport;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;

import io.micrometer.core.instrument.Metrics;

/**
 * Implementation of the neutral Helidon metrics factory based on Micrometer.
 */
public class MicrometerMetricsFactory implements MetricsFactory {


    @Override
    public MeterRegistry globalRegistry() {
        return MMeterRegistry.create(Metrics.globalRegistry);
    }

    @Override
    public Counter.Builder counterBuilder(String name) {
        return MCounter.builder(name);
    }

    @Override
    public DistributionStatisticsConfig.Builder distributionStatisticsConfigBuilder() {
        return null;
    }

    @Override
    public DistributionSummary.Builder distributionSummaryBuilder(String name, DistributionStatisticsConfig.Builder configBuilder) {
        return MDistributionSummary.builder(name, configBuilder);
    }

    @Override
    public <T> Gauge.Builder<T> gaugeBuilder(String name, T stateObject, ToDoubleFunction<T> fn) {
        return MGauge.builder(name, stateObject, fn);
    }

    @Override
    public HistogramSupport.Builder histogramSupportBuilder() {
        return null;
    }

    @Override
    public Timer.Builder timerBuilder(String name) {
        return Timer.builder(name);
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

    @Override
    public Meter.Id idOf(String name, Iterable<Tag> tags) {
        return null;
    }

    @Override
    public Tag tagOf(String key, String value) {
        return null;
    }

    @Override
    public HistogramSnapshot histogramSnapshotEmpty(long count, double total, double max) {
        return null;
    }
}
