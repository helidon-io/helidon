/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
 * Records a distribution of values (e.g., sizes of responses returned by a server).
 */
public interface DistributionSummary extends Meter {

    /**
     * Creates a builder for a new {@link io.helidon.metrics.api.DistributionSummary} using the specified statistics builder.
     *
     * @param name          name for the summary
     * @param configBuilder distribution stats config for the summary
     * @return new builder
     */
    static Builder builder(String name,
                           DistributionStatisticsConfig.Builder configBuilder) {
        return MetricsFactory.getInstance()
                .distributionSummaryBuilder(name, configBuilder);
    }

    /**
     * Creates a builder for a new {@link io.helidon.metrics.api.DistributionSummary} using default distribution statistics.
     *
     * @param name name for the summary
     * @return new builder
     */
    static Builder builder(String name) {
        return MetricsFactory.getInstance()
                .distributionSummaryBuilder(name,
                                            DistributionStatisticsConfig.builder());
    }

    /**
     * Updates the statistics kept by the summary with the specified amount.
     *
     * @param amount Amount for an event being measured. For example, if the size in bytes of responses
     *               from a server. If the amount is less than 0 the value will be dropped.
     */
    void record(double amount);

    /**
     * Returns the current count of observations in the distribution summary.
     *
     * @return number of observations recorded in the summary
     */
    long count();

    /**
     * Returns the total of the observations recorded by the distribution summary.
     *
     * @return total across all recorded events
     */
    double totalAmount();

    /**
     * Returns the mean of the observations recorded by the distribution summary.
     *
     * @return average value of events recorded in the summary
     */
    double mean();

    /**
     * Returns the maximum value among the observations recorded by the distribution summary.
     *
     * @return maximum value of recorded events
     */
    double max();

    /**
     * Returns a {@link io.helidon.metrics.api.HistogramSnapshot} of the current state of the distribution summary.
     *
     * @return snapshot
     */
    HistogramSnapshot snapshot();

    /**
     * Builder for a {@link io.helidon.metrics.api.DistributionSummary}.
     *
     * @see MeterRegistry#getOrCreate(Meter.Builder)
     */
    interface Builder extends Meter.Builder<Builder, DistributionSummary> {

        /**
         * Sets the scale factor for observations recorded by the summary.
         *
         * @param scale scaling factor to apply to each observation
         * @return updated builder
         */
        Builder scale(double scale);

        /**
         * Sets the config for distribution statistics for the distribution summary.
         *
         * @param distributionStatisticsConfigBuilder builder for the distribution statistics config
         * @return updated builder
         */
        Builder distributionStatisticsConfig(DistributionStatisticsConfig.Builder distributionStatisticsConfigBuilder);

        /**
         * Sets whether to publish a percentile histogram.
         *
         * @param value true/false
         * @return updated builder
         */
        Builder publishPercentileHistogram(boolean value);

        /**
         * Returns the scale set on the builder.
         *
         * @return the scale
         */
        Optional<Double> scale();

        /**
         * Returns the statistics config set on the builder, if any.
         *
         * @return distribution statistics config, if set; empty otherwise
         */
        Optional<DistributionStatisticsConfig.Builder> distributionStatisticsConfig();
    }
}
