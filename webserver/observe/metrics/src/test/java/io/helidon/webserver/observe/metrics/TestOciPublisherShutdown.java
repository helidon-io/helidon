/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.observe.ObserveFeature;

import com.oracle.bmc.monitoring.Monitoring;
import com.oracle.bmc.monitoring.responses.PostMetricDataResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestOciPublisherShutdown {
    private ServiceRegistry previousRegistry;
    private ServiceRegistryManager registryManager;
    @BeforeEach
    void resetMetrics() {
        MetricsFactory.closeAll();
        KeyPerformanceIndicatorMetricsImpls.close();
        previousRegistry = GlobalServiceRegistry.registry();
        registryManager = ServiceRegistryManager.create();
        Services.registry(registryManager.registry());
    }

    @AfterEach
    void closeMetrics() {
        try {
            MetricsFactory.closeAll();
        } finally {
            try {
                registryManager.shutdown();
            } finally {
                Services.registry(previousRegistry);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void configuredPublisherStopsWithItsWebServer(boolean observerEnabled) throws Exception {
        Monitoring monitoring = mock(Monitoring.class);
        var posts = new LinkedBlockingQueue<Boolean>();
        when(monitoring.getEndpoint()).thenReturn("https://telemetry.example.com");
        when(monitoring.postMetricData(any())).thenAnswer(invocation -> {
            posts.add(true);
            return PostMetricDataResponse.builder().__httpStatusCode__(200).build();
        });
        Services.set(Monitoring.class, monitoring);
        var config = Config.just("""
                metrics:
                  publishers:
                    - type: oci
                      compartment-id: test-compartment
                      namespace: test-namespace
                      initial-delay: PT0.02S
                      delay: PT0.02S
                      scopes: [application]
                    - type: prometheus
                """, MediaTypes.APPLICATION_YAML);
        Services.set(Config.class, config);
        var serverBuilder = WebServer.builder().host("localhost");
        if (!observerEnabled) {
            serverBuilder.addFeature(ObserveFeature.builder()
                                             .addObserver(MetricsObserver.builder().enabled(false).build())
                                             .build());
        }
        var server = serverBuilder.build();
        try {
            server.start();
            Metrics.globalRegistry().getOrCreate(Counter.builder("serverLifecycle")
                                                        .scope(Meter.Scope.APPLICATION)).increment();
            assertThat(posts.poll(5, TimeUnit.SECONDS), is(true));
        } finally {
            server.stop();
        }
        posts.clear();
        assertThat(posts.poll(100, TimeUnit.MILLISECONDS), is(nullValue()));
        server.stop();
    }
}
