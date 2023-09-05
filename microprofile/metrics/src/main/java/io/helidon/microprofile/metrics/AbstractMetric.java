/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.metrics;

import io.helidon.metrics.api.Meter;

import org.eclipse.microprofile.metrics.Metadata;

/**
 * Common reusable implementation for any category of metric implementation (full-featured, no-op).
 * <p>
 * Helidon relies on this additional behavior beyond that provided by MP {@link org.eclipse.microprofile.metrics.Metric}.
 * </p>
 * @param <M> type of {@link io.helidon.metrics.api.Meter} this metric wraps.
 */
abstract class AbstractMetric<M extends Meter> implements HelidonMetric<M> {

    private final String registryType;
    private final Metadata metadata;
    private volatile boolean isDeleted;

    /**
     * Common initialization logic in creating a new metric.
     *
     * @param registryType type of metric registry the metric is registered in
     * @param metadata metric metadata describing the metric
     */
    protected AbstractMetric(String registryType, Metadata metadata) {
        this.registryType = registryType;
        this.metadata = metadata;
    }

    @Override
    public Metadata metadata() {
        return metadata;
    }

    @Override
    public void markAsDeleted() {
        isDeleted = true;
    }

    @Override
    public boolean isDeleted() {
        return isDeleted;
    }

    @Override
    public String registryType() {
        return registryType;
    }
}
