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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import io.helidon.dbclient.DbInterceptor;
import io.helidon.dbclient.DbInterceptorContext;
import io.helidon.dbclient.DbStatementType;
import io.helidon.metrics.RegistryFactory;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricType;

/**
 * Common ancestor for Helidon DB metrics.
 */
abstract class DbMetric<T extends Metric> implements DbInterceptor {
    private final Metadata meta;
    private final String description;
    private final BiFunction<String, DbStatementType, String> nameFunction;
    private final MetricRegistry registry;
    private final ConcurrentHashMap<String, T> cache = new ConcurrentHashMap<>();
    private final boolean measureErrors;
    private final boolean measureSuccess;

    protected DbMetric(DbMetricBuilder<?> builder) {
        BiFunction<String, DbStatementType, String> namedFunction = builder.nameFormat();
        this.meta = builder.meta();

        if (null == namedFunction) {
            namedFunction = (name, statement) -> defaultNamePrefix() + name;
        }
        this.nameFunction = namedFunction;
        this.registry = RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.APPLICATION);
        this.measureErrors = builder.measureErrors();
        this.measureSuccess = builder.measureSuccess();
        String tmpDescription;
        if (builder.description() == null) {
            tmpDescription = ((null == meta) ? "" : meta.getDescription());
        } else {
            tmpDescription = builder.description();
        }
        this.description = tmpDescription;
    }

    protected abstract String defaultNamePrefix();

    @Override
    public CompletableFuture<DbInterceptorContext> statement(DbInterceptorContext interceptorContext) {
        DbStatementType dbStatementType = interceptorContext.statementType();
        String statementName = interceptorContext.statementName();

        T metric = cache.computeIfAbsent(statementName, s -> {
            String name = nameFunction.apply(statementName, dbStatementType);
            Metadata metadata;

            if (null == meta) {
                metadata = new Metadata(name, metricType());
                metadata.setDescription(description);
            } else {
                metadata = new Metadata(name,
                                        meta.getDisplayName(),
                                        description,
                                        meta.getTypeRaw(),
                                        meta.getUnit(),
                                        meta.getTagsAsString());
            }
            return metric(registry, metadata);
        });

        executeMetric(metric, interceptorContext.statementFuture());

        return CompletableFuture.completedFuture(interceptorContext);
    }

    protected boolean measureErrors() {
        return measureErrors;
    }

    protected boolean measureSuccess() {
        return measureSuccess;
    }

    protected abstract void executeMetric(T metric, CompletionStage<Void> aFuture);
    protected abstract MetricType metricType();
    protected abstract T metric(MetricRegistry registry, Metadata meta);
}
