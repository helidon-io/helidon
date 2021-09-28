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

/**
 * Settings which control metrics behavior for metrics overall.
 */
public interface MetricsSettings {

    /**
     * Config key within the config {@code metrics} section controlling whether metrics are enabled.
     */
    String ENABLED_CONFIG_KEY = "enabled";

    /**
     * The config key containing settings for all of metrics.
     */
    String METRICS_CONFIG_KEY = "metrics";

    /**
     * Config key within the config {@code metrics} section controlling the base registry.
     */
    String BASE_CONFIG_KEY = "base";

    /**
     * Returns metrics settings based on a {@code Config} node, by convention the {@code metrics} config
     * section within the overall {@code metrics} config.
     * <p>Equivalent to {@code MetricsSettings.builder().config(config).build()}.</p>
     *
     * @param config the metrics config section
     * @return new settings reflecting the config, using defaults as needed
     */
    static MetricsSettings create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Returns a builder for {@code MetricsSettings}.
     *
     * @return new builder
     */
    static Builder builder() {
        return new MetricsSettingsImpl.Builder();
    }

    /**
     *
     * @return whether metrics are enabled according to the settings
     */
     boolean isEnabled();

    /**
     *
     * @return whether the base metrics registry is enabled according to the settings
     */
    boolean isBaseEnabled();

    /**
     *
     * @param dottedName dotted name (e.g., {@code memory.usedHeap}) for the base metric of interest
     * @return whether that metric is enabled or not
     */
    boolean isBaseMetricEnabled(String dottedName);

    /**
     *
     * @return the KPI metrics settings
     */
    KeyPerformanceIndicatorMetricsSettings keyPerformanceIndicatorSettings();

    /**
     * Builder for {@code MetricsSettings}.
     */
    interface Builder extends io.helidon.common.Builder<MetricsSettings> {

        /**
         * Constructs a {@code MetricsSettings} object from the builder.
         *
         * @return new settings instance based on the builder
         */
        MetricsSettings build();

        /**
         * Sets whether metrics should be enabled.
         *
         * @param value true if metrics should be enabled; false if not
         * @return updated builder
         */
        Builder enable(boolean value);

        /**
         * Updates the builder using the provided metrics config.
         *
         * @param config the component's or the overall {@code metrics} config from the configuration
         * @return updated builder
         */
        Builder config(Config config);

        /**
         * Sets whether base metrics should be enabled.
         *
         * @param value true if base metrics should be used; false otherwise
         * @return updated builder
         */
        Builder enableBase(boolean value);

        /**
         * Sets whether a specific base metric should be enabled.
         *
         * @param dottedName the dotted name (e.g., {@code memory.usedHeap} for the base metric
         * @param value whether that base metric should be enabled or not
         * @return updated builder
         */
        Builder enableBaseMetric(String dottedName, boolean value);

        /**
         * Set the KPI metrics settings.
         *
         * @param kpiSettings key performance indicator metrics settings to use
         * @return updated builder
         */
        Builder keyPerformanceIndicatorSettings(KeyPerformanceIndicatorMetricsSettings kpiSettings);
    }
}
