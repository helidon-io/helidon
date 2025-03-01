/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.webserver.observe.metrics;

import io.helidon.metrics.api.BuiltInMeterNameFormat;
import io.helidon.metrics.api.KeyPerformanceIndicatorMetricsConfig;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

class TestKpiMeterNameCase {

    @BeforeEach
    void clear() {
        KeyPerformanceIndicatorMetricsImpls.close();
    }

    @Test
    void testExtendedWithNoCaseSetting() {
        MetricsConfig metricsConfig = MetricsConfig.builder()
                .keyPerformanceIndicatorMetricsConfig(() -> KeyPerformanceIndicatorMetricsConfig.builder()
                        .extended(true)
                        .build())
                .build();
        MeterRegistry meterRegistry = Metrics.createMeterRegistry(metricsConfig);

        // As a side-effect, the following line registers the KPI metrics in the meter registry.
        KeyPerformanceIndicatorMetricsImpls.get(meterRegistry,
                                                "requests.",
                                                KeyPerformanceIndicatorMetricsConfig.builder().extended(true).build(),
                                                BuiltInMeterNameFormat.CAMEL);

        assertThat("In-flight KPI",
                   meterRegistry.meters().stream()
                           .map(m -> m.id().name())
                           .toList(),
                   hasItem("requests.inFlight"));
    }

    @Test
    void testExtendedWithSnakeCaseSetting() {
        MetricsConfig metricsConfig = MetricsConfig.builder()
                .keyPerformanceIndicatorMetricsConfig(() -> KeyPerformanceIndicatorMetricsConfig.builder()
                        .extended(true)
                        .build())
                .builtInMeterNameFormat(BuiltInMeterNameFormat.SNAKE)
                .build();
        MeterRegistry meterRegistry = Metrics.createMeterRegistry(metricsConfig);

        // As a side-effect, the following line registers the KPI metrics in the meter registry.
        KeyPerformanceIndicatorMetricsImpls.get(meterRegistry,
                                                "requests.",
                                                KeyPerformanceIndicatorMetricsConfig.builder().extended(true).build(),
                                                BuiltInMeterNameFormat.SNAKE);

        assertThat("In-flight KPI",
                   meterRegistry.meters().stream()
                           .map(m -> m.id().name())
                           .toList(),
                   hasItem("requests.in_flight"));
    }
}
