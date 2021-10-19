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

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.config.metadata.ConfiguredValue;

/**
 * Settings which control base metrics.
 * <p>
 *     Application code can control the operation of base metrics collection, enabling it or disabling it as a whole using
 *     {@link Builder#enabled(boolean)} as well as selectively enabling or disabling specific base metrics using
 *     {@link Builder#enableBaseMetric(String, boolean)}.
 * </p>
 * <p>
 *     Callers can also pass a {@link Config} object to builder or static
 *     factory methods as well.
 * </p>
 */
public interface BaseMetricsSettings {

    /**
     * Creates a new instance of {@code BaseMetricsSettings} with defaults.
     *
     * @return new default instance
     */
    static BaseMetricsSettings create() {
        return builder().build();
    }

    /**
     * Creates a new instance of {@code BaseMetricsSettings} based on the specified {@code Config} node containing base metrics
     * settings.
     *
     * @param config {@code Config} node containing base metrics settings
     * @return new {@code BaseMetricsSettings} according to the configuration
     */
    static BaseMetricsSettings create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Creates a new instance of the builder for {@code BaseMetricsSettings}.
     *
     * @return new builder
     */
    static Builder builder() {
        return BaseMetricsSettingsImpl.Builder.create();
    }

    /**
     * Creates a new instance of the builder based on the current settings in a {@code BaseMetricsSettings} object.
     *
     * @param baseMetricsSettings existing base metrics settings
     * @return new {@code Builder} initialized with the provided settings
     */
    static Builder builder(BaseMetricsSettings baseMetricsSettings) {
        return BaseMetricsSettingsImpl.Builder.create(baseMetricsSettings);
    }

    /**
     *
     * @return whether base metrics are enabled in the settings.
     */
    boolean isEnabled();

    /**
     *
     * @param dottedName dotted name (e.g., {@code memory.usedHeap}) for the base metric of interest
     * @return whether that metric is enabled or not
     */
    boolean isBaseMetricEnabled(String dottedName);

    /**
     *
     * @return {@code Map} from base metric names to explicit enabled/disabled settings
     */
    Map<String, Boolean> baseMetricEnabledSettings();

    /**
     * Builder for {@code BaseMetricsSettings}.
     */
    @Configured(prefix = MetricsSettings.Builder.METRICS_CONFIG_KEY + "." + Builder.BASE_METRICS_CONFIG_KEY)
    interface Builder extends io.helidon.common.Builder<BaseMetricsSettings> {

        /**
         * Config key within the config {@code metrics} section controlling base metrics behavior.
         */
        String BASE_METRICS_CONFIG_KEY = "base";

        /**
         * Config key within the config {code metrics.base} section controlling whether base metrics should be enabled.
         */
        String ENABLED_CONFIG_KEY = "enabled";

        /**
         * Sets whether base metrics should be enabled.
         *
         * @param value true if base metrics should be enabled; false otherwise
         * @return updated builder
         */
        @ConfiguredOption(
                key = ENABLED_CONFIG_KEY,
                allowedValues = {
                        @ConfiguredValue(value = "true", description = "base metrics are enabled"),
                        @ConfiguredValue(value = "false", description = "base metrics are disabled")
                },
                value = "true")
        Builder enabled(boolean value);

        /**
         * Sets values in the builder based on the provided {@code Config} node.
         *
         * @param baseMetricsConfig {@code Config} node contain base metrics settings
         * @return updated builder
         */
        Builder config(Config baseMetricsConfig);

        /**
         * Sets whether a specific base metric should be enabled.
         *
         * @param dottedName the dotted name (e.g., {@code memory.usedHeap} for the base metric
         * @param value whether that base metric should be enabled or not
         * @return updated builder
         */
        @ConfiguredOption(
                key = "x.y." + ENABLED_CONFIG_KEY,
                type = Boolean.class,
                value = "true",
                allowedValues = {
                        @ConfiguredValue(value = "true", description = "the specified base metric is enabled"),
                        @ConfiguredValue(value = "false", description = "the specified base metric is disabled")
                })
        Builder enableBaseMetric(String dottedName, boolean value);

        /**
         *
         * @return builder's current setting for whether base metrics are to be used
         */
        boolean isEnabled();
    }
}
