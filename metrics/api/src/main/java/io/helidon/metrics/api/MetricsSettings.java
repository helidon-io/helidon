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
 * Settings which control behavior for metrics overall.
 * <p>
 *     This class controls all of metrics, not just a single component's usage of metrics. For that, see
 *     {@link io.helidon.metrics.api.ComponentMetricsSettings}.
 * </p>
 */
public interface MetricsSettings {

    /**
     * Returns default metrics settings based on default config.
     *
     * @return new settings reflecting the default config
     */
    static MetricsSettings create() {
        return create(Config.create());
    }

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
     * Creates a builder based on the values in an existing {@code MetricsSettings} instance.
     *
     * @param metricsSettings existing instance to copy
     * @return {@code MetricsSettings.Builder} initialized according to the provided settings
     */
    static Builder builder(MetricsSettings metricsSettings) {
        return new MetricsSettingsImpl.Builder(metricsSettings);
    }

    /**
     *
     * @return whether metrics are enabled according to the settings
     */
    boolean isEnabled();

    /**
     *
     * @return the KPI metrics settings
     */
    KeyPerformanceIndicatorMetricsSettings keyPerformanceIndicatorSettings();

    /**
     *
     * @return the base metrics settings
     */
    BaseMetricsSettings baseMetricsSettings();

    /**
     * Builder for {@code MetricsSettings}.
     */
    @Configured(prefix = Builder.METRICS_CONFIG_KEY)
    interface Builder extends io.helidon.common.Builder<MetricsSettings> {

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
         * Default web context for the metrics endpoint.
         */
        String DEFAULT_CONTEXT = "/metrics";

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
        @ConfiguredOption(key = ENABLED_CONFIG_KEY)
        Builder enabled(boolean value);

        /**
         * Updates the builder using the provided metrics config.
         *
         * @param config the component's or the overall {@code metrics} config from the configuration
         * @return updated builder
         */
        Builder config(Config config);

        /**
         * Set the KPI metrics settings.
         *
         * @param kpiSettings key performance indicator metrics settings to use
         * @return updated builder
         */
        @ConfiguredOption(key = KeyPerformanceIndicatorMetricsSettings.Builder.KEY_PERFORMANCE_INDICATORS_CONFIG_KEY,
                          kind = ConfiguredOption.Kind.MAP)
        Builder keyPerformanceIndicatorSettings(KeyPerformanceIndicatorMetricsSettings.Builder kpiSettings);

        /**
         * Set the base metrics settings.
         *
         * @param baseMetricsSettingsBuilder base metrics settings to use
         * @return updated builder
         */
        @ConfiguredOption(key = BASE_CONFIG_KEY,
                          kind = ConfiguredOption.Kind.MAP)
        Builder baseMetricsSettings(BaseMetricsSettings.Builder baseMetricsSettingsBuilder);
    }
}
