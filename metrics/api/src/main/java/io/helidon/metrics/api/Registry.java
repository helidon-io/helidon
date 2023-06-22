/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.metrics.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;

/**
 * Helidon Metric registry.
 */
public interface Registry extends MetricRegistry {

    /**
     * Built-in scope names.
     */
    Set<String> BUILT_IN_SCOPES = Set.of(BASE_SCOPE, VENDOR_SCOPE, APPLICATION_SCOPE);

    /**
     * Whether a metric is enabled. Metrics can be disabled by name (disables all IDs associated with that name).
     *
     * @param metricName name to look for
     * @return whether the metric is enabled
     */
    boolean enabled(String metricName);

    /**
     * Steam all metrics from this registry.
     *
     * @return stream of metric instances and their IDs
     */
    Stream<MetricInstance> stream();
    /**
     * Returns an {@code Optional} for an entry containing a metric ID and the corresponding metric matching the specified
     * metric name.
     * <p>
     *     If multiple metrics match the name (because of tags), the returned metric is, preferentially, the one (if any) with
     *     no tags. If all metrics registered under the specified name have tags, then the method returns the metric which was
     *     registered earliest
     * </p>
     *
     * @param metricName name of the metric of interest
     * @return {@code Optional} of a map entry containing the metric ID and the metric selected
     */
    Optional<MetricInstance> find(String metricName);
    /**
     * Returns a list of metric ID/metric pairs which match the provided metric name.
     *
     * @param metricName name of the metric of interest
     * @return List of entries indicating metrics with the specified name; empty of no matches
     */
    List<MetricInstance> list(String metricName);

    /**
     * Whether this registry is empty, or contains any metrics.
     *
     * @return {@code true} if this registry is empty
     */
    boolean empty();

    /**
     * Registry type.
     *
     * @return type
     */
    String scope();

    /**
     * Get all metric IDs for a specified name.
     *
     * @param name name to look for
     * @return metric IDs for the name (may have more than one, as tags may be used)
     */
    List<MetricID> metricIdsByName(String name);

    /**
     * Get all metrics by a specified name.
     *
     * @param name name to look for
     * @return all metrics (and associated metadata) if exist
     */
    Optional<MetricsForMetadata> metricsByName(String name);

    /**
     * Metric instance for a metric ID.
     *
     * @param metricID lookup key, not {@code null}
     * @return metric instance
     */
    HelidonMetric getMetric(MetricID metricID);
}
