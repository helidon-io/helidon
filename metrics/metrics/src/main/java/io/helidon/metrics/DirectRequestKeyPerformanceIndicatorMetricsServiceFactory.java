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
package io.helidon.metrics;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Priority;

import io.helidon.webserver.KeyPerformanceIndicatorMetricsConfig;
import io.helidon.webserver.KeyPerformanceIndicatorMetricsService;
import io.helidon.webserver.KeyPerformanceIndicatorMetricsServiceFactory;

/**
 * Factory for {@link KeyPerformanceIndicatorMetricsService} environments that handle request directly (e.g., non-Jersey).
 */
@Priority(1000)
public class DirectRequestKeyPerformanceIndicatorMetricsServiceFactory implements KeyPerformanceIndicatorMetricsServiceFactory {

    // Multiple apps can lead to multiple attempts to create the KPI metrics service. Reuse them as we can.
    private final Map<String, KeyPerformanceIndicatorMetricsService> services = new HashMap<>();

    protected KeyPerformanceIndicatorMetricsService createExtended(String metricsPrefix,
            KeyPerformanceIndicatorMetricsConfig kpiMetricsConfig) {
        return new KeyPerformanceIndicatorMetricsServices.Extended(metricsPrefix, kpiMetricsConfig);
    }

    protected KeyPerformanceIndicatorMetricsService createBasic(String metricsPrefix,
            KeyPerformanceIndicatorMetricsConfig kpiMetricsConfig) {
        return new KeyPerformanceIndicatorMetricsServices.Basic(metricsPrefix, kpiMetricsConfig);
    }

    @Override
    public KeyPerformanceIndicatorMetricsService create(String metricsPrefix,
            KeyPerformanceIndicatorMetricsConfig kpiMetricsConfig) {
        return services.computeIfAbsent(metricsPrefix, p ->
                kpiMetricsConfig.isExtended()
                        ? createExtended(metricsPrefix, kpiMetricsConfig)
                        : createBasic(metricsPrefix, kpiMetricsConfig));
    }
}
