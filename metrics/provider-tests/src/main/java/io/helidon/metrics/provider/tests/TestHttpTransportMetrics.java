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

package io.helidon.metrics.provider.tests;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.HandshakeOutcome;
import io.helidon.http.HttpTransportObserver.StreamObservation;
import io.helidon.http.HttpTransportObserver.StreamOutcome;
import io.helidon.http.metrics.HttpTransportMetrics;
import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;
import io.helidon.service.registry.Services;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static io.helidon.http.HttpTransportObserver.ConnectionOutcome.LOCAL_CLOSE;
import static io.helidon.http.HttpTransportObserver.ConnectionOutcome.NORMAL;
import static io.helidon.http.HttpTransportObserver.ConnectionOutcome.REMOTE_CLOSE;
import static io.helidon.http.HttpTransportObserver.Direction.BIDIRECTIONAL;
import static io.helidon.http.HttpTransportObserver.Handshake.NONE;
import static io.helidon.http.HttpTransportObserver.Handshake.TLS;
import static io.helidon.http.HttpTransportObserver.HandshakeOutcome.SUCCESS;
import static io.helidon.http.HttpTransportObserver.Initiator.REMOTE;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_1_1;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_2;
import static io.helidon.http.HttpTransportObserver.Role.SERVER;
import static io.helidon.http.HttpTransportObserver.StreamOutcome.COMPLETED;
import static io.helidon.http.HttpTransportObserver.TRANSPORT_TCP;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

class TestHttpTransportMetrics {
    private static final long TIMEOUT_SECONDS = 5;
    private static final Set<String> TAG_NAMES = Set.of("role",
                                                        "transport",
                                                        "protocol",
                                                        "handshake",
                                                        "direction",
                                                        "initiator",
                                                        "outcome",
                                                        "scope");

    @BeforeAll
    static void initializeMetricsProvider() {
        Services.get(MeterRegistry.class);
    }

    @Test
    void recordsConnectionHandshakeAndHttp1StreamLifecycle() throws Exception {
        TestClock clock = new TestClock();
        TestRegistry registry = newRegistry(clock);
        HttpTransportMetrics.Lease lease = HttpTransportMetrics.acquire(registry);
        try {
            ConnectionObservation connection = lease.connectionOpened(SERVER, TRANSPORT_TCP, TLS);
            clock.advance(10);
            var handshake = connection.handshakeStarted();
            clock.advance(20);
            handshake.close(SUCCESS);
            handshake.close(SUCCESS);
            connection.protocolSelected(PROTOCOL_HTTP_1_1);
            clock.advance(30);
            var stream = connection.streamOpened(BIDIRECTIONAL, REMOTE);
            clock.advance(40);
            stream.close(COMPLETED);
            stream.close(COMPLETED);
            clock.advance(50);
            connection.close(REMOTE_CLOSE);
            connection.close(LOCAL_CLOSE);

            synchronize(registry, lease);

            assertThat(counter(registry,
                               "helidon.http.connections.opened",
                               "role", "server",
                               "transport", "tcp",
                               "handshake", "tls").count(),
                       is(1L));
            assertThat(counter(registry,
                               "helidon.http.connections.established",
                               "role", "server",
                               "transport", "tcp",
                               "protocol", "http/1.1").count(),
                       is(1L));
            assertThat(gauge(registry,
                             "helidon.http.connections.active",
                             "role", "server",
                             "transport", "tcp",
                             "protocol", "http/1.1").value().longValue(),
                       is(0L));
            assertThat(counter(registry,
                               "helidon.http.handshakes",
                               "role", "server",
                               "transport", "tcp",
                               "handshake", "tls",
                               "outcome", "success").count(),
                       is(1L));
            assertThat(timer(registry,
                             "helidon.http.handshakes.duration",
                             "role", "server",
                             "transport", "tcp",
                             "handshake", "tls",
                             "outcome", "success").totalTime(TimeUnit.NANOSECONDS),
                       is(20.0));
            assertThat(counter(registry,
                               "helidon.http.streams.opened",
                               "role", "server",
                               "protocol", "http/1.1",
                               "direction", "bidi",
                               "initiator", "remote").count(),
                       is(1L));
            assertThat(gauge(registry,
                             "helidon.http.streams.active",
                             "role", "server",
                             "protocol", "http/1.1",
                             "direction", "bidi",
                             "initiator", "remote").value().longValue(),
                       is(0L));
            assertThat(counter(registry,
                               "helidon.http.streams.closed",
                               "role", "server",
                               "protocol", "http/1.1",
                               "direction", "bidi",
                               "initiator", "remote",
                               "outcome", "completed").count(),
                       is(1L));
            assertThat(timer(registry,
                             "helidon.http.streams.duration",
                             "role", "server",
                             "protocol", "http/1.1",
                             "direction", "bidi",
                             "initiator", "remote",
                             "outcome", "completed").totalTime(TimeUnit.NANOSECONDS),
                       is(40.0));
            assertThat(counter(registry,
                               "helidon.http.connections.closed",
                               "role", "server",
                               "transport", "tcp",
                               "protocol", "http/1.1",
                               "outcome", "remote-close").count(),
                       is(1L));
            assertThat(timer(registry,
                             "helidon.http.connections.duration",
                             "role", "server",
                             "transport", "tcp",
                             "protocol", "http/1.1",
                             "outcome", "remote-close").totalTime(TimeUnit.NANOSECONDS),
                       is(150.0));
            assertBoundedTags(registry);
        } finally {
            closeAndAwait(lease);
            assertRegistryEmpty(registry);
            registry.close();
        }
    }

