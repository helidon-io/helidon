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

package io.helidon.tracing.providers.opentelemetry;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.helidon.common.Weighted;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.logging.common.HelidonMdc;
import io.helidon.service.registry.ExistingInstanceDescriptor;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.ServiceRegistryManager;
import io.helidon.service.registry.Services;
import io.helidon.telemetry.opentelemetry.spi.OpenTelemetryOwnershipStrategy;
import io.helidon.testing.junit5.Testing;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testing.Test(perMethod = true)
class TestApplicationOpenTelemetryOwnership {
    private static final String OTEL_AUTO_CONFIGURE = "otel.java.global-autoconfigure.enabled";

    @BeforeEach
    void resetGlobalOpenTelemetryBefore() {
        GlobalOpenTelemetry.resetForTest();
        OpenTelemetryTracerProvider.resetForTest();
    }

    @AfterEach
    void resetGlobalOpenTelemetryAfter() {
        GlobalOpenTelemetry.resetForTest();
        OpenTelemetryTracerProvider.resetForTest();
    }

    @Test
    void tracingConfigOwnsOpenTelemetryAndTracerTogether() {
        Config config = Config.just(ConfigSources.create(
                """
                        tracing:
                          service: tracing-owner
                        """,
                MediaTypes.APPLICATION_YAML));
        Services.set(Config.class, config);

        OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
        Tracer tracer = Services.get(Tracer.class);

        assertThat("Tracer-backed OpenTelemetry",
                   tracer.unwrap(OpenTelemetryTracer.class).prototype().openTelemetry(),
                   sameInstance(openTelemetry));

        Span span = tracer.spanBuilder("owned-span").start();
        try {
            assertThat("Span ID", span.context().spanId(), not(containsString("00000000")));
            try (Scope ignored = span.activate()) {
                Span currentSpan = Span.current().orElseThrow();
                assertThat("Current span", currentSpan.context().spanId(), is(span.context().spanId()));
                assertThat("MDC trace ID", HelidonMdc.get("trace_id").orElseThrow(), is(span.context().traceId()));
            }
        } finally {
            span.end();
        }
    }

    @Test
    void tracingConfigOwnsWhenTelemetryConfigPresentWithoutTelemetryOwner() {
        Config config = Config.just(ConfigSources.create(
                """
                        telemetry:
                          service: telemetry-owner
                        tracing:
                          service: tracing-owner
                          global: false
                        """,
                MediaTypes.APPLICATION_YAML));
        Services.set(Config.class, config);

        OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
        Tracer tracer = Services.get(Tracer.class);

        assertThat("OpenTelemetry service", openTelemetry, notNullValue());
        assertThat("Tracer-backed OpenTelemetry",
                   tracer.unwrap(OpenTelemetryTracer.class).prototype().openTelemetry(),
                   sameInstance(openTelemetry));
        assertThat("Tracer delegate",
                   tracer.unwrap(io.opentelemetry.api.trace.Tracer.class),
                   sameInstance(openTelemetry.getTracer("tracing-owner")));
        assertFalse(GlobalOpenTelemetry.isSet(), "Tracing ownership should not assign the OpenTelemetry global");

        Span span = tracer.spanBuilder("tracing-with-telemetry-config-span").start();
        try {
            assertThat("Span ID", span.context().spanId(), not(containsString("00000000")));
        } finally {
            span.end();
        }
    }

    @Test
    void tracingConfigPublishesGlobalOpenTelemetryByDefault() {
        Config config = Config.just(ConfigSources.create(
                """
                        tracing:
                          service: tracing-owner
                        """,
                MediaTypes.APPLICATION_YAML));
        Services.set(Config.class, config);

        OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
        Tracer tracer = Services.get(Tracer.class);

        assertTrue(GlobalOpenTelemetry.isSet(), "Helidon tracing should assign the OpenTelemetry global when requested");
        assertThat("OpenTelemetry service", openTelemetry, sameInstance(GlobalOpenTelemetry.get()));
        assertThat("Tracer-backed OpenTelemetry",
                   tracer.unwrap(OpenTelemetryTracer.class).prototype().openTelemetry(),
                   sameInstance(openTelemetry));

        Span span = tracer.spanBuilder("global-owned-span").start();
        try {
            assertThat("Span ID", span.context().spanId(), not(containsString("00000000")));
        } finally {
            span.end();
        }
    }

