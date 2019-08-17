/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;

import javax.json.JsonObjectBuilder;

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
     */
    void jsonMeta(JsonObjectBuilder builder);

    /**
     * Return this metric data in prometheus format.
     *
     * @param sb {@code StringBuilder} used for accumulating the output
     * @param effectiveName metric name, possibly prefixed depending on the usage
     * @param tags comma-separated tags expressed as name=value
     */
    void prometheusData(StringBuilder sb, String effectiveName, Map<String,String> tags);
}