    @Test
    void recordsAllNormalizedOutcomes() throws Exception {
        TestRegistry registry = newRegistry();
        HttpTransportMetrics.Lease lease = HttpTransportMetrics.acquire(registry);
        try {
            for (HandshakeOutcome outcome : HandshakeOutcome.values()) {
                ConnectionObservation connection = lease.connectionOpened(SERVER, TRANSPORT_TCP, TLS);
                connection.handshakeStarted().close(outcome);
                connection.close(NORMAL);
            }

            ConnectionObservation streamConnection = lease.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
            streamConnection.protocolSelected(PROTOCOL_HTTP_1_1);
            for (StreamOutcome outcome : StreamOutcome.values()) {
                streamConnection.streamOpened(BIDIRECTIONAL, REMOTE).close(outcome);
            }
            streamConnection.close(NORMAL);

            for (ConnectionOutcome outcome : ConnectionOutcome.values()) {
                ConnectionObservation connection = lease.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
                connection.protocolSelected(PROTOCOL_HTTP_1_1);
                connection.close(outcome);
            }

            synchronize(registry, lease);

            for (HandshakeOutcome outcome : HandshakeOutcome.values()) {
                assertThat(counter(registry,
                                   "helidon.http.handshakes",
                                   "role", "server",
                                   "transport", "tcp",
                                   "handshake", "tls",
                                   "outcome", tagValue(outcome)).count(),
                           is(1L));
            }
            for (StreamOutcome outcome : StreamOutcome.values()) {
                assertThat(counter(registry,
                                   "helidon.http.streams.closed",
                                   "role", "server",
                                   "protocol", "http/1.1",
                                   "direction", "bidi",
                                   "initiator", "remote",
                                   "outcome", tagValue(outcome)).count(),
                           is(1L));
            }
            for (ConnectionOutcome outcome : ConnectionOutcome.values()) {
                assertThat(counter(registry,
                                   "helidon.http.connections.closed",
                                   "role", "server",
                                   "transport", "tcp",
                                   "protocol", "http/1.1",
                                   "outcome", tagValue(outcome)).count(),
                           is(outcome == NORMAL ? 2L : 1L));
            }
        } finally {
            closeAndAwait(lease);
            assertRegistryEmpty(registry);
            registry.close();
        }
    }

