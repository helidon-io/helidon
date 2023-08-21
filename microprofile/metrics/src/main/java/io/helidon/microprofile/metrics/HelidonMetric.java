/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
import org.eclipse.microprofile.metrics.Metric;

/**
 * Helidon Extension of {@link Metric}.
 * All metrics should inherit from {@link MetricImpl}.
 */
interface HelidonMetric<M extends Meter> extends Metric {

    /**
     * Indicates if the specified metric is known to have been marked as deleted.
     * <p>
     *     This check makes sense only for metrics which implement {@link HelidonMetric}, which all Helidon-provided metrics do.
     * </p>
     *
     * @param metric the metric to check
     * @return true if the metrics implements {@code HelidonMetric} and also has been marked as deleted; false otherwise
     */
    static boolean isMarkedAsDeleted(Metric metric) {
        return AbstractRegistry.isMarkedAsDeleted(metric);
    }

    /**
     * @return the metadata for the metric
     */
    Metadata metadata();

    /**
     * Record that a previously-registered metric has been removed from the registry.
     */
    void markAsDeleted();

    /**
     * @return true if the metric has been removed from its registry; false if it is still registered
     */
    boolean isDeleted();

    /**
     * @return the name of the registry type in which the metric was registered
     */
    String registryType();

    /**
     * Name of this metric.
     *
     * @return metric name
     */
    default String getName() {
        return metadata().getName();
    }

    /**
     * Returns the metrics name.
     *
     * @return metric name
     */
    default String name() {
        return getName();
    }

    /**
     * Returns the delegate metric which actually records the value(s) of interest.
     *
     * @return the delegate
     */
    M delegate();
}