    @Test
    void tracingDisabledProvidesNoOpApplicationTracer() {
        GlobalOpenTelemetry.set(OpenTelemetry.noop());
        Config config = Config.just(ConfigSources.create(
                """
                        tracing:
                          service: tracing-owner
                          enabled: false
                        """,
                MediaTypes.APPLICATION_YAML));
        Services.set(Config.class, config);

        OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
        Tracer tracer = Services.get(Tracer.class);

        assertThat("OpenTelemetry service", openTelemetry, sameInstance(GlobalOpenTelemetry.get()));
        assertFalse(tracer.enabled(), "Disabled tracing should provide a disabled application tracer");
        assertFalse(new OpenTelemetryTracerProvider().available(),
                    "Disabled tracing should not make the OpenTelemetry tracer provider available");

        Span span = tracer.spanBuilder("disabled-span").start();
        try {
            assertThat("Span ID", span.context().spanId(), containsString("no-op"));
        } finally {
            span.end();
        }
    }

    @Test
    void tracingConfigGlobalFalseOwnsWithoutPublishingGlobalOpenTelemetry() {
        Config config = Config.just(ConfigSources.create(
                """
                        tracing:
                          service: tracing-owner
                          global: false
                        """,
                MediaTypes.APPLICATION_YAML));
        Services.set(Config.class, config);

        OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
        Tracer tracer = Services.get(Tracer.class);

        assertFalse(GlobalOpenTelemetry.isSet(), "Tracing ownership should not assign the OpenTelemetry global");
        assertThat("Tracer-backed OpenTelemetry",
                   tracer.unwrap(OpenTelemetryTracer.class).prototype().openTelemetry(),
                   sameInstance(openTelemetry));

        Span span = tracer.spanBuilder("global-disabled-span").start();
        try {
            assertThat("Span ID", span.context().spanId(), not(containsString("00000000")));
        } finally {
            span.end();
        }
    }

    @Test
    void tracingConfigRegisteredFalseAdoptsAutoConfiguredGlobalOpenTelemetry() {
        String originalAutoConfigure = System.setProperty(OTEL_AUTO_CONFIGURE, "true");
        try {
            Config config = Config.just(ConfigSources.create(
                    """
                            tracing:
                              service: tracing-owner
                              registered: false
                            """,
                    MediaTypes.APPLICATION_YAML));
            Services.set(Config.class, config);

            OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
            Tracer tracer = Services.get(Tracer.class);

            assertTrue(GlobalOpenTelemetry.isSet(), "OpenTelemetry autoconfigure should assign the OpenTelemetry global");
            assertThat("OpenTelemetry service", openTelemetry, notNullValue());
            assertThat("Tracer-backed OpenTelemetry",
                       tracer.unwrap(OpenTelemetryTracer.class).prototype().openTelemetry(),
                       sameInstance(openTelemetry));

            Span span = tracer.spanBuilder("global-disabled-autoconfigured-span").start();
            try {
                assertThat("Span ID", span.context().spanId(), not(containsString("00000000")));
            } finally {
                span.end();
            }
        } finally {
            restoreAutoConfigure(originalAutoConfigure);
        }
    }

    @Test
    void tracingConfigGlobalAdoptsExistingGlobalOpenTelemetry() {
        OpenTelemetry globalOpenTelemetry = new NamedOpenTelemetry();
        GlobalOpenTelemetry.set(globalOpenTelemetry);
        Config config = Config.just(ConfigSources.create(
                """
                        tracing:
                          service: tracing-owner
                        """,
                MediaTypes.APPLICATION_YAML));
        Services.set(Config.class, config);

        OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
        Tracer tracer = Services.get(Tracer.class);

        assertThat("OpenTelemetry service",
                   openTelemetry.getTracerProvider().get("global"),
                   sameInstance(globalOpenTelemetry.getTracerProvider().get("global")));
        assertThat("OpenTelemetry global",
                   GlobalOpenTelemetry.get().getTracerProvider().get("global"),
                   sameInstance(globalOpenTelemetry.getTracerProvider().get("global")));
        assertThat("Tracer-backed OpenTelemetry",
                   tracer.unwrap(OpenTelemetryTracer.class).prototype().openTelemetry(),
                   sameInstance(openTelemetry));
    }