    @Test
    void closesPartialChildrenUsingConnectionOutcomeMapping() throws Exception {
        TestRegistry registry = newRegistry();
        HttpTransportMetrics.Lease lease = HttpTransportMetrics.acquire(registry);
        try {
            for (ConnectionOutcome outcome : ConnectionOutcome.values()) {
                ConnectionObservation connection = lease.connectionOpened(SERVER, TRANSPORT_TCP, TLS);
                connection.protocolSelected(PROTOCOL_HTTP_1_1);
                connection.handshakeStarted();
                connection.streamOpened(BIDIRECTIONAL, REMOTE);
                connection.close(outcome);
            }

            synchronize(registry, lease);

            assertChildOutcome(registry, HandshakeOutcome.CANCELLED, 2, StreamOutcome.CANCELLED, 3);
            assertChildOutcome(registry, HandshakeOutcome.FAILURE, 2, StreamOutcome.ERROR, 2);
            assertChildOutcome(registry, HandshakeOutcome.TIMEOUT, 1, null, 0);
        } finally {
            closeAndAwait(lease);
            assertRegistryEmpty(registry);
            registry.close();
        }
    }

    @ParameterizedTest
    @EnumSource(ConnectionOutcome.class)
    void retainsOverlappingStreamsAfterOutOfOrderCompletion(ConnectionOutcome connectionOutcome) throws Exception {
        TestRegistry registry = newRegistry();
        HttpTransportMetrics.Lease lease = HttpTransportMetrics.acquire(registry);
        ConnectionObservation connection = lease.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
        try {
            connection.protocolSelected(PROTOCOL_HTTP_2);
            var oldest = connection.streamOpened(BIDIRECTIONAL, REMOTE);
            var middle = connection.streamOpened(BIDIRECTIONAL, REMOTE);
            var remaining = connection.streamOpened(BIDIRECTIONAL, REMOTE);
            var newest = connection.streamOpened(BIDIRECTIONAL, REMOTE);
            synchronize(registry, lease);

            List<Tag> streamTags = tags("role", "server",
                                        "protocol", "http/2",
                                        "direction", "bidi",
                                        "initiator", "remote");
            Gauge<?> active = registry.gauge("helidon.http.streams.active", streamTags).orElseThrow();
            assertThat("All overlapping streams are active", active.value().longValue(), is(4L));

            middle.close(COMPLETED);
            middle.close(COMPLETED);
            assertThat("Repeated middle completion preserves its live neighbors", active.value().longValue(), is(3L));
            oldest.close(COMPLETED);
            oldest.close(COMPLETED);
            assertThat("Repeated oldest completion preserves the later streams", active.value().longValue(), is(2L));
            newest.close(COMPLETED);
            newest.close(COMPLETED);
            assertThat("Repeated newest completion preserves the remaining stream", active.value().longValue(), is(1L));

            var later = connection.streamOpened(BIDIRECTIONAL, REMOTE);
            assertThat("A later stream joins the still-active stream", active.value().longValue(), is(2L));
            connection.close(connectionOutcome);
            connection.close(NORMAL);
            remaining.close(COMPLETED);
            later.close(COMPLETED);
            assertThat("Connection close completes every remaining stream once", active.value().longValue(), is(0L));
            assertThat("Closed connections reject further streams",
                       connection.streamOpened(BIDIRECTIONAL, REMOTE),
                       sameInstance(StreamObservation.noop()));
            synchronize(registry, lease);

            assertThat("Every admitted stream is counted once",
                       registry.counter("helidon.http.streams.opened", streamTags).orElseThrow().count(),
                       is(5L));
            StreamOutcome fallback = switch (connectionOutcome) {
                case NORMAL, LOCAL_CLOSE, REMOTE_CLOSE -> StreamOutcome.CANCELLED;
                case TIMEOUT, ERROR -> StreamOutcome.ERROR;
            };
            for (var expectation : Map.of(COMPLETED, 3L, fallback, 2L).entrySet()) {
                List<Tag> outcomeTags = new ArrayList<>(streamTags);
                outcomeTags.add(metricsFactory().tagCreate("outcome", tagValue(expectation.getKey())));
                assertThat("Only the intended streams have outcome " + expectation.getKey(),
                           registry.counter("helidon.http.streams.closed", outcomeTags).orElseThrow().count(),
                           is(expectation.getValue()));
                assertThat("Each stream duration is recorded once for " + expectation.getKey(),
                           registry.timer("helidon.http.streams.duration", outcomeTags).orElseThrow().count(),
                           is(expectation.getValue()));
            }
            assertThat("The physical connection is no longer active",
                       gauge(registry,
                             "helidon.http.connections.active",
                             "role", "server",
                             "transport", "tcp",
                             "protocol", "http/2").value().longValue(),
                       is(0L));
            closeAndAwait(lease);
            assertRegistryEmpty(registry);
        } finally {
            connection.close(NORMAL);
            closeAndAwait(lease);
            registry.close();
        }
    }

