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
package io.helidon.metrics.api;

import java.util.Optional;

/**
 * Configuration which controls the behavior of distribution statistics from meters that support them
 * (for example, timers and distribution summaries).
 *
 */
public interface DistributionStatisticsConfig extends Wrapper {

    /**
     * Creates a builder for a new {@link io.helidon.metrics.api.DistributionStatisticsConfig} instance.
     *
     * @return new builder
     */
    static Builder builder() {
        return MetricsFactory.getInstance().distributionStatisticsConfigBuilder();
    }

    /**
     * Returns the settings for non-aggregable percentiles.
     *
     * @return percentiles to compute and publish
     */
    Optional<Iterable<Double>> percentiles();

    /**
     * Returns the minimum expected value that the meter is expected to observe.
     *
     * @return minimum expected value
     */
    Optional<Double> minimumExpectedValue();

    /**
     * Returns the maximum value that the meter is expected to observe.
     *
     * @return maximum value
     */
    Optional<Double> maximumExpectedValue();

    /**
     * Returns the configured boundary boundaries.
     *
     * @return the boundary boundaries
     */
    Optional<Iterable<Double>> buckets();

    /**
     * Builder for a new {@link io.helidon.metrics.api.DistributionStatisticsConfig} instance.
     */
    interface Builder extends Wrapper, io.helidon.common.Builder<Builder, DistributionStatisticsConfig> {

        /**
         * Sets the minimum value that the meter is expected to observe.
         *
         * @param min minimum value that this distribution summary is expected to observe
         * @return updated builder
         */
        Builder minimumExpectedValue(Double min);

        /**
         * Sets the maximum value that the meter is expected to observe.
         *
         * @param max maximum value that the meter is expected to observe
         * @return updated builder
         */
        Builder maximumExpectedValue(Double max);

        /**
         * Specifies time series percentiles.
         * <p>
         *     The system computes these percentiles locally, so they cannot be aggregated with percentiles computed
         *     elsewhere.
         * </p>
         * <p>
         *     Specify percentiles a decimals, for example express the 95th percentile as {@code 0.95}.
         * </p>
         * @param percentiles percentiles to compute and publish
         * @return updated builder
         */
        Builder percentiles(double... percentiles);

        /**
         * Specifies time series percentiles.
         * <p>
         *     The system computes these percentiles locally, so they cannot be aggregated with percentiles computed
         *     elsewhere.
         * </p>
         * <p>
         *     Specify percentiles a decimals, for example express the 95th percentile as {@code 0.95}.
         * </p>
         * @param percentiles percentiles to compute and publish
         * @return updated builder
         */
        Builder percentiles(Iterable<Double> percentiles);

        /**
         * Sets the boundary boundaries.
         *
         * @param buckets boundary boundaries
         * @return updated builder
         */
        Builder buckets(double... buckets);

        /**
         * Sets the boundary boundaries.
         *
         * @param buckets boundary boundaries
         * @return updated builder
         */
        Builder buckets(Iterable<Double> buckets);
    }
}
