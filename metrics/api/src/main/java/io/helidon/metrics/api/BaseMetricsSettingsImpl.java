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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.helidon.config.Config;

class BaseMetricsSettingsImpl implements BaseMetricsSettings {

    private final boolean isEnabled;
    private final Map<String, Boolean> isBaseMetricEnabled;

    private BaseMetricsSettingsImpl(Builder builder) {
        isEnabled = builder.isEnabled;
        isBaseMetricEnabled = builder.isBaseMetricEnabled;
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    @Override
    public boolean isBaseMetricEnabled(String dottedName) {
        return isEnabled && isBaseMetricEnabled.getOrDefault(dottedName, true);
    }

    @Override
    public Map<String, Boolean> baseMetricEnabledSettings() {
        return Collections.unmodifiableMap(isBaseMetricEnabled);
    }

    static class Builder implements BaseMetricsSettings.Builder {

        static Builder create() {
            return new Builder();
        }

        static Builder create(BaseMetricsSettings baseMetricsSettings) {
            Builder result = new Builder();
            result.enabled(baseMetricsSettings.isEnabled());
            baseMetricsSettings.baseMetricEnabledSettings()
                    .forEach(result::enableBaseMetric);
            return result;
        }

        private boolean isEnabled = true;
        private final Map<String, Boolean> isBaseMetricEnabled = new HashMap<>();

        @Override
        public BaseMetricsSettings build() {
            return new BaseMetricsSettingsImpl(this);
        }

        @Override
        public Builder enabled(boolean value) {
            isEnabled = value;
            return this;
        }

        @Override
        public Builder config(Config baseMetricsConfig) {
            baseMetricsConfig.get(ENABLED_CONFIG_KEY)
                    .asBoolean()
                    .ifPresent(this::enabled);

            baseMetricsConfig.detach()
                    .asMap()
                    .ifPresent(map -> map.forEach((key, value) -> {
                        int enabledSuffixStart = key.lastIndexOf(".enabled");
                        if (enabledSuffixStart > -1) {
                            String metricName = key.substring(0, enabledSuffixStart);
                            enableBaseMetric(metricName, Boolean.parseBoolean(value));
                        }
                    }));

            return this;
        }

        @Override
        public Builder enableBaseMetric(String dottedName, boolean value) {
            isBaseMetricEnabled.put(dottedName, value);
            return this;
        }

        @Override
        public boolean isEnabled() {
            return isEnabled;
        }
    }
}
