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

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.http.HttpTransportObserver;
import io.helidon.http.metrics.HttpTransportMetrics;
import io.helidon.http.metrics.HttpTransportMetrics.Lease;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.webserver.HttpTransportObserverSupport.ObserverLifecycle;

import static java.lang.System.Logger.Level.WARNING;

final class HttpTransportMetricsObserver implements HttpTransportObserver,
                                                    ObserverLifecycle {
    private static final System.Logger LOGGER = System.getLogger(HttpTransportMetricsObserver.class.getName());

    private final ReentrantLock lifecycleLock = new ReentrantLock();
    private final MeterRegistry meterRegistry;
    private volatile HttpTransportObserver delegate = HttpTransportObserver.noop();
    private Lease lease;
    private int activeListeners;

    HttpTransportMetricsObserver(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    @Override
    public HttpTransportObserver start() {
        lifecycleLock.lock();
        try {
            activeListeners++;
            if (lease == null) {
                try {
                    lease = HttpTransportMetrics.acquire(meterRegistry);
                    delegate = lease;
                } catch (Throwable failure) {
                    LOGGER.log(WARNING, "Failed to acquire HTTP transport metrics", failure);
                }
            }
        } finally {
            lifecycleLock.unlock();
        }
        return this;
    }

    @Override
    public CompletionStage<Void> stop() {
        Lease closingLease = null;
        lifecycleLock.lock();
        try {
            if (activeListeners == 0) {
                LOGGER.log(WARNING, "HTTP transport metrics listener lifecycle is already stopped");
                return CompletableFuture.completedFuture(null);
            }
            activeListeners--;
            if (activeListeners == 0) {
                delegate = HttpTransportObserver.noop();
                closingLease = lease;
                lease = null;
            }
        } finally {
            lifecycleLock.unlock();
        }
        if (closingLease != null) {
            try {
                closingLease.close();
                return Objects.requireNonNull(closingLease.completion(),
                                              "HTTP transport metrics lease completion");
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public ConnectionObservation connectionOpened(Role role, String transport, Handshake handshake) {
        return delegate.connectionOpened(role, transport, handshake);
    }
}
