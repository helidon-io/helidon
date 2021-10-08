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
import io.helidon.servicecommon.rest.RestServiceSettings;
import io.helidon.webserver.cors.CrossOriginConfig;

class MetricsSettingsImpl implements MetricsSettings {

    private final boolean isEnabled;
    private final KeyPerformanceIndicatorMetricsSettings kpiMetricsSettings;
    private final BaseMetricsSettings baseMetricsSettings;
    private final RestServiceSettings serviceSettings;

    private MetricsSettingsImpl(MetricsSettingsImpl.Builder builder) {
        isEnabled = builder.isEnabled;
        kpiMetricsSettings = builder.kpiMetricsSettingsBuilder.build();
        baseMetricsSettings = builder.baseMetricsSettingsBuilder.build();
        serviceSettings = builder.serviceSettingsBuilder.build();
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

    @Override
    public String webContext() {
        return serviceSettings.webContext();
    }

    @Override
    public String routing() {
        return serviceSettings.routing();
    }

    @Override
    public CrossOriginConfig crossOriginConfig() {
        return serviceSettings.crossOriginConfig();
    }

    static class Builder implements MetricsSettings.Builder {

        private boolean isEnabled = true;
        private KeyPerformanceIndicatorMetricsSettings.Builder kpiMetricsSettingsBuilder =
                KeyPerformanceIndicatorMetricsSettings.builder();
        private BaseMetricsSettings.Builder baseMetricsSettingsBuilder = BaseMetricsSettings.builder();
        private RestServiceSettings.Builder serviceSettingsBuilder = RestServiceSettings.builder()
                .webContext(DEFAULT_CONTEXT);

        protected Builder() {
        }

        protected Builder(MetricsSettings serviceSettings) {
            isEnabled = serviceSettings.isEnabled();
            kpiMetricsSettingsBuilder = KeyPerformanceIndicatorMetricsSettings.builder(
                    serviceSettings.keyPerformanceIndicatorSettings());
            baseMetricsSettingsBuilder = BaseMetricsSettings.builder(serviceSettings.baseMetricsSettings());
        }

        @Override
        public MetricsSettingsImpl build() {
            return new MetricsSettingsImpl(this);
        }

        @Override
        public Builder enabled(boolean value) {
            isEnabled = value;
            return this;
        }

        @Override
        public Builder baseMetricsSettings(BaseMetricsSettings.Builder baseMetricsSettingsBuilder) {
            this.baseMetricsSettingsBuilder = baseMetricsSettingsBuilder;
            return this;
        }

        @Override
        public Builder config(Config metricsSettingsConfig) {
            serviceSettingsBuilder.config(metricsSettingsConfig);
            baseMetricsSettingsBuilder.config(metricsSettingsConfig.get(BaseMetricsSettings.Builder.BASE_METRICS_CONFIG_KEY));
            kpiMetricsSettingsBuilder.config(metricsSettingsConfig
                                                     .get(KeyPerformanceIndicatorMetricsSettings.Builder
                                                                  .KEY_PERFORMANCE_INDICATORS_CONFIG_KEY));
            metricsSettingsConfig.get(MetricsSettings.Builder.ENABLED_CONFIG_KEY)
                    .asBoolean()
                    .ifPresent(this::enabled);
            return this;
        }

        @Override
        public Builder keyPerformanceIndicatorSettings(
                KeyPerformanceIndicatorMetricsSettings.Builder kpiMetricsSettings) {
            this.kpiMetricsSettingsBuilder = kpiMetricsSettings;
            return this;
        }

        @Override
        public MetricsSettings.Builder serviceSettings(RestServiceSettings.Builder serviceSettingsBuilder) {
            this.serviceSettingsBuilder = serviceSettingsBuilder;
            return this;
        }
    }
}
