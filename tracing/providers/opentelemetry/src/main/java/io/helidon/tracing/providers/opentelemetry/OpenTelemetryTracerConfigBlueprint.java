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

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.tracing.ExtendedTracerConfig;
import io.helidon.tracing.SpanListener;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.trace.SpanProcessor;

/**
 * Settings for OpenTelemetry tracer configuration under the {@value OpenTelemetryTracerConfig#TRACING_CONFIG_KEY} config key.
 */
@Prototype.Configured(OpenTelemetryTracerConfigBlueprint.TRACING_CONFIG_KEY)
@Prototype.Blueprint(decorator = OpenTelemetryTracerBlueprintSupport.Decorator.class)
@Prototype.CustomMethods(OpenTelemetryTracerBlueprintSupport.CustomMethods.class)
interface OpenTelemetryTracerConfigBlueprint extends ExtendedTracerConfig, Prototype.Factory<OpenTelemetryTracer> {

    /**
     * Config key for tracing settings.
     */
    String TRACING_CONFIG_KEY = "tracing";

    /**
     * Context propagators.
     *
     * @return context propagators
     */
    @Option.Configured
    @Option.Singular
    @Option.DefaultCode(OpenTelemetryTracerBlueprintSupport.PROPAGATORS_DEFAULT)
    List<TextMapPropagator> propagators();

    /**
     * Type of OTLP exporter to use for pushing span data.
     *
     * @return OTLP exporter type
     */
    @Option.Configured
    @Option.Default("GRPC")
    OtlpExporterProtocolType exporterType();

    /**
     * Span listeners to be notified of span life cycle events.
     *
     * @return span listeners
     */
    @Option.Singular
    List<SpanListener> spanListeners();

    /**
     * {@linkplain io.opentelemetry.api.OpenTelemetry OpenTelemetry} instance to use instead of constructing one from other
     * config settings.
     *
     * @return {@code OpenTelemetry} instance
     */
    @Option.Access("")
    @Option.Decorator(OpenTelemetryTracerBlueprintSupport.OpenTelemetryDecorator.class)
    OpenTelemetry openTelemetry();

    /**
     * {@linkplain io.helidon.tracing.Tracer Tracer} instance to use instead of constructing one from other config settings.
     *
     * @return {@code Tracer} instance
     */
    @Option.Access("")
    Tracer delegate();

    /**
     * Typically a composite propagator gathering the propagators assigned.
     *
     * @return propagator
     */
    @Option.Access("")
    TextMapPropagator propagator();

    /**
     * Span processors added to the implicit one automatically created.
     * <p>
     * Primarily for testing to enroll a span processor with an in-memory span exporter.
     *
     * @return span processors
     */
    @Option.Access("")
    @Option.Singular
    List<SpanProcessor> spanProcessors();

}
