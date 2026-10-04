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

package io.helidon.metrics.providers.micrometer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.metrics.HttpTransportMetrics;
import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.service.registry.Services;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.http.HttpTransportObserver.ConnectionOutcome.NORMAL;
import static io.helidon.http.HttpTransportObserver.Direction.BIDIRECTIONAL;
import static io.helidon.http.HttpTransportObserver.Handshake.NONE;
import static io.helidon.http.HttpTransportObserver.Initiator.REMOTE;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_1_1;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_2;
import static io.helidon.http.HttpTransportObserver.Role.SERVER;
import static io.helidon.http.HttpTransportObserver.StreamOutcome.COMPLETED;
import static io.helidon.http.HttpTransportObserver.TRANSPORT_TCP;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

class TestMicrometerHttpTransportMetrics {
    private static final int MAX_PROVIDER_ACTIONS_PER_WAVE = 1024;
    private static final long TIMEOUT_SECONDS = 5;

    @BeforeAll
    static void initializeMetricsProvider() {
        Services.get(MeterRegistry.class);
    }

    @Test
    void explicitWrappersShareNativeMetersUntilFinalOwnerReleasesThem() throws Exception {
        MeterRegistry owningRegistry = createRegistry();
        TestRegistry firstRegistry = new TestRegistry(owningRegistry);
        TestRegistry secondRegistry = new TestRegistry(owningRegistry);
        HttpTransportMetrics.Lease first = HttpTransportMetrics.acquire(firstRegistry);
        HttpTransportMetrics.Lease second = HttpTransportMetrics.acquire(secondRegistry);
        ConnectionObservation firstConnection = null;
        ConnectionObservation secondConnection = null;
        StreamObservation firstStream = null;
        StreamObservation secondStream = null;
        try {
            Object nativeIdentity = owningRegistry.unwrap(Object.class);
            assertThat(firstRegistry.unwrap(Object.class), sameInstance(nativeIdentity));
            assertThat(secondRegistry.unwrap(Object.class), sameInstance(nativeIdentity));

            firstConnection = first.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
            firstConnection.protocolSelected(PROTOCOL_HTTP_1_1);
            firstStream = firstConnection.streamOpened(BIDIRECTIONAL, REMOTE);
            secondConnection = second.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
            secondConnection.protocolSelected(PROTOCOL_HTTP_1_1);
            secondStream = secondConnection.streamOpened(BIDIRECTIONAL, REMOTE);
            synchronize(firstRegistry, first);

            Counter helidonCounter = owningRegistry.counter("helidon.http.connections.opened", openedTags()).orElseThrow();
            io.micrometer.core.instrument.MeterRegistry micrometerRegistry =
                    (io.micrometer.core.instrument.MeterRegistry) nativeIdentity;
            io.micrometer.core.instrument.Counter micrometerCounter =
                    micrometerRegistry.find("helidon.http.connections.opened")
                            .tags("role", "server", "transport", "tcp", "handshake", "none")
                            .counter();

            assertThat(helidonCounter.unwrap(Object.class), sameInstance(micrometerCounter));
            assertThat(micrometerCounter.count(), is(2.0));
            assertThat(micrometerRegistry.find("helidon.http.connections.active")
                               .tags("role", "server", "transport", "tcp", "protocol", "http/1.1")
                               .gauge()
                               .value(),
                       is(2.0));
            assertGauge(micrometerRegistry, "helidon.http.streams.active", 2,
                        "role", "server", "protocol", "http/1.1", "direction", "bidi", "initiator", "remote");

            firstStream.close(COMPLETED);
            firstConnection.close(NORMAL);
            synchronize(secondRegistry, second);
            assertThat(micrometerRegistry.find("helidon.http.connections.active")
                               .tags("role", "server", "transport", "tcp", "protocol", "http/1.1")
                               .gauge()
                               .value(),
                       is(1.0));
            assertGauge(micrometerRegistry, "helidon.http.streams.active", 1,
                        "role", "server", "protocol", "http/1.1", "direction", "bidi", "initiator", "remote");

            first.close();
            assertThat(first.completion().toCompletableFuture().isDone(), is(false));
            assertThat(micrometerRegistry.find("helidon.http.connections.opened")
                               .tags("role", "server", "transport", "tcp", "handshake", "none")
                               .counter(),
                       sameInstance(micrometerCounter));

            secondStream.close(COMPLETED);
            secondConnection.close(NORMAL);
            second.close();
            awaitCompletion(first);
            awaitCompletion(second);

            assertThat(micrometerRegistry.find("helidon.http.connections.opened")
                               .tags("role", "server", "transport", "tcp", "handshake", "none")
                               .counter(),
                       nullValue());
            assertThat(micrometerRegistry.find("helidon.http.connections.active")
                               .tags("role", "server", "transport", "tcp", "protocol", "http/1.1")
                               .gauge(),
                       nullValue());
            assertThat(micrometerRegistry.find("helidon.http.streams.active")
                               .tags("role", "server", "protocol", "http/1.1", "direction", "bidi", "initiator", "remote")
                               .gauge(),
                       nullValue());
            assertThat(firstRegistry.closeCount(), is(0));
            assertThat(secondRegistry.closeCount(), is(0));
        } finally {
            firstRegistry.releaseBarrier();
            secondRegistry.releaseBarrier();
            if (firstStream != null) {
                firstStream.close(COMPLETED);
            }
            if (secondStream != null) {
                secondStream.close(COMPLETED);
            }
            if (firstConnection != null) {
                firstConnection.close(NORMAL);
            }
            if (secondConnection != null) {
                secondConnection.close(NORMAL);
            }
            first.close();
            second.close();
            try {
                awaitCompletion(first);
                awaitCompletion(second);
            } finally {
                owningRegistry.close();
            }
        }
    }

    @Test
    void differentlyTaggedWrappersKeepConnectionGaugesIsolated() throws Exception {
        assertDifferentlyTaggedGauges(false);
    }

    @Test
    void differentlyTaggedWrappersKeepStreamGaugesIsolated() throws Exception {
        assertDifferentlyTaggedGauges(true);
    }

    @Test
    void differentlyTaggedGaugesAreRemovedAndReacquiredAfterFinalRelease() throws Exception {
        assertTaggedGaugeReacquisition(false);
    }

