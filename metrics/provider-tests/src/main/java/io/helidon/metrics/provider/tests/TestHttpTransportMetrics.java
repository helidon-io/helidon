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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.HandshakeObservation;
import io.helidon.http.HttpTransportObserver.StreamObservation;
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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.http.HttpTransportObserver.ConnectionOutcome.NORMAL;
import static io.helidon.http.HttpTransportObserver.ConnectionOutcome.REMOTE_CLOSE;
import static io.helidon.http.HttpTransportObserver.Direction.BIDIRECTIONAL;
import static io.helidon.http.HttpTransportObserver.Direction.UNIDIRECTIONAL;
import static io.helidon.http.HttpTransportObserver.Handshake.QUIC_TLS;
import static io.helidon.http.HttpTransportObserver.Handshake.TLS;
import static io.helidon.http.HttpTransportObserver.HandshakeOutcome.SUCCESS;
import static io.helidon.http.HttpTransportObserver.Initiator.LOCAL;
import static io.helidon.http.HttpTransportObserver.Initiator.REMOTE;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_1_1;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_2;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_3;
import static io.helidon.http.HttpTransportObserver.Role.CLIENT;
import static io.helidon.http.HttpTransportObserver.Role.SERVER;
import static io.helidon.http.HttpTransportObserver.StreamOutcome.COMPLETED;
import static io.helidon.http.HttpTransportObserver.StreamOutcome.RESET;
import static io.helidon.http.HttpTransportObserver.TRANSPORT_QUIC;
import static io.helidon.http.HttpTransportObserver.TRANSPORT_TCP;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class TestHttpTransportMetrics {
    @BeforeAll
    static void initializeMetricsProvider() {
        MetricsFactory.getInstance().globalRegistry();
    }

    @Test
    void retainsMetersUntilLastRegistryLeaseCloses() {
        MeterRegistry registry = MeterRegistry.create();
        HttpTransportMetrics.Lease first = HttpTransportMetrics.acquire(registry);
        HttpTransportMetrics.Lease second = HttpTransportMetrics.acquire(registry);
        CompletableFuture<Void> firstCompletion = first.completion().toCompletableFuture();
        CompletableFuture<Void> finalCompletion = second.completion().toCompletableFuture();
        ConnectionObservation connection = first.connectionOpened(SERVER, TRANSPORT_TCP, TLS);
        connection.close(NORMAL);

        first.close();
        await(() -> !registry.meters().isEmpty());
        assertThat(registry.meters(), not(empty()));
        await(firstCompletion::isDone);
        assertThat(registry.meters(), not(empty()));

        second.close();
        second.close();
        await(finalCompletion::isDone);
        assertRegistryEmpty(registry);

        HttpTransportMetrics.Lease reacquired = HttpTransportMetrics.acquire(registry);
        CompletableFuture<Void> secondEpoch = reacquired.completion().toCompletableFuture();
        assertThat(secondEpoch.isDone(), is(false));
        ConnectionObservation nextConnection = reacquired.connectionOpened(SERVER, TRANSPORT_TCP, TLS);
        nextConnection.protocolSelected(PROTOCOL_HTTP_2);
        assertThat(gauge(registry,
                         "http.connections.active",
                         "role", "server",
                         "transport", "tcp",
                         "protocol", "http/2").value().longValue(),
                   is(1L));
        nextConnection.close(NORMAL);
        assertThat(gauge(registry,
                         "http.connections.active",
                         "role", "server",
                         "transport", "tcp",
                         "protocol", "http/2").value().longValue(),
                   is(0L));
        reacquired.close();
        await(secondEpoch::isDone);
        assertRegistryEmpty(registry);
    }

    @Test
    void handsOffCompletionOnlyToSameConfiguredRegistryInstance() {
        MeterRegistry nativeRegistry = MeterRegistry.create();
        MeterRegistry firstRegistry = new TestRegistry(nativeRegistry);
        MeterRegistry epochKeeperRegistry = new TestRegistry(nativeRegistry);
        HttpTransportMetrics.Lease first = HttpTransportMetrics.acquire(firstRegistry);
        HttpTransportMetrics.Lease epochKeeper = HttpTransportMetrics.acquire(epochKeeperRegistry);
        CompletableFuture<Void> firstCompletion = first.completion().toCompletableFuture();
        CompletableFuture<Void> keeperCompletion = epochKeeper.completion().toCompletableFuture();

        first.close();
        assertThat(firstCompletion.isDone(), is(false));

        HttpTransportMetrics.Lease reacquired = HttpTransportMetrics.acquire(firstRegistry);
        CompletableFuture<Void> reacquiredCompletion = reacquired.completion().toCompletableFuture();
        await(firstCompletion::isDone);

        reacquired.close();
        assertThat(reacquiredCompletion.isDone(), is(false));

        epochKeeper.close();
        await(reacquiredCompletion::isDone);
        await(keeperCompletion::isDone);
        assertRegistryEmpty(nativeRegistry);
    }

    @Test
    void recordsConnectionAndHandshakeLifecycle() {
        TestClock clock = new TestClock();
        MeterRegistry registry = MetricsFactory.getInstance().createMeterRegistry(clock, MetricsConfig.create());
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(registry);
        ConnectionObservation connection = observer.connectionOpened(SERVER, TRANSPORT_TCP, TLS);

        assertThat(counter(registry,
                           "http.connections.opened",
                           "role", "server",
                           "transport", "tcp",
                           "handshake", "tls").count(),
                   is(1L));
        assertThat(gauge(registry,
                         "http.connections.active",
                         "role", "server",
                         "transport", "tcp",
                         "protocol", "unknown").value().longValue(),
                   is(1L));

        clock.advance(10);
        HandshakeObservation handshake = connection.handshakeStarted();
        clock.advance(20);
        handshake.close(SUCCESS);
        handshake.close(SUCCESS);
        assertThat(counter(registry,
                           "http.handshakes",
                           "role", "server",
                           "transport", "tcp",
                           "handshake", "tls",
                           "outcome", "success").count(),
                   is(1L));
        assertThat(timer(registry,
                         "http.handshakes.duration",
                         "role", "server",
                         "transport", "tcp",
                         "handshake", "tls",
                         "outcome", "success").count(),
                   is(1L));
        assertThat(timer(registry,
                         "http.handshakes.duration",
                         "role", "server",
                         "transport", "tcp",
                         "handshake", "tls",
                         "outcome", "success").totalTime(TimeUnit.NANOSECONDS),
                   is(20.0));

        connection.protocolSelected(PROTOCOL_HTTP_1_1);
        connection.close(NORMAL);
        observer.close();
        assertRegistryEmpty(registry);
    }

    @Test
    void recordsProtocolTransitionAndStreamLifecycle() {
        TestClock clock = new TestClock();
        MeterRegistry registry = MetricsFactory.getInstance().createMeterRegistry(clock, MetricsConfig.create());
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(registry);
        ConnectionObservation connection = observer.connectionOpened(SERVER, TRANSPORT_TCP, TLS);

        clock.advance(30);
        connection.protocolSelected(PROTOCOL_HTTP_1_1);
        connection.protocolSelected(PROTOCOL_HTTP_1_1);
        connection.protocolSelected(PROTOCOL_HTTP_2);
        assertThat(counter(registry,
                           "http.connections.established",
                           "role", "server",
                           "transport", "tcp",
                           "protocol", "http/1.1").count(),
                   is(1L));
        assertThat(registry.counter("http.connections.established",
                                    tags("role", "server",
                                         "transport", "tcp",
                                         "protocol", "http/2")),
                   is(Optional.empty()));
        assertThat(gauge(registry,
                         "http.connections.active",
                         "role", "server",
                         "transport", "tcp",
                         "protocol", "unknown").value().longValue(),
                   is(0L));
        assertThat(gauge(registry,
                         "http.connections.active",
                         "role", "server",
                         "transport", "tcp",
                         "protocol", "http/1.1").value().longValue(),
                   is(0L));
        assertThat(gauge(registry,
                         "http.connections.active",
                         "role", "server",
                         "transport", "tcp",
                         "protocol", "http/2").value().longValue(),
                   is(1L));
        clock.advance(10);
        StreamObservation stream = connection.streamOpened(BIDIRECTIONAL, REMOTE);
        assertThat(counter(registry,
                           "http.streams.opened",
                           "role", "server",
                           "protocol", "http/2",
                           "direction", "bidi",
                           "initiator", "remote").count(),
                   is(1L));
        assertThat(gauge(registry,
                         "http.streams.active",
                         "role", "server",
                         "protocol", "http/2",
                         "direction", "bidi",
                         "initiator", "remote").value().longValue(),
                   is(1L));

        clock.advance(30);
        stream.close(COMPLETED);
        stream.close(COMPLETED);
        clock.advance(30);
        connection.close(REMOTE_CLOSE);
        connection.close(REMOTE_CLOSE);

        assertThat(gauge(registry,
                         "http.streams.active",
                         "role", "server",
                         "protocol", "http/2",
                         "direction", "bidi",
                         "initiator", "remote").value().longValue(),
                   is(0L));
        assertThat(counter(registry,
                           "http.streams.closed",
                           "role", "server",
                           "protocol", "http/2",
                           "direction", "bidi",
                           "initiator", "remote",
                           "outcome", "completed").count(),
                   is(1L));
        assertThat(timer(registry,
                         "http.streams.duration",
                         "role", "server",
                         "protocol", "http/2",
                         "direction", "bidi",
                         "initiator", "remote",
                         "outcome", "completed").count(),
                   is(1L));
        assertThat(timer(registry,
                         "http.streams.duration",
                         "role", "server",
                         "protocol", "http/2",
                         "direction", "bidi",
                         "initiator", "remote",
                         "outcome", "completed").totalTime(TimeUnit.NANOSECONDS),
                   is(30.0));
        assertThat(gauge(registry,
                         "http.connections.active",
                         "role", "server",
                         "transport", "tcp",
                         "protocol", "http/2").value().longValue(),
                   is(0L));
        assertThat(counter(registry,
                           "http.connections.closed",
                           "role", "server",
                           "transport", "tcp",
                           "protocol", "http/2",
                           "outcome", "remote-close").count(),
                   is(1L));
        assertThat(timer(registry,
                         "http.connections.duration",
                         "role", "server",
                         "transport", "tcp",
                         "protocol", "http/2",
                         "outcome", "remote-close").count(),
                   is(1L));
        assertThat(timer(registry,
                         "http.connections.duration",
                         "role", "server",
                         "transport", "tcp",
                         "protocol", "http/2",
                         "outcome", "remote-close").totalTime(TimeUnit.NANOSECONDS),
                   is(100.0));
        observer.close();
        assertRegistryEmpty(registry);
    }

    @Test
    void serializesConcurrentStreamAndConnectionClose() {
        MeterRegistry registry = MeterRegistry.create();
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(registry);
        try {
            for (int i = 0; i < 100; i++) {
                ConnectionObservation connection = observer.connectionOpened(SERVER, TRANSPORT_TCP, TLS);
                connection.protocolSelected(PROTOCOL_HTTP_2);
                StreamObservation stream = connection.streamOpened(BIDIRECTIONAL, REMOTE);
                CompletableFuture.allOf(CompletableFuture.runAsync(() -> stream.close(COMPLETED)),
                                        CompletableFuture.runAsync(() -> connection.close(NORMAL)))
                        .join();
            }

            for (int i = 0; i < 100; i++) {
                ConnectionObservation connection = observer.connectionOpened(SERVER, TRANSPORT_TCP, TLS);
                connection.protocolSelected(PROTOCOL_HTTP_2);
                AtomicReference<StreamObservation> stream = new AtomicReference<>();
                CompletableFuture.allOf(CompletableFuture.runAsync(() -> stream.set(connection.streamOpened(BIDIRECTIONAL,
                                                                                                             REMOTE))),
                                        CompletableFuture.runAsync(() -> connection.close(NORMAL)))
                        .join();
                stream.get().close(COMPLETED);
            }

            assertThat(counter(registry,
                               "http.connections.opened",
                               200,
                               "role", "server",
                               "transport", "tcp",
                               "handshake", "tls").count(),
                       is(200L));
            assertThat(counter(registry,
                               "http.connections.closed",
                               200,
                               "role", "server",
                               "transport", "tcp",
                               "protocol", "http/2",
                               "outcome", "normal").count(),
                       is(200L));
            assertThat(gauge(registry,
                             "http.connections.active",
                             "role", "server",
                             "transport", "tcp",
                             "protocol", "http/2").value().longValue(),
                       is(0L));
            assertThat(gauge(registry,
                             "http.streams.active",
                             "role", "server",
                             "protocol", "http/2",
                             "direction", "bidi",
                             "initiator", "remote").value().longValue(),
                       is(0L));
            await(() -> counterCount(registry,
                                     "http.streams.closed",
                                     "role", "server",
                                     "protocol", "http/2",
                                     "direction", "bidi",
                                     "initiator", "remote",
                                     "outcome", "completed")
                    + counterCount(registry,
                                   "http.streams.closed",
                                   "role", "server",
                                   "protocol", "http/2",
                                   "direction", "bidi",
                                   "initiator", "remote",
                                   "outcome", "cancelled")
                    == counterCount(registry,
                                    "http.streams.opened",
                                    "role", "server",
                                    "protocol", "http/2",
                                    "direction", "bidi",
                                    "initiator", "remote"));
            long completed = counterCount(registry,
                                          "http.streams.closed",
                                          "role", "server",
                                          "protocol", "http/2",
                                          "direction", "bidi",
                                          "initiator", "remote",
                                          "outcome", "completed");
            long cancelled = counterCount(registry,
                                          "http.streams.closed",
                                          "role", "server",
                                          "protocol", "http/2",
                                          "direction", "bidi",
                                          "initiator", "remote",
                                          "outcome", "cancelled");
            assertThat(completed + cancelled,
                       is(counter(registry,
                                  "http.streams.opened",
                                  "role", "server",
                                  "protocol", "http/2",
                                  "direction", "bidi",
                                  "initiator", "remote").count()));
        } finally {
            observer.close();
        }
        assertRegistryEmpty(registry);
    }

    @Test
    void recordsQuicAndHttp3Vocabulary() {
        MeterRegistry registry = MeterRegistry.create();
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(registry);
        ConnectionObservation connection = observer.connectionOpened(CLIENT, TRANSPORT_QUIC, QUIC_TLS);
        connection.handshakeStarted().close(SUCCESS);
        connection.protocolSelected(PROTOCOL_HTTP_3);
        connection.streamOpened(UNIDIRECTIONAL, LOCAL).close(RESET);
        connection.close(ConnectionOutcome.TIMEOUT);

        assertThat(counter(registry,
                           "http.handshakes",
                           "role", "client",
                           "transport", "quic",
                           "handshake", "quic-tls",
                           "outcome", "success").count(),
                   is(1L));
        assertThat(counter(registry,
                           "http.streams.closed",
                           "role", "client",
                           "protocol", "http/3",
                           "direction", "uni",
                           "initiator", "local",
                           "outcome", "reset").count(),
                   is(1L));
        assertThat(counter(registry,
                           "http.connections.closed",
                           "role", "client",
                           "transport", "quic",
                           "protocol", "http/3",
                           "outcome", "timeout").count(),
                   is(1L));

        observer.close();
        assertRegistryEmpty(registry);
    }

    @Test
    void recordsHandshakeCompletionOnceAfterTransientRegistrationFailure() {
        MeterRegistry registry = MeterRegistry.create();
        TestRegistry failingRegistry = new TestRegistry(registry);
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(failingRegistry);
        ConnectionObservation connection = observer.connectionOpened(SERVER, TRANSPORT_TCP, TLS);
        HandshakeObservation handshake = connection.handshakeStarted();
        failingRegistry.failNextRegistration("http.handshakes.duration");

        assertDoesNotThrow(() -> handshake.close(SUCCESS));
        assertDoesNotThrow(() -> handshake.close(SUCCESS));
        assertThat(counter(registry,
                           "http.handshakes",
                           "role", "server",
                           "transport", "tcp",
                           "handshake", "tls",
                           "outcome", "success").count(),
                   is(1L));
        assertThat(timer(registry,
                         "http.handshakes.duration",
                         "role", "server",
                         "transport", "tcp",
                         "handshake", "tls",
                         "outcome", "success").count(),
                   is(1L));

        connection.close(NORMAL);
        assertThat(gauge(registry,
                         "http.connections.active",
                         "role", "server",
                         "transport", "tcp",
                         "protocol", "unknown").value().longValue(),
                   is(0L));
        observer.close();
        assertRegistryEmpty(registry);
    }

    @Test
    void recordsStreamCompletionOnceAfterTransientRegistrationFailure() {
        MeterRegistry registry = MeterRegistry.create();
        TestRegistry failingRegistry = new TestRegistry(registry);
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(failingRegistry);
        ConnectionObservation connection = observer.connectionOpened(SERVER, TRANSPORT_TCP, TLS);
        connection.protocolSelected(PROTOCOL_HTTP_2);
        StreamObservation stream = connection.streamOpened(BIDIRECTIONAL, REMOTE);
        failingRegistry.failNextRegistration("http.streams.duration");

        assertDoesNotThrow(() -> stream.close(COMPLETED));
        assertDoesNotThrow(() -> stream.close(COMPLETED));
        assertThat(counter(registry,
                           "http.streams.closed",
                           "role", "server",
                           "protocol", "http/2",
                           "direction", "bidi",
                           "initiator", "remote",
                           "outcome", "completed").count(),
                   is(1L));
        assertThat(timer(registry,
                         "http.streams.duration",
                         "role", "server",
                         "protocol", "http/2",
                         "direction", "bidi",
                         "initiator", "remote",
                         "outcome", "completed").count(),
                   is(1L));
        assertThat(gauge(registry,
                         "http.streams.active",
                         "role", "server",
                         "protocol", "http/2",
                         "direction", "bidi",
                         "initiator", "remote").value().longValue(),
                   is(0L));

        connection.close(NORMAL);
        assertThat(gauge(registry,
                         "http.connections.active",
                         "role", "server",
                         "transport", "tcp",
                         "protocol", "http/2").value().longValue(),
                   is(0L));
        observer.close();
        assertRegistryEmpty(registry);
    }

    @Test
    void recordsConnectionCompletionOnceAfterTransientRegistrationFailure() {
        MeterRegistry registry = MeterRegistry.create();
        TestRegistry failingRegistry = new TestRegistry(registry);
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(failingRegistry);
        ConnectionObservation connection = observer.connectionOpened(SERVER, TRANSPORT_TCP, TLS);
        connection.protocolSelected(PROTOCOL_HTTP_2);
        failingRegistry.failNextRegistration("http.connections.duration");

        assertDoesNotThrow(() -> connection.close(NORMAL));
        assertDoesNotThrow(() -> connection.close(NORMAL));
        assertThat(counter(registry,
                           "http.connections.closed",
                           "role", "server",
                           "transport", "tcp",
                           "protocol", "http/2",
                           "outcome", "normal").count(),
                   is(1L));
        assertThat(timer(registry,
                         "http.connections.duration",
                         "role", "server",
                         "transport", "tcp",
                         "protocol", "http/2",
                         "outcome", "normal").count(),
                   is(1L));
        assertThat(gauge(registry,
                         "http.connections.active",
                         "role", "server",
                         "transport", "tcp",
                         "protocol", "http/2").value().longValue(),
                   is(0L));

        observer.close();
        assertRegistryEmpty(registry);
    }

    @Test
    void retainsCanonicalActiveGaugesAfterTransientEnablementFailure() {
        MeterRegistry registry = MeterRegistry.create();
        TestRegistry failingRegistry = new TestRegistry(registry);
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(failingRegistry);
        ConnectionObservation connection = observer.connectionOpened(SERVER, TRANSPORT_TCP, TLS);
        failingRegistry.failNextEnablement("http.connections.active");
        connection.protocolSelected(PROTOCOL_HTTP_2);
        connection.protocolSelected(PROTOCOL_HTTP_1_1);
        connection.protocolSelected(PROTOCOL_HTTP_2);

        assertThat(gauge(registry,
                         "http.connections.active",
                         "role", "server",
                         "transport", "tcp",
                         "protocol", "http/2").value().longValue(),
                   is(1L));

        failingRegistry.failNextEnablement("http.streams.active");
        StreamObservation first = connection.streamOpened(BIDIRECTIONAL, REMOTE);
        StreamObservation second = connection.streamOpened(BIDIRECTIONAL, REMOTE);
        assertThat(gauge(registry,
                         "http.streams.active",
                         "role", "server",
                         "protocol", "http/2",
                         "direction", "bidi",
                         "initiator", "remote").value().longValue(),
                   is(2L));

        first.close(COMPLETED);
        second.close(COMPLETED);
        connection.close(NORMAL);
        observer.close();
        assertRegistryEmpty(registry);
    }

    @Test
    void isolatesClockFailureDuringLifecycleClose() {
        TestClock clock = new TestClock();
        MeterRegistry registry = MetricsFactory.getInstance().createMeterRegistry(clock, MetricsConfig.create());
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(registry);
        ConnectionObservation connection = observer.connectionOpened(SERVER, TRANSPORT_TCP, TLS);
        HandshakeObservation handshake = connection.handshakeStarted();
        connection.protocolSelected(PROTOCOL_HTTP_2);
        StreamObservation stream = connection.streamOpened(BIDIRECTIONAL, REMOTE);
        clock.failMonotonicTime = true;

        try {
            assertDoesNotThrow(() -> handshake.close(SUCCESS));
            assertDoesNotThrow(() -> stream.close(COMPLETED));
            assertDoesNotThrow(() -> connection.close(NORMAL));
        } finally {
            observer.close();
        }
        assertRegistryEmpty(registry);
    }

    @Test
    void recordsZeroDurationAfterLifecycleStartClockFailure() {
        TestClock clock = new TestClock();
        MeterRegistry registry = MetricsFactory.getInstance().createMeterRegistry(clock, MetricsConfig.create());
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(registry);
        clock.failMonotonicTime = true;
        ConnectionObservation connection = observer.connectionOpened(SERVER, TRANSPORT_TCP, TLS);
        clock.failMonotonicTime = false;
        clock.advance(20);

        connection.close(NORMAL);
        assertThat(timer(registry,
                         "http.connections.duration",
                         "role", "server",
                         "transport", "tcp",
                         "protocol", "unknown",
                         "outcome", "normal").totalTime(TimeUnit.NANOSECONDS),
                   is(0.0));
        observer.close();
        assertRegistryEmpty(registry);
    }

    @Test
    void allowsMeterRegistrationToReenterSameConnectionKey() {
        MeterRegistry registry = MeterRegistry.create();
        TestRegistry reentrantRegistry = new TestRegistry(registry);
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(reentrantRegistry);
        reentrantRegistry.registrationCallback = () -> {
            ConnectionObservation nested = observer.connectionOpened(SERVER, TRANSPORT_TCP, TLS);
            nested.close(NORMAL);
        };
        reentrantRegistry.callbackRegistration = meter -> meter.id().name().equals("http.connections.opened");

        assertDoesNotThrow(() -> observer.connectionOpened(SERVER, TRANSPORT_TCP, TLS).close(NORMAL));
        assertThat(counter(registry,
                           "http.connections.opened",
                           2,
                           "role", "server",
                           "transport", "tcp",
                           "handshake", "tls").count(),
                   is(2L));
        assertThat(counter(registry,
                           "http.connections.closed",
                           2,
                           "role", "server",
                           "transport", "tcp",
                           "protocol", "unknown",
                           "outcome", "normal").count(),
                   is(2L));
        assertThat(gauge(registry,
                         "http.connections.active",
                         "role", "server",
                         "transport", "tcp",
                         "protocol", "unknown").value().longValue(),
                   is(0L));

        observer.close();
        assertRegistryEmpty(registry);
    }

    private static void assertRegistryEmpty(MeterRegistry registry) {
        await(() -> registry.meters().isEmpty());
        assertThat(registry.meters(), empty());
    }

    private static Counter counter(MeterRegistry registry, String name, String... tagPairs) {
        List<Tag> tags = tags(tagPairs);
        await(() -> registry.counter(name, tags).map(value -> value.count() > 0).orElse(false));
        return registry.counter(name, tags).orElseThrow();
    }

    private static Counter counter(MeterRegistry registry, String name, long expected, String... tagPairs) {
        List<Tag> tags = tags(tagPairs);
        await(() -> registry.counter(name, tags).map(value -> value.count() == expected).orElse(false));
        return registry.counter(name, tags).orElseThrow();
    }

    private static Gauge<?> gauge(MeterRegistry registry, String name, String... tagPairs) {
        List<Tag> tags = tags(tagPairs);
        await(() -> registry.gauge(name, tags).isPresent());
        return registry.gauge(name, tags).orElseThrow();
    }

    private static Timer timer(MeterRegistry registry, String name, String... tagPairs) {
        List<Tag> tags = tags(tagPairs);
        await(() -> registry.timer(name, tags).map(value -> value.count() > 0).orElse(false));
        return registry.timer(name, tags).orElseThrow();
    }

    private static long counterCount(MeterRegistry registry, String name, String... tagPairs) {
        return registry.counter(name, tags(tagPairs)).map(Counter::count).orElse(0L);
    }

    private static void await(BooleanSupplier condition) {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            while (!condition.getAsBoolean()) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                if (Thread.currentThread().isInterrupted()) {
                    throw new IllegalStateException("Interrupted while waiting for transport metrics");
                }
            }
        });
    }

    private static List<Tag> tags(String... pairs) {
        List<Tag> result = new ArrayList<>(pairs.length / 2);
        for (int i = 0; i < pairs.length; i += 2) {
            result.add(Tag.create(pairs[i], pairs[i + 1]));
        }
        return result;
    }

    private static final class TestClock implements Clock {
        private final AtomicLong monotonicTime = new AtomicLong();
        private boolean failMonotonicTime;

        @Override
        public long wallTime() {
            return 0;
        }

        @Override
        public long monotonicTime() {
            if (failMonotonicTime) {
                throw new IllegalStateException("Clock failure");
            }
            return monotonicTime.get();
        }

        private void advance(long nanoseconds) {
            monotonicTime.addAndGet(nanoseconds);
        }
    }

    private static final class TestRegistry implements MeterRegistry {
        private final MeterRegistry delegate;
        private volatile Predicate<Meter> failingRegistration = ignored -> false;
        private volatile Predicate<Meter> callbackRegistration = ignored -> false;
        private volatile boolean failNextRegistration;
        private volatile boolean failNextEnablement;
        private volatile String failingEnablementName;
        private volatile Runnable registrationCallback;

        private TestRegistry(MeterRegistry delegate) {
            this.delegate = delegate;
        }

        private void failNextRegistration(String name) {
            failNextRegistration = true;
            failingRegistration = meter -> meter.id().name().equals(name);
        }

        private void failNextEnablement(String name) {
            failNextEnablement = true;
            failingEnablementName = name;
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
            if (failNextEnablement && name.equals(failingEnablementName)) {
                failNextEnablement = false;
                throw new IllegalStateException("Meter enablement failure");
            }
            return delegate.isMeterEnabled(name, tags, scope);
        }

        @Override
        public Clock clock() {
            return delegate.clock();
        }

        @Override
        public <B extends Meter.Builder<B, M>, M extends Meter> M getOrCreate(B builder) {
            M meter = delegate.getOrCreate(builder);
            if (failNextRegistration && failingRegistration.test(meter)) {
                failNextRegistration = false;
                throw new IllegalStateException("Meter registration failure");
            }
            Runnable callback = registrationCallback;
            if (callback != null && callbackRegistration.test(meter)) {
                registrationCallback = null;
                callback.run();
            }
            return meter;
        }

        @Override
        public <M extends Meter> Optional<M> meter(Class<M> meterClass, String name, Iterable<Tag> tags) {
            return delegate.meter(meterClass, name, tags);
        }

        @Override
        public Optional<Meter> remove(Meter meter) {
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
            if (delegate.onMeterAdded(listener) == null) {
                throw new IllegalStateException("Meter registry did not accept the listener");
            }
            return this;
        }

        @Override
        public MeterRegistry onMeterRemoved(Consumer<Meter> listener) {
            if (delegate.onMeterRemoved(listener) == null) {
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
