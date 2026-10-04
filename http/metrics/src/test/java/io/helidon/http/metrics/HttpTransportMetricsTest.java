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

package io.helidon.http.metrics;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.Wrapper;
import io.helidon.http.HttpTransportObserver.ConnectionObservation;
import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.Handshake;
import io.helidon.http.HttpTransportObserver.Role;
import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsFactory;

import org.junit.jupiter.api.Test;

import static io.helidon.http.HttpTransportObserver.PROTOCOL_HTTP_1_1;
import static io.helidon.http.HttpTransportObserver.TRANSPORT_TCP;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpTransportMetricsTest {
    private static final int MAX_PROVIDER_ACTIONS_PER_WAVE = 1024;
    private static final long TEST_TIMEOUT_SECONDS = 5;
    private static final Clock CLOCK = new Clock() {
        @Override
        public long wallTime() {
            return 0;
        }

        @Override
        public long monotonicTime() {
            return System.nanoTime();
        }
    };
    private static final MetricsFactory UNUSED_FACTORY = proxy(MetricsFactory.class, (proxy, method, args) -> {
        throw new AssertionError("Metrics factory should not be invoked for a disabled meter registry: " + method);
    });

    @Test
    void completionWaitsForOpenConnections() throws Exception {
        MeterRegistry registry = disabledRegistry(new Object(), null, null, null);
        HttpTransportMetrics.Lease lease = HttpTransportMetrics.acquire(registry);
        ConnectionObservation connection = lease.connectionOpened(Role.SERVER, TRANSPORT_TCP, Handshake.NONE);

        lease.close();
        assertThat("Lease completion before its connection closed",
                   lease.completion().toCompletableFuture().isDone(),
                   is(false));

        connection.close(ConnectionOutcome.NORMAL);
        lease.completion().toCompletableFuture().get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void configuredRegistryHandoffCompletesReleasedLease() throws Exception {
        MeterRegistry registry = disabledRegistry(new Object(), null, null, null);
        HttpTransportMetrics.Lease first = HttpTransportMetrics.acquire(registry);
        HttpTransportMetrics.Lease second = HttpTransportMetrics.acquire(registry);

        first.close();
        first.completion().toCompletableFuture().get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat("Second lease should retain ownership",
                   second.completion().toCompletableFuture().isDone(),
                   is(false));

        second.close();
        second.completion().toCompletableFuture().get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void recursiveNativeIdentityDelaysCleanupUntilLastWrapper() throws Exception {
        Object nativeRegistry = new Object();
        NestedWrapper firstDelegate = new NestedWrapper(new NestedWrapper(nativeRegistry));
        NestedWrapper secondDelegate = new NestedWrapper(nativeRegistry);
        MeterRegistry firstRegistry = disabledRegistry(firstDelegate, null, null, null);
        MeterRegistry secondRegistry = disabledRegistry(secondDelegate, null, null, null);
        HttpTransportMetrics.Lease first = HttpTransportMetrics.acquire(firstRegistry);
        HttpTransportMetrics.Lease second = HttpTransportMetrics.acquire(secondRegistry);

        first.close();
        assertThat("A different configured wrapper still owns the native registry",
                   first.completion().toCompletableFuture().isDone(),
                   is(false));

        second.close();
        first.completion().toCompletableFuture().get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        second.completion().toCompletableFuture().get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void selfUnwrappingDelegateIsAValidIdentityTerminal() throws Exception {
        SelfWrapper nativeRegistry = new SelfWrapper();
        MeterRegistry registry = disabledRegistry(nativeRegistry, null, null, null);
        HttpTransportMetrics.Lease lease = HttpTransportMetrics.acquire(registry);

        lease.close();
        lease.completion().toCompletableFuture().get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void rejectsMultiWrapperCycles() {
        CycleWrapper first = new CycleWrapper();
        CycleWrapper second = new CycleWrapper();
        first.delegate = second;
        second.delegate = first;
        MeterRegistry registry = disabledRegistry(first, null, null, null);

        assertThrows(IllegalArgumentException.class, () -> HttpTransportMetrics.acquire(registry));
    }

    @Test
    void coldProviderWorkDoesNotBlockTransportCallback() throws Exception {
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch allowProvider = new CountDownLatch(1);
        AtomicReference<Thread> providerThread = new AtomicReference<>();
        MeterRegistry registry = disabledRegistry(new Object(), providerEntered, allowProvider, providerThread);
        HttpTransportMetrics.Lease lease = HttpTransportMetrics.acquire(registry);
        Thread callbackThread = Thread.currentThread();

        ConnectionObservation connection = lease.connectionOpened(Role.SERVER, TRANSPORT_TCP, Handshake.NONE);
        assertThat("Provider was not invoked",
                   providerEntered.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                   is(true));
        assertThat(providerThread.get(), not(sameInstance(callbackThread)));

        connection.close(ConnectionOutcome.NORMAL);
        lease.close();
        assertThat("Blocked accepted provider work should delay cleanup",
                   lease.completion().toCompletableFuture().isDone(),
                   is(false));

        allowProvider.countDown();
        lease.completion().toCompletableFuture().get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void concurrentLeaseReleasesWaitForPendingProviderWork() throws Exception {
        int leaseCount = 16;
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch allowProvider = new CountDownLatch(1);
        CountDownLatch releasesReady = new CountDownLatch(leaseCount);
        CountDownLatch startReleases = new CountDownLatch(1);
        MeterRegistry registry = disabledRegistry(new Object(), () -> {
            providerEntered.countDown();
            allowProvider.await();
            return false;
        });
        List<HttpTransportMetrics.Lease> leases = new ArrayList<>();
        ConnectionObservation connection = ConnectionObservation.noop();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                for (int i = 0; i < leaseCount; i++) {
                    leases.add(HttpTransportMetrics.acquire(registry));
                }
                connection = leases.getFirst().connectionOpened(Role.SERVER, TRANSPORT_TCP, Handshake.NONE);
                assertThat("Provider work did not start",
                           providerEntered.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                           is(true));
                connection.close(ConnectionOutcome.NORMAL);

                List<Future<?>> releases = new ArrayList<>();
                for (HttpTransportMetrics.Lease lease : leases) {
                    releases.add(executor.submit(() -> {
                        releasesReady.countDown();
                        assertThat("Concurrent release was not started",
                                   startReleases.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                                   is(true));
                        lease.close();
                        return null;
                    }));
                }
                assertThat("Lease release tasks did not become ready",
                           releasesReady.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                           is(true));
                startReleases.countDown();
                for (Future<?> release : releases) {
                    release.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }

                CompletableFuture<Void> completion = CompletableFuture.allOf(leases.stream()
                        .map(lease -> lease.completion().toCompletableFuture())
                        .toArray(CompletableFuture[]::new));
                assertThat("Final lease release completed before pending provider work",
                           completion.isDone(),
                           is(false));

                allowProvider.countDown();
                completion.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } finally {
                startReleases.countDown();
                allowProvider.countDown();
                connection.close(ConnectionOutcome.NORMAL);
                leases.forEach(HttpTransportMetrics.Lease::close);
            }
        }
    }

    @Test
    void boundsProviderAdmissionsUntilDispatcherIsTrulyIdle() throws Exception {
        Object nativeRegistry = new Object();
        AdmissionProbe admissionProbe = new AdmissionProbe();
        List<HttpTransportMetrics.Lease> leases = new ArrayList<>();
        List<ConnectionObservation> connections = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            leases.add(HttpTransportMetrics.acquire(disabledRegistry(nativeRegistry, admissionProbe)));
        }

        connections.add(leases.getFirst().connectionOpened(Role.SERVER, "transport-0-0", Handshake.NONE));
        assertThat("First provider action did not start",
                   admissionProbe.firstProviderEntered.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                   is(true));

        for (int registryIndex = 0; registryIndex < leases.size(); registryIndex++) {
            int connectionCount = switch (registryIndex) {
                case 0, 1 -> 128;
                case 2, 3 -> 256;
                default -> 2;
            };
            int firstConnection = registryIndex == 0 ? 1 : 0;
            for (int connectionIndex = firstConnection; connectionIndex < connectionCount; connectionIndex++) {
                connections.add(leases.get(registryIndex)
                                        .connectionOpened(Role.SERVER,
                                                          "transport-" + registryIndex + "-" + connectionIndex,
                                                          Handshake.NONE));
            }
        }

        admissionProbe.allowFirstProvider.countDown();
        Thread firstWaveThread = admissionProbe.firstWaveThread.get();
        firstWaveThread.join(TimeUnit.SECONDS.toMillis(TEST_TIMEOUT_SECONDS));
        assertThat("First provider wave did not become idle", firstWaveThread.isAlive(), is(false));
        assertThat("Continuously busy wave exceeded its total admission budget",
                   admissionProbe.providerActions.get(),
                   is(MAX_PROVIDER_ACTIONS_PER_WAVE));

        connections.add(leases.getLast().connectionOpened(Role.SERVER, "transport-after-idle", Handshake.NONE));
        assertThat("Provider admission budget did not reset at true idle",
                   admissionProbe.secondWaveActions.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                   is(true));
        Thread secondWaveThread = admissionProbe.secondWaveThread.get();
        secondWaveThread.join(TimeUnit.SECONDS.toMillis(TEST_TIMEOUT_SECONDS));
        assertThat("Second provider wave did not become idle", secondWaveThread.isAlive(), is(false));
        assertThat(admissionProbe.providerActions.get(), is(MAX_PROVIDER_ACTIONS_PER_WAVE + 1));

        connections.forEach(connection -> connection.close(ConnectionOutcome.NORMAL));
        leases.forEach(HttpTransportMetrics.Lease::close);
        CompletableFuture.allOf(leases.stream()
                                        .map(lease -> lease.completion().toCompletableFuture())
                                        .toArray(CompletableFuture[]::new))
                .get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    void rejectsBlankTransportAndProtocolIdentifiers() throws Exception {
        MeterRegistry registry = disabledRegistry(new Object(), null, null, null);
        HttpTransportMetrics.Lease lease = HttpTransportMetrics.acquire(registry);

        assertThrows(IllegalArgumentException.class,
                     () -> lease.connectionOpened(Role.SERVER, " ", Handshake.NONE));
        ConnectionObservation connection = lease.connectionOpened(Role.SERVER, TRANSPORT_TCP, Handshake.NONE);
        assertThrows(IllegalArgumentException.class, () -> connection.protocolSelected(" "));
        connection.protocolSelected(PROTOCOL_HTTP_1_1);
        connection.close(ConnectionOutcome.NORMAL);
        lease.close();
        lease.completion().toCompletableFuture().get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static MeterRegistry disabledRegistry(Object delegate,
                                                  CountDownLatch providerEntered,
                                                  CountDownLatch allowProvider,
                                                  AtomicReference<Thread> providerThread) {
        return disabledRegistry(delegate, () -> {
            if (providerThread != null) {
                providerThread.compareAndSet(null, Thread.currentThread());
            }
            if (providerEntered != null) {
                providerEntered.countDown();
            }
            if (allowProvider != null) {
                allowProvider.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
            return false;
        });
    }

    private static MeterRegistry disabledRegistry(Object delegate, MeterEnabled meterEnabled) {
        return proxy(MeterRegistry.class, (proxy, method, args) -> switch (method.getName()) {
            case "unwrap" -> ((Class<?>) args[0]).cast(delegate);
            case "metricsFactory" -> UNUSED_FACTORY;
            case "clock" -> CLOCK;
            case "isMeterEnabled" -> args.length == 1 || meterEnabled.get();
            case "meters", "scopes" -> List.of();
            case "meter", "counter", "gauge", "timer", "summary", "remove" -> Optional.empty();
            case "isDeleted" -> false;
            case "onMeterAdded", "onMeterRemoved" -> proxy;
            case "close" -> null;
            case "toString" -> "disabled test registry";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new AssertionError("Unexpected meter registry method: " + method);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    @FunctionalInterface
    private interface MeterEnabled {
        boolean get() throws InterruptedException;
    }

    private record NestedWrapper(Object delegate) implements Wrapper {
        @Override
        public <T> T unwrap(Class<? extends T> type) {
            return type.cast(delegate);
        }
    }

    private static final class SelfWrapper implements Wrapper {
        @Override
        public <T> T unwrap(Class<? extends T> type) {
            return type.cast(this);
        }
    }

    private static final class CycleWrapper implements Wrapper {
        private Object delegate;

        @Override
        public <T> T unwrap(Class<? extends T> type) {
            return type.cast(delegate);
        }
    }

    private static final class AdmissionProbe implements MeterEnabled {
        private final CountDownLatch firstProviderEntered = new CountDownLatch(1);
        private final CountDownLatch allowFirstProvider = new CountDownLatch(1);
        private final CountDownLatch secondWaveActions = new CountDownLatch(1);
        private final AtomicInteger providerActions = new AtomicInteger();
        private final AtomicReference<Thread> firstWaveThread = new AtomicReference<>();
        private final AtomicReference<Thread> secondWaveThread = new AtomicReference<>();

        @Override
        public boolean get() throws InterruptedException {
            int action = providerActions.incrementAndGet();
            if (action == 1) {
                firstWaveThread.set(Thread.currentThread());
                firstProviderEntered.countDown();
                allowFirstProvider.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } else if (action == MAX_PROVIDER_ACTIONS_PER_WAVE + 1) {
                secondWaveThread.set(Thread.currentThread());
            }
            if (action > MAX_PROVIDER_ACTIONS_PER_WAVE) {
                secondWaveActions.countDown();
            }
            return false;
        }
    }
}
