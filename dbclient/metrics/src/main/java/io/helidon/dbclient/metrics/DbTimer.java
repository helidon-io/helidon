/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;
import org.eclipse.microprofile.metrics.Timer;

/**
 * Timer metric for Helidon DB. This class implements the {@link io.helidon.dbclient.DbInterceptor} and
 * can be configured either through a {@link io.helidon.dbclient.DbClient.Builder} or through configuration.
 */
public final class DbTimer extends DbMetric<Timer> {

    private DbTimer(Builder builder) {
        super(builder);
    }

    /**
     * Create a timer from configuration.
     *
     * @param config configuration to read
     * @return a new timer
     * @see io.helidon.dbclient.metrics.DbMetricBuilder#config(io.helidon.config.Config)
     */
    public static DbTimer create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Create a new timer using default configuration.
     * <p>By default the name format is {@code db.timer.statement-name}, where {@code statement-name}
     * is provided at runtime.
     *
     * @return a new timer
     */
    public static DbTimer create() {
        return builder().build();
    }

    /**
     * Create a new fluent API builder to create a new timer metric.
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected void executeMetric(Timer metric, CompletionStage<Void> aFuture) {
        long started = System.nanoTime();

        aFuture
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
        metric.update(delta, TimeUnit.NANOSECONDS);
    }

    @Override
    protected String defaultNamePrefix() {
        return "db.timer.";
    }

    @Override
    protected MetricType metricType() {
        return MetricType.COUNTER;
    }

    @Override
    protected Timer metric(MetricRegistry registry, Metadata meta) {
        return registry.timer(meta);
    }

    /**
     * Fluent API builder for {@link io.helidon.dbclient.metrics.DbTimer}.
     */
    public static class Builder extends DbMetricBuilder<Builder> implements io.helidon.common.Builder<DbTimer> {
        @Override
        public DbTimer build() {
            return new DbTimer(this);
        }
    }
}
