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
package io.helidon.microprofile.metrics;

import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricID;

/**
 * Pair of a metric ID and the metric.
 * <p>
 *     This is useful in some cases where we select metrics under some criteria and need to return both the ID ahd the metric
 *     itself. (In MP metrics, the metric does not contain its ID.)
 * </p>
 *
 * @param metricID metric ID for the metric
 * @param metric metric
 */
record MetricInstance(MetricID metricID, Metric metric) {}
