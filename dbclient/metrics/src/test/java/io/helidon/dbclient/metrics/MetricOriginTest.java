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
package io.helidon.dbclient.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.spi.MeterBuilderCustomizer;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

class MetricOriginTest {
    private static final Set<String> TEST_METER_NAMES = Set.of("test-counter", "test-timer");

    @Test
    void usesStableDbClientOriginForMetadataMeters() {
        List<String> origins = new ArrayList<>();
        MeterBuilderCustomizer customizer = new MeterBuilderCustomizer() {
            @Override
            public void customize(Meter.Builder<?, ?> builder) {
                if (TEST_METER_NAMES.contains(builder.name())) {
                    builder.origin().ifPresent(origins::add);
                }
            }
        };
        ServiceRegistryConfig registryConfig = ServiceRegistryConfig.builder()
                .putContractInstance(MeterBuilderCustomizer.class, customizer)
                .build();
        ServiceRegistryManager manager = ServiceRegistryManager.create(registryConfig);
        try {
            Services.registry(manager.registry());
            MeterRegistry registry = manager.registry().get(MeterRegistry.class);

            MetricCounter counter = MetricCounter.builder().build();
            counter.metric(registry, MeterMetadata.builder().name("test-counter").build());
            MetricTimer timer = MetricTimer.builder().build();
            timer.metric(registry, MeterMetadata.builder().name("test-timer").build());

            assertThat(origins, contains(DbClientMetrics.class.getName(), DbClientMetrics.class.getName()));
        } finally {
            manager.shutdown();
        }
    }
}
