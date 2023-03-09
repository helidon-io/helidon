/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
 * Settings which control metrics within registries (application, vendor, base) as a group or individually.
 * <p>
 *     Application code can control the metrics of each registry type as a whole, enabling or disabling all metrics of the type
 *     using {@link Builder#enabled(boolean)} as well as selectively enabling or disabling specific metrics using
 *     {@link Builder#filterSettings(io.helidon.metrics.api.RegistryFilterSettings.Builder)}.
 * </p>
 * <p>
 *     Callers can also pass a {@link Config} object to builder or static factory methods as well.
 * </p>
 */
public interface RegistrySettings {

    /**
     * Creates a new default {@code RegistrySettings} instance.
     *
     * @return new default instance
     */
    static RegistrySettings create() {
        return builder().build();
    }

    /**
     * Creates a new {@code RegistrySettings} instance using the provided config.
     *
     * @param registrySettings the config node for the registry's settings
     * @return updated builder
     */
    static RegistrySettings create(Config registrySettings) {
        return builder().config(registrySettings).build();
    }

    /**
     * Creates a new defaulted builder for {@code RegistrySettings}.
     *
     * @return new builder with default values
     */
    static Builder builder() {
        return RegistrySettingsImpl.builder();
    }

    /**
     * Returns whether metrics of this type are enabled.
     *
     * @return true if metrics of this type are enabled as a whole; false otherwise
     */
    boolean isEnabled();

    /**
     * Returns whether strict exemplar behavior is enabled.
     *
     * @return true/false
     */
    boolean isStrictExemplars();

    /**
     * Returns whether the specified metric name is enabled or not, factoring in the enabled setting for this type as a whole
     * with the regex pattern.
     *
     * @param dottedName name of the metric to check
     * @return true if the specified metric should be full-featured; false otherwise
     */
    boolean isMetricEnabled(String dottedName);

    /**
     * Builder for {@code RegistrySettings}.
     */
    @Configured(prefix = MetricsSettings.Builder.METRICS_CONFIG_KEY + "." + "<metric-type>")
    interface Builder extends io.helidon.common.Builder<Builder, RegistrySettings> {

        /**
         * Config key within the registry's config section controlling whether the current type of metrics should be enabled.
         */
        String ENABLED_CONFIG_KEY = "enabled";

        /**
         * Config key within the registry's config section specifying a filter.
         */
        String FILTER_CONFIG_KEY = "filter";

        /**
         * Config key within the registry's config section identifying which registry type the settings apply to.
         */
        String TYPE_CONFIG_KEY = "type";

        /**
         * Sets whether the metric type should be enabled.
         *
         * @param value true if metric type should be enabled; false otherwise
         * @return updated builder
         */
        @ConfiguredOption(
                key = ENABLED_CONFIG_KEY,
                value = "true")
        Builder enabled(boolean value);

        /**
         * Sets the filter to use for identifying specific metrics to enable.
         *
         * @param registryFilterSettingsBuilder {@code String} specifying enabled and disabled metric name patterns
         * @return updated builder
         */
        @ConfiguredOption(
                key = FILTER_CONFIG_KEY,
                type = RegistryFilterSettings.class,
                description = "Name filtering, featuring optional exclude and include settings")
        Builder filterSettings(RegistryFilterSettings.Builder registryFilterSettingsBuilder);

        /**
         * Whether to add exemplars (if exemplar providers are present) only to counter totals and buckets.
         * <p>
         *     By default, Helidon adds exemplars only to those metric types described as accepting exemplars in the
         *     <a href="https://github.com/OpenObservability/OpenMetrics/blob/main/specification/OpenMetrics.md">OpenMetrics
         *     spec</a>. Helidon can add exemplars to additional metric types but only if the user sets {@code strcitExamplars}
         *     to @{code false}.
         * </p>
         *
         * @param value true/false
         * @return updated builder
         *
         */
        @ConfiguredOption(key = MetricsSettings.Builder.EXEMPLARS_STRICT_CONFIG_KEY, value = "true")
        Builder strictExemplars(boolean value);

        /**
         * Sets values in the builder based on the provided {@code Config} node.
         *
         * @param registrySettings {@code Config} node containing settings for the registry type
         * @return updated builder
         */
        Builder config(Config registrySettings);

        /**
         *
         * @return builder's current setting for whether metrics in the relevant registry are to be used
         */
        boolean isEnabled();

        /**
         *
         * @return whether strict exemplar behavior is enabled
         */
        boolean isStrictExemplars();

        /**
         * Creates a new {@code RegistrySettings} instance from the builder.
         *
         * @return new instance from the builder
         */
        RegistrySettings build();
    }
}
