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

package io.helidon.integrations.oci.metrics;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.Errors;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryException;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;
import io.helidon.webserver.WebServer;

import com.oracle.bmc.monitoring.Monitoring;
import com.oracle.bmc.monitoring.responses.PostMetricDataResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@Testing.Test(perMethod = true)
class OciMetricsLifecycleTest {
    @AfterEach
    void closeMetrics() {
        MetricsFactory.closeAll();
    }

    @Test
    void disabledPublisherDoesNotResolveMonitoring() {
        var serviceRegistry = unavailableMonitoring();
        var config = Config.just("""
                metrics:
                  publishers:
                    - type: oci
                      enabled: false
                """, MediaTypes.APPLICATION_YAML);
        assertThat(MetricsConfig.create(config.get("metrics")).publishers().isEmpty(), is(true));
        try (var publisher = OciMetricsConfig.builder().enabled(false).build()) {
            publisher.startPublishing();
            assertThat(publisher.enabled(), is(false));
        }
        var publisherConfig = config.get("metrics.publishers").asNodeList().get().getFirst();
        try (var publisher = (OciMetricsService) new OciMetricsPublisherProvider().create(publisherConfig, "oci")) {
            publisher.startPublishing();
            assertThat(publisher.enabled(), is(false));
        }
        verify(serviceRegistry, never()).get(Monitoring.class);
    }

    @Test
    void publisherConfigurationDoesNotResolveMonitoringOrStartTransmissions() throws Exception {
        var serviceRegistry = unavailableMonitoring();
        var config = Config.just("""
                metrics:
                  publishers:
                    - type: oci
                """, MediaTypes.APPLICATION_YAML);
        var metricsConfig = MetricsConfig.create(config.get("metrics"));
        try (var publisher = (AutoCloseable) metricsConfig.publishers().getFirst()) {
            verify(serviceRegistry, never()).get(Monitoring.class);
        }
    }

    @Test
    void emptyScopesDoNotResolveMonitoring() {
        var serviceRegistry = unavailableMonitoring();
        try (var publisher = OciMetricsConfig.builder().scopes(Set.of()).build()) {
            publisher.startPublishing();
            assertThat(publisher.prototype().monitoringClient().isEmpty(), is(true));
            verify(serviceRegistry, never()).get(Monitoring.class);
        }
    }

    @Test
    void failedStartupCanBeClosedWithoutRestarting() {
        unavailableMonitoring();
        var publisher = OciMetricsService.createStopped(OciMetricsConfig.builder().buildPrototype());
        assertThrows(ServiceRegistryException.class, publisher::startup);
        publisher.close();
        publisher.close();
        assertThrows(IllegalStateException.class, publisher::startup);
    }

    @Test
    void closeBeforeFirstExecutionPreventsTransmission() {
        Monitoring monitoring = mock(Monitoring.class);
        var publisher = OciMetricsConfig.builder()
                .monitoringClient(monitoring)
                .initialDelay(Duration.ofHours(1))
                .build();
        publisher.startPublishing();
        publisher.close();
        publisher.close();
        verifyZeroInteractions(monitoring);
        assertThrows(IllegalStateException.class, publisher::startup);
    }

    @Test
    void invalidSchedulingSettingsAreReportedTogether() {
        var failure = assertThrows(Errors.ErrorMessagesException.class, () -> OciMetricsConfig.builder()
                .initialDelay(Duration.ofSeconds(Long.MIN_VALUE))
                .delay(Duration.ZERO)
                .batchDelay(Duration.ofMillis(-1))
                .batchSize(0)
                .build());
        assertThat(failure.getMessage(), containsString("initial-delay must not be negative"));
        assertThat(failure.getMessage(), containsString("initial-delay must be representable"));
        assertThat(failure.getMessage(), containsString("delay must be positive"));
        assertThat(failure.getMessage(), containsString("batch-delay must not be negative"));
        assertThat(failure.getMessage(), containsString("batch-size must be greater than zero"));
    }

