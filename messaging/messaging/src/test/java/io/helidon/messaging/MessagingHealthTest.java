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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.config.Config;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.health.HealthCheckType;
import io.helidon.health.spi.HealthCheckProvider;
import io.helidon.messaging.spi.IncomingChannel;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static io.helidon.health.HealthCheckResponse.Status.DOWN;
import static io.helidon.health.HealthCheckResponse.Status.UP;
import static io.helidon.health.HealthCheckType.LIVENESS;
import static io.helidon.health.HealthCheckType.READINESS;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Timeout(10)
class MessagingHealthTest {
    private final List<ServiceRegistryManager> registries = new ArrayList<>();

    @AfterEach
    void shutdownRegistries() {
        registries.forEach(ServiceRegistryManager::shutdown);
    }

    @Test
    void explicitChecksTrackIdleGraphsAndStayBoundAfterClose() {
        var registry = registry();
        List<HealthCheck> aggregate = aggregate(registry);
        assertHealth(aggregate, UP, UP);

        MessagingGraph first = builder(registry).build();
        List<HealthCheck> firstChecks = first.healthChecks();
        assertThrows(UnsupportedOperationException.class, () -> firstChecks.clear());
        assertHealth(firstChecks, DOWN, UP);
        assertHealth(aggregate, DOWN, UP);
        first.start();
        assertHealth(firstChecks, UP, UP);
        assertHealth(aggregate, UP, UP);

        try (MessagingGraph second = builder(registry).build()) {
            assertThat(firstChecks.getFirst().name(), not(second.healthChecks().getFirst().name()));
            assertHealth(aggregate, DOWN, UP);
            first.close();
            assertHealth(firstChecks, DOWN, UP);
            assertHealth(aggregate, DOWN, UP);
            second.start();
            assertHealth(aggregate, UP, UP);
            assertHealth(firstChecks, DOWN, UP);
        } finally {
            first.close();
        }
        assertHealth(aggregate, UP, UP);
    }

    @Test
    void healthChecksReflectBlockedStartupAndBecomeReadyTogether() throws Exception {
        var registry = registry();
        List<HealthCheck> aggregate = aggregate(registry);
        TestIncoming source = new TestIncoming(true, null, null);
        try (MessagingGraph graph = incomingGraph(registry, source)) {
            CompletableFuture<Void> started = runAsync(graph::start);
            await(source.entered);
            assertHealth(graph.healthChecks(), DOWN, UP);
            assertHealth(aggregate, DOWN, UP);
            source.ready.countDown();
            started.get(5, TimeUnit.SECONDS);
            assertHealth(graph.healthChecks(), UP, UP);
            assertHealth(aggregate, UP, UP);
        } finally {
            source.forceClose();
        }
        assertHealth(aggregate, UP, UP);
    }

    @Test
    void startupFailureRemainsVisibleUntilExplicitClose() {
        var registry = registry();
        List<HealthCheck> aggregate = aggregate(registry);
        TestIncoming source = new TestIncoming(false, new IllegalStateException("startup failure"), null);
        MessagingGraph graph = incomingGraph(registry, source);
        List<HealthCheck> checks = graph.healthChecks();
        try {
            assertThrows(IllegalStateException.class, graph::start);
            assertHealth(checks, DOWN, DOWN);
            assertHealth(aggregate, DOWN, DOWN);
        } finally {
            graph.close();
        }
        assertHealth(checks, DOWN, DOWN);
        assertHealth(aggregate, UP, UP);
    }

    @Test
    void sourceFailureIsRemovedEvenWhenExplicitCloseThrows() throws Exception {
        var registry = registry();
        List<HealthCheck> aggregate = aggregate(registry);
        TestIncoming source = new TestIncoming(false, null, new IllegalStateException("runtime failure"));
        MessagingGraph graph = incomingGraph(registry, source);
        List<HealthCheck> checks = graph.healthChecks();
        graph.start();
        await(source.running);
        source.stop.countDown();
        awaitStatus(checks, LIVENESS, DOWN);
        assertHealth(aggregate, DOWN, DOWN);
        assertThrows(MessagingException.class, graph::close);
        assertHealth(checks, DOWN, DOWN);
        assertHealth(aggregate, UP, UP);
        assertThrows(MessagingException.class, graph::close);
        assertHealth(aggregate, UP, UP);
    }