    @Test
    void retainsMetersUntilLastLeaseForSameConfiguredRegistryCloses() throws Exception {
        TestRegistry registry = newRegistry();
        HttpTransportMetrics.Lease first = HttpTransportMetrics.acquire(registry);
        HttpTransportMetrics.Lease second = HttpTransportMetrics.acquire(registry);
        try {
            first.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);
            synchronize(registry, second);

            first.close();
            awaitCompletion(first);
            assertThat(registry.meters(), not(empty()));

            second.close();
            awaitCompletion(second);
            assertRegistryEmpty(registry);

            HttpTransportMetrics.Lease nextEpoch = HttpTransportMetrics.acquire(registry);
            nextEpoch.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);
            closeAndAwait(nextEpoch);
            assertRegistryEmpty(registry);
        } finally {
            closeAndAwait(first);
            closeAndAwait(second);
            registry.close();
        }
    }

    @Test
    void sharesNativeMetersAcrossWrappersAndWaitsForFinalOwner() throws Exception {
        MeterRegistry nativeRegistry = createRegistry(metricsFactory().clockSystem());
        TestRegistry firstRegistry = new TestRegistry(nativeRegistry);
        TestRegistry secondRegistry = new TestRegistry(nativeRegistry);
        HttpTransportMetrics.Lease first = HttpTransportMetrics.acquire(firstRegistry);
        HttpTransportMetrics.Lease second = HttpTransportMetrics.acquire(secondRegistry);
        try {
            assertThat(firstRegistry.unwrap(Object.class), sameInstance(secondRegistry.unwrap(Object.class)));
            first.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);
            second.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);
            synchronize(secondRegistry, second);

            assertThat(counter(nativeRegistry,
                               "helidon.http.connections.opened",
                               "role", "server",
                               "transport", "tcp",
                               "handshake", "none").count(),
                       is(2L));

            first.close();
            assertThat(first.completion().toCompletableFuture().isDone(), is(false));
            assertThat(nativeRegistry.meters(), not(empty()));

            second.close();
            awaitCompletion(first);
            awaitCompletion(second);
            assertThat(nativeRegistry.meters(), empty());
        } finally {
            closeAndAwait(first);
            closeAndAwait(second);
            nativeRegistry.close();
        }
    }

    @Test
    void isolatesIndependentNativeRegistries() throws Exception {
        TestRegistry firstRegistry = newRegistry();
        TestRegistry secondRegistry = newRegistry();
        HttpTransportMetrics.Lease first = HttpTransportMetrics.acquire(firstRegistry);
        HttpTransportMetrics.Lease second = HttpTransportMetrics.acquire(secondRegistry);
        try {
            assertThat(firstRegistry.unwrap(Object.class),
                       not(sameInstance(secondRegistry.unwrap(Object.class))));

            first.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);
            second.connectionOpened(SERVER, "unix", NONE).close(NORMAL);
            synchronize(firstRegistry, first);
            synchronize(secondRegistry, second);

            assertThat(counter(firstRegistry,
                               "helidon.http.connections.opened",
                               "role", "server",
                               "transport", "tcp",
                               "handshake", "none").count(),
                       is(1L));
            assertThat(firstRegistry.counter("helidon.http.connections.opened",
                                             tags("role", "server",
                                                  "transport", "unix",
                                                  "handshake", "none")),
                       is(Optional.empty()));
            assertThat(counter(secondRegistry,
                               "helidon.http.connections.opened",
                               "role", "server",
                               "transport", "unix",
                               "handshake", "none").count(),
                       is(1L));
            assertThat(secondRegistry.counter("helidon.http.connections.opened",
                                              tags("role", "server",
                                                   "transport", "tcp",
                                                   "handshake", "none")),
                       is(Optional.empty()));
        } finally {
            closeAndAwait(first);
            closeAndAwait(second);
            assertRegistryEmpty(firstRegistry);
            assertRegistryEmpty(secondRegistry);
            firstRegistry.close();
            secondRegistry.close();
        }
    }

    @Test
    void handlesConcurrentDifferentConnectionsAcrossWrappers() throws Exception {
        MeterRegistry nativeRegistry = createRegistry(metricsFactory().clockSystem());
        TestRegistry firstRegistry = new TestRegistry(nativeRegistry);
        TestRegistry secondRegistry = new TestRegistry(nativeRegistry);
        HttpTransportMetrics.Lease first = HttpTransportMetrics.acquire(firstRegistry);
        HttpTransportMetrics.Lease second = HttpTransportMetrics.acquire(secondRegistry);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        try {
            for (int i = 0; i < 32; i++) {
                HttpTransportMetrics.Lease owner = (i & 1) == 0 ? first : second;
                tasks.add(CompletableFuture.runAsync(() -> {
                    await(start);
                    ConnectionObservation connection = owner.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
                    connection.protocolSelected(PROTOCOL_HTTP_1_1);
                    connection.close(NORMAL);
                }));
            }
            start.countDown();
            CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new))
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            synchronize(firstRegistry, first);

            assertThat(counter(nativeRegistry,
                               "helidon.http.connections.opened",
                               "role", "server",
                               "transport", "tcp",
                               "handshake", "none").count(),
                       is(32L));
            assertThat(counter(nativeRegistry,
                               "helidon.http.connections.closed",
                               "role", "server",
                               "transport", "tcp",
                               "protocol", "http/1.1",
                               "outcome", "normal").count(),
                       is(32L));
            assertThat(gauge(nativeRegistry,
                             "helidon.http.connections.active",
                             "role", "server",
                             "transport", "tcp",
                             "protocol", "http/1.1").value().longValue(),
                       is(0L));
        } finally {
            start.countDown();
            first.close();
            second.close();
            awaitCompletion(first);
            awaitCompletion(second);
            nativeRegistry.close();
        }
    }

    @Test
    void handlesConcurrentRepeatedLifecycleCompletion() throws Exception {
        TestRegistry registry = newRegistry();
        HttpTransportMetrics.Lease lease = HttpTransportMetrics.acquire(registry);
        try {
            ConnectionObservation connection = lease.connectionOpened(SERVER, TRANSPORT_TCP, TLS);
            connection.protocolSelected(PROTOCOL_HTTP_1_1);
            var handshake = connection.handshakeStarted();
            var stream = connection.streamOpened(BIDIRECTIONAL, REMOTE);

            runConcurrently(() -> handshake.close(SUCCESS));
            runConcurrently(() -> stream.close(COMPLETED));
            runConcurrently(() -> connection.close(NORMAL));
            synchronize(registry, lease);

            assertThat(counter(registry,
                               "helidon.http.handshakes",
                               "role", "server",
                               "transport", "tcp",
                               "handshake", "tls",
                               "outcome", "success").count(),
                       is(1L));
            assertThat(counter(registry,
                               "helidon.http.streams.closed",
                               "role", "server",
                               "protocol", "http/1.1",
                               "direction", "bidi",
                               "initiator", "remote",
                               "outcome", "completed").count(),
                       is(1L));
            assertThat(counter(registry,
                               "helidon.http.connections.closed",
                               "role", "server",
                               "transport", "tcp",
                               "protocol", "http/1.1",
                               "outcome", "normal").count(),
                       is(1L));
            assertThat(gauge(registry,
                             "helidon.http.connections.active",
                             "role", "server",
                             "transport", "tcp",
                             "protocol", "http/1.1").value().longValue(),
                       is(0L));
            assertThat(gauge(registry,
                             "helidon.http.streams.active",
                             "role", "server",
                             "protocol", "http/1.1",
                             "direction", "bidi",
                             "initiator", "remote").value().longValue(),
                       is(0L));
        } finally {
            closeAndAwait(lease);
            registry.close();
        }
    }

    @Test
    void coldRegistrationAndCleanupDoNotBlockTransportCallbacks() throws Exception {
        TestRegistry registry = newRegistry();
        HttpTransportMetrics.Lease lease = HttpTransportMetrics.acquire(registry);
        try {
            ProviderBarrier registration = registry.blockNextRegistration("blocked-registration");
            CompletableFuture<ConnectionObservation> opened = CompletableFuture.supplyAsync(
                    () -> lease.connectionOpened(SERVER, "blocked-registration", NONE));

            registration.awaitEntered();
            ConnectionObservation connection = opened.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(registration.thread(), not(sameInstance(Thread.currentThread())));
            registration.release();

            synchronize(registry, lease);
            RemovalBarrier removal = registry.blockNextRemoval("helidon.http.connections.opened");
            connection.close(NORMAL);
            lease.close();

            removal.awaitEntered();
            assertThat(lease.completion().toCompletableFuture().isDone(), is(false));
            removal.release();
            awaitCompletion(lease);
            assertRegistryEmpty(registry);
        } finally {
            registry.releaseBarriers();
            closeAndAwait(lease);
            registry.close();
        }
    }

    @Test
    void retriesRegistrationAndRemovalAcrossObservationEpochs() throws Exception {
        TestRegistry registry = newRegistry();
        HttpTransportMetrics.Lease first = HttpTransportMetrics.acquire(registry);
        try {
            registry.failNextRegistration("helidon.http.connections.opened");
            first.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);
            first.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);
            synchronize(registry, first);
            assertThat(counter(registry,
                               "helidon.http.connections.opened",
                               "role", "server",
                               "transport", "tcp",
                               "handshake", "none").count(),
                       is(1L));

            registry.failAllRemovals(true);
            closeAndAwait(first);
            assertThat(registry.meters(), not(empty()));

            registry.failAllRemovals(false);
            HttpTransportMetrics.Lease second = HttpTransportMetrics.acquire(registry);
            ConnectionObservation secondConnection = second.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
            synchronize(registry, second);
            assertThat(gauge(registry,
                             "helidon.http.connections.active",
                             "role", "server",
                             "transport", "tcp",
                             "protocol", "unknown").value().longValue(),
                       is(1L));
            secondConnection.close(NORMAL);
            closeAndAwait(second);
            assertRegistryEmpty(registry);
        } finally {
            registry.failAllRemovals(false);
            registry.releaseBarriers();
            closeAndAwait(first);
            registry.close();
        }
    }

    private static void assertChildOutcome(TestRegistry registry,
                                           HandshakeOutcome handshakeOutcome,
                                           long handshakeCount,
                                           StreamOutcome streamOutcome,
                                           long streamCount) {
        assertThat(counter(registry,
                           "helidon.http.handshakes",
                           "role", "server",
                           "transport", "tcp",
                           "handshake", "tls",
                           "outcome", tagValue(handshakeOutcome)).count(),
                   is(handshakeCount));
        if (streamOutcome == null) {
            return;
        }
        assertThat(counter(registry,
                           "helidon.http.streams.closed",
                           "role", "server",
                           "protocol", "http/1.1",
                           "direction", "bidi",
                           "initiator", "remote",
                           "outcome", tagValue(streamOutcome)).count(),
                   is(streamCount));
    }

    private static void assertBoundedTags(TestRegistry registry) {
        registry.meters().forEach(meter -> {
            Map<String, String> tagMap = meter.id().tagsMap();
            assertThat("Unexpected tag name for " + meter.id().name() + ": " + tagMap,
                       TAG_NAMES.containsAll(tagMap.keySet()),
                       is(true));
            assertThat("Transport metric tag values must not expose addresses, paths, SNI, or errors",
                       tagMap.values().stream().noneMatch(value -> value.contains("/")
                               && !value.startsWith("http/")),
                       is(true));
        });
    }

    private static String tagValue(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static TestRegistry newRegistry() {
        return newRegistry(metricsFactory().clockSystem());
    }

    private static TestRegistry newRegistry(Clock clock) {
        return new TestRegistry(createRegistry(clock));
    }

    private static MeterRegistry createRegistry(Clock clock) {
        return metricsFactory().createMeterRegistry(clock, MetricsConfig.create());
    }

    private static MetricsFactory metricsFactory() {
        return Services.get(MetricsFactory.class);
    }

    private static void synchronize(TestRegistry registry, HttpTransportMetrics.Lease lease) {
        ProviderBarrier barrier = registry.blockNextRegistration();
        ConnectionObservation connection = lease.connectionOpened(SERVER, barrier.transport(), NONE);
        barrier.awaitEntered();
        connection.close(NORMAL);
        barrier.release();
    }

    private static void closeAndAwait(HttpTransportMetrics.Lease lease) throws Exception {
        lease.close();
        awaitCompletion(lease);
    }

    private static void awaitCompletion(HttpTransportMetrics.Lease lease) throws Exception {
        lease.completion().toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static void assertRegistryEmpty(MeterRegistry registry) {
        assertThat(registry.meters(), empty());
    }

    private static Counter counter(MeterRegistry registry, String name, String... tagPairs) {
        return registry.counter(name, tags(tagPairs))
                .orElseThrow(() -> new AssertionError("Missing counter " + name + " " + tags(tagPairs)));
    }

    private static Gauge<?> gauge(MeterRegistry registry, String name, String... tagPairs) {
        return registry.gauge(name, tags(tagPairs))
                .orElseThrow(() -> new AssertionError("Missing gauge " + name + " " + tags(tagPairs)));
    }

    private static Timer timer(MeterRegistry registry, String name, String... tagPairs) {
        return registry.timer(name, tags(tagPairs))
                .orElseThrow(() -> new AssertionError("Missing timer " + name + " " + tags(tagPairs)));
    }

    private static List<Tag> tags(String... pairs) {
        List<Tag> result = new ArrayList<>(pairs.length / 2);
        for (int i = 0; i < pairs.length; i += 2) {
            result.add(metricsFactory().tagCreate(pairs[i], pairs[i + 1]));
        }
        return result;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat("Concurrent work should start",
                       latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                       is(true));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while awaiting concurrent transport metric work", e);
        }
    }

    private static void runConcurrently(Runnable action) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            tasks.add(CompletableFuture.runAsync(() -> {
                await(start);
                action.run();
            }));
        }
        start.countDown();
        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new))
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static final class TestClock implements Clock {
        private final AtomicLong monotonicTime = new AtomicLong();

        @Override
        public long wallTime() {
            return 0;
        }

        @Override
        public long monotonicTime() {
            return monotonicTime.get();
        }

        private void advance(long nanoseconds) {
            monotonicTime.addAndGet(nanoseconds);
        }
    }

    private static final class ProviderBarrier {
        private final String transport;
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);
        private volatile Thread thread;

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
            thread = Thread.currentThread();
            entered.countDown();
            await(released);
        }

        private void awaitEntered() {
            await(entered);
        }

        private void release() {
            released.countDown();
        }

        private Thread thread() {
            return thread;
        }
    }

    private static final class RemovalBarrier {
        private final String meterName;
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        private RemovalBarrier(String meterName) {
            this.meterName = meterName;
        }

        private boolean matches(Meter meter) {
            return meter.id().name().equals(meterName);
        }

        private void enter() {
            entered.countDown();
            await(released);
        }

        private void awaitEntered() {
            await(entered);
        }

        private void release() {
            released.countDown();
        }
    }

    private static final class TestRegistry implements MeterRegistry {
        private final MeterRegistry delegate;
        private final AtomicInteger barrierSequence = new AtomicInteger();
        private final AtomicReference<ProviderBarrier> providerBarrier = new AtomicReference<>();
        private final AtomicReference<RemovalBarrier> removalBarrier = new AtomicReference<>();
        private volatile Predicate<Meter> failingRegistration = ignored -> false;
        private volatile boolean failNextRegistration;
        private volatile boolean failAllRemovals;

        private TestRegistry(MeterRegistry delegate) {
            this.delegate = delegate;
        }

        private ProviderBarrier blockNextRegistration() {
            return blockNextRegistration("provider-barrier-" + barrierSequence.incrementAndGet());
        }

        private ProviderBarrier blockNextRegistration(String transport) {
            ProviderBarrier barrier = new ProviderBarrier(transport);
            assertThat("Only one provider barrier can be active",
                       providerBarrier.compareAndSet(null, barrier),
                       is(true));
            return barrier;
        }

        private RemovalBarrier blockNextRemoval(String meterName) {
            RemovalBarrier barrier = new RemovalBarrier(meterName);
            assertThat("Only one removal barrier can be active",
                       removalBarrier.compareAndSet(null, barrier),
                       is(true));
            return barrier;
        }

        private void failNextRegistration(String name) {
            failNextRegistration = true;
            failingRegistration = meter -> meter.id().name().equals(name);
        }

        private void failAllRemovals(boolean fail) {
            failAllRemovals = fail;
        }

        private void releaseBarriers() {
            ProviderBarrier registration = providerBarrier.getAndSet(null);
            if (registration != null) {
                registration.release();
            }
            RemovalBarrier removal = removalBarrier.getAndSet(null);
            if (removal != null) {
                removal.release();
            }
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
            delegate.close();
        }

        @Override
        public boolean isMeterEnabled(String name, Map<String, String> tags, Optional<String> scope) {
            return delegate.isMeterEnabled(name, tags, scope);
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

            M meter = delegate.getOrCreate(builder);
            if (failNextRegistration && failingRegistration.test(meter)) {
                failNextRegistration = false;
                throw new IllegalStateException("Meter registration failure");
            }
            return meter;
        }

        @Override
        public <M extends Meter> Optional<M> meter(Class<M> meterClass, String name, Iterable<Tag> tags) {
            return delegate.meter(meterClass, name, tags);
        }

        @Override
        public Optional<Meter> remove(Meter meter) {
            RemovalBarrier barrier = removalBarrier.get();
            if (barrier != null
                    && barrier.matches(meter)
                    && removalBarrier.compareAndSet(barrier, null)) {
                barrier.enter();
            }
            if (failAllRemovals) {
                throw new IllegalStateException("Meter removal failure");
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