    @Test
    void externalGlobalOpenTelemetryIsAdopted() {
        GlobalOpenTelemetry.set(OpenTelemetry.noop());
        Config config = Config.just(ConfigSources.create(
                """
                        io:
                          helidon:
                            telemetry:
                              otel:
                                use-existing-instance: true
                        """,
                MediaTypes.APPLICATION_YAML));
        Services.set(Config.class, config);

        OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
        Tracer tracer = Services.get(Tracer.class);

        assertThat("OpenTelemetry service", openTelemetry, sameInstance(GlobalOpenTelemetry.get()));
        assertThat("Tracer-backed OpenTelemetry",
                   tracer.unwrap(OpenTelemetryTracer.class).prototype().openTelemetry(),
                   sameInstance(openTelemetry));
    }

    @Test
    void noConfigFallbackAdoptsExistingGlobalOpenTelemetry() {
        GlobalOpenTelemetry.set(OpenTelemetry.noop());
        Config config = Config.empty();
        Services.set(Config.class, config);

        OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
        Tracer tracer = Services.get(Tracer.class);

        assertThat("OpenTelemetry service", openTelemetry, sameInstance(GlobalOpenTelemetry.get()));
        assertThat("Tracer-backed OpenTelemetry",
                   tracer.unwrap(OpenTelemetryTracer.class).prototype().openTelemetry(),
                   sameInstance(openTelemetry));
    }

    @Test
    void noConfigFallbackAdoptsAutoConfiguredGlobalOpenTelemetry() {
        String originalAutoConfigure = System.setProperty(OTEL_AUTO_CONFIGURE, "true");
        try {
            Config config = Config.empty();
            Services.set(Config.class, config);

            OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
            Tracer tracer = Services.get(Tracer.class);

            assertTrue(GlobalOpenTelemetry.isSet(), "OpenTelemetry autoconfigure should assign the OpenTelemetry global");
            assertThat("OpenTelemetry service", openTelemetry, notNullValue());
            assertThat("Tracer-backed OpenTelemetry",
                       tracer.unwrap(OpenTelemetryTracer.class).prototype().openTelemetry(),
                       sameInstance(openTelemetry));

            Span span = tracer.spanBuilder("autoconfigured-span").start();
            try {
                assertThat("Span ID", span.context().spanId(), not(containsString("00000000")));
            } finally {
                span.end();
            }
        } finally {
            restoreAutoConfigure(originalAutoConfigure);
        }
    }

    @Test
    void noConfigFallbackUsesDefaultOpenTelemetry() {
        String originalAutoConfigure = System.setProperty(OTEL_AUTO_CONFIGURE, "false");
        try {
            GlobalOpenTelemetry.resetForTest();
            Config config = Config.empty();
            Services.set(Config.class, config);

            OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
            Tracer tracer = Services.get(Tracer.class);

            assertThat("OpenTelemetry service", openTelemetry, notNullValue());
            assertFalse(GlobalOpenTelemetry.isSet(), "Helidon tracing should not assign the OpenTelemetry global");
            assertThat("Tracer-backed OpenTelemetry",
                       tracer.unwrap(OpenTelemetryTracer.class).prototype().openTelemetry(),
                       sameInstance(openTelemetry));
        } finally {
            if (originalAutoConfigure == null) {
                System.clearProperty(OTEL_AUTO_CONFIGURE);
            } else {
                System.setProperty(OTEL_AUTO_CONFIGURE, originalAutoConfigure);
            }
            GlobalOpenTelemetry.resetForTest();
        }
    }