    @Test
    void handlerFailureDoesNotChangeGraphHealth() {
        var registry = registry();
        List<HealthCheck> aggregate = aggregate(registry);
        MessagingChannel<String> channel = MessagingChannel.create("events", String.class);
        try (MessagingGraph graph = builder(registry)
                .channel(channel)
                .payloadSink(channel, _ -> {
                    throw new IllegalArgumentException("handler failure");
                })
                .build()
                .start()) {
            assertThrows(BatchDeliveryException.class, () -> graph.emitter(channel).emit("event"));
            assertHealth(graph.healthChecks(), UP, UP);
            assertHealth(aggregate, UP, UP);
        }
    }

    @Test
    void handlerInitiatedCloseRetainsMembershipUntilTheCallbackCompletes() throws Exception {
        var registry = registry();
        List<HealthCheck> aggregate = aggregate(registry);
        CountDownLatch closeReturned = new CountDownLatch(1);
        CountDownLatch finishCallback = new CountDownLatch(1);
        AtomicReference<MessagingGraph> owner = new AtomicReference<>();
        MessagingChannel<String> channel = MessagingChannel.create("events", String.class);
        MessagingGraph graph = builder(registry)
                .channel(channel)
                .payloadSink(channel, _ -> {
                    owner.get().close();
                    closeReturned.countDown();
                    await(finishCallback);
                })
                .build();
        owner.set(graph);
        graph.start();
        CompletableFuture<Void> emitted = runAsync(() -> graph.emitter(channel).emit("event"));
        try {
            await(closeReturned);
            assertHealth(graph.healthChecks(), DOWN, UP);
            assertHealth(aggregate, DOWN, UP);
        } finally {
            finishCallback.countDown();
            emitted.get(5, TimeUnit.SECONDS);
            graph.close();
        }
        assertHealth(graph.healthChecks(), DOWN, UP);
        assertHealth(aggregate, UP, UP);
    }

    @Test
    void failedBuildAndUnstartedCloseLeaveNoAggregateMembership() {
        var registry = registry();
        List<HealthCheck> aggregate = aggregate(registry);
        MessagingChannel<String> outputless = MessagingChannel.create("outputless", String.class);
        assertThrows(IllegalArgumentException.class, () -> builder(registry).channel(outputless).build());
        assertHealth(aggregate, UP, UP);

        MessagingGraph graph = builder(registry).build();
        List<HealthCheck> checks = graph.healthChecks();
        assertHealth(aggregate, DOWN, UP);
        graph.close();
        graph.close();
        assertHealth(checks, DOWN, UP);
        assertHealth(aggregate, UP, UP);
    }

