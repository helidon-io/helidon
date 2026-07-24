/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.KeyPerformanceIndicatorMetricsConfig;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.service.registry.Services;
import io.helidon.webserver.KeyPerformanceIndicatorSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

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
        MeterRegistry meterRegistry = Services.get(MetricsFactory.class).createMeterRegistry(metricsConfig);

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
        MeterRegistry meterRegistry = Services.get(MetricsFactory.class).createMeterRegistry(metricsConfig);

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

    @Test
    void testKpiMetricsAreIsolatedByRegistry() {
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        MetricsConfig metricsConfig = MetricsConfig.builder()
                .warnOnMultipleRegistries(false)
                .build();
        MeterRegistry firstRegistry = metricsFactory.createMeterRegistry(metricsConfig);
        MeterRegistry secondRegistry = metricsFactory.createMeterRegistry(metricsConfig);
        KeyPerformanceIndicatorSupport.Metrics firstMetrics = null;
        KeyPerformanceIndicatorSupport.Metrics secondMetrics = null;
        try {
            firstMetrics = KeyPerformanceIndicatorMetricsImpls.get(firstRegistry,
                                                                   "requests.",
                                                                   KeyPerformanceIndicatorMetricsConfig.create(),
                                                                   BuiltInMeterNameFormat.CAMEL);
            secondMetrics = KeyPerformanceIndicatorMetricsImpls.get(secondRegistry,
                                                                    "requests.",
                                                                    KeyPerformanceIndicatorMetricsConfig.create(),
                                                                    BuiltInMeterNameFormat.CAMEL);

            assertThat("Each registry has its own KPI metrics", secondMetrics, not(sameInstance(firstMetrics)));

            firstMetrics.onRequestReceived();
            secondMetrics.onRequestReceived();
            assertThat("First registry count", counter(firstRegistry).count(), is(1L));
            assertThat("Second registry count", counter(secondRegistry).count(), is(1L));

            firstMetrics.close();
            firstMetrics = null;
            assertThat("Closing the first KPI metrics removes only its meters",
                       firstRegistry.meters().stream().noneMatch(meter -> meter.id().name().equals("requests.count")),
                       is(true));

            secondMetrics.onRequestReceived();
            assertThat("Second registry remains active", counter(secondRegistry).count(), is(2L));
        } finally {
            if (firstMetrics != null) {
                firstMetrics.close();
            }
            if (secondMetrics != null) {
                secondMetrics.close();
            }
            firstRegistry.close();
            secondRegistry.close();
        }
    }

    @Test
    void testSharedKpiMetricsRemainUntilLastOwnerCloses() {
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        MeterRegistry meterRegistry = metricsFactory.createMeterRegistry(MetricsConfig.create());
        KeyPerformanceIndicatorSupport.Metrics firstMetrics = null;
        KeyPerformanceIndicatorSupport.Metrics secondMetrics = null;
        try {
            firstMetrics = KeyPerformanceIndicatorMetricsImpls.get(meterRegistry,
                                                                   "requests.",
                                                                   KeyPerformanceIndicatorMetricsConfig.create(),
                                                                   BuiltInMeterNameFormat.CAMEL);
            secondMetrics = KeyPerformanceIndicatorMetricsImpls.get(meterRegistry,
                                                                    "requests.",
                                                                    KeyPerformanceIndicatorMetricsConfig.create(),
                                                                    BuiltInMeterNameFormat.CAMEL);

            assertThat("Observers share the same KPI metrics", secondMetrics, sameInstance(firstMetrics));

            firstMetrics.onRequestReceived();
            firstMetrics.close();
            firstMetrics = null;

            assertThat("First observer close preserves shared KPI meters", counter(meterRegistry).count(), is(1L));

            secondMetrics.onRequestReceived();
            assertThat("Second observer keeps recording KPI metrics", counter(meterRegistry).count(), is(2L));

            secondMetrics.close();
            secondMetrics = null;

            assertThat("Last observer close removes KPI meters",
                       meterRegistry.meters().stream().noneMatch(meter -> meter.id().name().equals("requests.count")),
                       is(true));
        } finally {
            if (firstMetrics != null) {
                firstMetrics.close();
            }
            if (secondMetrics != null) {
                secondMetrics.close();
            }
            meterRegistry.close();
        }
    }

    private static Counter counter(MeterRegistry meterRegistry) {
        return meterRegistry.meters().stream()
                .filter(meter -> meter.id().name().equals("requests.count"))
                .map(Counter.class::cast)
                .findFirst()
                .orElseThrow();
    }
}
