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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.ReentrantLock;

import io.helidon.http.HttpTransportObserver;
import io.helidon.http.metrics.HttpTransportMetrics;
import io.helidon.http.metrics.HttpTransportMetrics.Lease;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.webserver.HttpTransportObserverSupport;
import io.helidon.webserver.HttpTransportObserverSupport.ObserverLifecycle;
import io.helidon.webserver.spi.ServerFeature;

import static java.lang.System.Logger.Level.WARNING;

final class HttpTransportMetricsObserver implements HttpTransportObserver,
                                                    ObserverLifecycle {
    private static final System.Logger LOGGER = System.getLogger(HttpTransportMetricsObserver.class.getName());
    private static final ReentrantLock REGISTRATION_LOCK = new ReentrantLock();
    private static final Map<ServerFeature.ServerFeatureContext,
            List<WeakReference<Registration>>> REGISTRATIONS = new WeakHashMap<>();

    private final ReentrantLock lifecycleLock = new ReentrantLock();
    private final MeterRegistry meterRegistry;
    private volatile HttpTransportObserver delegate = HttpTransportObserver.noop();
    private Lease lease;
    private int activeListeners;

    HttpTransportMetricsObserver(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    static void addObserver(ServerFeature.ServerFeatureContext featureContext,
                            String socketName,
                            MeterRegistry meterRegistry) {
        Registration registration = reserve(featureContext, socketName, meterRegistry);
        if (registration == null) {
            return;
        }

        boolean added = false;
        try {
            added = HttpTransportObserverSupport.addObserver(
                    featureContext,
                    socketName,
                    new RegisteredObserver(featureContext, socketName, registration));
        } finally {
            if (!added) {
                release(featureContext, socketName, registration);
            }
        }
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
                } catch (RuntimeException failure) {
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
            } catch (RuntimeException failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public ConnectionObservation connectionOpened(Role role, String transport, Handshake handshake) {
        return delegate.connectionOpened(role, transport, handshake);
    }

    private static Registration reserve(ServerFeature.ServerFeatureContext featureContext,
                                        String socketName,
                                        MeterRegistry meterRegistry) {
        REGISTRATION_LOCK.lock();
        try {
            List<WeakReference<Registration>> registrations =
                    REGISTRATIONS.computeIfAbsent(featureContext, _ -> new ArrayList<>());
            Registration registration = null;
            for (Iterator<WeakReference<Registration>> iterator = registrations.iterator(); iterator.hasNext();) {
                Registration candidate = iterator.next().get();
                if (candidate == null) {
                    iterator.remove();
                } else if (candidate.meterRegistry == meterRegistry) {
                    registration = candidate;
                }
            }
            if (registration == null) {
                registration = new Registration(meterRegistry, new HttpTransportMetricsObserver(meterRegistry));
                registrations.add(new WeakReference<>(registration));
            }
            return registration.sockets.add(socketName) ? registration : null;
        } finally {
            REGISTRATION_LOCK.unlock();
        }
    }

    private static void release(ServerFeature.ServerFeatureContext featureContext,
                                String socketName,
                                Registration registration) {
        REGISTRATION_LOCK.lock();
        try {
            List<WeakReference<Registration>> registrations = REGISTRATIONS.get(featureContext);
            if (registrations == null) {
                return;
            }
            registration.sockets.remove(socketName);
            for (Iterator<WeakReference<Registration>> iterator = registrations.iterator(); iterator.hasNext();) {
                Registration candidate = iterator.next().get();
                if (candidate == null || (candidate == registration && registration.sockets.isEmpty())) {
                    iterator.remove();
                }
            }
            if (registrations.isEmpty()) {
                REGISTRATIONS.remove(featureContext);
            }
        } finally {
            REGISTRATION_LOCK.unlock();
        }
    }

    private static final class Registration {
        private final Set<String> sockets = new HashSet<>();
        private final MeterRegistry meterRegistry;
        private final HttpTransportMetricsObserver observer;

        private Registration(MeterRegistry meterRegistry, HttpTransportMetricsObserver observer) {
            this.meterRegistry = meterRegistry;
            this.observer = observer;
        }
    }

    private static final class RegisteredObserver implements ObserverLifecycle {
        private final ServerFeature.ServerFeatureContext featureContext;
        private final String socketName;
        private final Registration registration;

        private RegisteredObserver(ServerFeature.ServerFeatureContext featureContext,
                                   String socketName,
                                   Registration registration) {
            this.featureContext = featureContext;
            this.socketName = socketName;
            this.registration = registration;
        }

        @Override
        public HttpTransportObserver start() {
            return registration.observer.start();
        }

        @Override
        public CompletionStage<Void> stop() {
            try {
                return registration.observer.stop()
                        .whenComplete((_, _) -> release(featureContext, socketName, registration));
            } catch (RuntimeException failure) {
                release(featureContext, socketName, registration);
                throw failure;
            }
        }
    }
}