    @Test
    @SuppressWarnings("removal")
    void legacyPublisherFollowsServerLifecycle() throws InterruptedException {
        Monitoring monitoring = mock(Monitoring.class);
        var posts = new LinkedBlockingQueue<Boolean>();
        when(monitoring.getEndpoint()).thenReturn("https://telemetry.example.com");
        when(monitoring.postMetricData(any())).thenAnswer(invocation -> {
            posts.add(true);
            return PostMetricDataResponse.builder().__httpStatusCode__(200).build();
        });
        Metrics.globalRegistry().getOrCreate(Counter.builder("legacyLifecycle").scope(Meter.Scope.APPLICATION)).increment();
        var support = OciMetricsSupport.builder()
                .monitoringClient(monitoring)
                .initialDelay(0)
                .delay(20)
                .schedulingTimeUnit(TimeUnit.MILLISECONDS)
                .build();
        try {
            assertThat(posts.poll(100, TimeUnit.MILLISECONDS), is(nullValue()));
            var server = WebServer.builder().host("localhost").routing(routing -> routing.register(support)).build();
            try {
                server.start();
                assertThat(posts.poll(5, TimeUnit.SECONDS), is(true));
            } finally {
                server.stop();
            }
            posts.clear();
            assertThat(posts.poll(100, TimeUnit.MILLISECONDS), is(nullValue()));
            support.afterStop();
        } finally {
            support.afterStop();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void incompleteCloseRetainsOwnershipUntilTransmissionExits(boolean interruptClose) throws Exception {
        Monitoring monitoring = mock(Monitoring.class);
        when(monitoring.getEndpoint()).thenReturn("https://telemetry.example.com");
        var entered = new CountDownLatch(1);
        var interruptedSend = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var posts = new AtomicInteger();
        when(monitoring.postMetricData(any())).thenAnswer(invocation -> {
            posts.incrementAndGet();
            entered.countDown();
            boolean interrupted = false;
            while (release.getCount() != 0) {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    interrupted = true;
                    interruptedSend.countDown();
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            return PostMetricDataResponse.builder().__httpStatusCode__(200).build();
        });
        Metrics.globalRegistry().getOrCreate(Counter.builder("blockedLifecycle").scope(Meter.Scope.APPLICATION)).increment();
        var publisher = OciMetricsConfig.builder().monitoringClient(monitoring)
                .initialDelay(Duration.ZERO).delay(Duration.ofMillis(20)).build();
        publisher.startPublishing();
        var closeResult = new CompletableFuture<Throwable>();
        var interruptedClose = new AtomicBoolean();
        Thread closer = null;
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS), is(true));
            closer = Thread.ofVirtual().start(() -> {
                try {
                    publisher.close();
                    closeResult.complete(null);
                } catch (Throwable failure) {
                    interruptedClose.set(Thread.currentThread().isInterrupted());
                    closeResult.complete(failure);
                }
            });
            assertThat(interruptedSend.await(5, TimeUnit.SECONDS), is(true));
            assertThat(closeResult.isDone(), is(false));
            if (interruptClose) {
                closer.interrupt();
            }
            assertThat(closeResult.get(15, TimeUnit.SECONDS), instanceOf(IllegalStateException.class));
            assertThat(interruptedClose.get(), is(interruptClose));
            assertThrows(IllegalStateException.class, publisher::startup);
        } finally {
            release.countDown();
            if (closer != null) {
                closer.join(5000);
            }
            publisher.close();
        }
        publisher.close();
        assertThat(posts.get(), is(1));
    }

    @Test
    void recoverablePostingFailureDoesNotStopPublishing() throws InterruptedException {
        Monitoring monitoring = mock(Monitoring.class);
        var recovered = new CountDownLatch(1);
        var posts = new AtomicInteger();
        when(monitoring.getEndpoint()).thenReturn("https://telemetry.example.com");
        when(monitoring.postMetricData(any())).thenAnswer(invocation -> {
            if (posts.incrementAndGet() == 1) {
                throw new IllegalStateException("Simulated posting failure");
            }
            recovered.countDown();
            return PostMetricDataResponse.builder().__httpStatusCode__(200).build();
        });
        Metrics.globalRegistry().getOrCreate(Counter.builder("recoverableLifecycle").scope(Meter.Scope.APPLICATION)).increment();
        try (var publisher = OciMetricsConfig.builder().monitoringClient(monitoring)
                .initialDelay(Duration.ZERO).delay(Duration.ofMillis(20)).build()) {
            publisher.startPublishing();
            assertThat(recovered.await(5, TimeUnit.SECONDS), is(true));
        }
    }

    private static ServiceRegistry unavailableMonitoring() {
        var registry = mock(ServiceRegistry.class, delegatesTo(GlobalServiceRegistry.registry()));
        doThrow(new ServiceRegistryException("Monitoring is deliberately unavailable"))
                .when(registry).get(Monitoring.class);
        Services.registry(registry);
        return registry;
    }
}
