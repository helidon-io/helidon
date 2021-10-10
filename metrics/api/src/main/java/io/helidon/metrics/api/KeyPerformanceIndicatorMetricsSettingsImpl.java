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
import io.helidon.config.metadata.ConfiguredOption;

class KeyPerformanceIndicatorMetricsSettingsImpl implements KeyPerformanceIndicatorMetricsSettings {

    private final boolean isExtended;
    private final long longRunningRequestThresholdMs;

    KeyPerformanceIndicatorMetricsSettingsImpl(Builder builder) {
        this.isExtended = builder.isExtended();
        this.longRunningRequestThresholdMs = builder.longRunningRequestThresholdMs();
    }

    @Override
    public boolean isExtended() {
        return isExtended;
    }

    @Override
    public long longRunningRequestThresholdMs() {
        return longRunningRequestThresholdMs;
    }

    static class Builder implements KeyPerformanceIndicatorMetricsSettings.Builder {
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
        public KeyPerformanceIndicatorMetricsSettings.Builder extended(boolean value) {
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
        public KeyPerformanceIndicatorMetricsSettings.Builder longRunningRequestThresholdMs(long value) {
            longRunningRequestThresholdMs = value;
            return this;
        }

        /**
         * Updates the KPI metrics settings in the builder based on the provided {@code Config} object.
         *
         * @param kpiConfig KPI metrics config node
         * @return updated builder instance
         */
        public KeyPerformanceIndicatorMetricsSettings.Builder config(Config kpiConfig) {
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

        public boolean isExtended() {
            return isExtendedKpiEnabled;
        }

        public long longRunningRequestThresholdMs() {
            return longRunningRequestThresholdMs;
        }
    }
}
