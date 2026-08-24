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

package io.helidon.webclient.metrics;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.webclient.spi.WebClientService;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebClientMetricsProviderTest {
    private static final Config METRICS_CONFIG = metricsConfig();

    @Test
    void registryCreationUsesOwningMeterRegistry() {
        MeterRegistry meterRegistry = mock(MeterRegistry.class);
        MetricsFactory metricsFactory = mock(MetricsFactory.class);
        when(meterRegistry.metricsFactory()).thenReturn(metricsFactory);
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .putContractInstance(MeterRegistry.class,
                                                                                                     meterRegistry)
                                                                                .build());
        try {
            WebClientService service = new WebClientMetricsProvider()
                    .create(METRICS_CONFIG, "metrics", manager.registry());

            assertThat(service, instanceOf(WebClientMetrics.class));
            verify(meterRegistry, times(WebClientMetricType.values().length)).metricsFactory();
        } finally {
            manager.shutdown();
        }
    }

    private static Config metricsConfig() {
        ConfigNode.ListNode.Builder metrics = ConfigNode.ListNode.builder();
        for (WebClientMetricType type : WebClientMetricType.values()) {
            metrics.addObject(ConfigNode.ObjectNode.builder()
                                      .addValue("type", type.name())
                                      .build());
        }
        ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder()
                .addList("metrics", metrics.build())
                .build();
        return Config.just(ConfigSources.create(root)).get("metrics");
    }
}
