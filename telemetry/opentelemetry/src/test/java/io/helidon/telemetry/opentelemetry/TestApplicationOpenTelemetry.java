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

package io.helidon.telemetry.opentelemetry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.Weighted;
import io.helidon.config.Config;
import io.helidon.service.registry.ExistingInstanceDescriptor;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.telemetry.opentelemetry.spi.OpenTelemetryOwnershipStrategy;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestApplicationOpenTelemetry {

    @BeforeEach
    void resetGlobalOpenTelemetryBefore() {
        GlobalOpenTelemetry.resetForTest();
    }

    @AfterEach
    void resetGlobalOpenTelemetryAfter() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void applicationTelemetryCacheIsClearedWhenRegistryShutsDown() {
        ServiceRegistryManager manager = strategyRegistryManager(new FixedStrategy("owned-service"));
        ServiceRegistry registry = manager.registry();
        try {
            registry.get(OpenTelemetry.class);
            assertTrue(ApplicationOpenTelemetry.applicationTelemetryCached(registry),
                       "Application telemetry should be cached for the active registry");
        } finally {
            manager.shutdown();
        }

        assertFalse(ApplicationOpenTelemetry.applicationTelemetryCached(registry),
                    "Application telemetry should be released when the registry shuts down");
    }

    @Test
    void cachedApplicationOpenTelemetryDoesNotResolveRegistryOpenTelemetry() {
        OpenTelemetry registryOpenTelemetry = new NamedOpenTelemetry();
        ServiceRegistryManager manager = registryManager(Config.empty());
        AtomicInteger registryOpenTelemetryLookups = new AtomicInteger();
        ServiceRegistry registry = manager.registry();
        try {
            OpenTelemetry first = ApplicationOpenTelemetry.applicationOpenTelemetry(registry,
                                                                                   Config.empty(),
                                                                                   List.of(),
                                                                                   () -> {
                                                                                       registryOpenTelemetryLookups.incrementAndGet();
                                                                                       return registryOpenTelemetry;
                                                                                   });
            OpenTelemetry second = ApplicationOpenTelemetry.applicationOpenTelemetry(registry,
                                                                                    Config.empty(),
                                                                                    List.of(),
                                                                                    () -> {
                                                                                        throw new AssertionError(
                                                                                                "Cached OpenTelemetry should"
                                                                                                        + " not resolve registry"
                                                                                                        + " again");
                                                                                    });

            assertThat("Cached OpenTelemetry", second, sameInstance(first));
            assertThat("Registry OpenTelemetry", first, sameInstance(registryOpenTelemetry));
            assertThat("Registry OpenTelemetry lookups", registryOpenTelemetryLookups.get(), is(1));
        } finally {
            ApplicationOpenTelemetry.clearApplicationTelemetry(registry);
            manager.shutdown();
        }
    }

    @Test
    void ownedApplicationOpenTelemetryIsClosedWhenRegistryShutsDown() {
        CloseableOpenTelemetry openTelemetry = new CloseableOpenTelemetry();
        FixedStrategy strategy = new FixedStrategy("owned-service", openTelemetry);
        ServiceRegistryManager manager = strategyRegistryManager(strategy);
        try {
            assertThat("OpenTelemetry service", manager.registry().get(OpenTelemetry.class), sameInstance(openTelemetry));
            assertFalse(openTelemetry.closed(), "OpenTelemetry should stay open while the registry is active");
        } finally {
            manager.shutdown();
        }

        assertTrue(openTelemetry.closed(), "Owned OpenTelemetry should close when the registry shuts down");
    }

    @Test
    void ownedGlobalApplicationOpenTelemetryIsClosedWhenRegistryShutsDown() {
        CloseableOpenTelemetry openTelemetry = new CloseableOpenTelemetry();
        FixedStrategy strategy = new FixedStrategy("owned-global-service", openTelemetry, true);
        ServiceRegistryManager manager = strategyRegistryManager(strategy);
        try {
            assertThat("OpenTelemetry service", manager.registry().get(OpenTelemetry.class), sameInstance(GlobalOpenTelemetry.get()));
            assertFalse(openTelemetry.closed(), "OpenTelemetry should stay open while the registry is active");
        } finally {
            manager.shutdown();
        }

        assertTrue(openTelemetry.closed(), "Owned global OpenTelemetry should close when the registry shuts down");
    }

    @Test
    void ownedGlobalApplicationOpenTelemetryAdoptsExistingGlobalOpenTelemetry() {
        OpenTelemetry globalOpenTelemetry = new NamedOpenTelemetry();
        GlobalOpenTelemetry.set(globalOpenTelemetry);
        CloseableOpenTelemetry openTelemetry = new CloseableOpenTelemetry();
        FixedStrategy strategy = new FixedStrategy("owned-global-service", openTelemetry, true);
        ServiceRegistryManager manager = strategyRegistryManager(strategy);
        try {
            assertThat("OpenTelemetry service",
                       manager.registry().get(OpenTelemetry.class).getTracerProvider().get("global"),
                       sameInstance(globalOpenTelemetry.getTracerProvider().get("global")));
            assertThat("OpenTelemetry global",
                       GlobalOpenTelemetry.get().getTracerProvider().get("global"),
                       sameInstance(globalOpenTelemetry.getTracerProvider().get("global")));
            assertFalse(openTelemetry.closed(), "Unselected OpenTelemetry should not be closed while the registry is active");
        } finally {
            manager.shutdown();
        }

        assertFalse(openTelemetry.closed(), "Unselected OpenTelemetry should not be closed when the registry shuts down");
    }

    @Test
    void registryOpenTelemetryIsNotClosedWhenRegistryShutsDown() {
        CloseableOpenTelemetry openTelemetry = new CloseableOpenTelemetry();
        ServiceRegistryManager manager = ServiceRegistryManager.start(ServiceRegistryConfig.builder()
                .putContractInstance(Config.class, Config.empty())
                .putContractInstance(OpenTelemetry.class, openTelemetry)
                .build());
        try {
            assertThat("OpenTelemetry service", manager.registry().get(OpenTelemetry.class), sameInstance(openTelemetry));
        } finally {
            manager.shutdown();
        }

        assertFalse(openTelemetry.closed(), "Registry-provided OpenTelemetry should remain caller-owned");
    }

    @Test
    void firstActiveStrategyOwnsApplicationOpenTelemetry() {
        OpenTelemetry firstOpenTelemetry = new NamedOpenTelemetry();
        OpenTelemetry secondOpenTelemetry = new NamedOpenTelemetry();
        ServiceRegistryManager manager = strategyRegistryManager(new FixedStrategy("first-service", firstOpenTelemetry),
                                                                 new FixedStrategy("second-service", secondOpenTelemetry));
        try {
            assertThat("OpenTelemetry service", manager.registry().get(OpenTelemetry.class), sameInstance(firstOpenTelemetry));
        } finally {
            manager.shutdown();
        }
    }

    private static ServiceRegistryManager registryManager(Config config) {
        return ServiceRegistryManager.create(ServiceRegistryConfig.builder()
                                                     .putContractInstance(Config.class, config)
                                                     .build());
    }

    private static ServiceRegistryManager strategyRegistryManager(OpenTelemetryOwnershipStrategy... strategies) {
        ServiceRegistryConfig.Builder builder = ServiceRegistryConfig.builder()
                .putContractInstance(Config.class, Config.empty());
        double weight = Weighted.DEFAULT_WEIGHT + 100;
        for (OpenTelemetryOwnershipStrategy strategy : strategies) {
            builder.addServiceDescriptor(ExistingInstanceDescriptor.<OpenTelemetryOwnershipStrategy>create(
                    strategy,
                    Set.of(OpenTelemetryOwnershipStrategy.class),
                    weight--));
        }
        return ServiceRegistryManager.start(builder.build());
    }

    private static final class FixedStrategy implements OpenTelemetryOwnershipStrategy {
        private final String serviceName;
        private final OpenTelemetry openTelemetry;
        private final boolean global;

        private FixedStrategy(String serviceName) {
            this(serviceName, OpenTelemetry.noop());
        }

        private FixedStrategy(String serviceName, OpenTelemetry openTelemetry) {
            this(serviceName, openTelemetry, false);
        }

        private FixedStrategy(String serviceName, OpenTelemetry openTelemetry, boolean global) {
            this.serviceName = serviceName;
            this.openTelemetry = openTelemetry;
            this.global = global;
        }

        @Override
        public boolean active(Config rootConfig) {
            return true;
        }

        @Override
        public String serviceName(Config rootConfig) {
            return serviceName;
        }

        @Override
        public OpenTelemetry create(Config rootConfig) {
            return openTelemetry;
        }

        @Override
        public boolean global(Config rootConfig) {
            return global;
        }
    }

    private static class NamedOpenTelemetry implements OpenTelemetry {
        private final Map<String, io.opentelemetry.api.trace.Tracer> tracers = new HashMap<>();
        private final io.opentelemetry.api.trace.TracerProvider tracerProvider =
                new io.opentelemetry.api.trace.TracerProvider() {
                    @Override
                    public io.opentelemetry.api.trace.Tracer get(String instrumentationScopeName) {
                        return tracers.computeIfAbsent(instrumentationScopeName, NamedTracer::new);
                    }

                    @Override
                    public io.opentelemetry.api.trace.Tracer get(String instrumentationScopeName,
                                                                 String instrumentationScopeVersion) {
                        return get(instrumentationScopeName);
                    }
                };

        @Override
        public io.opentelemetry.api.trace.TracerProvider getTracerProvider() {
            return tracerProvider;
        }

        @Override
        public ContextPropagators getPropagators() {
            return ContextPropagators.noop();
        }
    }

    private static final class CloseableOpenTelemetry extends NamedOpenTelemetry implements AutoCloseable {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }

        private boolean closed() {
            return closed;
        }
    }

    private static final class NamedTracer implements io.opentelemetry.api.trace.Tracer {
        private final String name;

        private NamedTracer(String name) {
            this.name = name;
        }

        @Override
        public io.opentelemetry.api.trace.SpanBuilder spanBuilder(String spanName) {
            throw new UnsupportedOperationException(name + " test tracer does not create spans");
        }
    }
}
