/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.metrics;

import java.util.List;

import javax.json.JsonObjectBuilder;

import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;

/**
 * Helidon Extension of {@link Metric}.
 * All metrics should inherit from {@link MetricImpl}.
 */
interface HelidonMetric extends Metric {
    /**
     * Name of this metric.
     *
     * @return metric name
     */
    String getName();

    /**
     * Add this metrics data to the JSON builder.
     *
     * @param builder builder of the registry (or of a single metric) result
     */
    void jsonData(JsonObjectBuilder builder, MetricID metricID);

    /**
     * Add this metrics metadata to the JSON builder.
     *
     * @param builder builder of the registry (or of a single metric) result
     * @param metricIDs IDs from which to harvest tags (if present)
     */
    void jsonMeta(JsonObjectBuilder builder, List<MetricID> metricIDs);

    /**
     * Return this metric data in prometheus format.
     *
     * @param sb the {@code StringBuilder} used to accumulate the output
     * @param metricID the {@code MetricID} for the metric to be formatted
     * @param withHelpType flag to control if TYPE and HELP are to be included
     */
    void prometheusData(StringBuilder sb, MetricID metricID, boolean withHelpType);

    /**
     * Return a name for this metric, possibly including a unit suffix.
     *
     * @param metricID the {@code MetricID} for the metric to be formatted
     * @return Name for metric.
     */
    String prometheusNameWithUnits(MetricID metricID);

    /**
     * Returns the metadata associated with this metric.
     *
     * @return the metric's {@link Metadata}
     */
    Metadata metadata();

    /**
     * Returns whether the metric has been deleted.
     *
     * @return true if the metrics was removed from the registry; false otherwise
     */
    boolean isDeleted();

    /**
     * Mark this metric as deleted.
     */
    void markAsDeleted();

}