    @Test
    void autoConfigureGlobalOpenTelemetryUsesPropertyBeforeEnvironment() {
        assertTrue(HelidonOpenTelemetry.autoConfigureGlobalOpenTelemetry("true", null));
        assertTrue(HelidonOpenTelemetry.autoConfigureGlobalOpenTelemetry("true", "false"));
        assertTrue(HelidonOpenTelemetry.autoConfigureGlobalOpenTelemetry(null, "true"));

        assertFalse(HelidonOpenTelemetry.autoConfigureGlobalOpenTelemetry("false", "true"));
        assertFalse(HelidonOpenTelemetry.autoConfigureGlobalOpenTelemetry(null, null));
    }

    @Test
    void openTelemetryProviderIsAvailableAfterApplicationOpenTelemetryIsSelected() {
        assertFalse(GlobalOpenTelemetry.isSet(), "Test should start without an OpenTelemetry global");
        assertFalse(new OpenTelemetryTracerProvider().available(),
                    "OpenTelemetry provider should not become available from classpath presence alone");

        Config config = Config.just(ConfigSources.create(
                """
                        tracing:
                          service: tracing-owner
                        """,
                MediaTypes.APPLICATION_YAML));
        Services.set(Config.class, config);
        Services.get(OpenTelemetry.class);

        assertTrue(new OpenTelemetryTracerProvider().available(),
                   "OpenTelemetry provider should become available after application OpenTelemetry selection");
    }

    @Test
    void registryOpenTelemetryPublishesGlobalAndKeepsRegistryInstanceForApplicationTracer() {
        OpenTelemetry registryOpenTelemetry = OpenTelemetry.noop();
        Config config = Config.just(ConfigSources.create(
                """
                        tracing:
                          service: tracing-owner
                        """,
                MediaTypes.APPLICATION_YAML));
        Services.set(Config.class, config);
        Services.set(OpenTelemetry.class, registryOpenTelemetry);

        Tracer tracer = Services.get(Tracer.class);

        assertTrue(GlobalOpenTelemetry.isSet(), "Helidon tracing should assign the OpenTelemetry global when requested");
        assertThat("OpenTelemetry service", Services.get(OpenTelemetry.class), sameInstance(registryOpenTelemetry));
        assertThat("Tracer-backed OpenTelemetry",
                   tracer.unwrap(OpenTelemetryTracer.class).prototype().openTelemetry(),
                   sameInstance(registryOpenTelemetry));
    }

    @Test
    void registryOpenTelemetryIsUsedForApplicationTracer() {
        OpenTelemetry openTelemetry = OpenTelemetry.noop();
        Config config = Config.empty();
        Services.set(Config.class, config);
        Services.set(OpenTelemetry.class, openTelemetry);

        Tracer tracer = Services.get(Tracer.class);

        assertThat("OpenTelemetry service", Services.get(OpenTelemetry.class), sameInstance(openTelemetry));
        assertThat("Tracer-backed OpenTelemetry",
                   tracer.unwrap(OpenTelemetryTracer.class).prototype().openTelemetry(),
                   sameInstance(openTelemetry));
    }

