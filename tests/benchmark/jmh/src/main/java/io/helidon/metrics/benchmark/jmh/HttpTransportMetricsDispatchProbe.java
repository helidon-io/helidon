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

package io.helidon.metrics.benchmark.jmh;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import io.helidon.http.metrics.HttpTransportMetrics;
import io.helidon.metrics.api.Clock;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.service.registry.Services;

import static io.helidon.http.HttpTransportObserver.ConnectionOutcome.NORMAL;
import static io.helidon.http.HttpTransportObserver.Handshake.NONE;
import static io.helidon.http.HttpTransportObserver.Role.SERVER;

/**
 * One cold-registration workload, with a cheap provider callback that filters every meter out.
 */
final class HttpTransportMetricsDispatchProbe implements AutoCloseable {
    private static final String CONNECTIONS_OPENED = "helidon.http.connections.opened";
    private static final int MAX_PROVIDER_ACTIONS = 1024;
    private static final int TIMEOUT_SECONDS = 10;

    private final MeterRegistry registry;
    private final HttpTransportMetrics.Lease[] leases;
    private final String[][] transports;
    private final AtomicIntegerArray submitted;
    private final CountDownLatch producersFinished;
    private final CountDownLatch callbacksFinished;
    private final CountDownLatch providerEntered = new CountDownLatch(1);
    private final CountDownLatch providerReleased;
    private final AtomicInteger attempted = new AtomicInteger();
    private final AtomicInteger executed = new AtomicInteger();
    private final AtomicReference<Thread> lastProviderThread = new AtomicReference<>();
    private final AtomicReference<Exception> providerFailure = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final int expected;

    HttpTransportMetricsDispatchProbe(int producers, int registrationsPerProducer, boolean blockProvider) {
        if (producers < 1 || registrationsPerProducer < 1 || registrationsPerProducer > 256) {
            throw new IllegalArgumentException("Require at least one producer and 1-256 registrations per producer");
        }
        long total = (long) producers * registrationsPerProducer;
        if (total > 4096 || (total > MAX_PROVIDER_ACTIONS && !blockProvider)) {
            throw new IllegalArgumentException("Require at most 4096 actions, and a blocked provider above 1024 actions");
        }
        expected = (int) Math.min(total, MAX_PROVIDER_ACTIONS);
        leases = new HttpTransportMetrics.Lease[producers];
        transports = new String[producers][registrationsPerProducer];
        submitted = new AtomicIntegerArray(producers);
        producersFinished = new CountDownLatch(producers);
        callbacksFinished = new CountDownLatch(expected);
        providerReleased = new CountDownLatch(blockProvider ? 1 : 0);
        registry = Services.get(MetricsFactory.class)
                .createMeterRegistry(MetricsConfig.builder().warnOnMultipleRegistries(false).build());
        try {
            for (int producer = 0; producer < producers; producer++) {
                for (int registration = 0; registration < registrationsPerProducer; registration++) {
                    transports[producer][registration] = "dispatch-" + producer + "-" + registration;
                }
                // Distinct configured wrappers have independent cold caches but share one native dispatcher.
                leases[producer] = HttpTransportMetrics.acquire(new ProbeRegistry(registry));
            }
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (Exception cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    void submit(int producer) {
        Objects.checkIndex(producer, leases.length);
        if (closed.get() || !submitted.compareAndSet(producer, 0, 1)) {
            throw new IllegalStateException("Producer " + producer + " was already submitted or the probe is closed");
        }
        int count = 0;
        try {
            for (String transport : transports[producer]) {
                count++;
                leases[producer].connectionOpened(SERVER, transport, NONE).close(NORMAL);
            }
        } finally {
            attempted.addAndGet(count);
            producersFinished.countDown();
        }
    }

    void awaitProviderEntered() throws InterruptedException {
        await(providerEntered, "Provider did not enter its first callback");
    }

    void releaseProvider() {
        providerReleased.countDown();
    }

    int awaitDrained() throws InterruptedException {
        await(producersFinished, "Producers did not finish submitting actions");
        if (!callbacksFinished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Expected " + expected + " provider callbacks but observed " + executed.get(),
                                            providerFailure.get());
        }
        Thread provider = lastProviderThread.get();
        if (provider == null || !provider.join(Duration.ofSeconds(TIMEOUT_SECONDS))) {
            throw new IllegalStateException("Provider did not finish draining its accepted actions");
        }
        Exception failure = providerFailure.get();
        if (failure != null) {
            throw new IllegalStateException("Provider callback failed", failure);
        }
        int observed = executed.get();
        if (observed != expected) {
            throw new IllegalStateException("Expected " + expected + " provider callbacks but observed " + observed);
        }
        return observed;
    }

    int attemptedActions() {
        return attempted.get();
    }

    int executedActions() {
        return executed.get();
    }

    @Override
    public void close() throws Exception {
        releaseProvider();
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        RuntimeException closeFailure = null;
        for (HttpTransportMetrics.Lease lease : leases) {
            if (lease != null) {
                try {
                    lease.close();
                } catch (RuntimeException failure) {
                    if (closeFailure == null) {
                        closeFailure = failure;
                    } else {
                        closeFailure.addSuppressed(failure);
                    }
                }
            }
        }
        CompletableFuture<?>[] completions = Arrays.stream(leases)
                .filter(Objects::nonNull)
                .map(lease -> lease.completion().toCompletableFuture())
                .toArray(CompletableFuture<?>[]::new);
        try {
            // If the bounded wait fails, close the registry once outstanding lease cleanup eventually completes.
            CompletableFuture.allOf(completions)
                    .whenComplete((_, _) -> registry.close())
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception failure) {
            if (closeFailure != null) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
        if (closeFailure != null) {
            throw closeFailure;
        }
    }

    private static void await(CountDownLatch latch, String failureMessage) throws InterruptedException {
        if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException(failureMessage);
        }
    }

    private boolean observeProvider(String name) {
        lastProviderThread.set(Thread.currentThread());
        int callback = executed.incrementAndGet();
        try {
            if (!CONNECTIONS_OPENED.equals(name)) {
                throw new IllegalStateException("Unexpected provider callback for " + name);
            }
            if (callback == 1) {
                providerEntered.countDown();
                await(providerReleased, "Provider release was not signaled");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            providerFailure.compareAndSet(null, failure);
        } catch (RuntimeException failure) {
            // The transport dispatcher isolates provider failures; retain them for the harness to report.
            providerFailure.compareAndSet(null, failure);
        } finally {
            callbacksFinished.countDown();
        }
        return false;
    }

    private final class ProbeRegistry implements MeterRegistry {
        private final MeterRegistry delegate;

        private ProbeRegistry(MeterRegistry delegate) {
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
        public void close() {
            delegate.close();
        }

        @Override
        public boolean isMeterEnabled(String name) {
            return CONNECTIONS_OPENED.equals(name);
        }

        @Override
        public boolean isMeterEnabled(String name, Map<String, String> tags) {
            return observeProvider(name);
        }

        @Override
        @SuppressWarnings("removal")
        public boolean isMeterEnabled(String name, Map<String, String> tags, Optional<String> scope) {
            return observeProvider(name);
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
            return delegate.getOrCreate(builder);
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
        public Optional<Meter> remove(String name, Iterable<Tag> tags) {
            return delegate.remove(name, tags);
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
    }
}
