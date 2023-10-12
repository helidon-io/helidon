/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.dbclient.metrics;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Timer;

/**
 * {@link MetricService} implementation for {@link Timer}.
 */
final class MetricTimer extends MetricService<Timer> {

    private MetricTimer(Builder builder) {
        super(builder);
    }

    /**
     * Create a new fluent API builder to create a new timer metric.
     * @return a new builder instance
     */
    static Builder builder() {
        return new Builder();
    }

    @Override
    protected void executeMetric(Timer metric, CompletionStage<Void> future) {
        long started = System.nanoTime();

        future
                .thenAccept(nothing -> {
                    if (measureSuccess()) {
                        update(metric, started);
                    }
                })
                .exceptionally(throwable -> {
                    if (measureErrors()) {
                        update(metric, started);
                    }
                    return null;
                });
    }

    private void update(Timer metric, long started) {
        long delta = System.nanoTime() - started;
        metric.record(Duration.ofNanos(delta));
    }

    @Override
    protected String defaultNamePrefix() {
        return "db.timer.";
    }

    @Override
    protected Class<Timer> metricType() {
        return Timer.class;
    }

    @Override
    protected Timer metric(MeterRegistry registry, MeterMetadata meta) {
        return registry.getOrCreate(meta.apply(Timer.builder(meta.name())));
    }

    /**
     * Fluent API builder for {@link MetricTimer}.
     */
    static class Builder extends DbClientMetricBuilder<Builder, MetricTimer> {

        @Override
        public MetricTimer build() {
            return new MetricTimer(this);
        }
    }
}