    @Test
    void registryStartupOpenTelemetryIsUsedForApplicationTracer() {
        OpenTelemetry openTelemetry = new NamedOpenTelemetry();
        Config config = Config.just(ConfigSources.create(
                """
                        tracing:
                          service: tracing-owner
                        """,
                MediaTypes.APPLICATION_YAML));
        ServiceRegistryManager manager = ServiceRegistryManager.start(ServiceRegistryConfig.builder()
                                                                           .putContractInstance(Config.class, config)
                                                                           .putContractInstance(OpenTelemetry.class,
                                                                                                openTelemetry)
                                                                           .build());
        try {
            ServiceRegistry registry = manager.registry();
            Tracer tracer = registry.get(Tracer.class);

            assertThat("OpenTelemetry service", registry.get(OpenTelemetry.class), sameInstance(openTelemetry));
            assertThat("Tracer-backed OpenTelemetry",
                       tracer.unwrap(OpenTelemetryTracer.class).prototype().openTelemetry(),
                       sameInstance(openTelemetry));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void registryStartupOpenTelemetryUsesLaterRegistryService() {
        OpenTelemetry openTelemetry = new NamedOpenTelemetry();
        ServiceRegistryManager manager = ServiceRegistryManager.start(ServiceRegistryConfig.builder()
                .putContractInstance(Config.class, Config.empty())
                .addServiceDescriptor(ExistingInstanceDescriptor.<OpenTelemetry>create(
                        openTelemetry,
                        Set.of(OpenTelemetry.class),
                        Weighted.DEFAULT_WEIGHT - 100))
                .build());
        try {
            ServiceRegistry registry = manager.registry();
            Tracer tracer = registry.get(Tracer.class);

            assertThat("OpenTelemetry service", registry.get(OpenTelemetry.class), sameInstance(openTelemetry));
            assertThat("Tracer-backed OpenTelemetry",
                       tracer.unwrap(OpenTelemetryTracer.class).prototype().openTelemetry(),
                       sameInstance(openTelemetry));
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void applicationTelemetryIsCachedPerRegistry() {
        ServiceRegistryManager firstManager = registryManager(Config.just(ConfigSources.create(
                """
                        tracing:
                          service: first-owner
                          global: false
                        """,
                MediaTypes.APPLICATION_YAML)));
        ServiceRegistryManager secondManager = registryManager(Config.just(ConfigSources.create(
                """
                        tracing:
                          service: second-owner
                          global: false
                        """,
                MediaTypes.APPLICATION_YAML)));
        try {
            ServiceRegistry firstRegistry = firstManager.registry();
            ServiceRegistry secondRegistry = secondManager.registry();

            OpenTelemetry firstOpenTelemetry = firstRegistry.get(OpenTelemetry.class);
            OpenTelemetry secondOpenTelemetry = secondRegistry.get(OpenTelemetry.class);
            Tracer firstTracer = firstRegistry.get(Tracer.class);
            Tracer secondTracer = secondRegistry.get(Tracer.class);

            assertThat("Each registry has its own OpenTelemetry",
                       firstOpenTelemetry,
                       not(sameInstance(secondOpenTelemetry)));
            assertThat("Each registry has its own Tracer",
                       firstTracer,
                       not(sameInstance(secondTracer)));
            assertThat("First tracer-backed OpenTelemetry",
                       firstTracer.unwrap(OpenTelemetryTracer.class).prototype().openTelemetry(),
                       sameInstance(firstOpenTelemetry));
            assertThat("Second tracer-backed OpenTelemetry",
                       secondTracer.unwrap(OpenTelemetryTracer.class).prototype().openTelemetry(),
                       sameInstance(secondOpenTelemetry));
        } finally {
            firstManager.shutdown();
            secondManager.shutdown();
        }
    }

    @Test
    void firstActiveStrategyOwnsWhenMultipleStrategiesAreActive() {
        NamedOpenTelemetry openTelemetry = new NamedOpenTelemetry();
        OpenTelemetryOwnershipStrategy first = new FixedStrategy("first-service", openTelemetry);
        OpenTelemetryOwnershipStrategy second = new FixedStrategy("second-service", new NamedOpenTelemetry());
        ServiceRegistryManager manager = strategyRegistryManager(first, second);
        try {
            Tracer tracer = manager.registry().get(Tracer.class);

            assertThat("Selected tracer delegate",
                       tracer.unwrap(io.opentelemetry.api.trace.Tracer.class),
                       sameInstance(openTelemetry.getTracer("first-service")));
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

    private static void restoreAutoConfigure(String originalAutoConfigure) {
        if (originalAutoConfigure == null) {
            System.clearProperty(OTEL_AUTO_CONFIGURE);
        } else {
            System.setProperty(OTEL_AUTO_CONFIGURE, originalAutoConfigure);
        }
        GlobalOpenTelemetry.resetForTest();
    }

    private static final class FixedStrategy implements OpenTelemetryOwnershipStrategy {
        private final String serviceName;
        private final OpenTelemetry openTelemetry;

        private FixedStrategy(String serviceName) {
            this(serviceName, OpenTelemetry.noop());
        }

        private FixedStrategy(String serviceName, OpenTelemetry openTelemetry) {
            this.serviceName = serviceName;
            this.openTelemetry = openTelemetry;
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
