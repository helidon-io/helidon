/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;

/**
 * Meter for Helidon DB. This class implements the {@link io.helidon.dbclient.DbClientService} and
 * can be configured either through a {@link io.helidon.dbclient.DbClient.Builder} or through configuration.
 */
final class DbClientMeter extends DbClientMetric<Meter> {
    private DbClientMeter(Builder builder) {
        super(builder);
    }

    /**
     * Create a new fluent API builder to create a new meter metric.
     * @return a new builder instance
     */
    static Builder builder() {
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

    /**
     * Fluent API builder for {@link DbClientMeter}.
     */
    static class Builder extends DbClientMetricBuilder {
        @Override
        public DbClientMeter build() {
            return new DbClientMeter(this);
        }
    }
}
