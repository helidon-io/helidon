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

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode;
import io.helidon.http.HttpTransportObserver;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.Handshake;
import io.helidon.http.HttpTransportObserver.Role;
import io.helidon.http.metrics.HttpTransportMetrics;
import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.webclient.api.HttpTransportObserverSupport.ObserverLifecycle;

import org.junit.jupiter.api.Test;

import static io.helidon.http.HttpTransportObserver.TRANSPORT_TCP;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class WebClientTransportMetricsTest {
    @Test
    void disabledServiceDoesNotResolveRegistry() throws Exception {
        ServiceRegistry serviceRegistry = mock(ServiceRegistry.class);
        Config config = Config.just(ConfigSources.create(Map.of("enabled", "false")));
        var service = (WebClientTransportMetrics) new WebClientTransportMetricsProvider()
                .create(config, "disabled", serviceRegistry);

        assertThat(service.enabled(), is(false));
        assertThat(service.name(), is("disabled"));
        assertThat(service.type(), is("http-metrics"));
        assertThat(service.scope(), sameInstance(service));
        ObserverLifecycle lifecycle = service.createObserver();
        assertThat(lifecycle.start(), sameInstance(HttpTransportObserver.noop()));
        lifecycle.stop().toCompletableFuture().get(5, TimeUnit.SECONDS);
        verifyNoMoreInteractions(serviceRegistry);
    }

    @Test
    void disabledProgrammaticServiceDoesNotAcquireRegistryLease() throws Exception {
        MeterRegistry registry = mock(MeterRegistry.class);
        var service = WebClientTransportMetrics.builder()
                .enabled(false)
                .meterRegistry(registry)
                .build();

        ObserverLifecycle lifecycle = service.createObserver();
        assertThat(lifecycle.start(), sameInstance(HttpTransportObserver.noop()));
        lifecycle.stop().toCompletableFuture().get(5, TimeUnit.SECONDS);
        verifyNoMoreInteractions(registry);
    }

    @Test
    void unconfiguredProviderDoesNotResolveRegistry() throws Exception {
        ServiceRegistry serviceRegistry = mock(ServiceRegistry.class);
        var service = (WebClientTransportMetrics) new WebClientTransportMetricsProvider()
                .create(Config.empty().get("http-metrics"), "http-metrics", serviceRegistry);

        assertThat(service.enabled(), is(false));
        ObserverLifecycle lifecycle = service.createObserver();
        assertThat(lifecycle.start(), sameInstance(HttpTransportObserver.noop()));
        lifecycle.stop().toCompletableFuture().get(5, TimeUnit.SECONDS);
        verifyNoMoreInteractions(serviceRegistry);
    }

    @Test
    void registryProviderResolvesOwningRegistryLazily() throws Exception {
        MeterRegistry registry = disabledRegistry();
        ServiceRegistry serviceRegistry = mock(ServiceRegistry.class);
        when(serviceRegistry.get(MeterRegistry.class)).thenReturn(registry);
        var service = (WebClientTransportMetrics) new WebClientTransportMetricsProvider()
                .create(emptyConfiguration(), "transport", serviceRegistry);

        assertThat(service.enabled(), is(true));
        assertThat(service.name(), is("transport"));
        verifyNoMoreInteractions(serviceRegistry);
        assertThat(service.scope(), sameInstance(registry));
        assertThat(service.scope(), sameInstance(registry));
        ObserverLifecycle lifecycle = service.createObserver();
        lifecycle.start();
        lifecycle.stop().toCompletableFuture().get(5, TimeUnit.SECONDS);
        verify(serviceRegistry, times(1)).get(MeterRegistry.class);
        verify(registry, times(1)).metricsFactory();
    }

    @Test
    void servicesUsingSameConfiguredRegistryShareScope() {
        MeterRegistry registry = mock(MeterRegistry.class);
        var first = WebClientTransportMetrics.builder().meterRegistry(registry).build();
        var second = WebClientTransportMetrics.builder().meterRegistry(registry).build();
        var other = WebClientTransportMetrics.builder().meterRegistry(mock(MeterRegistry.class)).build();

        assertThat(first.scope(), sameInstance(second.scope()));
        assertThat(first.scope(), not(sameInstance(other.scope())));
        verifyNoMoreInteractions(registry);
    }

    @Test
    void lifecycleReleaseWaitsForItsOpenConnection() throws Exception {
        MeterRegistry registry = disabledRegistry();
        var service = WebClientTransportMetrics.builder().meterRegistry(registry).build();
        ObserverLifecycle lifecycle = service.createObserver();
        HttpTransportObserver observer = lifecycle.start();
        assertThat(lifecycle.start(), sameInstance(observer));
        ConnectionObservation connection = observer.connectionOpened(Role.CLIENT, TRANSPORT_TCP, Handshake.NONE);

        try {
            var completion = lifecycle.stop();
            assertThat("Release must wait for the open client connection",
                       completion.toCompletableFuture().isDone(),
                       is(false));
            assertThat(lifecycle.stop(), sameInstance(completion));
            assertThat(lifecycle.start(), sameInstance(HttpTransportObserver.noop()));
        } finally {
            connection.close(ConnectionOutcome.NORMAL);
            lifecycle.stop().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
        verify(registry, times(1)).metricsFactory();
    }

    @Test
    void clientAndServerLeaseLifecyclesAreIndependent() throws Exception {
        MeterRegistry registry = disabledRegistry();
        var service = WebClientTransportMetrics.builder().meterRegistry(registry).build();
        ObserverLifecycle first = service.createObserver();
        ObserverLifecycle second = service.createObserver();
        HttpTransportMetrics.Lease server = HttpTransportMetrics.acquire(registry);

        try {
            HttpTransportObserver firstObserver = first.start();
            HttpTransportObserver secondObserver = second.start();
            assertThat(firstObserver, not(sameInstance(secondObserver)));
            first.stop().toCompletableFuture().get(5, TimeUnit.SECONDS);
            second.stop().toCompletableFuture().get(5, TimeUnit.SECONDS);
            assertThat("Stopping the clients must retain the server lease",
                       server.completion().toCompletableFuture().isDone(),
                       is(false));
            ConnectionObservation connection = server.connectionOpened(Role.SERVER, TRANSPORT_TCP, Handshake.NONE);
            connection.close(ConnectionOutcome.NORMAL);
        } finally {
            first.stop().toCompletableFuture().get(5, TimeUnit.SECONDS);
            second.stop().toCompletableFuture().get(5, TimeUnit.SECONDS);
            server.close();
            server.completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void stoppingBeforeStartDoesNotAcquireLease() throws Exception {
        MeterRegistry registry = mock(MeterRegistry.class);
        ObserverLifecycle lifecycle = WebClientTransportMetrics.builder()
                .meterRegistry(registry)
                .build()
                .createObserver();

        lifecycle.stop().toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertThat(lifecycle.start(), sameInstance(HttpTransportObserver.noop()));
        verifyNoMoreInteractions(registry);
    }

    @Test
    void transportConfigurationDoesNotReplaceRequestMetricProvider() {
        assertThat(new WebClientTransportMetricsProvider().configKey(), is("http-metrics"));
        assertThat(new WebClientMetricsProvider().configKey(), is("metrics"));
        var service = (WebClientTransportMetrics) new WebClientTransportMetricsProvider()
                .create(emptyConfiguration(), "transport");
        assertThat(service.enabled(), is(true));
        assertThat(service.prototype().name(), is("transport"));
    }

    private static Config emptyConfiguration() {
        ConfigNode.ObjectNode root = ConfigNode.ObjectNode.builder()
                .addObject("http-metrics", ConfigNode.ObjectNode.builder().build())
                .build();
        return Config.just(ConfigSources.create(root)).get("http-metrics");
    }

    private static MeterRegistry disabledRegistry() {
        MeterRegistry registry = mock(MeterRegistry.class);
        when(registry.unwrap(Object.class)).thenReturn(new Object());
        when(registry.metricsFactory()).thenReturn(mock(MetricsFactory.class));
        when(registry.clock()).thenReturn(mock(Clock.class));
        when(registry.meters()).thenReturn(List.of());
        return registry;
    }
}