    @Test
    void forcedCloseRemovesMembershipWhileAnUncooperativeCallbackSurvives() throws Exception {
        var registry = registry();
        List<HealthCheck> aggregate = aggregate(registry);
        CountDownLatch processing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        MessagingChannel<String> channel = MessagingChannel.create("events", String.class);
        MessagingGraph graph = builder(registry)
                .shutdownTimeout(Duration.ofMillis(50))
                .channel(channel)
                .payloadSink(channel, _ -> {
                    processing.countDown();
                    try {
                        awaitUninterruptibly(release);
                    } finally {
                        finished.countDown();
                    }
                })
                .build()
                .start();
        CompletableFuture<Void> emitted = runAsync(() -> graph.emitter(channel).emit("event"));
        try {
            await(processing);
            assertThrows(MessagingException.class, graph::close);
            assertThat(finished.getCount(), is(1L));
            assertHealth(graph.healthChecks(), DOWN, DOWN);
            assertHealth(aggregate, UP, UP);
        } finally {
            release.countDown();
            await(finished);
            emitted.handle((_, _) -> null).get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void disabledHealthDoesNotRegisterChecks() {
        var registry = registry();
        List<HealthCheck> aggregate = aggregate(registry);
        try (MessagingGraph graph = builder(registry).enableHealth(false).build()) {
            assertThat(graph.healthChecks(), is(List.of()));
            assertHealth(aggregate, UP, UP);
            graph.start();
            assertThat(graph.healthChecks(), is(List.of()));
        }
    }

    @Test
    void serviceRegistriesHaveIndependentAggregateMembership() {
        var firstRegistry = registry();
        var secondRegistry = registry();
        List<HealthCheck> firstAggregate = aggregate(firstRegistry);
        List<HealthCheck> secondAggregate = aggregate(secondRegistry);
        try (MessagingGraph first = builder(firstRegistry).build();
             MessagingGraph second = builder(secondRegistry).build()) {
            assertHealth(firstAggregate, DOWN, UP);
            assertHealth(secondAggregate, DOWN, UP);
            first.start();
            assertHealth(firstAggregate, UP, UP);
            assertHealth(secondAggregate, DOWN, UP);
            second.close();
            assertHealth(firstAggregate, UP, UP);
            assertHealth(secondAggregate, UP, UP);
        }
    }

    @Test
    void explicitChecksWorkWithoutServiceDiscovery() {
        var manager = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                           .discoverServices(false)
                                                           .discoverServicesFromServiceLoader(false)
                                                           .build());
        registries.add(manager);
        try (MessagingGraph graph = builder(manager).build()) {
            assertHealth(graph.healthChecks(), DOWN, UP);
            graph.start();
            assertHealth(graph.healthChecks(), UP, UP);
        }
    }

    private static MessagingConfig.Builder builder(ServiceRegistryManager registry) {
        return MessagingGraph.builder()
                .serviceRegistry(registry.registry())
                .enableMetrics(false)
                .shutdownTimeout(Duration.ofSeconds(1));
    }

    private static MessagingGraph incomingGraph(ServiceRegistryManager registry, TestIncoming source) {
        MessagingChannel<String> channel = MessagingChannel.create("incoming", String.class);
        return builder(registry).channel(channel)
                .incomingChannel(channel, source)
                .payloadSink(channel, _ -> { })
                .build();
    }

    private static List<HealthCheck> aggregate(ServiceRegistryManager manager) {
        List<HealthCheck> checks = manager.registry().all(HealthCheckProvider.class)
                .stream()
                .flatMap(provider -> provider.healthChecks(Config.empty()).stream())
                .filter(check -> check.name().equals("messaging-ready") || check.name().equals("messaging-live"))
                .toList();
        assertThat(checks.size(), is(2));
        return checks;
    }

    private static void assertHealth(List<HealthCheck> checks,
                                     HealthCheckResponse.Status readiness,
                                     HealthCheckResponse.Status liveness) {
        assertThat(status(checks, READINESS), is(readiness));
        assertThat(status(checks, LIVENESS), is(liveness));
    }

    private static HealthCheckResponse.Status status(List<HealthCheck> checks, HealthCheckType type) {
        return checks.stream().filter(check -> check.type() == type).findFirst().orElseThrow().call().status();
    }

    private static void awaitStatus(List<HealthCheck> checks,
                                    HealthCheckType type,
                                    HealthCheckResponse.Status expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (status(checks, type) != expected && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertThat(status(checks, type), is(expected));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat("Timed out waiting for test coordination", latch.await(5, TimeUnit.SECONDS), is(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    latch.await();
                    return;
                } catch (InterruptedException _) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static CompletableFuture<Void> runAsync(Runnable action) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> {
            try {
                action.run();
                result.complete(null);
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    private ServiceRegistryManager registry() {
        var result = ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                          .putContractInstance(Config.class, Config.empty())
                                                          .build());
        registries.add(result);
        return result;
    }

    private static final class TestIncoming implements IncomingChannel {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch running = new CountDownLatch(1);
        private final CountDownLatch stop = new CountDownLatch(1);
        private final CountDownLatch ready;
        private final RuntimeException startupFailure;
        private final RuntimeException runtimeFailure;

        private TestIncoming(boolean blockStartup, RuntimeException startupFailure, RuntimeException runtimeFailure) {
            this.ready = new CountDownLatch(blockStartup ? 1 : 0);
            this.startupFailure = startupFailure;
            this.runtimeFailure = runtimeFailure;
        }

        @Override
        public void run(IncomingConnectorContext context) {
            entered.countDown();
            await(ready);
            if (startupFailure != null) {
                throw startupFailure;
            }
            if (context.awaitRunning()) {
                running.countDown();
                await(stop);
                if (runtimeFailure != null) {
                    throw runtimeFailure;
                }
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
