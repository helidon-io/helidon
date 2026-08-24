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
package io.helidon.webclient.grpc;

import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

@Testing.Test(perMethod = true)
class GrpcChannelTest {

    @Test
    void directClientUsesStaticMeterRegistryOwningFactory() {
        ServiceRegistryManager ownerManager = ServiceRegistryManager.create();
        ServiceRegistryManager foreignManager = ServiceRegistryManager.create();
        try {
            MeterRegistry meterRegistry = ownerManager.registry().get(MeterRegistry.class);
            MetricsFactory owningFactory = meterRegistry.metricsFactory();
            MetricsFactory foreignFactory = foreignManager.registry().get(MetricsFactory.class);
            Services.set(MeterRegistry.class, meterRegistry);
            Services.set(MetricsFactory.class, foreignFactory);

            GrpcChannel channel = (GrpcChannel) GrpcClient.builder()
                    .baseUri("http://localhost")
                    .enableMetrics(true)
                    .build()
                    .channel();

            assertThat("Channel uses the registry's owning factory",
                       channel.metricsFactory(),
                       sameInstance(owningFactory));
            assertThat("Channel ignores an independently overridden factory service",
                       channel.metricsFactory(),
                       not(sameInstance(foreignFactory)));
        } finally {
            foreignManager.shutdown();
            ownerManager.shutdown();
        }
    }

    @Test
    void managedClientUsesInjectedMeterRegistry() {
        ServiceRegistryManager injectedManager = ServiceRegistryManager.create();
        ServiceRegistryManager staticManager = ServiceRegistryManager.create();
        try {
            MeterRegistry injectedRegistry = injectedManager.registry().get(MeterRegistry.class);
            MeterRegistry staticRegistry = staticManager.registry().get(MeterRegistry.class);
            Services.set(MeterRegistry.class, staticRegistry);

            GrpcChannel channel = (GrpcChannel) GrpcClient.builder()
                    .baseUri("http://localhost")
                    .enableMetrics(true)
                    .meterRegistry(injectedRegistry)
                    .build()
                    .channel();

            assertThat("Channel uses the injected meter registry",
                       channel.meterRegistry(),
                       sameInstance(injectedRegistry));
            assertThat("Channel ignores the static meter registry",
                       channel.meterRegistry(),
                       not(sameInstance(staticRegistry)));
        } finally {
            staticManager.shutdown();
            injectedManager.shutdown();
        }
    }
}
