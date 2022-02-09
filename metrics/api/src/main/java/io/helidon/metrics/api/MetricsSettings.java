/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

import org.eclipse.microprofile.metrics.MetricRegistry;

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
     * Reports whether the specified metric is enabled in the indicated registry type.
     *
     * @param registryType which registry type to check
     * @param metricName name of the metric to check
     * @return true if metrics overall is enabled and if the metric is enabled in the specified registry; false otherwise
     */
    boolean isMetricEnabled(MetricRegistry.Type registryType, String metricName);

    /**
     * Returns the {@link RegistrySettings} for the indicated registry type.
     *
     * @param registryType registry type of interest
     * @return {@code RegistrySettings} for the selected type
     */
    RegistrySettings registrySettings(MetricRegistry.Type registryType);

    /**
     * Returns the global tags, if any.
     *
     * @return global tag names and values
     */
    Map<String, String> globalTags();

    /**
     * Returns the app tag value, if any.
     *
     * @return app tag value; null if none set
     */
    String appTagValue();

    /**
     * Builder for {@code MetricsSettings}.
     */
    @Configured(prefix = Builder.METRICS_CONFIG_KEY)
    interface Builder extends io.helidon.common.Builder<Builder, MetricsSettings> {

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
         * Config key within the config {@code metrics} section containing settings for individual registries.
         */
        String REGISTRIES_CONFIG_KEY = "registries";

        /**
         * Default web context for the metrics endpoint.
         */
        String DEFAULT_CONTEXT = "/metrics";

        /**
         * Config key for comma-separated, {@code tag=value} global tag settings.
         */
        String GLOBAL_TAGS_CONFIG_KEY = "tags";

        /**
         * Config key for the app tag value to be applied to all metrics in this application.
         */
        String APP_TAG_CONFIG_KEY = "appName";

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

        /**
         * Sets the registry settings for the specified registry type.
         *
         * @param registryType type of registry for which to assign settings
         * @param registrySettings assigned registry settings
         * @return updated builder
         */
        @ConfiguredOption(key = REGISTRIES_CONFIG_KEY,
                          kind = ConfiguredOption.Kind.LIST,
                          type = RegistrySettings.class)
        Builder registrySettings(MetricRegistry.Type registryType, RegistrySettings registrySettings);

        /**
         * Sets the global tags to be applied to all metrics.
         *
         * @param globalTags map of tag name/tag value pairs
         * @return updatedbuilder
         */
        @ConfiguredOption(key = GLOBAL_TAGS_CONFIG_KEY,
                          kind = ConfiguredOption.Kind.MAP)
        Builder globalTags(Map<String, String> globalTags);

        /**
         * Sets the value for the {@code _app} tag to be applied to all metrics.
         *
         * @param appTag app tag value
         * @return updated builder
         */
        @ConfiguredOption(key = APP_TAG_CONFIG_KEY)
        Builder appTagValue(String appTag);
    }
}
