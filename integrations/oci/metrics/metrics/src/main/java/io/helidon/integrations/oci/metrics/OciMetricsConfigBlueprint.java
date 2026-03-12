/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.metrics;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.DistributionSummary;
import io.helidon.metrics.api.FunctionalCounter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MetricsPublisherConfig;
import io.helidon.metrics.api.Timer;

import com.oracle.bmc.monitoring.Monitoring;

import static io.helidon.integrations.oci.metrics.OciMetricsService.formattedBaseUnits;

/**
 * OCI metrics configuration blueprint.
 */
@Prototype.Blueprint
@Prototype.Configured
interface OciMetricsConfigBlueprint extends MetricsPublisherConfig, Prototype.Factory<OciMetricsService> {

    /**
     * Default transmission batch size.
     */
    int DEFAULT_BATCH_SIZE = 50;

    /**
     * Name for the OCI metrics service instance.
     *
     * @return OCI metrics service name
     */
    @Option.Configured
    Optional<String> name();

    /**
     * Namespace used for OCI metric data.
     *
     * @return metric namespace
     */
    @Option.Configured
    Optional<String> namespace();

    /**
     * Resource group used for OCI metric data.
     *
     * @return metric resource group
     */
    @Option.Configured
    Optional<String> resourceGroup();

    /**
     * Initial delay before metrics are first posted.
     *
     * @return initial delay
     */
    @Option.Configured
    @Option.Default("PT1S")
    Duration initialDelay();

    /**
     * Delay between metric postings.
     *
     * @return delay
     */
    @Option.Configured
    @Option.Default("PT60S")
    Duration delay();

    /**
     * Delay between individual metric batch postings.
     *
     * @return batch delay
     */
    @Option.Configured
    @Option.Default("PT1S")
    Duration batchDelay();

    /**
     * Compartment ID used in OCI metric details.
     *
     * @return compartment ID
     */
    @Option.Configured
    Optional<String> compartmentId();

    /**
     * Whether metric descriptions should be included.
     *
     * @return whether descriptions are enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean descriptionEnabled();

    /**
     * Metric scopes to transmit.
     *
     * @return metric scopes
     */
    @Option.Configured
    @Option.DefaultCode("new java.util.HashSet<>(io.helidon.metrics.api.Meter.Scope.BUILT_IN_SCOPES)")
    Set<String> scopes();

    /**
     * Whether posting metrics is enabled.
     *
     * @return whether OCI metrics posting is enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Maximum number of metrics in one posting batch.
     *
     * @return batch size
     */
    @Option.Configured
    @Option.DefaultInt(DEFAULT_BATCH_SIZE)
    int batchSize();

    /**
     * Formatter for metrics names.
     *
     * @return name formatter
     */
    @Option.DefaultCode("NameFormatter.DEFAULT")
    NameFormatter nameFormatter();

    /**
     * {@link com.oracle.bmc.monitoring.Monitoring} instance to use in sending telemetry.
     *
     * @return {@code Monitoring} client instance
     */
    Optional<Monitoring> monitoringClient();

    /**
     * Prescribes behavior for formatting metric names for use by OCI.
     */
    interface NameFormatter {

        NameFormatter DEFAULT = new NameFormatter() {
        };

        /**
         * Converts a metric instance into the corresponding text representation of its metric type.
         *
         * @param metric meter to convert
         * @return text type of the metric
         */
        static String textType(Meter metric) {
            if (metric instanceof Counter) {
                return "counter";
            }
            if (metric instanceof FunctionalCounter) {
                return "counter";
            }
            if (metric instanceof Gauge) {
                return "gauge";
            }
            if (metric instanceof DistributionSummary) {
                return "histogram";
            }
            if (metric instanceof Timer) {
                return "timer";
            }
            throw new IllegalArgumentException("Cannot map metric of type " + metric.getClass().getName());
        }

        /**
         * Formats a metric name for OCI.
         *
         * @param metric   the metric to be formatted
         * @param metricId {@code MetricID} of the metric being formatted
         * @param suffix   name suffix to append to the recorded metric name (e.g, "total"); can be null
         * @param unit     metric unit
         * @return the formatted metric name
         */
        default String format(Meter metric, Meter.Id metricId, String suffix, String unit) {
            StringBuilder result = new StringBuilder(metricId.name());
            if (suffix != null) {
                result.append("_").append(suffix);
            }
            result.append("_").append(textType(metric).replace(" ", "_"));

            String units = formattedBaseUnits(unit);
            if (units != null && !units.isBlank()) {
                result.append("_").append(units);
            }
            return result.toString();
        }
    }
}
