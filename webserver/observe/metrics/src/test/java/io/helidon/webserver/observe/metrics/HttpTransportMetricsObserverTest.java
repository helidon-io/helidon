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

package io.helidon.webserver.observe.metrics;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import io.helidon.http.HttpTransportObserver.ConnectionOutcome;
import io.helidon.http.HttpTransportObserver.Handshake;
import io.helidon.http.HttpTransportObserver.Role;
import io.helidon.metrics.api.MeterRegistry;

import org.junit.jupiter.api.Test;

import static io.helidon.http.HttpTransportObserver.TRANSPORT_TCP;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpTransportMetricsObserverTest {
    @Test
    void sharesOneConfiguredRegistryLeaseAcrossListeners() throws Exception {
        MeterRegistry configuredRegistry = MeterRegistry.create();
        HttpTransportMetricsObserver observer = new HttpTransportMetricsObserver(configuredRegistry);

        observer.start();
        observer.start();
        boolean firstStopped = false;
        try {
            observer.connectionOpened(Role.SERVER, TRANSPORT_TCP, Handshake.NONE)
                    .close(ConnectionOutcome.NORMAL);

            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                while (configuredRegistry.meters().isEmpty()) {
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IllegalStateException("Interrupted while waiting for transport meters");
                    }
                }
            });
            observer.stop().toCompletableFuture().get(5, TimeUnit.SECONDS);
            firstStopped = true;
            observer.connectionOpened(Role.SERVER, TRANSPORT_TCP, Handshake.NONE)
                    .close(ConnectionOutcome.NORMAL);
            assertFalse(configuredRegistry.meters().isEmpty());
        } finally {
            observer.stop().toCompletableFuture().get(5, TimeUnit.SECONDS);
            if (!firstStopped) {
                observer.stop().toCompletableFuture().get(5, TimeUnit.SECONDS);
            }
        }
        assertTrue(configuredRegistry.meters().isEmpty());
    }

    @Test
    void laterListenerTakesOwnershipOfPendingRegistryCleanup() throws Exception {
        CountDownLatch cleanupStarted = new CountDownLatch(1);
        CountDownLatch continueCleanup = new CountDownLatch(1);
        AtomicBoolean blockFirstRemoval = new AtomicBoolean(true);
        MeterRegistry configuredRegistry = MeterRegistry.create().onMeterRemoved(_ -> {
            if (blockFirstRemoval.compareAndSet(true, false)) {
                cleanupStarted.countDown();
                try {
                    continueCleanup.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        HttpTransportMetricsObserver observer = new HttpTransportMetricsObserver(configuredRegistry);
        boolean observerActive = true;
        observer.start();

        try {
            observer.connectionOpened(Role.SERVER, TRANSPORT_TCP, Handshake.NONE)
                    .close(ConnectionOutcome.NORMAL);
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                while (configuredRegistry.meters().isEmpty()) {
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IllegalStateException("Interrupted while waiting for transport meters");
                    }
                }
            });

            CompletionStage<Void> firstStop = observer.stop();
            observerActive = false;
            assertTrue(cleanupStarted.await(5, TimeUnit.SECONDS));
            assertFalse(firstStop.toCompletableFuture().isDone());

            observer.start();
            observerActive = true;
            firstStop.toCompletableFuture().get(5, TimeUnit.SECONDS);

            continueCleanup.countDown();
            observer.stop().toCompletableFuture().get(5, TimeUnit.SECONDS);
            observerActive = false;
            assertTrue(configuredRegistry.meters().isEmpty());
        } finally {
            continueCleanup.countDown();
            if (observerActive) {
                observer.stop().toCompletableFuture().get(5, TimeUnit.SECONDS);
            }
        }
    }
}
