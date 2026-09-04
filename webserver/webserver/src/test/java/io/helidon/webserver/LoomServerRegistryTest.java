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

package io.helidon.webserver;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.webserver.spi.ServerFeature;
import io.helidon.webserver.spi.ServerFeatureProvider;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoomServerRegistryTest {
    @Test
    void managedServerUsesOwningRegistryFeatureProvider() {
        TestFeature feature = new TestFeature();
        ServerFeatureProvider<TestFeature> provider = new ServerFeatureProvider<>() {
            @Override
            public String configKey() {
                return "owning-registry";
            }

            @Override
            public TestFeature create(Config config, String name) {
                return feature;
            }
        };
        Config config = Config.just(ConfigSources.create(Map.of("server.features.owning-registry.enabled", "true")));
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .putContractInstance(Config.class, config)
                                                                                .putContractInstance(ServerFeatureProvider.class,
                                                                                                     provider)
                                                                                .build());
        try {
            WebServer server = manager.registry().get(WebServer.class);

            assertThat(server.prototype().features(), hasItem(feature));
            assertThat(feature.setup(), is(true));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void managedLimitReceivesOwningMeterRegistry() {
        Config config = Config.just(ConfigSources.create(Map.of("server.concurrency-limit.fixed.enable-metrics", "true")));
        MeterRegistry meterRegistry = mock(MeterRegistry.class);
        MetricsFactory metricsFactory = mock(MetricsFactory.class);
        Gauge.Builder<Integer> gaugeBuilder = mock(Gauge.Builder.class, RETURNS_SELF);
        Timer.Builder timerBuilder = mock(Timer.Builder.class, RETURNS_SELF);
        when(meterRegistry.metricsFactory()).thenReturn(metricsFactory);
        when(metricsFactory.gaugeBuilder(anyString(), ArgumentMatchers.<Supplier<Integer>>any()))
                .thenReturn(gaugeBuilder);
        when(metricsFactory.timerBuilder(anyString())).thenReturn(timerBuilder);
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .putContractInstance(Config.class, config)
                                                                                .putContractInstance(MeterRegistry.class,
                                                                                                     meterRegistry)
                                                                                .build());
        try {
            manager.registry().get(WebServer.class);

            verify(meterRegistry, atLeastOnce()).metricsFactory();
        } finally {
            manager.shutdown();
        }
    }

    private static final class TestFeature implements ServerFeature {
        private final AtomicBoolean setup = new AtomicBoolean();

        @Override
        public void setup(ServerFeatureContext featureContext) {
            setup.set(true);
        }

        @Override
        public String type() {
            return "owning-registry";
        }

        @Override
        public String name() {
            return type();
        }

        private boolean setup() {
            return setup.get();
        }
    }

}
