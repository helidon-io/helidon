/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

import io.helidon.common.DeprecationSupport;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Settings which control metrics behavior for a metrics-capable component.
 * <p>
 *     <em>Do not</em> use this for controlling overall metrics. Use {@link io.helidon.metrics.api.MetricsConfig} instead.
 * </p>
 */
public interface ComponentMetricsSettings {

    /**
     * Returns component metrics settings created from configuration.
     *
     * @param config configuration
     * @return new settings reflecting the config, using defaults as needed
     * @deprecated use {@link #create(io.helidon.config.Config)} instead
     */
    @SuppressWarnings("removal")
    @Deprecated(since = "4.4.0", forRemoval = true)
    static ComponentMetricsSettings create(io.helidon.common.config.Config config) {
        return builder(config).build();
    }

    /**
     * Returns component metrics settings created from a {@code Config} node, by convention the {@code metrics} config
     * section within the component's own config section.
     * <p>Equivalent to {@code ComponentMetricsSettings.builder().config(config).build()}.</p>
     *
     * @param config the metrics config section within the component's configuration
     * @return new settings reflecting the config, using defaults as needed
     */
    static ComponentMetricsSettings create(Config config) {
        return builder(config).build();
    }

    /**
     * Returns a builder for {@code ComponentMetricsSettings}.
     *
     * @return new builder
     */
    static ComponentMetricsSettings.Builder builder() {
        return new ComponentMetricsSettingsImpl.Builder();
    }

    /**
     * Returns a configured builder.
     *
     * @param config configuration
     * @return new builder initialized with the config settings
     * @deprecated use {@link #builder(io.helidon.config.Config)} instead
     */
    @SuppressWarnings("removal")
    @Deprecated(since = "4.4.0", forRemoval = true)
    static ComponentMetricsSettings.Builder builder(io.helidon.common.config.Config config) {
        return builder().config(config);
    }

    /**
     * Returns a builder for {@code ComponentMetricsSettings} based on the provided component metric settings config node.
     *
     * @param componentMetricsConfig the config node containing the component metrics config section
     * @return new builder initialized with the config settings
     */
    static ComponentMetricsSettings.Builder builder(Config componentMetricsConfig) {
        return builder().config(componentMetricsConfig);
    }

    /**
     * Returns whether the component settings indicate that metrics are enabled for the component.
     *
     * @return whether metrics are enabled for the component according to the settings
     */
    boolean isEnabled();

    /**
     * Builder for {@code ComponentMetricsSettings}.
     */
    @Configured(prefix = Builder.METRICS_CONFIG_KEY)
    interface Builder extends io.helidon.common.Builder<Builder, ComponentMetricsSettings> {

        /**
         * By convention, the config key within the component's config section containing metrics settings for the component.
         */
        String METRICS_CONFIG_KEY = "metrics";
        /**
         * Config key within the component's {@code metrics} config section controlling whether metrics are enabled for that
         * component.
         */
        String ENABLED_CONFIG_KEY = "enabled";

        /**
         * Constructs a {@code ComponentMetricsSettings} object from the builder.
         *
         * @return new settings instance based on the builder
         */
        ComponentMetricsSettings build();

        /**
         * Sets whether metrics should be enabled for the component.
         *
         * @param value true if metrics should be enabled for the component; false if not
         * @return updated builder
         */
        @ConfiguredOption(key = ENABLED_CONFIG_KEY)
        ComponentMetricsSettings.Builder enabled(boolean value);

        /**
         * Updates the builder using the provided metrics config.
         *
         * @param config the component's {@code metrics} config section
         * @return updated builder
         * @deprecated use {@link #config(io.helidon.config.Config)} instead
         */
        @SuppressWarnings("removal")
        @Deprecated(since = "4.4.0", forRemoval = true)
        default ComponentMetricsSettings.Builder config(io.helidon.common.config.Config config) {
            return config(Config.config(config));
        }

        /**
         * Updates the builder using the provided metrics config.
         *
         * @param config the component's {@code metrics} config section
         * @return updated builder
         */
        @SuppressWarnings("removal")
        default ComponentMetricsSettings.Builder config(Config config) {
            // default to preserve backward compatibility
            // require the deprecated variant to be implemented
            DeprecationSupport.requireOverride(this, Builder.class, "config", io.helidon.common.config.Config.class);
            return config((io.helidon.common.config.Config) config);
        }
    }
}
