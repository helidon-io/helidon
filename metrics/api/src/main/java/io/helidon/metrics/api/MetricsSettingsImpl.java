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

class MetricsSettingsImpl implements MetricsSettings {

    static final MetricsSettings DEFAULT = builder().build();

    static Builder builder() {
        return new Builder();
    }

    private final boolean isEnabled;
    private final KeyPerformanceIndicatorMetricsSettings kpiMetricsSettings;
    private final BaseMetricsSettings baseMetricsSettings;

    private MetricsSettingsImpl(MetricsSettingsImpl.Builder builder) {
        this.isEnabled = builder.isEnabled;
        this.kpiMetricsSettings = builder.kpiMetricsSettingsBuilder.build();
        this.baseMetricsSettings = builder.baseMetricsSettingsBuilder.build();
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    @Override
    public KeyPerformanceIndicatorMetricsSettings keyPerformanceIndicatorSettings() {
        return kpiMetricsSettings;
    }

    @Override
    public BaseMetricsSettings baseMetricsSettings() {
        return baseMetricsSettings;
    }

    static class Builder implements MetricsSettings.Builder {

        private boolean isEnabled = true;
        private KeyPerformanceIndicatorMetricsSettings.Builder kpiMetricsSettingsBuilder =
                KeyPerformanceIndicatorMetricsSettings.builder();
        private BaseMetricsSettings.Builder baseMetricsSettingsBuilder = BaseMetricsSettings.builder();

        @Override
        public MetricsSettings build() {
            return new MetricsSettingsImpl(this);
        }

        @Override
        public MetricsSettings.Builder enabled(boolean value) {
            isEnabled = value;
            return this;
        }

        @Override
        public MetricsSettings.Builder baseMetricsSettings(BaseMetricsSettings.Builder baseMetricsSettingsBuilder) {
            this.baseMetricsSettingsBuilder = baseMetricsSettingsBuilder;
            return this;
        }

        @Override
        public MetricsSettings.Builder config(Config metricsSettingsConfig) {
            metricsSettingsConfig.get(MetricsSettings.Builder.ENABLED_CONFIG_KEY)
                    .asBoolean()
                    .ifPresent(this::enabled);
            metricsSettingsConfig.get(BaseMetricsSettings.Builder.BASE_METRICS_CONFIG_KEY)
                    .as(cfg -> BaseMetricsSettings.builder().config(cfg))
                    .ifPresent(this::baseMetricsSettings);
            metricsSettingsConfig.get(KeyPerformanceIndicatorMetricsSettings.Builder.KEY_PERFORMANCE_INDICATORS_CONFIG_KEY)
                    .as(cfg -> KeyPerformanceIndicatorMetricsSettings.builder().config(cfg))
                    .ifPresent(this::keyPerformanceIndicatorSettings);
            return this;
        }

        @Override
        public MetricsSettings.Builder keyPerformanceIndicatorSettings(
                KeyPerformanceIndicatorMetricsSettings.Builder kpiMetricsSettings) {
            this.kpiMetricsSettingsBuilder = kpiMetricsSettings;
            return this;
        }
    }
}
