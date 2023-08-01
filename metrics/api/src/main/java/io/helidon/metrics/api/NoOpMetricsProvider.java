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

import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import io.helidon.metrics.spi.MetricsProvider;

/**
 * No-op implementation of the {@link io.helidon.metrics.api.spi.MetricFactory} interface.
 */
class NoOpMetricsProvider implements MetricsProvider {

    private final MeterRegistry meterRegistry = null;

    static NoOpMetricsProvider create() {
        return new NoOpMetricsProvider();
    }

    @Override
    public MeterRegistry globalRegistry() {
        return null;
    }

    @Override
    public Tag tagOf(String key, String value) {
        return new NoOpTag(key, value);
    }

    @Override
    public Counter metricsCounter(String name, Iterable<Tag> tags) {
        return null;
    }

    @Override
    public Counter metricsCounter(String name, String... tags) {
        return null;
    }

    @Override
    public <T> Counter metricsCounter(String name, Iterable<Tag> tags, T target, Function<T, Double> fn) {
        return null;
    }

    @Override
    public DistributionSummary metricsSummary(String name, Iterable<Tag> tags) {
        return null;
    }

    @Override
    public DistributionSummary metricsSummary(String name, String... tags) {
        return null;
    }

    @Override
    public Timer metricsTimer(String name, Iterable<Tag> tags) {
        return null;
    }

    @Override
    public Timer metricsTimer(String name, String... tags) {
        return null;
    }

    @Override
    public <T> T metricsGauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> valueFunction) {
        return null;
    }

    @Override
    public <T extends Number> T metricsGauge(String name, Iterable<Tag> tags, T number) {
        return null;
    }

    @Override
    public <T extends Number> T metricsGauge(String name, T number) {
        return null;
    }

    @Override
    public <T> T metricsGauge(String name, T obj, ToDoubleFunction<T> valueFunction) {
        return null;
    }

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
