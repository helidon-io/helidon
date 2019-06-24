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
package io.helidon.db.metrics;

import java.util.concurrent.CompletionStage;

import io.helidon.config.Config;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;

/**
 * Meter for Helidon DB.
 */
public final class DbMeter extends DbMetric<Meter> {
    private DbMeter(Builder builder) {
        super(builder);
    }

    public static DbMeter create(Config config) {
        return builder().config(config).build();
    }

    public static DbMeter create() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected void executeMetric(Meter metric, CompletionStage<Void> aFuture) {
        aFuture
                .thenAccept(nothing -> {
                    if (measureSuccess()) {
                        metric.mark();
                    }
                })
                .exceptionally(throwable -> {
                    if (measureErrors()) {
                        metric.mark();
                    }
                    return null;
                });
    }

    @Override
    protected MetricType metricType() {
        return MetricType.COUNTER;
    }

    @Override
    protected Meter metric(MetricRegistry registry, Metadata meta) {
        return registry.meter(meta);
    }

    @Override
    protected String defaultNamePrefix() {
        return "db.meter.";
    }

    public static class Builder extends DbMetricBuilder<Builder> implements io.helidon.common.Builder<DbMeter> {
        @Override
        public DbMeter build() {
            return new DbMeter(this);
        }
    }
}
