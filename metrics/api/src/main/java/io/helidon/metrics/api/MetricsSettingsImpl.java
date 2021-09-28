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

import java.util.HashMap;
import java.util.Map;

import io.helidon.config.Config;

class MetricsSettingsImpl implements MetricsSettings {

    static final MetricsSettings DEFAULT = builder().build();

    static Builder builder() {
        return new Builder();
    }

    private final boolean isEnabled;
    private final boolean isBaseEnabled;
    private final Map<String, Boolean> isBaseMetricEnabled;
    private final KeyPerformanceIndicatorMetricsSettings kpiMetricsSettings;

    private MetricsSettingsImpl(MetricsSettingsImpl.Builder builder) {
        this.isEnabled = builder.isEnabled;
        this.isBaseEnabled = builder.isBaseEnabled;
        this.isBaseMetricEnabled = builder.isBaseMetricEnabled;
        this.kpiMetricsSettings = builder.kpiMetricsSettings;
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    @Override
    public boolean isBaseEnabled() {
        return isBaseEnabled;
    }

    @Override
    public boolean isBaseMetricEnabled(String dottedName) {
        return isBaseEnabled && isBaseMetricEnabled.getOrDefault(dottedName, true);
    }

    @Override
    public KeyPerformanceIndicatorMetricsSettings keyPerformanceIndicatorSettings() {
        return kpiMetricsSettings;
    }

    static class Builder implements MetricsSettings.Builder {

        private boolean isEnabled = true;
        private boolean isBaseEnabled = true;
        private final Map<String, Boolean> isBaseMetricEnabled = new HashMap<>();
        private KeyPerformanceIndicatorMetricsSettings kpiMetricsSettings =
                KeyPerformanceIndicatorMetricsSettings.builder().build();

        @Override
        public MetricsSettings build() {
            return new MetricsSettingsImpl(this);
        }

        @Override
        public MetricsSettings.Builder enable(boolean value) {
            isEnabled = value;
            return this;
        }

        @Override
        public MetricsSettings.Builder enableBase(boolean value) {
            isBaseEnabled = value;
            return this;
        }

        @Override
        public MetricsSettings.Builder enableBaseMetric(String dottedName, boolean value) {
            isBaseMetricEnabled.put(dottedName, value);
            return this;
        }

        @Override
        public MetricsSettings.Builder config(Config config) {
            config.get(MetricsSettings.ENABLED_CONFIG_KEY)
                    .asBoolean()
                    .ifPresent(this::enable);
            Config baseConfig = config.get(MetricsSettings.BASE_CONFIG_KEY);
                    baseConfig.get(MetricsSettings.ENABLED_CONFIG_KEY)
                    .asBoolean()
                    .ifPresent(this::enableBase);
            // The metrics config section might contain individual base metric settings: base.${metricName}.enabled
            baseConfig.detach()
                    .asMap()
                    .ifPresent(map -> map.forEach((key, value) -> {
                        int enabledSuffixStart = key.lastIndexOf(".enabled");
                        if (enabledSuffixStart > -1) {
                            String metricName = key.substring(0, enabledSuffixStart);
                            enableBaseMetric(metricName, Boolean.parseBoolean(value));
                        }
                    }));
            config.get(KeyPerformanceIndicatorMetricsSettings.Builder.KEY_PERFORMANCE_INDICATORS_CONFIG_KEY)
                    .as(KeyPerformanceIndicatorMetricsSettings::create)
                    .ifPresent(this::keyPerformanceIndicatorSettings);
            return this;
        }

        @Override
        public MetricsSettings.Builder keyPerformanceIndicatorSettings(
                KeyPerformanceIndicatorMetricsSettings kpiMetricsSettings) {
            this.kpiMetricsSettings = kpiMetricsSettings;
            return this;
        }
    }
}
