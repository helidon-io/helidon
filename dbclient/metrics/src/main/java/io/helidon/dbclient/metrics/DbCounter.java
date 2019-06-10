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

import io.helidon.config.Config;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;

/**
 * Counter metric for Helidon DB. This class implements the {@link io.helidon.dbclient.DbInterceptor} and
 * can be configured either through a {@link io.helidon.dbclient.DbClient.Builder} or through configuration.
 */
public final class DbCounter extends DbMetric<Counter> {
    private DbCounter(Builder builder) {
        super(builder);
    }

    /**
     * Create a counter from configuration.
     *
     * @param config configuration to read
     * @return a new counter
     * @see io.helidon.dbclient.metrics.DbMetricBuilder#config(io.helidon.config.Config)
     */
    public static DbCounter create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Create a new counter using default configuration.
     * <p>By default the name format is {@code db.counter.statement-name}, where {@code statement-name}
     * is provided at runtime.
     *
     * @return a new counter
     */
    public static DbCounter create() {
        return builder().build();
    }

    /**
     * Create a new fluent API builder to create a new counter metric.
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected void executeMetric(Counter metric, CompletionStage<Void> aFuture) {
        aFuture
                .thenAccept(nothing -> {
                    if (measureSuccess()) {
                        metric.inc();
                    }
                })
                .exceptionally(throwable -> {
                    if (measureErrors()) {
                        metric.inc();
                    }
                    return null;
                });
    }

    @Override
    protected MetricType metricType() {
        return MetricType.COUNTER;
    }

    @Override
    protected Counter metric(MetricRegistry registry, Metadata meta) {
        return registry.counter(meta);
    }

    @Override
    protected String defaultNamePrefix() {
        return "db.counter.";
    }

    /**
     * Fluent API builder for {@link io.helidon.dbclient.metrics.DbCounter}.
     */
    public static class Builder extends DbMetricBuilder<Builder> implements io.helidon.common.Builder<DbCounter> {
        @Override
        public DbCounter build() {
            return new DbCounter(this);
        }
    }
}
