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
package io.helidon.dbclient.metrics;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import io.helidon.common.LazyValue;
import io.helidon.dbclient.DbClientServiceBase;
import io.helidon.dbclient.DbClientServiceContext;
import io.helidon.dbclient.DbStatementType;
import io.helidon.metrics.api.RegistryFactory;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetadataBuilder;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;

/**
 * Common ancestor for DbClient metrics.
 */
abstract class MetricService<T extends Metric> extends DbClientServiceBase {
    private final Metadata meta;
    private final String description;
    private final BiFunction<String, DbStatementType, String> nameFunction;
    private final LazyValue<MetricRegistry> registry = LazyValue.create(
            () -> RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.APPLICATION));
    private final ConcurrentHashMap<String, T> cache = new ConcurrentHashMap<>();
    private final boolean measureErrors;
    private final boolean measureSuccess;

    /**
     * Create a new instance.
     *
     * @param builder builder
     */
    protected MetricService(MetricBuilderBase<?, ?> builder) {
        super(builder);

        BiFunction<String, DbStatementType, String> nameFunction = builder.nameFormat();
        this.meta = builder.meta();

        if (null == nameFunction) {
            nameFunction = (name, statement) -> defaultNamePrefix() + name;
        }
        this.nameFunction = nameFunction;
        this.measureErrors = builder.errors();
        this.measureSuccess = builder.success();
        String tmpDescription;
        if (builder.description() == null) {
            tmpDescription = ((null == meta) ? null : meta.getDescription());
        } else {
            tmpDescription = builder.description();
        }
        this.description = tmpDescription;
    }

    /**
     * Get the default name prefix.
     *
     * @return default name prefix
     */
    protected abstract String defaultNamePrefix();

    @Override
    protected DbClientServiceContext apply(DbClientServiceContext context) {
        DbStatementType dbStatementType = context.statementType();
        String statementName = context.statementName();

        T metric = cache.computeIfAbsent(statementName, s -> {
            String name = nameFunction.apply(statementName, dbStatementType);
            MetadataBuilder builder = (meta == null)
                    ? Metadata.builder().withName(name).withType(metricType())
                    : Metadata.builder(meta);
            if (description != null) {
                builder = builder.withDescription(description);
            }
            return metric(registry.get(), builder.build());
        });

        executeMetric(metric, context.statementFuture());

        return context;
    }

    /**
     * Indicate if errors are measured.
     *
     * @return {@code true} if errors are measured
     */
    protected boolean measureErrors() {
        return measureErrors;
    }

    /**
     * Indicate if success is measured.
     *
     * @return {@code true} if success is measured
     */
    protected boolean measureSuccess() {
        return measureSuccess;
    }

    /**
     * Execute the given metric.
     *
     * @param metric metric to execute
     * @param future future
     */
    protected abstract void executeMetric(T metric, CompletionStage<Void> future);

    /**
     * Get the metric type.
     *
     * @return metric type
     */
    protected abstract MetricType metricType();

    /**
     * Create a new metric.
     *
     * @param registry registry
     * @param meta     metadata
     * @return metric
     */
    protected abstract T metric(MetricRegistry registry, Metadata meta);
}
