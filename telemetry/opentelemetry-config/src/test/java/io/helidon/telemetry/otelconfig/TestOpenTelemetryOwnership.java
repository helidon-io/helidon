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

package io.helidon.telemetry.otelconfig;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testing.Test(perMethod = true)
class TestOpenTelemetryOwnership {
    private static final String OTEL_AUTO_CONFIGURE = "otel.java.global-autoconfigure.enabled";

    @BeforeEach
    void resetGlobalOpenTelemetryBefore() {
        GlobalOpenTelemetry.resetForTest();
    }

    @AfterEach
    void resetGlobalOpenTelemetryAfter() {
        GlobalOpenTelemetry.resetForTest();
    }

    @Test
    void telemetryConfigOwnsOpenTelemetryAndTracerTogether() {
        Config config = Config.just(ConfigSources.create(
                """
                        telemetry:
                          service: telemetry-owner
                        """,
                MediaTypes.APPLICATION_YAML));
        Services.set(Config.class, config);

        OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
        Tracer tracer = Services.get(Tracer.class);

        assertThat("OpenTelemetry service", openTelemetry, notNullValue());
        assertThat("Tracer service", tracer, notNullValue());
        assertTracerUsesOpenTelemetry(openTelemetry, tracer, "telemetry-owner");
        assertTrue(GlobalOpenTelemetry.isSet(), "Helidon telemetry should assign the OpenTelemetry global by default");
        assertThat("OpenTelemetry global", openTelemetry, sameInstance(GlobalOpenTelemetry.get()));

        Span span = tracer.spanBuilder("telemetry-owned-span").start();
        try {
            assertThat("Span ID", span.context().spanId(), not(containsString("00000000")));
        } finally {
            span.end();
        }
    }

    @Test
    void telemetryConfigGlobalFalseOwnsWithoutPublishingGlobalOpenTelemetry() {
        Config config = Config.just(ConfigSources.create(
                """
                        telemetry:
                          service: telemetry-owner
                          global: false
                        """,
                MediaTypes.APPLICATION_YAML));
        Services.set(Config.class, config);

        OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
        Tracer tracer = Services.get(Tracer.class);

        assertThat("OpenTelemetry service", openTelemetry, notNullValue());
        assertThat("Tracer service", tracer, notNullValue());
        assertTracerUsesOpenTelemetry(openTelemetry, tracer, "telemetry-owner");
        assertFalse(GlobalOpenTelemetry.isSet(), "Helidon telemetry should not assign the OpenTelemetry global");

        Span span = tracer.spanBuilder("telemetry-global-span").start();
        try {
            assertThat("Span ID", span.context().spanId(), not(containsString("00000000")));
        } finally {
            span.end();
        }
    }

    @Test
    void noConfigFallbackAdoptsExistingGlobalOpenTelemetry() {
        GlobalOpenTelemetry.set(OpenTelemetry.noop());
        Config config = Config.empty();
        Services.set(Config.class, config);

        OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
        Tracer tracer = Services.get(Tracer.class);

        assertThat("OpenTelemetry service", openTelemetry, sameInstance(GlobalOpenTelemetry.get()));
        assertThat("Tracer service", tracer, notNullValue());
    }

    @Test
    void telemetryDisabledDoesNotAdoptExistingGlobalOpenTelemetry() {
        OpenTelemetry globalOpenTelemetry = OpenTelemetrySdk.builder().build();
        GlobalOpenTelemetry.set(globalOpenTelemetry);
        Config config = Config.just(ConfigSources.create(
                """
                        telemetry:
                          service: telemetry-owner
                          enabled: false
                        """,
                MediaTypes.APPLICATION_YAML));
        Services.set(Config.class, config);

        OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
        Tracer tracer = Services.get(Tracer.class);

        assertThat("OpenTelemetry service", openTelemetry, not(sameInstance(globalOpenTelemetry)));
        assertFalse(tracer.enabled(), "Disabled telemetry should provide a disabled application tracer");

        Span span = tracer.spanBuilder("telemetry-disabled-span").start();
        try {
            assertThat("Span ID", span.context().spanId(), containsString("no-op"));
        } finally {
            span.end();
        }
    }

