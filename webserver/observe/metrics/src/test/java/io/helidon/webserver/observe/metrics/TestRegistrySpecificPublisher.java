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
package io.helidon.webserver.observe.metrics;

import java.time.Duration;

import io.helidon.config.Config;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.MetricsFactoryFactory__ServiceDescriptor;
import io.helidon.metrics.providers.micrometer.OtlpPublisher;
import io.helidon.metrics.providers.micrometer.PrometheusPublisher;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class TestRegistrySpecificPublisher {

    @Test
    void nonMicrometerRegistryDisablesEndpointWithoutFailure() {
        ServiceRegistryManager manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                                                .discoverServices(false)
                                                                                .discoverServicesFromServiceLoader(false)
                                                                                .addServiceDescriptor(
                                                                                        MetricsFactoryFactory__ServiceDescriptor.INSTANCE)
                                                                                .putContractInstance(Config.class, Config.empty())
                                                                                .build());
        try {
            MetricsFactory metricsFactory = manager.registry().get(MetricsFactory.class);
            MetricsConfig metricsConfig = MetricsConfig.create();
            MeterRegistry meterRegistry = metricsFactory.createMeterRegistry(metricsConfig);
            MetricsFeature metricsFeature = new MetricsFeature(MetricsObserverConfig.builder()
                                                                      .metricsConfig(metricsConfig)
                                                                      .meterRegistry(meterRegistry)
                                                                      .buildPrototype());

            assertThat("Non-Micrometer registry disables the endpoint", metricsFeature.enabled(), is(false));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void endpointAvailabilityUsesSelectedRegistryPublishers() {
        MetricsFactory metricsFactory = Services.get(MetricsFactory.class);
        MetricsConfig prometheusConfig = MetricsConfig.builder()
                .addPublisher(PrometheusPublisher.create())
                .warnOnMultipleRegistries(false)
                .build();
        MetricsConfig otlpConfig = MetricsConfig.builder()
                .addPublisher(OtlpPublisher.create(builder -> builder.config(Config.empty())
                        .interval(Duration.ofDays(1))))
                .warnOnMultipleRegistries(false)
                .build();
        MeterRegistry prometheusRegistry = metricsFactory.createMeterRegistry(prometheusConfig);
        MeterRegistry otlpRegistry = metricsFactory.createMeterRegistry(otlpConfig);
        try {
            CompositeMeterRegistry otlpDelegate = otlpRegistry.unwrap(CompositeMeterRegistry.class);
            assertThat("OTLP configuration is treated as explicitly configured",
                       otlpConfig.publishersConfigured(), is(true));
            assertThat("OTLP-only registry does not contain a Prometheus registry",
                       otlpDelegate.getRegistries().stream().anyMatch(PrometheusMeterRegistry.class::isInstance),
                       is(false));

            MetricsFeature prometheusFeature = new MetricsFeature(MetricsObserverConfig.builder()
                                                                          .metricsConfig(prometheusConfig)
                                                                          .meterRegistry(prometheusRegistry)
                                                                          .buildPrototype());
            MetricsFeature otlpFeature = new MetricsFeature(MetricsObserverConfig.builder()
                                                                  .metricsConfig(otlpConfig)
                                                                  .meterRegistry(otlpRegistry)
                                                                  .buildPrototype());

            assertThat("Prometheus-backed registry enables the endpoint", prometheusFeature.enabled(), is(true));
            assertThat("OTLP-only registry disables the endpoint", otlpFeature.enabled(), is(false));
        } finally {
            prometheusRegistry.close();
            otlpRegistry.close();
        }
    }

    @Test
    void endpointAvailabilityUsesObserverConfigWithSharedRegistry() {
        MetricsConfig observerConfig = MetricsConfig.builder()
                .enabled(false)
                .build();

        MetricsFeature metricsFeature = new MetricsFeature(MetricsObserverConfig.builder()
                                                                  .metricsConfig(observerConfig)
                                                                  .buildPrototype());

        assertThat("Observer config disables the shared-registry endpoint", metricsFeature.enabled(), is(false));
    }
}
