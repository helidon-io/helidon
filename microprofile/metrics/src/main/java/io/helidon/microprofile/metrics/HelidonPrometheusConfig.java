/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.microprofile.metrics;

import io.helidon.metrics.api.MetricsSettings;

import io.micrometer.prometheus.PrometheusConfig;

/**
 * Helidon implementation of {@link io.micrometer.prometheus.PrometheusConfig} for creating a Micrometer Prometheus meter
 * registry.
 */
class HelidonPrometheusConfig implements PrometheusConfig {

    private MetricsSettings metricsSettings;

    HelidonPrometheusConfig(MetricsSettings metricsSettings) {
        this.metricsSettings = metricsSettings;
    }

    @Override
    public String get(String key) {
        return metricsSettings.value(key);
    }

    void update(MetricsSettings metricsSettings) {
        this.metricsSettings = metricsSettings;
    }
}