    @Test
    void telemetryDisabledAllowsTracingConfigToOwnApplicationTelemetry() {
        Config config = Config.just(ConfigSources.create(
                """
                        telemetry:
                          service: telemetry-owner
                          enabled: false
                        tracing:
                          service: tracing-owner
                          global: false
                        """,
                MediaTypes.APPLICATION_YAML));
        Services.set(Config.class, config);

        OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
        Tracer tracer = Services.get(Tracer.class);

        assertThat("OpenTelemetry service", openTelemetry, notNullValue());
        assertThat("Tracer service", tracer, notNullValue());
        assertTracerUsesOpenTelemetry(openTelemetry, tracer, "tracing-owner");
        assertFalse(GlobalOpenTelemetry.isSet(), "Tracing ownership should not assign the OpenTelemetry global");

        Span span = tracer.spanBuilder("telemetry-disabled-tracing-owned-span").start();
        try {
            assertThat("Span ID", span.context().spanId(), not(containsString("00000000")));
        } finally {
            span.end();
        }
    }

    @Test
    void telemetryRegisteredFalseAllowsTracingConfigToOwnApplicationTelemetry() {
        Config config = Config.just(ConfigSources.create(
                """
                        telemetry:
                          service: telemetry-owner
                          registered: false
                        tracing:
                          service: tracing-owner
                          global: false
                        """,
                MediaTypes.APPLICATION_YAML));
        Services.set(Config.class, config);

        OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
        Tracer tracer = Services.get(Tracer.class);

        assertThat("OpenTelemetry service", openTelemetry, notNullValue());
        assertThat("Tracer service", tracer, notNullValue());
        assertTracerUsesOpenTelemetry(openTelemetry, tracer, "tracing-owner");
        assertFalse(GlobalOpenTelemetry.isSet(), "Tracing ownership should not assign the OpenTelemetry global");

        Span span = tracer.spanBuilder("telemetry-registered-false-tracing-owned-span").start();
        try {
            assertThat("Span ID", span.context().spanId(), not(containsString("00000000")));
        } finally {
            span.end();
        }
    }

    @Test
    void tracingDisabledOverridesTelemetryApplicationTracer() {
        Config config = Config.just(ConfigSources.create(
                """
                        telemetry:
                          service: telemetry-owner
                        tracing:
                          service: tracing-owner
                          enabled: false
                        """,
                MediaTypes.APPLICATION_YAML));
        Services.set(Config.class, config);

        OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
        Tracer tracer = Services.get(Tracer.class);

        assertThat("OpenTelemetry service", openTelemetry, notNullValue());
        assertFalse(tracer.enabled(), "Disabled tracing should provide a disabled application tracer");

        Span span = tracer.spanBuilder("telemetry-with-disabled-tracing-span").start();
        try {
            assertThat("Span ID", span.context().spanId(), containsString("no-op"));
        } finally {
            span.end();
        }
    }

    @Test
    void telemetryOwnerAllowsPathOnlyTracingConfiguration() {
        Config config = Config.just(ConfigSources.create(
                """
                        telemetry:
                          service: telemetry-owner
                        tracing:
                          paths:
                          - path: "/health"
                            enabled: false
                        """,
                MediaTypes.APPLICATION_YAML));
        Services.set(Config.class, config);

        OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
        Tracer tracer = Services.get(Tracer.class);

        assertThat("OpenTelemetry service", openTelemetry, notNullValue());
        assertThat("Tracer service", tracer, notNullValue());
        assertTracerUsesOpenTelemetry(openTelemetry, tracer, "telemetry-owner");
        assertTrue(GlobalOpenTelemetry.isSet(), "Telemetry ownership should assign the OpenTelemetry global by default");
        assertThat("OpenTelemetry global", openTelemetry, sameInstance(GlobalOpenTelemetry.get()));

        Span span = tracer.spanBuilder("telemetry-owner-with-path-config").start();
        try {
            assertThat("Span ID", span.context().spanId(), not(containsString("00000000")));
        } finally {
            span.end();
        }
    }