    @Test
    void differentlyTaggedGaugesAreReacquiredAfterRemovalFailure() throws Exception {
        assertTaggedGaugeReacquisition(true);
    }

    @Test
    void connectionGaugeRecoversBackingAfterRegistrationThrows() throws Exception {
        assertGaugeRegistrationRecovery(false);
    }

    @Test
    void streamGaugeRecoversBackingAfterRegistrationThrows() throws Exception {
        assertGaugeRegistrationRecovery(true);
    }

    @Test
    void tagRejectedWrapperDoesNotContributeWhenOpenedFirst() throws Exception {
        assertTagFilteredWrappers(true);
    }

    @Test
    void tagRejectedWrapperDoesNotContributeWhenOpenedLast() throws Exception {
        assertTagFilteredWrappers(false);
    }

    @Test
    void observationsCanCloseBeforeTagSelectionCompletes() throws Exception {
        MeterRegistry owningRegistry = createRegistry();
        TestRegistry enabledRegistry = new TestRegistry(owningRegistry);
        TestRegistry rejectedRegistry = new TestRegistry(owningRegistry, _ -> true,
                                                        tags -> !"server".equals(tags.get("role")));
        HttpTransportMetrics.Lease enabled = HttpTransportMetrics.acquire(enabledRegistry);
        HttpTransportMetrics.Lease rejected = HttpTransportMetrics.acquire(rejectedRegistry);
        ProviderBarrier barrier = enabledRegistry.blockNextRegistration();
        ConnectionObservation barrierConnection = enabled.connectionOpened(SERVER, barrier.transport(), NONE);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                barrier.awaitEntered();
                executor.submit(() -> {
                    for (HttpTransportMetrics.Lease lease : List.of(enabled, rejected)) {
                        ConnectionObservation connection = lease.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
                        try {
                            connection.protocolSelected(PROTOCOL_HTTP_1_1);
                            var stream = connection.streamOpened(BIDIRECTIONAL, REMOTE);
                            connection.protocolSelected(PROTOCOL_HTTP_2);
                            stream.close(COMPLETED);
                        } finally {
                            connection.close(NORMAL);
                        }
                    }
                }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

                barrier.release();
                synchronize(enabledRegistry, enabled);
                io.micrometer.core.instrument.MeterRegistry nativeRegistry =
                        owningRegistry.unwrap(io.micrometer.core.instrument.MeterRegistry.class);
                for (String protocol : List.of("unknown", PROTOCOL_HTTP_1_1, PROTOCOL_HTTP_2)) {
                    assertGauge(nativeRegistry, "helidon.http.connections.active", 0,
                                "role", "server", "transport", "tcp", "protocol", protocol);
                }
                assertGauge(nativeRegistry, "helidon.http.streams.active", 0,
                            "role", "server", "protocol", "http/1.1", "direction", "bidi", "initiator", "remote");
                assertCounter(nativeRegistry, "helidon.http.connections.opened", 1,
                              "role", "server", "transport", "tcp", "handshake", "none");
                assertCounter(nativeRegistry, "helidon.http.connections.closed", 1,
                              "role", "server", "transport", "tcp", "protocol", "http/2", "outcome", "normal");
                assertCounter(nativeRegistry, "helidon.http.streams.opened", 1,
                              "role", "server", "protocol", "http/1.1", "direction", "bidi", "initiator", "remote");
                assertCounter(nativeRegistry, "helidon.http.streams.closed", 1,
                              "role", "server", "protocol", "http/1.1", "direction", "bidi", "initiator", "remote",
                              "outcome", "completed");
            } finally {
                barrier.release();
                enabledRegistry.releaseBarrier();
                barrierConnection.close(NORMAL);
                enabled.close();
                rejected.close();
                awaitCompletion(enabled);
                awaitCompletion(rejected);
                owningRegistry.close();
            }
        }
    }

    @Test
    void independentlyCreatedRegistriesRemainIsolatedAndCallerOwned() throws Exception {
        MeterRegistry firstOwningRegistry = createRegistry();
        MeterRegistry secondOwningRegistry = createRegistry();
        TestRegistry firstRegistry = new TestRegistry(firstOwningRegistry);
        TestRegistry secondRegistry = new TestRegistry(secondOwningRegistry);
        HttpTransportMetrics.Lease first = HttpTransportMetrics.acquire(firstRegistry);
        HttpTransportMetrics.Lease second = HttpTransportMetrics.acquire(secondRegistry);
        try {
            Object firstNativeIdentity = firstOwningRegistry.unwrap(Object.class);
            Object secondNativeIdentity = secondOwningRegistry.unwrap(Object.class);
            assertThat(firstNativeIdentity, not(sameInstance(secondNativeIdentity)));

            first.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);
            second.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);
            synchronize(firstRegistry, first);
            synchronize(secondRegistry, second);

            assertThat(firstOwningRegistry.counter("helidon.http.connections.opened", openedTags()).orElseThrow().count(),
                       is(1L));
            assertThat(secondOwningRegistry.counter("helidon.http.connections.opened", openedTags()).orElseThrow().count(),
                       is(1L));

            io.micrometer.core.instrument.MeterRegistry firstMicrometerRegistry =
                    (io.micrometer.core.instrument.MeterRegistry) firstNativeIdentity;
            io.micrometer.core.instrument.MeterRegistry secondMicrometerRegistry =
                    (io.micrometer.core.instrument.MeterRegistry) secondNativeIdentity;
            assertThat(firstMicrometerRegistry.find("helidon.http.connections.opened")
                               .tags("role", "server", "transport", "tcp", "handshake", "none")
                               .counter()
                               .count(),
                       is(1.0));
            assertThat(secondMicrometerRegistry.find("helidon.http.connections.opened")
                               .tags("role", "server", "transport", "tcp", "handshake", "none")
                               .counter()
                               .count(),
                       is(1.0));

            first.close();
            awaitCompletion(first);
            assertThat(firstMicrometerRegistry.find("helidon.http.connections.opened")
                               .tags("role", "server", "transport", "tcp", "handshake", "none")
                               .counter(),
                       nullValue());
            assertThat(secondMicrometerRegistry.find("helidon.http.connections.opened")
                               .tags("role", "server", "transport", "tcp", "handshake", "none")
                               .counter(),
                       not(nullValue()));
            assertThat(firstRegistry.closeCount(), is(0));
            assertThat(secondRegistry.closeCount(), is(0));

            second.close();
            awaitCompletion(second);
            assertThat(secondMicrometerRegistry.find("helidon.http.connections.opened")
                               .tags("role", "server", "transport", "tcp", "handshake", "none")
                               .counter(),
                       nullValue());
        } finally {
            firstRegistry.releaseBarrier();
            secondRegistry.releaseBarrier();
            closeAndAwait(first);
            closeAndAwait(second);
            firstOwningRegistry.close();
            secondOwningRegistry.close();
        }
    }

    @Test
    void admissionBudgetResetsOnlyAfterOutstandingCleanupCompletes() throws Exception {
        MeterRegistry owningRegistry = createRegistry();
        TestRegistry registry = new TestRegistry(owningRegistry, "helidon.http.connections.opened"::equals);
        HttpTransportMetrics.Lease first = HttpTransportMetrics.acquire(registry);
        AtomicReference<HttpTransportMetrics.Lease> second = new AtomicReference<>();
        CountDownLatch removalEntered = new CountDownLatch(1);
        CountDownLatch allowRemoval = new CountDownLatch(1);
        AtomicReference<Thread> cleanupThread = new AtomicReference<>();
        AtomicLong recordedConnections = new AtomicLong();
        List<ConnectionObservation> connections = new ArrayList<>();
        ProviderBarrier barrier = registry.blockNextRegistration();
        owningRegistry.onMeterRemoved(meter -> {
            if (cleanupThread.compareAndSet(null, Thread.currentThread())) {
                recordedConnections.set(((Counter) meter).count());
                removalEntered.countDown();
                await(allowRemoval);
            }
        });

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                connections.add(first.connectionOpened(SERVER, barrier.transport(), NONE));
                barrier.awaitEntered();
                for (int i = 1; i < MAX_PROVIDER_ACTIONS_PER_WAVE; i++) {
                    connections.add(first.connectionOpened(SERVER, barrier.transport(), NONE));
                }
                connections.forEach(connection -> connection.close(NORMAL));
                first.close();
                barrier.release();
                await(removalEntered);
                assertThat("Accepted provider actions should finish before cleanup",
                           recordedConnections.get(),
                           is((long) MAX_PROVIDER_ACTIONS_PER_WAVE));

                executor.submit(() -> second.set(HttpTransportMetrics.acquire(registry)))
                        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                connections.add(executor.submit(() -> second.get().connectionOpened(SERVER, "during-cleanup", NONE))
                                        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                assertThat("Lease completion should await provider cleanup",
                           first.completion().toCompletableFuture().isDone(),
                           is(false));

                allowRemoval.countDown();
                Thread thread = cleanupThread.get();
                thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
                assertThat("Cleanup provider wave did not become idle", thread.isAlive(), is(false));
                awaitCompletion(first);

                io.micrometer.core.instrument.MeterRegistry nativeRegistry =
                        owningRegistry.unwrap(io.micrometer.core.instrument.MeterRegistry.class);
                assertThat("Outstanding cleanup must retain the exhausted admission budget",
                           nativeRegistry.find("helidon.http.connections.opened")
                                   .tag("transport", "during-cleanup")
                                   .counter(),
                           nullValue());

                connections.add(second.get().connectionOpened(SERVER, "after-cleanup", NONE));
                synchronize(registry, second.get());
                io.micrometer.core.instrument.Counter counter = nativeRegistry.find("helidon.http.connections.opened")
                        .tag("transport", "after-cleanup")
                        .counter();
                assertThat("Admission budget should reset after all provider and control work completes",
                           counter,
                           not(nullValue()));
                assertThat("First connection in the new provider wave should be recorded", counter.count(), is(1.0));
            } finally {
                barrier.release();
                registry.releaseBarrier();
                allowRemoval.countDown();
                connections.forEach(connection -> connection.close(NORMAL));
                closeAndAwait(first);
                if (second.get() != null) {
                    closeAndAwait(second.get());
                }
                owningRegistry.close();
            }
        }
    }

    @Test
    void firstProtocolGaugeRegistrationSurvivesRepeatedIdleTransitions() throws Exception {
        MeterRegistry owningRegistry = createRegistry();
        TestRegistry registry = new TestRegistry(owningRegistry);
        HttpTransportMetrics.Lease lease = HttpTransportMetrics.acquire(registry);
        ConnectionObservation connection = lease.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
        try {
            io.micrometer.core.instrument.MeterRegistry nativeRegistry =
                    owningRegistry.unwrap(io.micrometer.core.instrument.MeterRegistry.class);
            synchronize(registry, lease).awaitIdle();
            for (int i = 0; i < 16; i++) {
                String protocol = "idle-protocol-" + i;
                connection.protocolSelected(protocol);
                connection.streamOpened(BIDIRECTIONAL, REMOTE).close(COMPLETED);
                synchronize(registry, lease).awaitIdle();
                io.micrometer.core.instrument.Gauge gauge = nativeRegistry.find("helidon.http.connections.active")
                        .tags("role", "server", "transport", "tcp", "protocol", protocol)
                        .gauge();
                assertThat("First gauge registration after idle for " + protocol, gauge, not(nullValue()));
                assertThat("Active connection gauge after idle for " + protocol, gauge.value(), is(1.0));
                io.micrometer.core.instrument.Gauge streamGauge = nativeRegistry.find("helidon.http.streams.active")
                        .tags("role", "server", "protocol", protocol, "direction", "bidi", "initiator", "remote")
                        .gauge();
                assertThat("First stream gauge registration after idle for " + protocol, streamGauge, not(nullValue()));
                assertThat("Completed stream gauge after idle for " + protocol, streamGauge.value(), is(0.0));
            }
        } finally {
            registry.releaseBarrier();
            connection.close(NORMAL);
            closeAndAwait(lease);
            owningRegistry.close();
        }
    }

    @Test
    void streamDurationOmitsPercentilesByDefault() throws Exception {
        assertStreamDurationStatistics(MetricsConfig.create(), List.of());
    }

    @Test
    void streamDurationPercentilesCanBeEnabledProgrammatically() throws Exception {
        MetricsConfig config = MetricsConfig.builder()
                .addMeter(meter -> meter.namePattern(Pattern.compile("helidon\\.http\\.streams\\.duration"))
                        .percentiles(List.of(0.5, 0.99)))
                .build();
        assertStreamDurationStatistics(config, List.of(0.5, 0.99));
    }

    @Test
    void streamDurationPercentilesCanBeEnabledUsingConfig() throws Exception {
        String configText = """
                metrics:
                  meters:
                    - name-pattern: 'helidon\\.http\\.streams\\.duration'
                      percentiles: [0.5, 0.99]
                """;
        MetricsConfig config = MetricsConfig.create(Config.just(configText, MediaTypes.APPLICATION_YAML).get("metrics"));
        assertStreamDurationStatistics(config, List.of(0.5, 0.99));
    }

    private static void assertGaugeRegistrationRecovery(boolean streams) throws Exception {
        MeterRegistry owningRegistry = createRegistry();
        TestRegistry firstRegistry = new TestRegistry(owningRegistry);
        TestRegistry secondRegistry = new TestRegistry(owningRegistry);
        String gaugeName = streams ? "helidon.http.streams.active" : "helidon.http.connections.active";
        firstRegistry.failGaugeRegistrationName.set(gaugeName);
        HttpTransportMetrics.Lease first = HttpTransportMetrics.acquire(firstRegistry);
        HttpTransportMetrics.Lease second = HttpTransportMetrics.acquire(secondRegistry);
        List<ConnectionObservation> connections = new ArrayList<>();
        List<StreamObservation> openedStreams = new ArrayList<>();
        try {
            assertThat("Wrappers share the native registry", firstRegistry.unwrap(Object.class),
                       sameInstance(secondRegistry.unwrap(Object.class)));
            ConnectionObservation firstConnection = first.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
            connections.add(firstConnection);
            firstConnection.protocolSelected(PROTOCOL_HTTP_1_1);
            if (streams) {
                openedStreams.add(firstConnection.streamOpened(BIDIRECTIONAL, REMOTE));
            }
            synchronize(secondRegistry, second).awaitIdle();
            assertThat("Failure after native gauge registration was injected",
                       firstRegistry.failedGaugeRegistrations.get(), is(1));
            io.micrometer.core.instrument.MeterRegistry nativeRegistry =
                    owningRegistry.unwrap(io.micrometer.core.instrument.MeterRegistry.class);
            Gauge gauge = streams
                    ? nativeRegistry.get(gaugeName)
                            .tags("role", "server", "protocol", "http/1.1", "direction", "bidi", "initiator", "remote").gauge()
                    : nativeRegistry.get(gaugeName)
                            .tags("role", "server", "transport", "tcp", "protocol", "http/1.1").gauge();
            assertThat("Failed registration does not contribute", gauge.value(), is(0.0));

            ConnectionObservation secondConnection = second.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
            connections.add(secondConnection);
            secondConnection.protocolSelected(PROTOCOL_HTTP_1_1);
            if (streams) {
                openedStreams.add(secondConnection.streamOpened(BIDIRECTIONAL, REMOTE));
            }
            synchronize(secondRegistry, second).awaitIdle();
            assertThat("Successful wrapper contributes through the native gauge's existing backing", gauge.value(), is(1.0));

            if (!streams) {
                ConnectionObservation retryConnection = first.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
                connections.add(retryConnection);
                retryConnection.protocolSelected(PROTOCOL_HTTP_1_1);
                synchronize(secondRegistry, second).awaitIdle();
                assertThat("Retry attaches the failed wrapper's outstanding observations", gauge.value(), is(3.0));
                retryConnection.close(NORMAL);
                synchronize(secondRegistry, second).awaitIdle();
                assertThat("Closing the retry removes one contribution", gauge.value(), is(2.0));
            }
            if (streams) {
                openedStreams.getFirst().close(COMPLETED);
            }
            firstConnection.close(NORMAL);
            synchronize(secondRegistry, second).awaitIdle();
            assertThat("Closing the first wrapper preserves the second", gauge.value(), is(1.0));
            if (streams) {
                openedStreams.get(1).close(COMPLETED);
            }
            secondConnection.close(NORMAL);
            synchronize(secondRegistry, second).awaitIdle();
            assertThat("All observations closed", gauge.value(), is(0.0));
            first.close();
            second.close();
            awaitCompletion(first);
            awaitCompletion(second);
            assertThat("Native gauge removed after final release", nativeRegistry.find(gaugeName).gauge(), nullValue());
        } finally {
            firstRegistry.releaseBarrier();
            secondRegistry.releaseBarrier();
            firstRegistry.failGaugeRegistrationName.set(null);
            openedStreams.forEach(stream -> stream.close(COMPLETED));
            connections.forEach(connection -> connection.close(NORMAL));
            first.close();
            second.close();
            try {
                awaitCompletion(first);
                awaitCompletion(second);
            } finally {
                owningRegistry.close();
            }
        }
    }

    private static void assertTaggedGaugeReacquisition(boolean removalFails) throws Exception {
        MeterRegistry owningRegistry = createRegistry();
        TestRegistry firstRegistry = new TestRegistry(owningRegistry, "first");
        TestRegistry secondRegistry = new TestRegistry(owningRegistry, "second");
        HttpTransportMetrics.Lease first = HttpTransportMetrics.acquire(firstRegistry);
        HttpTransportMetrics.Lease second = HttpTransportMetrics.acquire(secondRegistry);
        ConnectionObservation firstConnection = null;
        ConnectionObservation secondConnection = null;
        StreamObservation firstStream = null;
        StreamObservation secondStream = null;
        try {
            io.micrometer.core.instrument.MeterRegistry nativeRegistry =
                    owningRegistry.unwrap(io.micrometer.core.instrument.MeterRegistry.class);
            firstConnection = first.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
            firstConnection.protocolSelected(PROTOCOL_HTTP_1_1);
            firstConnection.streamOpened(BIDIRECTIONAL, REMOTE).close(COMPLETED);
            secondConnection = second.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
            secondConnection.protocolSelected(PROTOCOL_HTTP_1_1);
            secondConnection.streamOpened(BIDIRECTIONAL, REMOTE).close(COMPLETED);
            synchronize(firstRegistry, first);
            Gauge previousFirst = activeGauge(nativeRegistry, "first", false);
            Gauge previousSecond = activeGauge(nativeRegistry, "second", false);
            Gauge previousFirstStream = activeGauge(nativeRegistry, "first", true);
            Gauge previousSecondStream = activeGauge(nativeRegistry, "second", true);

            firstConnection.close(NORMAL);
            secondConnection.close(NORMAL);
            firstRegistry.failGaugeRemoval.set(removalFails);
            secondRegistry.failGaugeRemoval.set(removalFails);
            first.close();
            second.close();
            awaitCompletion(first);
            awaitCompletion(second);
            if (removalFails) {
                assertThat("First connection gauge retained", activeGauge(nativeRegistry, "first", false),
                           sameInstance(previousFirst));
                assertThat("Second connection gauge retained", activeGauge(nativeRegistry, "second", false),
                           sameInstance(previousSecond));
                assertThat("First stream gauge retained", activeGauge(nativeRegistry, "first", true),
                           sameInstance(previousFirstStream));
                assertThat("Second stream gauge retained", activeGauge(nativeRegistry, "second", true),
                           sameInstance(previousSecondStream));
            } else {
                assertTaggedGaugesRemoved(nativeRegistry);
            }
            firstRegistry.failGaugeRemoval.set(false);
            secondRegistry.failGaugeRemoval.set(false);

            first = HttpTransportMetrics.acquire(firstRegistry);
            second = HttpTransportMetrics.acquire(secondRegistry);
            firstConnection = first.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
            firstConnection.protocolSelected(PROTOCOL_HTTP_1_1);
            firstStream = firstConnection.streamOpened(BIDIRECTIONAL, REMOTE);
            secondConnection = second.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
            secondConnection.protocolSelected(PROTOCOL_HTTP_1_1);
            secondStream = secondConnection.streamOpened(BIDIRECTIONAL, REMOTE);
            synchronize(firstRegistry, first);
            if (removalFails) {
                assertThat("Reacquired first connection gauge", activeGauge(nativeRegistry, "first", false),
                           sameInstance(previousFirst));
                assertThat("Reacquired second connection gauge", activeGauge(nativeRegistry, "second", false),
                           sameInstance(previousSecond));
                assertThat("Reacquired first stream gauge", activeGauge(nativeRegistry, "first", true),
                           sameInstance(previousFirstStream));
                assertThat("Reacquired second stream gauge", activeGauge(nativeRegistry, "second", true),
                           sameInstance(previousSecondStream));
            } else {
                assertThat("Reacquired first connection gauge", activeGauge(nativeRegistry, "first", false),
                           not(sameInstance(previousFirst)));
                assertThat("Reacquired second connection gauge", activeGauge(nativeRegistry, "second", false),
                           not(sameInstance(previousSecond)));
                assertThat("Reacquired first stream gauge", activeGauge(nativeRegistry, "first", true),
                           not(sameInstance(previousFirstStream)));
                assertThat("Reacquired second stream gauge", activeGauge(nativeRegistry, "second", true),
                           not(sameInstance(previousSecondStream)));
            }
            for (String owner : List.of("first", "second")) {
                assertThat("Reacquired connection count for " + owner, activeGauge(nativeRegistry, owner, false).value(), is(1.0));
                assertThat("Reacquired stream count for " + owner, activeGauge(nativeRegistry, owner, true).value(), is(1.0));
            }
            firstStream.close(COMPLETED);
            firstConnection.close(NORMAL);
            synchronize(secondRegistry, second);
            assertThat("Closed first reacquired connection", activeGauge(nativeRegistry, "first", false).value(), is(0.0));
            assertThat("Second reacquired connection remains active", activeGauge(nativeRegistry, "second", false).value(), is(1.0));
            assertThat("Closed first reacquired stream", activeGauge(nativeRegistry, "first", true).value(), is(0.0));
            assertThat("Second reacquired stream remains active", activeGauge(nativeRegistry, "second", true).value(), is(1.0));
            secondStream.close(COMPLETED);
            secondConnection.close(NORMAL);
            first.close();
            second.close();
            awaitCompletion(first);
            awaitCompletion(second);
            assertTaggedGaugesRemoved(nativeRegistry);
        } finally {
            firstRegistry.releaseBarrier();
            secondRegistry.releaseBarrier();
            firstRegistry.failGaugeRemoval.set(false);
            secondRegistry.failGaugeRemoval.set(false);
            if (firstStream != null) {
                firstStream.close(COMPLETED);
            }
            if (secondStream != null) {
                secondStream.close(COMPLETED);
            }
            if (firstConnection != null) {
                firstConnection.close(NORMAL);
            }
            if (secondConnection != null) {
                secondConnection.close(NORMAL);
            }
            first.close();
            second.close();
            try {
                awaitCompletion(first);
                awaitCompletion(second);
            } finally {
                owningRegistry.close();
            }
        }
    }

    private static void assertTaggedGaugesRemoved(io.micrometer.core.instrument.MeterRegistry registry) {
        for (String owner : List.of("first", "second")) {
            assertThat("Connection gauge removed for " + owner,
                       registry.find("helidon.http.connections.active").tag("owner", owner).gauge(),
                       nullValue());
            assertThat("Stream gauge removed for " + owner,
                       registry.find("helidon.http.streams.active").tag("owner", owner).gauge(),
                       nullValue());
        }
    }

    private static void assertDifferentlyTaggedGauges(boolean streams) throws Exception {
        MeterRegistry owningRegistry = createRegistry();
        TestRegistry firstRegistry = new TestRegistry(owningRegistry, "first");
        TestRegistry secondRegistry = new TestRegistry(owningRegistry, "second");
        HttpTransportMetrics.Lease first = HttpTransportMetrics.acquire(firstRegistry);
        HttpTransportMetrics.Lease second = HttpTransportMetrics.acquire(secondRegistry);
        ConnectionObservation firstConnection = null;
        ConnectionObservation secondConnection = null;
        StreamObservation firstStream = null;
        StreamObservation secondStream = null;
        try {
            assertThat("Wrappers share their native registry",
                       firstRegistry.unwrap(Object.class), sameInstance(secondRegistry.unwrap(Object.class)));
            firstConnection = first.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
            firstConnection.protocolSelected(PROTOCOL_HTTP_1_1);
            firstStream = firstConnection.streamOpened(BIDIRECTIONAL, REMOTE);
            secondConnection = second.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
            secondConnection.protocolSelected(PROTOCOL_HTTP_1_1);
            secondStream = secondConnection.streamOpened(BIDIRECTIONAL, REMOTE);
            synchronize(firstRegistry, first);

            io.micrometer.core.instrument.MeterRegistry nativeRegistry =
                    owningRegistry.unwrap(io.micrometer.core.instrument.MeterRegistry.class);
            Gauge firstGauge = activeGauge(nativeRegistry, "first", streams);
            Gauge secondGauge = activeGauge(nativeRegistry, "second", streams);
            assertThat("Different tags create distinct native gauges", firstGauge, not(sameInstance(secondGauge)));
            assertThat("First wrapper's active count", firstGauge.value(), is(1.0));
            assertThat("Second wrapper's active count", secondGauge.value(), is(1.0));

            firstStream.close(COMPLETED);
            firstConnection.close(NORMAL);
            synchronize(secondRegistry, second);
            assertThat("Closing first observation clears only its gauge", firstGauge.value(), is(0.0));
            assertThat("Second observation remains active", secondGauge.value(), is(1.0));

            secondStream.close(COMPLETED);
            secondConnection.close(NORMAL);
            synchronize(secondRegistry, second);
            assertThat("First gauge remains empty", firstGauge.value(), is(0.0));
            assertThat("Closing second observation clears its gauge", secondGauge.value(), is(0.0));
        } finally {
            firstRegistry.releaseBarrier();
            secondRegistry.releaseBarrier();
            if (firstStream != null) {
                firstStream.close(COMPLETED);
            }
            if (secondStream != null) {
                secondStream.close(COMPLETED);
            }
            if (firstConnection != null) {
                firstConnection.close(NORMAL);
            }
            if (secondConnection != null) {
                secondConnection.close(NORMAL);
            }
            first.close();
            second.close();
            try {
                awaitCompletion(first);
                awaitCompletion(second);
            } finally {
                owningRegistry.close();
            }
        }
    }

    private static Gauge activeGauge(io.micrometer.core.instrument.MeterRegistry registry, String owner, boolean streams) {
        if (streams) {
            return registry.get("helidon.http.streams.active")
                    .tags("role", "server", "protocol", "http/1.1", "direction", "bidi", "initiator", "remote", "owner", owner)
                    .gauge();
        }
        return registry.get("helidon.http.connections.active")
                .tags("role", "server", "transport", "tcp", "protocol", "http/1.1", "owner", owner)
                .gauge();
    }

    private static void assertTagFilteredWrappers(boolean rejectedFirst) throws Exception {
        MeterRegistry owningRegistry = createRegistry();
        TestRegistry enabledRegistry = new TestRegistry(owningRegistry);
        TestRegistry rejectedRegistry = new TestRegistry(owningRegistry, _ -> true,
                                                        tags -> !"server".equals(tags.get("role")));
        HttpTransportMetrics.Lease enabled = HttpTransportMetrics.acquire(enabledRegistry);
        HttpTransportMetrics.Lease rejected = HttpTransportMetrics.acquire(rejectedRegistry);
        HttpTransportMetrics.Lease first = rejectedFirst ? rejected : enabled;
        HttpTransportMetrics.Lease second = rejectedFirst ? enabled : rejected;
        ConnectionObservation firstConnection = null;
        ConnectionObservation secondConnection = null;
        try {
            assertThat(rejectedRegistry.isMeterEnabled("helidon.http.connections.active"), is(true));
            assertThat(rejectedRegistry.isMeterEnabled("helidon.http.streams.active"), is(true));
            assertThat(enabledRegistry.unwrap(Object.class), sameInstance(rejectedRegistry.unwrap(Object.class)));
            firstConnection = first.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
            firstConnection.protocolSelected(PROTOCOL_HTTP_1_1);
            var firstStream = firstConnection.streamOpened(BIDIRECTIONAL, REMOTE);
            synchronize(enabledRegistry, enabled);
            secondConnection = second.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
            secondConnection.protocolSelected(PROTOCOL_HTTP_1_1);
            var secondStream = secondConnection.streamOpened(BIDIRECTIONAL, REMOTE);
            synchronize(enabledRegistry, enabled);

            io.micrometer.core.instrument.MeterRegistry nativeRegistry =
                    owningRegistry.unwrap(io.micrometer.core.instrument.MeterRegistry.class);
            assertCounter(nativeRegistry, "helidon.http.connections.opened", 1,
                          "role", "server", "transport", "tcp", "handshake", "none");
            assertCounter(nativeRegistry, "helidon.http.streams.opened", 1,
                          "role", "server", "protocol", "http/1.1", "direction", "bidi", "initiator", "remote");
            assertGauge(nativeRegistry, "helidon.http.connections.active", 1,
                        "role", "server", "transport", "tcp", "protocol", "http/1.1");
            assertGauge(nativeRegistry, "helidon.http.streams.active", 1,
                        "role", "server", "protocol", "http/1.1", "direction", "bidi", "initiator", "remote");

            firstConnection.protocolSelected(PROTOCOL_HTTP_2);
            secondConnection.protocolSelected(PROTOCOL_HTTP_2);
            synchronize(enabledRegistry, enabled);
            assertGauge(nativeRegistry, "helidon.http.connections.active", 0,
                        "role", "server", "transport", "tcp", "protocol", "http/1.1");
            assertGauge(nativeRegistry, "helidon.http.connections.active", 1,
                        "role", "server", "transport", "tcp", "protocol", "http/2");
            assertGauge(nativeRegistry, "helidon.http.streams.active", 1,
                        "role", "server", "protocol", "http/1.1", "direction", "bidi", "initiator", "remote");

            var rejectedStream = rejectedFirst ? firstStream : secondStream;
            ConnectionObservation rejectedConnection = rejectedFirst ? firstConnection : secondConnection;
            rejectedStream.close(COMPLETED);
            rejectedConnection.close(NORMAL);
            synchronize(enabledRegistry, enabled);
            assertGauge(nativeRegistry, "helidon.http.connections.active", 1,
                        "role", "server", "transport", "tcp", "protocol", "http/2");
            assertGauge(nativeRegistry, "helidon.http.streams.active", 1,
                        "role", "server", "protocol", "http/1.1", "direction", "bidi", "initiator", "remote");

            var enabledStream = rejectedFirst ? secondStream : firstStream;
            ConnectionObservation enabledConnection = rejectedFirst ? secondConnection : firstConnection;
            enabledStream.close(COMPLETED);
            enabledConnection.close(NORMAL);
            synchronize(enabledRegistry, enabled);
            assertGauge(nativeRegistry, "helidon.http.connections.active", 0,
                        "role", "server", "transport", "tcp", "protocol", "http/2");
            assertGauge(nativeRegistry, "helidon.http.streams.active", 0,
                        "role", "server", "protocol", "http/1.1", "direction", "bidi", "initiator", "remote");
            assertCounter(nativeRegistry, "helidon.http.connections.closed", 1,
                          "role", "server", "transport", "tcp", "protocol", "http/2", "outcome", "normal");
            assertCounter(nativeRegistry, "helidon.http.streams.closed", 1,
                          "role", "server", "protocol", "http/1.1", "direction", "bidi", "initiator", "remote",
                          "outcome", "completed");
        } finally {
            enabledRegistry.releaseBarrier();
            if (firstConnection != null) {
                firstConnection.close(NORMAL);
            }
            if (secondConnection != null) {
                secondConnection.close(NORMAL);
            }
            enabled.close();
            rejected.close();
            awaitCompletion(enabled);
            awaitCompletion(rejected);
            owningRegistry.close();
        }
    }

    private static void assertGauge(io.micrometer.core.instrument.MeterRegistry registry,
                                   String name,
                                   double expected,
                                   String... tags) {
        Gauge gauge = registry.get(name).tags(tags).gauge();
        assertThat(name + " " + Arrays.toString(tags), gauge.value(), is(expected));
    }

    private static void assertCounter(io.micrometer.core.instrument.MeterRegistry registry,
                                     String name,
                                     double expected,
                                     String... tags) {
        io.micrometer.core.instrument.Counter counter = registry.get(name).tags(tags).counter();
        assertThat(name + " " + Arrays.toString(tags), counter.count(), is(expected));
    }

    private static void assertStreamDurationStatistics(MetricsConfig config, List<Double> expectedPercentiles) throws Exception {
        MeterRegistry owningRegistry = metricsFactory().createMeterRegistry(config);
        TestRegistry registry = new TestRegistry(owningRegistry);
        HttpTransportMetrics.Lease lease = HttpTransportMetrics.acquire(registry);
        ConnectionObservation connection = lease.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
        try {
            connection.protocolSelected(PROTOCOL_HTTP_1_1);
            var stream = connection.streamOpened(BIDIRECTIONAL, REMOTE);
            synchronize(registry, lease);
            stream.close(COMPLETED);
            connection.close(NORMAL);
            synchronize(registry, lease);

            io.micrometer.core.instrument.MeterRegistry nativeRegistry =
                    owningRegistry.unwrap(io.micrometer.core.instrument.MeterRegistry.class);
            Timer streamTimer = nativeRegistry.get("helidon.http.streams.duration")
                    .tags("role", "server", "protocol", "http/1.1", "direction", "bidi", "initiator", "remote",
                          "outcome", "completed")
                    .timer();
            assertThat("Stream duration percentiles",
                       Arrays.stream(streamTimer.takeSnapshot().percentileValues()).map(ValueAtPercentile::percentile).toList(),
                       is(expectedPercentiles));
            assertThat("Stream duration count", streamTimer.count(), is(1L));
            double duration = streamTimer.totalTime(TimeUnit.NANOSECONDS);
            assertThat("Stream duration total", duration, greaterThan(0D));
            assertThat("Stream duration mean", streamTimer.mean(TimeUnit.NANOSECONDS), is(duration));
            assertThat("Stream duration maximum", streamTimer.max(TimeUnit.NANOSECONDS), is(duration));

            Timer connectionTimer = nativeRegistry.get("helidon.http.connections.duration")
                    .tags("role", "server", "transport", "tcp", "protocol", "http/1.1", "outcome", "normal")
                    .timer();
            assertThat("Connection duration retains default percentiles",
                       Arrays.stream(connectionTimer.takeSnapshot().percentileValues())
                               .map(ValueAtPercentile::percentile)
                               .toList(),
                       is(List.of(0.5, 0.75, 0.95, 0.98, 0.99, 0.999)));
        } finally {
            registry.releaseBarrier();
            connection.close(NORMAL);
            closeAndAwait(lease);
            owningRegistry.close();
        }
    }

    private static MeterRegistry createRegistry() {
        return metricsFactory().createMeterRegistry(MetricsConfig.create());
    }

    private static List<Tag> openedTags() {
        MetricsFactory metricsFactory = metricsFactory();
        return List.of(metricsFactory.tagCreate("role", "server"),
                       metricsFactory.tagCreate("transport", "tcp"),
                       metricsFactory.tagCreate("handshake", "none"));
    }

    private static MetricsFactory metricsFactory() {
        return Services.get(MetricsFactory.class);
    }

    private static ProviderBarrier synchronize(TestRegistry registry, HttpTransportMetrics.Lease lease) {
        ProviderBarrier barrier = registry.blockNextRegistration();
        ConnectionObservation connection = lease.connectionOpened(SERVER, barrier.transport(), NONE);
        try {
            barrier.awaitEntered();
            return barrier;
        } finally {
            connection.close(NORMAL);
            barrier.release();
        }
    }

    private static void closeAndAwait(HttpTransportMetrics.Lease lease) throws Exception {
        lease.close();
        awaitCompletion(lease);
    }

    private static void awaitCompletion(HttpTransportMetrics.Lease lease) throws Exception {
        lease.completion().toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat("Provider work should reach the deterministic barrier",
                       latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                       is(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while awaiting provider work", e);
        }
    }

    private static final class ProviderBarrier {
        private final String transport;
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);
        private volatile Thread providerThread;

        private ProviderBarrier(String transport) {
            this.transport = transport;
        }

        private String transport() {
            return transport;
        }

        private boolean matches(Meter.Builder<?, ?> builder) {
            return builder.name().equals("helidon.http.connections.opened")
                    && transport.equals(builder.tags().get("transport"));
        }

        private void enter() {
            providerThread = Thread.currentThread();
            entered.countDown();
            await(released);
        }

        private void awaitEntered() {
            await(entered);
        }

        private void release() {
            released.countDown();
        }

        private void awaitIdle() throws InterruptedException {
            providerThread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            assertThat("Provider wave did not become idle", providerThread.isAlive(), is(false));
        }
    }

    private static final class TestRegistry implements MeterRegistry {
        private final MeterRegistry delegate;
        private final Predicate<String> enabledMeters;
        private final Predicate<Map<String, String>> enabledTags;
        private final List<Tag> additionalTags;
        private final AtomicBoolean failGaugeRemoval = new AtomicBoolean();
        private final AtomicReference<String> failGaugeRegistrationName = new AtomicReference<>();
        private final AtomicInteger failedGaugeRegistrations = new AtomicInteger();
        private final AtomicInteger barrierSequence = new AtomicInteger();
        private final AtomicReference<ProviderBarrier> providerBarrier = new AtomicReference<>();
        private final AtomicInteger closeCount = new AtomicInteger();

        private TestRegistry(MeterRegistry delegate) {
            this(delegate, _ -> true);
        }

        private TestRegistry(MeterRegistry delegate, Predicate<String> enabledMeters) {
            this(delegate, enabledMeters, _ -> true);
        }

        private TestRegistry(MeterRegistry delegate, String owner) {
            this(delegate, _ -> true, _ -> true,
                 List.of(TestMicrometerHttpTransportMetrics.metricsFactory().tagCreate("owner", owner)));
        }

        private TestRegistry(MeterRegistry delegate,
                             Predicate<String> enabledMeters,
                             Predicate<Map<String, String>> enabledTags) {
            this(delegate, enabledMeters, enabledTags, List.of());
        }

        private TestRegistry(MeterRegistry delegate,
                             Predicate<String> enabledMeters,
                             Predicate<Map<String, String>> enabledTags,
                             List<Tag> additionalTags) {
            this.delegate = delegate;
            this.enabledMeters = enabledMeters;
            this.enabledTags = enabledTags;
            this.additionalTags = additionalTags;
        }

        private ProviderBarrier blockNextRegistration() {
            ProviderBarrier barrier = new ProviderBarrier("micrometer-provider-barrier-"
                                                                  + barrierSequence.incrementAndGet());
            assertThat("Only one provider barrier can be active",
                       providerBarrier.compareAndSet(null, barrier),
                       is(true));
            return barrier;
        }

        private void releaseBarrier() {
            ProviderBarrier barrier = providerBarrier.getAndSet(null);
            if (barrier != null) {
                barrier.release();
            }
        }

        private int closeCount() {
            return closeCount.get();
        }

        @Override
        public List<Meter> meters() {
            return delegate.meters();
        }

        @Override
        public Collection<Meter> meters(Predicate<Meter> filter) {
            return delegate.meters(filter);
        }

        @Override
        public Iterable<Meter> meters(Iterable<String> scopeSelection) {
            return delegate.meters(scopeSelection);
        }

        @Override
        public Iterable<String> scopes() {
            return delegate.scopes();
        }

        @Override
        public void close() {
            closeCount.incrementAndGet();
            delegate.close();
        }

        @Override
        public boolean isMeterEnabled(String name) {
            return enabledMeters.test(name);
        }

        @Override
        public boolean isMeterEnabled(String name, Map<String, String> tags, Optional<String> scope) {
            return enabledTags.test(tags) && delegate.isMeterEnabled(name, tags, scope);
        }

        @Override
        public Clock clock() {
            return delegate.clock();
        }

        @Override
        public MetricsFactory metricsFactory() {
            return delegate.metricsFactory();
        }

        @Override
        public <B extends Meter.Builder<B, M>, M extends Meter> M getOrCreate(B builder) {
            ProviderBarrier barrier = providerBarrier.get();
            if (barrier != null
                    && barrier.matches(builder)
                    && providerBarrier.compareAndSet(barrier, null)) {
                barrier.enter();
            }
            additionalTags.forEach(builder::addTag);
            M meter = delegate.getOrCreate(builder);
            String failedGaugeName = failGaugeRegistrationName.get();
            if (PROTOCOL_HTTP_1_1.equals(builder.tags().get("protocol"))
                    && builder.name().equals(failedGaugeName)
                    && failGaugeRegistrationName.compareAndSet(failedGaugeName, null)) {
                failedGaugeRegistrations.incrementAndGet();
                throw new IllegalStateException("Simulated failure after native gauge registration");
            }
            return meter;
        }

        @Override
        public <M extends Meter> Optional<M> meter(Class<M> meterClass, String name, Iterable<Tag> tags) {
            return delegate.meter(meterClass, name, tags);
        }

        @Override
        public Optional<Meter> remove(Meter meter) {
            if (failGaugeRemoval.get()
                    && (meter.id().name().equals("helidon.http.connections.active")
                    || meter.id().name().equals("helidon.http.streams.active"))) {
                throw new IllegalStateException("Simulated active gauge removal failure");
            }
            return delegate.remove(meter);
        }

        @Override
        public Optional<Meter> remove(Meter.Id id) {
            return delegate.remove(id);
        }

        @Override
        public Optional<Meter> remove(Meter.Id id, String scope) {
            return delegate.remove(id, scope);
        }

        @Override
        public Optional<Meter> remove(String name, Iterable<Tag> tags) {
            return delegate.remove(name, tags);
        }

        @Override
        public Optional<Meter> remove(String name, Iterable<Tag> tags, String scope) {
            return delegate.remove(name, tags, scope);
        }

        @Override
        public boolean isDeleted(Meter meter) {
            return delegate.isDeleted(meter);
        }

        @Override
        public MeterRegistry onMeterAdded(Consumer<Meter> listener) {
            MeterRegistry result = delegate.onMeterAdded(listener);
            if (result == null) {
                throw new IllegalStateException("Meter registry did not accept the listener");
            }
            return this;
        }

        @Override
        public MeterRegistry onMeterRemoved(Consumer<Meter> listener) {
            MeterRegistry result = delegate.onMeterRemoved(listener);
            if (result == null) {
                throw new IllegalStateException("Meter registry did not accept the listener");
            }
            return this;
        }

        @Override
        public <R> R unwrap(Class<? extends R> type) {
            return delegate.unwrap(type);
        }
    }
}
