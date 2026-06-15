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

package io.helidon.webserver;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.config.Config;
import io.helidon.webserver.spi.PortTransportBinding;
import io.helidon.webserver.spi.TransportBinding;
import io.helidon.webserver.spi.TransportBindingFactoryProvider;

public class TestTransportBindingProvider implements TransportBindingFactoryProvider {
    private static final Map<String, BindingPlanContext> PLAN_CONTEXTS = new ConcurrentHashMap<>();
    private static final Map<String, Integer> PORT_AT_CREATE = new ConcurrentHashMap<>();
    private static final Map<String, Integer> PORT_AT_START = new ConcurrentHashMap<>();
    private static final Map<String, Integer> BOUND_PORTS = new ConcurrentHashMap<>();
    private static final Map<String, DatagramSocket> BOUND_SOCKETS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> STARTS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> STOPS = new ConcurrentHashMap<>();
    private static final Map<String, CountDownLatch> PENDING_STOPS = new ConcurrentHashMap<>();
    private static final Map<String, CountDownLatch> PENDING_EXECUTOR_TASKS = new ConcurrentHashMap<>();
    private static final Map<String, CountDownLatch> EXECUTOR_TASKS_STARTED = new ConcurrentHashMap<>();

    static void reset() {
        PLAN_CONTEXTS.clear();
        PORT_AT_CREATE.clear();
        PORT_AT_START.clear();
        BOUND_PORTS.clear();
        BOUND_SOCKETS.values().forEach(DatagramSocket::close);
        BOUND_SOCKETS.clear();
        STARTS.clear();
        STOPS.clear();
        PENDING_STOPS.clear();
        PENDING_EXECUTOR_TASKS.values().forEach(CountDownLatch::countDown);
        PENDING_EXECUTOR_TASKS.clear();
        EXECUTOR_TASKS_STARTED.clear();
    }

    static ListenerConfig listenerConfigAtPlan(String name) {
        BindingPlanContext context = PLAN_CONTEXTS.get(name);
        return context == null ? null : context.listenerConfig();
    }

    static int portAtCreate(String name) {
        return PORT_AT_CREATE.getOrDefault(name, -1);
    }

    static int portAtStart(String name) {
        return PORT_AT_START.getOrDefault(name, -1);
    }

    static int boundPort(String name) {
        return BOUND_PORTS.getOrDefault(name, -1);
    }

    static int starts(String name) {
        return counter(STARTS, name).get();
    }

    static int stops(String name) {
        return counter(STOPS, name).get();
    }

    static void completeStop(String name) {
        CountDownLatch latch = PENDING_STOPS.remove(name);
        if (latch != null) {
            latch.countDown();
        }
    }

    static boolean awaitExecutorTask(String name) throws InterruptedException {
        CountDownLatch latch = EXECUTOR_TASKS_STARTED.get(name);
        return latch != null && latch.await(5, TimeUnit.SECONDS);
    }

    static void completeExecutorTask(String name) {
        CountDownLatch latch = PENDING_EXECUTOR_TASKS.remove(name);
        if (latch != null) {
            latch.countDown();
        }
    }

    @Override
    public String configKey() {
        return TestTransportBindingConfig.TYPE;
    }

    @Override
    public TestTransportBindingConfig create(Config config, String name) {
        return new TestTransportBindingConfig(name, config.get("enabled").asBoolean().orElse(false));
    }

    static boolean canBind(TestTransportBindingConfig config, BindingPlanContext context) {
        PLAN_CONTEXTS.put(config.name(), context);
        return config.enabled();
    }

    static TransportBinding create(TestTransportBindingConfig config, TransportBindingContext context) {
        PORT_AT_CREATE.put(config.name(), context.boundPort().orElse(-1));
        return new TestTransportBinding(context, config);
    }

    private static AtomicInteger counter(Map<String, AtomicInteger> counters, String name) {
        return counters.computeIfAbsent(name, _ -> new AtomicInteger());
    }

    private record TestTransportBinding(TransportBindingContext context,
                                        TestTransportBindingConfig config) implements TransportBinding, PortTransportBinding {
        @Override
        public String type() {
            return config.type();
        }

        @Override
        public String name() {
            return config.name();
        }

        @Override
        public String configuredEndpoint() {
            return "test-transport";
        }

        @Override
        public void start() {
            counter(STARTS, config.name()).incrementAndGet();
            if (config.failStart()) {
                throw new IllegalStateException("test transport start failed " + config.name());
            }
            int portAtStart = context.boundPort().orElse(-1);
            PORT_AT_START.put(config.name(), portAtStart);
            if (config.portCapable()) {
                BOUND_PORTS.put(config.name(), portAtStart > 0 ? portAtStart : bindDatagramSocket(config.name()));
            }
            if (config.blockSharedExecutor()) {
                CountDownLatch release = new CountDownLatch(1);
                CountDownLatch started = new CountDownLatch(1);
                PENDING_EXECUTOR_TASKS.put(config.name(), release);
                EXECUTOR_TASKS_STARTED.put(config.name(), started);
                context.listenerContext().executor().execute(() -> {
                    started.countDown();
                    while (release.getCount() != 0) {
                        try {
                            release.await(1, TimeUnit.SECONDS);
                        } catch (InterruptedException _) {
                            // Deliberately ignore interrupts to keep the shared executor occupied.
                        }
                    }
                });
            }
            if (config.fatalAfterStart()) {
                context.listenerContext().executor()
                        .execute(() -> context.fatalBindingFailure(this,
                                                                   new IllegalStateException("test transport fatal failure "
                                                                                                     + config.name())));
            }
        }

        @Override
        public ShutdownResult stop(Duration gracefulPeriod) {
            counter(STOPS, config.name()).incrementAndGet();
            closeBoundSocket(config.name());
            if (config.hangStop()) {
                CountDownLatch latch = new CountDownLatch(1);
                PENDING_STOPS.put(config.name(), latch);
                while (true) {
                    try {
                        latch.await();
                        break;
                    } catch (InterruptedException e) {
                        if (!config.ignoreStopInterrupt()) {
                            Thread.currentThread().interrupt();
                            return ShutdownResult.FORCED;
                        }
                    }
                }
            }
            if (config.forceStop()) {
                return ShutdownResult.FORCED;
            }
            return ShutdownResult.GRACEFUL;
        }

        @Override
        public Security security() {
            return config.security();
        }

        @Override
        public int port() {
            if (!config.portCapable()) {
                return -1;
            }
            return BOUND_PORTS.getOrDefault(config.name(), -1);
        }
    }

    private static int bindDatagramSocket(String name) {
        try {
            DatagramSocket socket = new DatagramSocket(0, InetAddress.getLoopbackAddress());
            closeBoundSocket(name);
            BOUND_SOCKETS.put(name, socket);
            return socket.getLocalPort();
        } catch (SocketException e) {
            throw new IllegalStateException("Failed to bind test transport socket", e);
        }
    }

    private static void closeBoundSocket(String name) {
        DatagramSocket socket = BOUND_SOCKETS.remove(name);
        if (socket != null) {
            socket.close();
        }
    }
}