    @Test
    void telemetryConfigRegisteredFalseDoesNotOwnApplicationOpenTelemetry() {
        String originalAutoConfigure = System.setProperty(OTEL_AUTO_CONFIGURE, "false");
        try {
            Config config = Config.just(ConfigSources.create(
                    """
                            telemetry:
                              service: telemetry-owner
                              registered: false
                            """,
                    MediaTypes.APPLICATION_YAML));
            Services.set(Config.class, config);

            OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
            Tracer tracer = Services.get(Tracer.class);

            assertThat("OpenTelemetry service", openTelemetry, notNullValue());
            assertThat("Tracer service", tracer, notNullValue());
            assertFalse(GlobalOpenTelemetry.isSet(), "Disabled telemetry ownership should not assign the OpenTelemetry global");
            assertThat("OpenTelemetry service", openTelemetry, sameInstance(OpenTelemetry.noop()));

            Span span = tracer.spanBuilder("telemetry-registered-disabled-span").start();
            try {
                assertThat("Span ID", span.context().spanId(), containsString("00000000"));
            } finally {
                span.end();
            }
        } finally {
            restoreAutoConfigure(originalAutoConfigure);
        }
    }

    @Test
    void telemetryConfigRegisteredFalseAdoptsAutoConfiguredGlobalOpenTelemetry() {
        String originalAutoConfigure = System.setProperty(OTEL_AUTO_CONFIGURE, "true");
        try {
            Config config = Config.just(ConfigSources.create(
                    """
                            telemetry:
                              service: telemetry-owner
                              registered: false
                            """,
                    MediaTypes.APPLICATION_YAML));
            Services.set(Config.class, config);

            OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
            Tracer tracer = Services.get(Tracer.class);

            assertThat("OpenTelemetry service", openTelemetry, notNullValue());
            assertThat("Tracer service", tracer, notNullValue());
            assertTrue(GlobalOpenTelemetry.isSet(), "OpenTelemetry autoconfigure should assign the OpenTelemetry global");

            Span span = tracer.spanBuilder("telemetry-registered-disabled-autoconfigured-span").start();
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
    void telemetryConfigGlobalFailsWhenGlobalAlreadyExists() {
        GlobalOpenTelemetry.set(OpenTelemetry.noop());
        Config config = Config.just(ConfigSources.create(
                """
                        telemetry:
                          service: telemetry-owner
                        """,
                MediaTypes.APPLICATION_YAML));
        Services.set(Config.class, config);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> Services.get(OpenTelemetry.class));
        assertThat(e.getMessage(), containsString("OpenTelemetry global is already initialized"));
    }

    @Test
    void telemetryConfigTakesPrecedenceOverTracingConfig() {
        Config config = Config.just(ConfigSources.create(
                """
                        telemetry:
                          service: telemetry-owner
                        tracing:
                          service: tracing-owner
                        """,
                MediaTypes.APPLICATION_YAML));
        Services.set(Config.class, config);

        OpenTelemetry openTelemetry = Services.get(OpenTelemetry.class);
        Tracer tracer = Services.get(Tracer.class);

        assertThat("OpenTelemetry service", openTelemetry, notNullValue());
        assertThat("Tracer service", tracer, notNullValue());
        assertTracerUsesOpenTelemetry(openTelemetry, tracer, "telemetry-owner");
        assertTrue(GlobalOpenTelemetry.isSet(), "Telemetry ownership should assign the OpenTelemetry global by default");
        assertThat("OpenTelemetry global", openTelemetry, sameInstance(GlobalOpenTelemetry.get()));

        Span span = tracer.spanBuilder("telemetry-precedence-span").start();
        try {
            assertThat("Span ID", span.context().spanId(), not(containsString("00000000")));
        } finally {
            span.end();
        }
    }

    private static void assertTracerUsesOpenTelemetry(OpenTelemetry openTelemetry, Tracer tracer, String serviceName) {
        assertThat("Tracer delegate",
                   tracer.unwrap(io.opentelemetry.api.trace.Tracer.class),
                   sameInstance(openTelemetry.getTracer(serviceName)));
    }

    private static void restoreAutoConfigure(String originalAutoConfigure) {
        if (originalAutoConfigure == null) {
            System.clearProperty(OTEL_AUTO_CONFIGURE);
        } else {
            System.setProperty(OTEL_AUTO_CONFIGURE, originalAutoConfigure);
        }
        GlobalOpenTelemetry.resetForTest();
    }
}
