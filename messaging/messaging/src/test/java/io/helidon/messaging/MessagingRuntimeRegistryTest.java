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

package io.helidon.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;
import io.helidon.messaging.spi.IncomingChannel;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.providers.helidon.HelidonMetricsFactory;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.helidon.health.HealthCheckResponse.Status.DOWN;
import static io.helidon.health.HealthCheckResponse.Status.UP;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(60)
class MessagingRuntimeRegistryTest {
    private static final String CHANNEL = "supplied-config";
    private static final String DURATION = "messaging.processing.duration";

    @Test
    void suppliedConfigurationUsesTheConstructingRegistry() throws Exception {
        // Two applications supply their own configuration; startup health and identical channel names stay isolated.
        try (var first = new Fixture(true, true, true);
             var second = new Fixture(true, true, false);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            MessagingRuntime secondRuntime = second.runtime();
            var firstStartup = executor.submit(first::runtime);
            try {
                await(first.incoming.entered);
                assertThat(first.readiness(), is(DOWN));
                assertThat(second.readiness(), is(UP));
            } finally {
                first.incoming.ready.countDown();
            }
            MessagingRuntime firstRuntime = firstStartup.get(30, TimeUnit.SECONDS);
            firstRuntime.emit(CHANNEL, Message.create("first"));
            secondRuntime.emit(CHANNEL, Message.create("second"));
            secondRuntime.emit(CHANNEL, Message.create("third"));

            assertThat(first.received, is(List.of("first")));
            assertThat(second.received, is(List.of("second", "third")));
            assertThat(first.attempts(), is(1L));
            assertThat(second.attempts(), is(2L));
            assertThat(first.readiness(), is(UP));
            first.manager.shutdown();
            assertThat(first.meters.meters(), empty());
            assertThat(second.attempts(), is(2L));
            assertThat(second.readiness(), is(UP));
        }
    }

    @ParameterizedTest
    @CsvSource({"true,true", "false,true", "true,false", "false,false"})
    void suppliedNestedConfigurationControlsEachObservation(boolean metricsEnabled, boolean healthEnabled) throws Exception {
        // Injected configuration must independently control both observers, without relying on its default supplier.
        try (var fixture = new Fixture(metricsEnabled, healthEnabled, true);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var startup = executor.submit(fixture::runtime);
            try {
                await(fixture.incoming.entered);
                assertThat(fixture.readiness(), is(healthEnabled ? DOWN : UP));
            } finally {
                fixture.incoming.ready.countDown();
            }
            startup.get(30, TimeUnit.SECONDS).emit(CHANNEL, Message.create("delivered"));

            assertThat(fixture.received, is(List.of("delivered")));
            assertThat(fixture.meters.timer(DURATION, fixture.tags()).isPresent(), is(metricsEnabled));
            if (metricsEnabled) {
                assertThat(fixture.attempts(), is(1L));
            }
            assertThat(fixture.readiness(), is(UP));
        }
    }

    @Test
    void explicitRegistryFactoryRejectsNullArguments() {
        // The explicit owner-registry entry point rejects nulls before attempting graph assembly.
        try (var fixture = new Fixture(false, false, false)) {
            assertThrows(NullPointerException.class, () -> MessagingGraph.create(null, fixture.registry));
            assertThrows(NullPointerException.class,
                         () -> MessagingGraph.create(MessagingConfig.builder().buildPrototype(), null));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat("test coordination", latch.await(30, TimeUnit.SECONDS), is(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final HelidonMetricsFactory factory = HelidonMetricsFactory.create();
        private final MeterRegistry meters = factory.createMeterRegistry(MetricsConfig.create());
        private final List<String> received = new ArrayList<>();
        private final ServiceRegistryManager manager;
        private final ServiceRegistry registry;
        private final List<HealthCheck> healthChecks;
        private final BlockingIncoming incoming;

        private Fixture(boolean metricsEnabled, boolean healthEnabled, boolean blockStartup) {
            MessagingChannel<String> channel = MessagingChannel.create(CHANNEL, String.class);
            var builder = MessagingConfig.builder()
                    .metrics(metrics -> metrics.enabled(metricsEnabled))
                    .health(health -> health.enabled(healthEnabled))
                    .channel(channel)
                    .payloadSink(channel, received::add);
            incoming = blockStartup ? new BlockingIncoming() : null;
            if (incoming != null) {
                builder.incomingChannel(channel, incoming);
            }
            // Application-supplied configuration bypasses the default configuration provider. The runtime must still
            // resolve both observations from its constructing service registry.
            MessagingConfig supplied = builder.buildPrototype();
            manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                    .discoverServices(false)
                    .discoverServicesFromServiceLoader(false)
                    .addServiceDescriptor(ChannelRegistry__ServiceDescriptor.INSTANCE)
                    .addServiceDescriptor(MessagingLifecycleGuard__ServiceDescriptor.INSTANCE)
                    .addServiceDescriptor(MessagingHealthProvider__ServiceDescriptor.INSTANCE)
                    .putContractInstance(MessagingConfig.class, supplied)
                    .putContractInstance(MeterRegistry.class, meters)
                    .build());
            registry = manager.registry();
            healthChecks = registry.get(MessagingHealthProvider.class).healthChecks(Config.empty());
        }

        @Override
        public void close() {
            if (incoming != null) {
                incoming.ready.countDown();
            }
            try {
                manager.shutdown();
            } finally {
                factory.close();
            }
        }

        private MessagingRuntime runtime() {
            return registry.get(MessagingRuntime.class);
        }

        private HealthCheckResponse.Status readiness() {
            return healthChecks.stream().filter(check -> check.type() == HealthCheckType.READINESS)
                    .findFirst().orElseThrow().call().status();
        }

        private List<Tag> tags() {
            return List.of(factory.tagCreate("channel", CHANNEL));
        }

        private long attempts() {
            return meters.timer(DURATION, tags()).orElseThrow().count();
        }
    }

    private static final class BlockingIncoming implements IncomingChannel {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch ready = new CountDownLatch(1);
        private final CountDownLatch stop = new CountDownLatch(1);

        @Override
        public void run(IncomingConnectorContext context) {
            // Hold source startup while the test observes readiness, then keep the source alive until graph shutdown.
            entered.countDown();
            await(ready);
            if (context.awaitRunning()) {
                await(stop);
            }
        }

        @Override
        public void drain() {
            stop.countDown();
        }

        @Override
        public void forceClose() {
            ready.countDown();
            stop.countDown();
        }

        @Override
        public void close() {
            forceClose();
        }
    }
}
