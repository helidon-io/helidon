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

import java.time.Duration;
import java.util.Optional;

/**
 * Configuration which controls the behavior of distribution statistics from meters that support them
 * (for example, timers and distribution summaries).
 *
 */
public interface DistributionStatisticsConfig extends Wrapped {

    /**
     * Creates a builder for a new {@link io.helidon.metrics.api.DistributionStatisticsConfig} instance.
     *
     * @return new builder
     */
    static Builder builder() {
        return MetricsFactory.getInstance().distributionStatisticsConfigBuilder();
    }

    /**
     * Creates a new configuration by merging another one (called the "parent") with the current instance,
     * using values from the current instance if they have been set and from the parent otherwise.
     *
     * @param parent the other configuration
     * @return new config resulting from the merge
     */
    DistributionStatisticsConfig merge(DistributionStatisticsConfig parent);

    /**
     * Returns whether the configuration is set for percentile histograms which can be aggregated for percentile approximations.
     *
     * @return whether percentile histograms are configured
     */
    Optional<Boolean> isPercentileHistogram();

    /**
     * Returns whether the configuration is set to publish percentiles.
     *
     * @return true/false
     */
    Optional<Boolean> isPublishingPercentiles();

    /**
     * Returns whether the configuration is set to publish a histogram.
     *
     * @return true/false
     */
    Optional<Boolean> isPublishingHistogram();

    /**
     * Returns the settings for non-aggregable percentiles.
     *
     * @return percentiles to compute and publish
     */
    Optional<Iterable<Double>> percentiles();

    /**
     * Returns the configured number of digits of precision for percentiles.
     *
     * @return digits of precision to maintain for percentile approximations
     */
    Optional<Integer> percentilePrecision();

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
     * Returns how long decaying past observations remain in the ring buffer.
     *
     * @see #bufferLength()
     * @return time during which samples accumulate in a histogram
     */
    Optional<Duration> expiry();

    /**
     * Returns the size of the ring buffer for holding decaying observations.
     *
     * @return number of observations to keep in the ring buffer
     */
    Optional<Integer> bufferLength();

    /**
     * Returns the configured service level objective boundaries.
     *
     * @return the SLO boundaries
     */
    Optional<Iterable<Double>> serviceLevelObjectiveBoundaries();

    /**
     * Builder for a new {@link io.helidon.metrics.api.DistributionStatisticsConfig} instance.
     */
    interface Builder extends Wrapped, io.helidon.common.Builder<Builder, DistributionStatisticsConfig> {

        /**
         * Sets how long to keep samples before they are assumed to have decayed to zero and are discareded.
         *
         * @param expiry how long to retain samples
         * @return updated builder
         */
        Builder expiry(Duration expiry);

        /**
         * Sets the size of the ring buffer which holds saved samples as they decay.
         *
         * @param bufferLength number of histograms to keep in the ring buffer
         * @return updated builder
         */
        Builder bufferLength(Integer bufferLength);

        /**
         * Sets whether to publish percentiles histograms (which are aggregable).
         *
         * @param enabled true to publish percentile histograms; false otherwise
         * @return updated builder
         */
        Builder percentilesHistogram(Boolean enabled);

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
         * Specifies additional time series percentiles.
         * <p>
         *     The system computes these percentiles locally, so they cannot be aggregated with percentiles computed
         *     elsewhere. In contrast, a percentile histogram triggered by invoking {@link #percentilesHistogram} can
         *     be aggregated.
         * </p>
         * <p>
         *     Specify percentiles a decimals, for example express the 95th percentile as {@code 0.95}.
         * </p>
         * @param percentiles percentiles to compute and publish
         * @return updated builder
         */
        Builder percentiles(double... percentiles);

        /**
         * Specifies additional time series percentiles.
         * <p>
         *     The system computes these percentiles locally, so they cannot be aggregated with percentiles computed
         *     elsewhere. In contrast, a percentile histogram triggered by invoking {@link #percentilesHistogram} can
         *     be aggregated.
         * </p>
         * <p>
         *     Specify percentiles a decimals, for example express the 95th percentile as {@code 0.95}.
         * </p>
         * @param percentiles percentiles to compute and publish
         * @return updated builder
         */
        Builder percentiles(Iterable<Double> percentiles);

        /**
         * Sets the number of digits of precision to maintain on the dynamic range
         * histogram used to compute percentile approximations.
         *
         * @param digitsOfPrecision digits of precision to maintain for percentile approximations
         * @return updated builder
         */
        Builder percentilePrecision(Integer digitsOfPrecision);

        /**
         * Sets the bucket boundaries.
         *
         * @param buckets bucket boundaries
         * @return updated builder
         */
        Builder buckets(double... buckets);

        /**
         * Sets the bucket boundaries.
         *
         * @param buckets bucket boundaries
         * @return updated builder
         */
        Builder buckets(Iterable<Double> buckets);
    }
}
