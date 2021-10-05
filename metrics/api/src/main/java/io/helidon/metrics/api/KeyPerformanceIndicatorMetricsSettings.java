/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Settings for KPI metrics.
 */
public interface KeyPerformanceIndicatorMetricsSettings {

    /**
     * Creates a new {@code KeyPerformanceIndicatorMetricsSettings} instance from the specified config node containing KPI
     * metrics settings.
     *
     * @param kpiConfig config node containing KPI metrics settings
     * @return new KPI metrics settings reflecting the config
     */
    static KeyPerformanceIndicatorMetricsSettings create(Config kpiConfig) {
        return builder().config(kpiConfig).build();
    }

    /**
     * Creates a new builder for the settings.
     *
     * @return new {@link Builder}
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder using the values from an existing {@code KeyPerformanceIndicatorMetricsSettings} instance.
     *
     * @param kpiMetricsSettings existing KPI metrics settings to copy
     * @return new {@code Builder} initialized according to the provide settings
     */
    static Builder builder(KeyPerformanceIndicatorMetricsSettings kpiMetricsSettings) {
        return builder()
                .extended(kpiMetricsSettings.isExtended())
                .longRunningRequestThresholdMs(kpiMetricsSettings.longRunningRequestThresholdMs());
    }

    /**
     *
     * @return whether extended KPI metrics are enabled in the settings
     */
    boolean isExtended();

    /**
     *
     * @return the threshold (in ms) for long-running requests
     */
    long longRunningRequestThresholdMs();

    /**
     * Override default settings.
     * <p>
     * Configuration options:
     *
     * <table class="config">
     * <caption>Key performance indicator metrics configuration ({@value CONFIG_KEY_PREFIX}</caption>
     * <tr>
     *     <th>Key</th>
     *     <th>Default</th>
     *     <th>Description</th>
     *     <th>Builder method</th>
     * </tr>
     * <tr>
     *     <td>
     *         {@value KEY_PERFORMANCE_INDICATORS_EXTENDED_CONFIG_KEY}
     *     </td>
     *
     *     <td>{@value KEY_PERFORMANCE_INDICATORS_EXTENDED_DEFAULT}</td>
     *     <td>Whether the extended key performance indicator metrics should be enabled</td>
     *     <td>{@link #extended(boolean)} </td>
     * </tr>
     * <tr>
     *     <td>{@value LONG_RUNNING_REQUESTS_THRESHOLD_CONFIG_KEY}
     *     </td>
     *     <td>{@value LONG_RUNNING_REQUESTS_THRESHOLD_MS_DEFAULT}</td>
     *     <td>Threshold (in milliseconds) for long-running requests</td>
     *     <td>{@link #longRunningRequestThresholdMs(long)}</td>
     * </tr>
     * </table>
     */
    @Configured(prefix = MetricsSettings.Builder.METRICS_CONFIG_KEY + "." + Builder.KEY_PERFORMANCE_INDICATORS_CONFIG_KEY)
    class Builder implements io.helidon.common.Builder<KeyPerformanceIndicatorMetricsSettings> {
        private boolean isExtendedKpiEnabled = KEY_PERFORMANCE_INDICATORS_EXTENDED_DEFAULT;
        private long longRunningRequestThresholdMs = LONG_RUNNING_REQUESTS_THRESHOLD_MS_DEFAULT;

        /**
         * Config key for extended key performance indicator metrics settings.
         */
        public static final String KEY_PERFORMANCE_INDICATORS_CONFIG_KEY = "key-performance-indicators";

        /**
         * Config key for {@code enabled} setting of the extended KPI metrics.
         */
        public static final String KEY_PERFORMANCE_INDICATORS_EXTENDED_CONFIG_KEY = "extended";

        /**
         * Default enabled setting for extended KPI metrics.
         */
        static final boolean KEY_PERFORMANCE_INDICATORS_EXTENDED_DEFAULT = false;

        /**
         * Config key for long-running requests threshold setting (in milliseconds).
         */
        public static final String LONG_RUNNING_REQUESTS_THRESHOLD_CONFIG_KEY = "threshold-ms";

        /**
         * Config key for long-running requests settings.
         */
        public static final String LONG_RUNNING_REQUESTS_CONFIG_KEY = "long-running-requests";

        /**
         * Default long-running requests threshold.
         */
        static final long LONG_RUNNING_REQUESTS_THRESHOLD_MS_DEFAULT = 10 * 1000; // 10 seconds

        // The following constants are used in JavaDoc.
        private static final String CONFIG_KEY_PREFIX = "metrics." + KEY_PERFORMANCE_INDICATORS_CONFIG_KEY;
        private static final String QUALIFIED_LONG_RUNNING_REQUESTS_THRESHOLD_CONFIG_KEY =
                LONG_RUNNING_REQUESTS_CONFIG_KEY + "." + KEY_PERFORMANCE_INDICATORS_EXTENDED_CONFIG_KEY;

        /**
         * Sets whether exntended KPI metrics should be enabled in the settings.
         *
         * @param value whether extended KPI metrics should be enabled
         * @return updated builder instance
         */
        @ConfiguredOption(
                key = KEY_PERFORMANCE_INDICATORS_EXTENDED_CONFIG_KEY)
        public Builder extended(boolean value) {
            this.isExtendedKpiEnabled = value;
            return this;
        }

        /**
         * Sets the long-running request threshold (in ms).
         *
         * @param value long-running request threshold
         * @return updated builder instance
         */
        @ConfiguredOption(
                key = LONG_RUNNING_REQUESTS_CONFIG_KEY + "." + LONG_RUNNING_REQUESTS_THRESHOLD_CONFIG_KEY,
                value = "" + LONG_RUNNING_REQUESTS_THRESHOLD_MS_DEFAULT)
        public Builder longRunningRequestThresholdMs(long value) {
            longRunningRequestThresholdMs = value;
            return this;
        }

        /**
         * Updates the KPI metrics settings in the builder based on the provided {@code Config} object.
         *
         * @param kpiConfig KPI metrics config node
         * @return updated builder instance
         */
        public Builder config(Config kpiConfig) {
            kpiConfig.get(KEY_PERFORMANCE_INDICATORS_EXTENDED_CONFIG_KEY)
                    .asBoolean()
                    .ifPresent(this::extended);
            kpiConfig.get(LONG_RUNNING_REQUESTS_CONFIG_KEY)
                    .get(LONG_RUNNING_REQUESTS_THRESHOLD_CONFIG_KEY)
                    .asLong()
                    .ifPresent(this::longRunningRequestThresholdMs);
            return this;
        }

        /**
         * Builds a {@link KeyPerformanceIndicatorMetricsSettings} using the settings from the builder.
         *
         * @return {@code KeyPerformanceIndicatorMetricsSettings} prepared according to the builder
         */
        public KeyPerformanceIndicatorMetricsSettings build() {
            return new KeyPerformanceIndicatorMetricsSettingsImpl(this);
        }

        boolean isExtended() {
            return isExtendedKpiEnabled;
        }

        long longRunningRequestThresholdMs() {
            return longRunningRequestThresholdMs;
        }
    }
}
