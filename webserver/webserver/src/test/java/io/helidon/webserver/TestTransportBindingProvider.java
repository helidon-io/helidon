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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.tls.TlsMaterial;
import io.helidon.config.Config;
import io.helidon.webserver.spi.PortTransportBinding;
import io.helidon.webserver.spi.TlsTransportBinding;
import io.helidon.webserver.spi.TransportBinding;
import io.helidon.webserver.spi.TransportBindingProvider;

public class TestTransportBindingProvider implements TransportBindingProvider<TestTransportBindingConfig> {
    private static final Map<String, Optional<SocketAddress>> BIND_ADDRESS_AT_PLAN = new ConcurrentHashMap<>();
    private static final Map<String, String> HOST_AT_PLAN = new ConcurrentHashMap<>();
    private static final Map<String, Integer> PORT_AT_PLAN = new ConcurrentHashMap<>();
    private static final Map<String, Integer> PORT_AT_START = new ConcurrentHashMap<>();
    private static final Map<String, Integer> BOUND_PORTS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> STARTS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> STOPS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> RELOADS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> VIRTUAL_HOST_RELOADS = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<TransportBinding.ShutdownResult>> PENDING_STOPS =
            new ConcurrentHashMap<>();

    static void reset() {
        BIND_ADDRESS_AT_PLAN.clear();
        HOST_AT_PLAN.clear();
        PORT_AT_PLAN.clear();
        PORT_AT_START.clear();
        BOUND_PORTS.clear();
        STARTS.clear();
        STOPS.clear();
        RELOADS.clear();
        VIRTUAL_HOST_RELOADS.clear();
        PENDING_STOPS.clear();
    }

    static Optional<SocketAddress> bindAddressAtPlan(String name) {
        return BIND_ADDRESS_AT_PLAN.getOrDefault(name, Optional.empty());
    }

    static String hostAtPlan(String name) {
        return HOST_AT_PLAN.get(name);
    }

    static int portAtPlan(String name) {
        return PORT_AT_PLAN.getOrDefault(name, -1);
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

    static int reloads(String name) {
        return counter(RELOADS, name).get();
    }

    static int virtualHostReloads(String name) {
        return counter(VIRTUAL_HOST_RELOADS, name).get();
    }

    static void completeStop(String name) {
        CompletableFuture<TransportBinding.ShutdownResult> future = PENDING_STOPS.remove(name);
        if (future != null) {
            future.complete(TransportBinding.ShutdownResult.GRACEFUL);
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

    @Override
    public Class<TestTransportBindingConfig> configType() {
        return TestTransportBindingConfig.class;
    }

    @Override
    public boolean canBind(BindingPlanContext context, TestTransportBindingConfig config) {
        BIND_ADDRESS_AT_PLAN.put(config.name(), context.bindAddress());
        HOST_AT_PLAN.put(config.name(), context.host());
        PORT_AT_PLAN.put(config.name(), context.port());
        return config.enabled();
    }

    @Override
    public TransportBinding create(TransportBindingContext context, TestTransportBindingConfig config) {
        return new TestTransportBinding(context, config);
    }

    private static AtomicInteger counter(Map<String, AtomicInteger> counters, String name) {
        return counters.computeIfAbsent(name, _ -> new AtomicInteger());
    }

    private record TestTransportBinding(TransportBindingContext context,
                                        TestTransportBindingConfig config) implements TlsTransportBinding, PortTransportBinding {
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
                BOUND_PORTS.put(config.name(), portAtStart > 0 ? portAtStart : availablePort());
            }
        }

        @Override
        public CompletionStage<ShutdownResult> stop(Duration gracefulPeriod) {
            counter(STOPS, config.name()).incrementAndGet();
            if (config.hangStop()) {
                CompletableFuture<ShutdownResult> future = new CompletableFuture<>();
                PENDING_STOPS.put(config.name(), future);
                return future;
            }
            return CompletableFuture.completedFuture(ShutdownResult.GRACEFUL);
        }

        @Override
        public boolean hasTls() {
            return config.tlsEnabled();
        }

        @Override
        public void reloadTls(TlsMaterial material) {
            counter(RELOADS, config.name()).incrementAndGet();
            if (config.failReload()) {
                throw new IllegalStateException("test transport TLS reload failed " + config.name());
            }
        }

        @Override
        public void reloadVirtualHostTls(TlsMaterial material, String configuredHost) {
            counter(VIRTUAL_HOST_RELOADS, config.name()).incrementAndGet();
            if (config.failReload()) {
                throw new IllegalStateException("test transport virtual host TLS reload failed " + config.name());
            }
        }

        @Override
        public boolean supportsListenerVirtualHosts() {
            return config.supportsVirtualHosts();
        }

        @Override
        public int port() {
            if (!config.portCapable()) {
                return -1;
            }
            return BOUND_PORTS.getOrDefault(config.name(), -1);
        }
    }

    private static int availablePort() {
        try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
