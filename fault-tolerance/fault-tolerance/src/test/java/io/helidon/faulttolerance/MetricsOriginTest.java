/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
package io.helidon.faulttolerance;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.spi.MeterBuilderCustomizer;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class MetricsOriginTest {
    private static final String GAUGE_NAME = "ft.origin.gauge";
    private static final String COUNTER_NAME = "ft.origin.counter";
    private static final String TIMER_NAME = "ft.origin.timer";
    private static final Set<String> METER_NAMES = Set.of(GAUGE_NAME, COUNTER_NAME, TIMER_NAME);

    @Test
    void reportsFaultToleranceOriginForAllMeterTypes() {
        Map<String, String> origins = new HashMap<>();
        MeterBuilderCustomizer customizer = new MeterBuilderCustomizer() {
            @Override
            public void customize(Meter.Builder<?, ?> builder, String origin) {
                if (METER_NAMES.contains(builder.name())) {
                    origins.put(builder.name(), origin);
                }
            }
        };
        ServiceRegistryConfig registryConfig = ServiceRegistryConfig.builder()
                .putContractInstance(MeterBuilderCustomizer.class, customizer)
                .build();
        ServiceRegistryManager manager = ServiceRegistryManager.create(registryConfig);
        try {
            MeterRegistry meterRegistry = manager.registry().get(MeterRegistry.class);
            MetricsFactory metricsFactory = meterRegistry.metricsFactory();

            MetricsUtils.gaugeBuilder(metricsFactory, meterRegistry, GAUGE_NAME, () -> 1);
            MetricsUtils.counterBuilder(metricsFactory, meterRegistry, COUNTER_NAME);
            MetricsUtils.timerBuilder(metricsFactory, meterRegistry, TIMER_NAME);

            assertThat(origins,
                       is(Map.of(GAUGE_NAME, FaultTolerance.class.getName(),
                                 COUNTER_NAME, FaultTolerance.class.getName(),
                                 TIMER_NAME, FaultTolerance.class.getName())));
        } finally {
            manager.shutdown();
        }
    }
}
