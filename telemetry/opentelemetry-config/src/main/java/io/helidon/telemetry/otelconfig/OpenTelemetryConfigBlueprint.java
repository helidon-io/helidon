/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;

/**
 * OpenTelemetry settings.
 */
@Prototype.Blueprint(decorator = OpenTelemetryConfigSupport.BuildDecorator.class)
@Prototype.Configured("telemetry")
@Prototype.CustomMethods(OpenTelemetryConfigSupport.CustomMethods.class)
interface OpenTelemetryConfigBlueprint extends Prototype.Factory<HelidonOpenTelemetry> {

    /**
     * Service name used in sending telemetry data to the collector.
     *
     * @return service name
     */
    @Option.Configured
    @Option.Required
    String service();

    /**
     * Whether the OpenTelemetry support is enabled.
     *
     * @return true if enabled; false otherwise
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Whether the {@link io.opentelemetry.api.OpenTelemetry} instance created from this configuration should be made the
     * global one.
     *
     * @return true if the configured instance should be made global; false otherwise
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean global();

    /**
     * OpenTelemetry {@link io.opentelemetry.context.propagation.TextMapPropagator} instances added explicitly by the app.
     * <p>
     * Default: {@value ContextPropagationType#DEFAULT_NAMES}.
     *
     * @return explicitly-added
     */
    @Option.Configured
    @Option.Singular
    @Option.DefaultCode("ContextPropagationType.DEFAULT_PROPAGATORS")
    List<TextMapPropagator> propagators();

    /**
     * OpenTelemetry tracing settings.
     *
     * @return tracing settings
     */
    @Option.Access("")
    @Option.Configured("signals.tracing")
    Optional<OpenTelemetryTracingConfig> tracingConfig();

    /**
     * Sets the tracer provider that OpenTelemetry should use.
     *
     * @return OpenTelemetry tracer provider
     */
    Optional<TracerProvider> tracerProvider();

    /**
     * The {@link io.opentelemetry.api.OpenTelemetry} instance to use for telemetry.
     * <p>
     * Typically, this value will be the OpenTelemetry SDK instance created using this configuration, but if some other
     * code (such as the OpenTelemetry agent) has already set the OTel global instance, this value will be that global instance.
     *
     * @return the OpenTelemetry instance
     */
    io.opentelemetry.api.OpenTelemetry openTelemetry();

    /**
     * The {@link io.opentelemetry.sdk.OpenTelemetrySdk} to use (restricted visibility).
     *
     * @return the SDK
     */
    @Option.Access("")
    OpenTelemetrySdk openTelemetrySdk();

}
