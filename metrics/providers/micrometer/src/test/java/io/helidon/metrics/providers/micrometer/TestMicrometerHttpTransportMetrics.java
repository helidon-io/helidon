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

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
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

import io.micrometer.core.instrument.Gauge;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.helidon.http.HttpTransportObserver.ConnectionOutcome.NORMAL;
import static io.helidon.http.HttpTransportObserver.Direction.BIDIRECTIONAL;
import static io.helidon.http.HttpTransportObserver.Handshake.NONE;
import static io.helidon.http.HttpTransportObserver.Initiator.REMOTE;
import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_2;
import static io.helidon.http.HttpTransportObserver.Role.SERVER;
import static io.helidon.http.HttpTransportObserver.StreamOutcome.COMPLETED;
import static io.helidon.http.HttpTransportObserver.TRANSPORT_TCP;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class TestMicrometerHttpTransportMetrics {
    private static final Set<String> TRANSPORT_METER_NAMES = Set.of("http.connections.opened",
                                                                    "http.connections.established",
                                                                    "http.connections.active",
                                                                    "http.connections.closed",
                                                                    "http.connections.duration",
                                                                    "http.handshakes",
                                                                    "http.handshakes.duration",
                                                                    "http.streams.opened",
                                                                    "http.streams.active",
                                                                    "http.streams.closed",
                                                                    "http.streams.duration");

    @BeforeAll
    static void initializeMetricsProvider() {
        MetricsFactory.getInstance().globalRegistry();
    }

    @AfterEach
    void releasesAllHttpTransportMeters() {
        io.micrometer.core.instrument.MeterRegistry registry =
                (io.micrometer.core.instrument.MeterRegistry) MeterRegistry.create().unwrap(Object.class);
        await(() -> registry.getMeters().stream()
                .noneMatch(meter -> TRANSPORT_METER_NAMES.contains(meter.getId().getName())));
        assertThat(registry.getMeters().stream()
                           .noneMatch(meter -> TRANSPORT_METER_NAMES.contains(meter.getId().getName())),
                   is(true));
    }

    @Test
    void sharesMetersAcrossWrappersForOneMicrometerRegistry() {
        MeterRegistry firstRegistry = MeterRegistry.create();
        MeterRegistry secondRegistry = MeterRegistry.create();
        Object delegate = firstRegistry.unwrap(Object.class);
        assertThat(delegate, instanceOf(io.micrometer.core.instrument.MeterRegistry.class));
        assertThat(secondRegistry.unwrap(Object.class), sameInstance(delegate));

        HttpTransportMetrics.Lease first = HttpTransportMetrics.acquire(firstRegistry);
        HttpTransportMetrics.Lease second = HttpTransportMetrics.acquire(secondRegistry);
        ConnectionObservation firstConnection = first.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
        ConnectionObservation secondConnection = second.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
        firstConnection.protocolSelected(PROTOCOL_HTTP_2);
        secondConnection.protocolSelected(PROTOCOL_HTTP_2);

        assertThat(nativeGauge(delegate, 2), is(2.0));
        firstConnection.close(NORMAL);
        assertThat(nativeGauge(delegate, 1), is(1.0));

        first.close();
        assertThat(nativeGauge(delegate, 1), is(1.0));

        secondConnection.close(NORMAL);
        assertThat(nativeGauge(delegate, 0), is(0.0));
        second.close();
        assertEventuallyNull(() -> nativeGaugeOrNull(delegate));
    }

    @Test
    void retainsCumulativeMetersForIdleWrapperLease() {
        MeterRegistry firstRegistry = MeterRegistry.create();
        MeterRegistry secondRegistry = MeterRegistry.create();
        Object delegate = firstRegistry.unwrap(Object.class);
        assertThat(secondRegistry.unwrap(Object.class), sameInstance(delegate));
        HttpTransportMetrics.Lease first = HttpTransportMetrics.acquire(firstRegistry);
        HttpTransportMetrics.Lease second = HttpTransportMetrics.acquire(secondRegistry);

        first.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);
        first.close();
        assertThat(nativeOpenedCounter(delegate).count(), is(1.0));

        second.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);
        assertThat(nativeOpenedCounter(delegate, 2).count(), is(2.0));
        second.close();
        assertEventuallyNull(() -> nativeOpenedCounterOrNull(delegate));
    }

    @Test
    void sharesGaugeDuringConcurrentFirstRegistration() {
        MeterRegistry firstRegistry = MeterRegistry.create();
        MeterRegistry secondRegistry = MeterRegistry.create();
        Object delegate = firstRegistry.unwrap(Object.class);
        assertThat(delegate, instanceOf(io.micrometer.core.instrument.MeterRegistry.class));
        assertThat(secondRegistry.unwrap(Object.class), sameInstance(delegate));
        HttpTransportMetrics.Lease first = HttpTransportMetrics.acquire(firstRegistry);
        HttpTransportMetrics.Lease second = HttpTransportMetrics.acquire(secondRegistry);
        AtomicReference<ConnectionObservation> firstConnection = new AtomicReference<>();
        AtomicReference<ConnectionObservation> secondConnection = new AtomicReference<>();
        Phaser registrationStart = new Phaser(3);

        CompletableFuture<Void> firstRegistration = CompletableFuture.runAsync(() -> {
            registrationStart.arriveAndAwaitAdvance();
            ConnectionObservation connection = first.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
            connection.protocolSelected(PROTOCOL_HTTP_2);
            firstConnection.set(connection);
        });
        CompletableFuture<Void> secondRegistration = CompletableFuture.runAsync(() -> {
            registrationStart.arriveAndAwaitAdvance();
            ConnectionObservation connection = second.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
            connection.protocolSelected(PROTOCOL_HTTP_2);
            secondConnection.set(connection);
        });
        registrationStart.arriveAndAwaitAdvance();
        CompletableFuture.allOf(firstRegistration, secondRegistration).join();

        assertThat(nativeGauge(delegate, 2), is(2.0));
        firstConnection.get().close(NORMAL);
        secondConnection.get().close(NORMAL);
        first.close();
        second.close();
        assertEventuallyNull(() -> nativeGaugeOrNull(delegate));
    }

    @Test
    void preservesConfigurationAcrossWrappersForOneMicrometerRegistry() {
        Config disabledConfig = Config.just(ConfigSources.create(Map.of("enabled", "false")));
        MeterRegistry disabledRegistry = MeterRegistry.create(MetricsConfig.create(disabledConfig));
        MeterRegistry enabledRegistry = MeterRegistry.create();
        Object delegate = disabledRegistry.unwrap(Object.class);
        assertThat(delegate, instanceOf(io.micrometer.core.instrument.MeterRegistry.class));
        assertThat(enabledRegistry.unwrap(Object.class), sameInstance(delegate));

        HttpTransportMetrics.Lease disabled = HttpTransportMetrics.acquire(disabledRegistry);
        HttpTransportMetrics.Lease enabled = HttpTransportMetrics.acquire(enabledRegistry);
        ConnectionObservation disabledConnection = disabled.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
        ConnectionObservation enabledConnection = enabled.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
        disabledConnection.protocolSelected(PROTOCOL_HTTP_2);
        enabledConnection.protocolSelected(PROTOCOL_HTTP_2);

        assertThat(nativeGauge(delegate, 1), is(1.0));
        disabledConnection.close(NORMAL);
        assertThat(nativeGauge(delegate, 1), is(1.0));
        disabled.close();
        assertThat(nativeGauge(delegate, 1), is(1.0));

        enabledConnection.close(NORMAL);
        assertThat(nativeGauge(delegate, 0), is(0.0));
        enabled.close();
        assertEventuallyNull(() -> nativeGaugeOrNull(delegate));
    }

    @Test
    void preservesDisabledWrapperAfterTransientEnablementFailure() {
        Config disabledConfig = Config.just(ConfigSources.create(Map.of("enabled", "false")));
        MeterRegistry disabledRegistry = MeterRegistry.create(MetricsConfig.create(disabledConfig));
        MeterRegistry enabledRegistry = MeterRegistry.create();
        TestRegistry failingDisabledRegistry = new TestRegistry(disabledRegistry);
        Object delegate = disabledRegistry.unwrap(Object.class);
        assertThat(enabledRegistry.unwrap(Object.class), sameInstance(delegate));

        HttpTransportMetrics.Lease disabled = HttpTransportMetrics.acquire(failingDisabledRegistry);
        HttpTransportMetrics.Lease enabled = HttpTransportMetrics.acquire(enabledRegistry);
        ConnectionObservation disabledConnection = disabled.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
        ConnectionObservation enabledConnection = enabled.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
        failingDisabledRegistry.failNextEnablementName = "http.connections.active";
        disabledConnection.protocolSelected(PROTOCOL_HTTP_2);
        enabledConnection.protocolSelected(PROTOCOL_HTTP_2);

        assertThat(nativeGauge(delegate, 1), is(1.0));
        failingDisabledRegistry.failNextEnablementName = "http.streams.active";
        StreamObservation disabledStream = disabledConnection.streamOpened(BIDIRECTIONAL, REMOTE);
        StreamObservation enabledStream = enabledConnection.streamOpened(BIDIRECTIONAL, REMOTE);
        assertThat(nativeStreamGauge(delegate, 1).value(), is(1.0));

        disabledStream.close(COMPLETED);
        enabledStream.close(COMPLETED);
        disabledConnection.close(NORMAL);
        enabledConnection.close(NORMAL);
        disabled.close();
        enabled.close();
        assertEventuallyNull(() -> nativeGaugeOrNull(delegate));
    }

    @Test
    void completesAfterGaugeRemovalFailure() {
        MeterRegistry registry = MeterRegistry.create();
        TestRegistry failingRegistry = new TestRegistry(registry);
        failingRegistry.failGaugeRemovals = true;
        Object delegate = registry.unwrap(Object.class);
        assertThat(delegate, instanceOf(io.micrometer.core.instrument.MeterRegistry.class));
        HttpTransportMetrics.Lease first = HttpTransportMetrics.acquire(failingRegistry);
        CompletableFuture<Void> firstCompletion = first.completion().toCompletableFuture();
        ConnectionObservation firstConnection = first.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
        firstConnection.protocolSelected(PROTOCOL_HTTP_2);
        firstConnection.close(NORMAL);

        first.close();
        await(firstCompletion::isDone);
        assertThat(nativeGauge(delegate, 0), is(0.0));

        failingRegistry.failGaugeRemovals = false;
        HttpTransportMetrics.Lease second = HttpTransportMetrics.acquire(failingRegistry);
        CompletableFuture<Void> secondCompletion = second.completion().toCompletableFuture();
        assertThat(secondCompletion.isDone(), is(false));
        ConnectionObservation secondConnection = second.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
        secondConnection.protocolSelected(PROTOCOL_HTTP_2);
        assertThat(nativeGauge(delegate, 1), is(1.0));

        secondConnection.close(NORMAL);
        second.close();
        await(secondCompletion::isDone);
        assertEventuallyNull(() -> nativeGaugeOrNull(delegate));
    }

    @Test
    void retainsGaugeCandidateAfterAddedListenerFailure() {
        MeterRegistry registry = MeterRegistry.create();
        TestRegistry failingRegistry = new TestRegistry(registry);
        failingRegistry.failNextRegistration = true;
        failingRegistry.failingRegistration = meter -> meter.id().name().equals("http.connections.active")
                && meter.id().tagsMap().get("protocol").equals("unknown");
        Object delegate = registry.unwrap(Object.class);
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(failingRegistry);

        ConnectionObservation connection = observer.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
        assertThat(nativeConnectionGauge(delegate, "unknown", 1).value(), is(1.0));

        connection.close(NORMAL);
        assertThat(nativeConnectionGauge(delegate, "unknown", 0).value(), is(0.0));
        observer.close();
        assertEventuallyNull(() -> nativeConnectionGaugeOrNull(delegate, "unknown"));
    }

    @Test
    void removesGaugeAfterAddedListenerFailureWithoutRetry() {
        MeterRegistry registry = MeterRegistry.create();
        TestRegistry failingRegistry = new TestRegistry(registry);
        failingRegistry.failNextRegistration = true;
        failingRegistry.failingRegistration = meter -> meter.id().name().equals("http.connections.active")
                && meter.id().tagsMap().get("protocol").equals("unknown");
        Object delegate = registry.unwrap(Object.class);
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(failingRegistry);

        observer.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);
        observer.close();
        assertEventuallyNull(() -> nativeConnectionGaugeOrNull(delegate, "unknown"));
    }

    @Test
    void removesCounterAfterAddedListenerFailureWithoutRetry() {
        MeterRegistry registry = MeterRegistry.create();
        TestRegistry failingRegistry = new TestRegistry(registry);
        failingRegistry.failNextRegistration = true;
        failingRegistry.failingRegistration = meter -> meter.id().name().equals("http.connections.opened");
        Object delegate = registry.unwrap(Object.class);
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(failingRegistry);

        observer.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);
        assertThat(nativeOpenedCounter(delegate).count(), is(1.0));
        observer.close();
        assertEventuallyNull(() -> nativeOpenedCounterOrNull(delegate));
    }

    @Test
    void keepsProtocolGaugeTransactionalAfterRegistrationFailure() {
        MeterRegistry registry = MeterRegistry.create();
        TestRegistry failingRegistry = new TestRegistry(registry);
        failingRegistry.failNextRegistration = true;
        failingRegistry.failingRegistration = meter -> meter.id().name().equals("http.connections.active")
                && meter.id().tagsMap().get("protocol").equals("http/2");
        Object delegate = registry.unwrap(Object.class);
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(failingRegistry);
        ConnectionObservation connection = observer.connectionOpened(SERVER, TRANSPORT_TCP, NONE);

        connection.protocolSelected(PROTOCOL_HTTP_2);
        assertThat(nativeConnectionGauge(delegate, "unknown", 0).value(), is(0.0));
        assertThat(nativeConnectionGauge(delegate, "http/2", 1).value(), is(1.0));

        connection.close(NORMAL);
        assertThat(nativeConnectionGauge(delegate, "http/2", 0).value(), is(0.0));
        observer.close();
    }

    @Test
    void keepsStreamGaugeTransactionalAfterRegistrationFailure() {
        MeterRegistry registry = MeterRegistry.create();
        TestRegistry failingRegistry = new TestRegistry(registry);
        failingRegistry.failNextRegistration = true;
        failingRegistry.failingRegistration = meter -> meter.id().name().equals("http.streams.active");
        Object delegate = registry.unwrap(Object.class);
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(failingRegistry);
        ConnectionObservation connection = observer.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
        connection.protocolSelected(PROTOCOL_HTTP_2);

        StreamObservation stream = connection.streamOpened(BIDIRECTIONAL, REMOTE);
        assertThat(nativeStreamOpenedCounter(delegate).count(), is(1.0));
        assertThat(nativeStreamGauge(delegate, 1).value(), is(1.0));

        stream.close(COMPLETED);
        connection.close(NORMAL);
        assertThat(nativeStreamGauge(delegate, 0).value(), is(0.0));
        observer.close();
    }

    @Test
    void completesConnectionStateAfterChildMeterFailure() {
        MeterRegistry registry = MeterRegistry.create();
        TestRegistry failingRegistry = new TestRegistry(registry);
        Object delegate = registry.unwrap(Object.class);
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(failingRegistry);
        ConnectionObservation connection = observer.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
        connection.protocolSelected(PROTOCOL_HTTP_2);
        connection.handshakeStarted();
        connection.streamOpened(BIDIRECTIONAL, REMOTE);
        failingRegistry.failNextRegistration = true;
        failingRegistry.failingRegistration = meter -> meter.id().name().equals("http.handshakes");

        connection.close(NORMAL);
        assertThat(nativeConnectionGauge(delegate, "http/2", 0).value(), is(0.0));
        assertThat(nativeStreamGauge(delegate, 0).value(), is(0.0));

        observer.close();
        await(() -> registry.meters().isEmpty());
        assertThat(registry.meters().isEmpty(), is(true));
    }

    @Test
    void recoveryDoesNotAdoptApplicationMeterWithVendorId() {
        MeterRegistry registry = MeterRegistry.create();
        List<Tag> tags = List.of(Tag.create("role", "server"),
                                 Tag.create("transport", "tcp"),
                                 Tag.create("handshake", "none"));
        Counter applicationCounter = registry.getOrCreate(Counter.builder("http.connections.opened")
                                                                  .scope(Meter.Scope.APPLICATION)
                                                                  .tags(tags));
        applicationCounter.increment(7);
        Object delegate = registry.unwrap(Object.class);
        TestRegistry failingRegistry = new TestRegistry(registry);
        failingRegistry.lookupOverrideName = "http.connections.opened";
        failingRegistry.lookupOverride = applicationCounter;
        failingRegistry.remainingRegistrationFailures = 1;
        failingRegistry.failingRegistration = meter -> meter.id().name().equals("http.connections.opened")
                && meter.scope().orElse("").equals(Meter.Scope.VENDOR);
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(failingRegistry);

        try {
            observer.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);
            observer.close();

            io.micrometer.core.instrument.Counter retainedApplicationCounter =
                    nativeOpenedCounterForScope(delegate, Meter.Scope.APPLICATION);
            assertThat(retainedApplicationCounter, is(sameInstance(applicationCounter.unwrap(Object.class))));
            assertThat(retainedApplicationCounter.count(), is(7.0));
            await(() -> failingRegistry.remainingRegistrationFailures == 0);
            assertEventuallyNull(() -> nativeOpenedCounterForScope(delegate, Meter.Scope.VENDOR));
        } finally {
            observer.close();
            registry.remove("http.connections.opened", tags, Meter.Scope.APPLICATION);
            registry.remove("http.connections.opened", tags, Meter.Scope.VENDOR);
        }
    }

    @Test
    void allowsAddListenerToReenterTransportMetricsFromAnotherThread() {
        MeterRegistry registry = MeterRegistry.create();
        TestRegistry reentrantRegistry = new TestRegistry(registry);
        AtomicBoolean firstRegistration = new AtomicBoolean(true);
        AtomicBoolean completedDuringCallback = new AtomicBoolean();
        AtomicReference<CompletableFuture<Void>> nestedRegistration = new AtomicReference<>();
        MeterRegistry nestedRegistry = MeterRegistry.create();
        reentrantRegistry.registrationCallback = meter -> {
            if (!meter.id().name().equals("http.connections.opened")
                    || !firstRegistration.compareAndSet(true, false)) {
                return;
            }
            CountDownLatch completed = new CountDownLatch(1);
            nestedRegistration.set(CompletableFuture.runAsync(() -> {
                try (HttpTransportMetrics.Lease nested = HttpTransportMetrics.acquire(nestedRegistry)) {
                    ConnectionObservation connection = nested.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
                    connection.protocolSelected(PROTOCOL_HTTP_2);
                    connection.close(NORMAL);
                } finally {
                    completed.countDown();
                }
            }));
            try {
                completedDuringCallback.set(completed.await(1, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(reentrantRegistry);

        try {
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                observer.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);
                await(() -> nestedRegistration.get() != null);
                nestedRegistration.get().join();
            });
            await(completedDuringCallback::get);
            assertThat(completedDuringCallback.get(), is(true));
            assertThat(nativeOpenedCounter(registry.unwrap(Object.class), 2).count(), is(2.0));
            assertThat(nativeEstablishedCounter(registry.unwrap(Object.class)).count(), is(1.0));
            assertThat(nativeConnectionGauge(registry.unwrap(Object.class), "http/2", 0).value(), is(0.0));
        } finally {
            reentrantRegistry.registrationCallback = null;
            CompletableFuture<Void> nested = nestedRegistration.get();
            if (nested != null) {
                nested.join();
            }
            observer.close();
        }
    }

    @Test
    void repeatedRegistrationFailuresUseOneRemovalAttempt() {
        MeterRegistry registry = MeterRegistry.create();
        TestRegistry failingRegistry = new TestRegistry(registry);
        failingRegistry.hideMeters = true;
        failingRegistry.remainingRegistrationFailures = 6;
        failingRegistry.failingRegistration = meter -> meter.id().name().equals("http.connections.opened");
        failingRegistry.countedRemovalName = "http.connections.opened";
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(failingRegistry);

        for (int i = 0; i < 3; i++) {
            observer.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);
        }
        failingRegistry.hideMeters = false;
        observer.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);
        observer.close();

        await(() -> failingRegistry.countedRemovalAttempts.get() == 1);
        assertThat(failingRegistry.countedRemovalAttempts.get(), is(1));
        assertEventuallyNull(() -> nativeOpenedCounterOrNull(registry.unwrap(Object.class)));
    }

    @Test
    void coldGaugeEnablementAndLeaseCleanupStayOffTransportThread() {
        MeterRegistry registry = MeterRegistry.create();
        TestRegistry blockingRegistry = new TestRegistry(registry);
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        blockingRegistry.enablementCallback = name -> {
            if (name.equals("http.connections.active")) {
                providerEntered.countDown();
                try {
                    releaseProvider.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(blockingRegistry);
        AtomicReference<ConnectionObservation> connection = new AtomicReference<>();
        CompletableFuture<Void> completion = observer.completion().toCompletableFuture();

        try {
            assertTimeoutPreemptively(Duration.ofSeconds(1),
                                      () -> connection.set(observer.connectionOpened(SERVER, TRANSPORT_TCP, NONE)));
            await(() -> providerEntered.getCount() == 0);
            connection.get().close(NORMAL);
            observer.close();
            assertThat(completion.isDone(), is(false));
        } finally {
            releaseProvider.countDown();
            ConnectionObservation opened = connection.get();
            if (opened != null) {
                opened.close(NORMAL);
            }
            observer.close();
        }

        await(completion::isDone);
        assertThat(registry.meters().isEmpty(), is(true));
    }

    @Test
    void boundsProviderBacklogWithoutBlockingTransportCallbacks() {
        MeterRegistry registry = MeterRegistry.create();
        TestRegistry blockingRegistry = new TestRegistry(registry);
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        AtomicBoolean blockOnce = new AtomicBoolean(true);
        blockingRegistry.registrationCallback = meter -> {
            if (meter.id().name().equals("http.connections.opened") && blockOnce.compareAndSet(true, false)) {
                providerEntered.countDown();
                try {
                    releaseProvider.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(blockingRegistry);
        HttpTransportMetrics.Lease keeper = HttpTransportMetrics.acquire(blockingRegistry);
        ConnectionObservation[] connections = new ConnectionObservation[1100];

        try {
            assertTimeoutPreemptively(Duration.ofSeconds(1),
                                      () -> connections[0] = observer.connectionOpened(SERVER, TRANSPORT_TCP, NONE));
            await(() -> providerEntered.getCount() == 0);
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                for (int i = 1; i < connections.length; i++) {
                    connections[i] = observer.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
                }
                for (ConnectionObservation connection : connections) {
                    connection.close(NORMAL);
                }
                observer.close();
            });
            releaseProvider.countDown();
            assertThat(nativeOpenedCounter(registry.unwrap(Object.class), 1024).count(), is(1024.0));
        } finally {
            releaseProvider.countDown();
            for (ConnectionObservation connection : connections) {
                if (connection != null) {
                    connection.close(NORMAL);
                }
            }
            observer.close();
            keeper.close();
        }
        assertEventuallyNull(() -> nativeOpenedCounterOrNull(registry.unwrap(Object.class)));
    }

    @Test
    void admitsCleanupAtNormalProviderActionLimit() {
        MeterRegistry registry = MeterRegistry.create();
        TestRegistry blockingRegistry = new TestRegistry(registry);
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        AtomicBoolean blockOnce = new AtomicBoolean(true);
        blockingRegistry.registrationCallback = meter -> {
            if (meter.id().name().equals("http.connections.opened") && blockOnce.compareAndSet(true, false)) {
                providerEntered.countDown();
                try {
                    releaseProvider.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        blockingRegistry.countedRemovalName = "http.connections.opened";
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(blockingRegistry);
        CompletableFuture<Void> completion = observer.completion().toCompletableFuture();
        ConnectionObservation[] connections = new ConnectionObservation[1024];

        try {
            connections[0] = observer.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
            await(() -> providerEntered.getCount() == 0);
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                for (int i = 1; i < connections.length; i++) {
                    connections[i] = observer.connectionOpened(SERVER, TRANSPORT_TCP, NONE);
                }
                for (ConnectionObservation connection : connections) {
                    connection.close(NORMAL);
                }
                observer.close();
            });
            releaseProvider.countDown();
            await(() -> blockingRegistry.countedRemovalAttempts.get() == 1);
            await(completion::isDone);
            assertThat(blockingRegistry.countedRemovalAttempts.get(), is(1));
            assertThat(nativeOpenedCounterOrNull(registry.unwrap(Object.class)), nullValue());
        } finally {
            releaseProvider.countDown();
            for (ConnectionObservation connection : connections) {
                if (connection != null) {
                    connection.close(NORMAL);
                }
            }
            observer.close();
        }
    }

    @Test
    void persistentRemovalActivityDoesNotLoopLeaseClose() {
        MeterRegistry registry = MeterRegistry.create();
        TestRegistry reentrantRegistry = new TestRegistry(registry);
        AtomicInteger removalCallbacks = new AtomicInteger();
        reentrantRegistry.persistentRemovalCallback = true;
        reentrantRegistry.removalCallback = () -> {
            if (removalCallbacks.incrementAndGet() < 32) {
                try (HttpTransportMetrics.Lease nested = HttpTransportMetrics.acquire(reentrantRegistry)) {
                    nested.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);
                }
            }
        };
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(reentrantRegistry);
        observer.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);

        try {
            assertTimeoutPreemptively(Duration.ofSeconds(5), observer::close);
            await(() -> removalCallbacks.get() >= 32);
            await(() -> registry.meters().isEmpty());
            assertThat(removalCallbacks.get() >= 32, is(true));
        } finally {
            reentrantRegistry.removalCallback = null;
            observer.close();
            try (HttpTransportMetrics.Lease cleanup = HttpTransportMetrics.acquire(reentrantRegistry)) {
                // Trigger final cleanup after disabling the deliberately persistent listener.
            }
        }
    }

    @Test
    void allowsRemovalListenerToReenterTransportMetrics() {
        MeterRegistry registry = MeterRegistry.create();
        TestRegistry reentrantRegistry = new TestRegistry(registry);
        reentrantRegistry.removalCallback = () -> {
            try (HttpTransportMetrics.Lease nested = HttpTransportMetrics.acquire(reentrantRegistry)) {
                nested.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);
            }
        };
        HttpTransportMetrics.Lease observer = HttpTransportMetrics.acquire(reentrantRegistry);
        observer.connectionOpened(SERVER, TRANSPORT_TCP, NONE).close(NORMAL);
        nativeOpenedCounter(registry.unwrap(Object.class));
        AtomicBoolean nestedRegistered = new AtomicBoolean();
        reentrantRegistry.registrationCallback = meter -> {
            if (meter.id().name().equals("http.connections.opened")) {
                nestedRegistered.set(true);
            }
        };

        assertTimeoutPreemptively(Duration.ofSeconds(5), observer::close);
        await(nestedRegistered::get);
        reentrantRegistry.registrationCallback = null;
        await(() -> registry.meters().isEmpty());
        assertThat(registry.meters().isEmpty(), is(true));
    }

    private static double nativeGauge(Object registry, double expected) {
        await(() -> {
            Gauge gauge = nativeGaugeOrNull(registry);
            return gauge != null && gauge.value() == expected;
        });
        return nativeGaugeOrNull(registry).value();
    }

    private static Gauge nativeGaugeOrNull(Object registry) {
        return nativeConnectionGaugeOrNull(registry, "http/2");
    }

    private static Gauge nativeConnectionGauge(Object registry, String protocol, double expected) {
        await(() -> {
            Gauge gauge = nativeConnectionGaugeOrNull(registry, protocol);
            return gauge != null && gauge.value() == expected;
        });
        return nativeConnectionGaugeOrNull(registry, protocol);
    }

    private static Gauge nativeConnectionGaugeOrNull(Object registry, String protocol) {
        return ((io.micrometer.core.instrument.MeterRegistry) registry)
                .find("http.connections.active")
                .tags("role", "server", "transport", "tcp", "protocol", protocol)
                .gauge();
    }

    private static io.micrometer.core.instrument.Counter nativeOpenedCounter(Object registry) {
        await(() -> {
            io.micrometer.core.instrument.Counter counter = nativeOpenedCounterOrNull(registry);
            return counter != null && counter.count() > 0;
        });
        return nativeOpenedCounterOrNull(registry);
    }

    private static io.micrometer.core.instrument.Counter nativeOpenedCounter(Object registry, double expected) {
        await(() -> {
            io.micrometer.core.instrument.Counter counter = nativeOpenedCounterOrNull(registry);
            return counter != null && counter.count() == expected;
        });
        return nativeOpenedCounterOrNull(registry);
    }

    private static io.micrometer.core.instrument.Counter nativeOpenedCounterOrNull(Object registry) {
        return ((io.micrometer.core.instrument.MeterRegistry) registry)
                .find("http.connections.opened")
                .tags("role", "server", "transport", "tcp", "handshake", "none")
                .counter();
    }

    private static io.micrometer.core.instrument.Counter nativeOpenedCounterForScope(Object registry, String scope) {
        return ((io.micrometer.core.instrument.MeterRegistry) registry)
                .find("http.connections.opened")
                .tags("role", "server", "transport", "tcp", "handshake", "none", "scope", scope)
                .counter();
    }

    private static io.micrometer.core.instrument.Counter nativeEstablishedCounter(Object registry) {
        io.micrometer.core.instrument.MeterRegistry meterRegistry =
                (io.micrometer.core.instrument.MeterRegistry) registry;
        await(() -> {
            io.micrometer.core.instrument.Counter counter = meterRegistry
                    .find("http.connections.established")
                    .tags("role", "server", "transport", "tcp", "protocol", "http/2")
                    .counter();
            return counter != null && counter.count() > 0;
        });
        return meterRegistry.find("http.connections.established")
                .tags("role", "server", "transport", "tcp", "protocol", "http/2")
                .counter();
    }

    private static io.micrometer.core.instrument.Counter nativeStreamOpenedCounter(Object registry) {
        io.micrometer.core.instrument.MeterRegistry meterRegistry =
                (io.micrometer.core.instrument.MeterRegistry) registry;
        await(() -> {
            io.micrometer.core.instrument.Counter counter = meterRegistry
                    .find("http.streams.opened")
                    .tags("role", "server", "protocol", "http/2", "direction", "bidi", "initiator", "remote")
                    .counter();
            return counter != null && counter.count() > 0;
        });
        return meterRegistry.find("http.streams.opened")
                .tags("role", "server", "protocol", "http/2", "direction", "bidi", "initiator", "remote")
                .counter();
    }

    private static Gauge nativeStreamGauge(Object registry, double expected) {
        io.micrometer.core.instrument.MeterRegistry meterRegistry =
                (io.micrometer.core.instrument.MeterRegistry) registry;
        await(() -> {
            Gauge gauge = meterRegistry.find("http.streams.active")
                    .tags("role", "server", "protocol", "http/2", "direction", "bidi", "initiator", "remote")
                    .gauge();
            return gauge != null && gauge.value() == expected;
        });
        return meterRegistry.find("http.streams.active")
                .tags("role", "server", "protocol", "http/2", "direction", "bidi", "initiator", "remote")
                .gauge();
    }

    private static void assertEventuallyNull(Supplier<?> supplier) {
        await(() -> supplier.get() == null);
        assertThat(supplier.get(), nullValue());
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

    private static final class TestRegistry implements MeterRegistry {
        private final MeterRegistry delegate;
        private final AtomicInteger countedRemovalAttempts = new AtomicInteger();
        private volatile Predicate<Meter> failingRegistration = ignored -> false;
        private volatile Consumer<Meter> registrationCallback;
        private volatile Consumer<String> enablementCallback;
        private volatile Meter lookupOverride;
        private volatile String countedRemovalName;
        private volatile String failNextEnablementName;
        private volatile String lookupOverrideName;
        private volatile boolean failNextRegistration;
        private volatile int remainingRegistrationFailures;
        private volatile boolean failGaugeRemovals;
        private volatile boolean hideMeters;
        private volatile boolean persistentRemovalCallback;
        private volatile Runnable removalCallback;

        private TestRegistry(MeterRegistry delegate) {
            this.delegate = delegate;
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
            return hideMeters ? List.of() : delegate.meters(scopeSelection);
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
            Consumer<String> callback = enablementCallback;
            if (callback != null) {
                callback.accept(name);
            }
            if (name.equals(failNextEnablementName)) {
                failNextEnablementName = null;
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
            Consumer<Meter> callback = registrationCallback;
            if (callback != null) {
                callback.accept(meter);
            }
            if ((failNextRegistration || remainingRegistrationFailures > 0) && failingRegistration.test(meter)) {
                failNextRegistration = false;
                if (remainingRegistrationFailures > 0) {
                    remainingRegistrationFailures--;
                }
                throw new IllegalStateException("Meter registration failure");
            }
            return meter;
        }

        @Override
        public <M extends Meter> Optional<M> meter(Class<M> meterClass, String name, Iterable<Tag> tags) {
            if (lookupOverride != null && name.equals(lookupOverrideName)) {
                return Optional.of(meterClass.cast(lookupOverride));
            }
            return delegate.meter(meterClass, name, tags);
        }

        @Override
        public Optional<Meter> remove(Meter meter) {
            countRemoval(meter.id().name());
            if (failGaugeRemovals && meter.type() == Meter.Type.GAUGE) {
                throw new IllegalStateException("Gauge removal failure");
            }
            return afterRemoval(delegate.remove(meter));
        }

        @Override
        public Optional<Meter> remove(Meter.Id id) {
            countRemoval(id.name());
            return afterRemoval(delegate.remove(id));
        }

        @Override
        public Optional<Meter> remove(Meter.Id id, String scope) {
            countRemoval(id.name());
            return afterRemoval(delegate.remove(id, scope));
        }

        @Override
        public Optional<Meter> remove(String name, Iterable<Tag> tags) {
            countRemoval(name);
            return afterRemoval(delegate.remove(name, tags));
        }

        @Override
        public Optional<Meter> remove(String name, Iterable<Tag> tags, String scope) {
            countRemoval(name);
            return afterRemoval(delegate.remove(name, tags, scope));
        }

        @Override
        public boolean isDeleted(Meter meter) {
            return delegate.isDeleted(meter);
        }

        @Override
        public MeterRegistry onMeterAdded(Consumer<Meter> listener) {
            delegate.onMeterAdded(listener);
            return this;
        }

        @Override
        public MeterRegistry onMeterRemoved(Consumer<Meter> listener) {
            delegate.onMeterRemoved(listener);
            return this;
        }

        @Override
        public <R> R unwrap(Class<? extends R> type) {
            return delegate.unwrap(type);
        }

        private void countRemoval(String name) {
            if (name.equals(countedRemovalName)) {
                countedRemovalAttempts.incrementAndGet();
            }
        }

        private Optional<Meter> afterRemoval(Optional<Meter> result) {
            Runnable callback = removalCallback;
            if (!persistentRemovalCallback) {
                removalCallback = null;
            }
            if (callback != null) {
                callback.run();
            }
            return result;
        }
    }
}
