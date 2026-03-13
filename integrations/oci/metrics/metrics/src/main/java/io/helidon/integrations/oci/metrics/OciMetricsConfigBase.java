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
 * Common configuration options for transmitting metrics data to an OCI metrics backend.
 */
public interface OciMetricsConfigBase extends MetricsPublisherConfig {

    /**
     * Default batch size.
     */
    int DEFAULT_BATCH_SIZE = 50;

    /**
     * Name of the config section.
     *
     * @return name
     */
    @Option.Configured
    Optional<String> name();

    /**
     * Namespace associated with each transmission.
     *
     * @return namespace
     */
    @Option.Configured
    Optional<String> namespace();

    /**
     * Resource group associated with each transmission.
     *
     * @return resource group
     */
    @Option.Configured
    Optional<String> resourceGroup();

    /**
     * Initial delay before first check for data to transmit.
     *
     * @return initial delay
     */
    @Option.Configured
    @Option.Default("PT1S")
    Duration initialDelay();

    /**
     * Delay between successive checks for data to transmit.
     *
     * @return delay
     */
    @Option.Configured
    @Option.Default("PT60S")
    Duration delay();

    /**
     * Delay between sending successive batches during a single check of data to transmit.
     *
     * @return batch delay
     */
    @Option.Configured
    @Option.Default("PT1S")
    Duration batchDelay();

    /**
     * Compartment ID.
     *
     * @return compartment ID
     */
    @Option.Configured
    Optional<String> compartmentId();

    /**
     * Whether meter descriptions should be sent along with data to the backend system.
     *
     * @return description enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean descriptionEnabled();

    /**
     * Scopes for filtering which meters are transmitted. Default: the built-in scopes.
     *
     * @return scopes to select which meters to transmit
     */
    @Option.Configured
    @Option.DefaultCode("new java.util.HashSet<>(io.helidon.metrics.api.Meter.Scope.BUILT_IN_SCOPES)")
    Set<String> scopes();

    /**
     * Whether meter data transmission is enabled.
     *
     * @return true if enabled, false otherwise
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Size of each batch of meter data transmitted to the backend system.
     *
     * @return batch size
     */
    @Option.Configured
    @Option.DefaultInt(DEFAULT_BATCH_SIZE)
    int batchSize();

    /**
     * Name formatter to transform meter names before transmission. See the {@linkplain NameFormatter#DEFAULT default formatter}.
     *
     * @return name formatter
     */
    @Option.DefaultCode("NameFormatter.DEFAULT")
    NameFormatter nameFormatter();

    /**
     * {@linkplain com.oracle.bmc.monitoring.Monitoring monitoring client} to use in transmitting meter data.
     * Default: the automatically-configured {@code Monitoring} instance in the service registry.
     *
     * @return monitoring client
     */
    Monitoring monitoringClient();

    /**
     * Formatter for adjusting meter names before transmission.
     */
    interface NameFormatter {

        /**
         * Default name formatter which emits names of the form {@code {meter-name}_{suffix}_{meter-type}_{unit}} with
         * spaces replaced with underscores, where meter types are {@code counter}, {@code gauge}, {@code histogram},
         * or {@code timer}.
         */
        NameFormatter DEFAULT = new NameFormatter() {
        };

        /**
         * Given a {@link Meter} returns the converted meter type.
         *
         * @param metric meter whose type is requested
         *
         * @return meter type
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
         * Prepares the formatted meter name for the specified {@link Meter} with the given {@link Meter.Id}, adjusting for
         * the provided suffix and unit (if specified).
         *
         * @param metric the meter for which to derive a name for transmission
         * @param metricId meter ID for the meter
         * @param suffix suffix to add to the meter name, if any
         * @param unit unit to add to the formatted meter name, if any
         * @return formatted meter name for transmission
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
