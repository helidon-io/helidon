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
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;

/**
 * OpenTelemetry tracer settings.
 */
@Prototype.Configured
@Prototype.Blueprint(decorator = OpenTelemetryTracingConfigSupport.BuilderDecorator.class)
@Prototype.CustomMethods(OpenTelemetryTracingConfigSupport.CustomMethods.class)
interface OpenTelemetryTracingConfigBlueprint {

    /**
     * Tracing sampler.
     *
     * @return tracing sampler
     */
    @Option.Configured()
    Optional<Sampler> sampler();

    /**
     * Tracing span limits.
     *
     * @return tracing span limits
     */
    @Option.Configured
    Optional<SpanLimits> spanLimits();

    /**
     * Settings for span processors.
     *
     * @return span processors
     */
    @Option.Access("")
    @Option.Configured("processors")
    @Option.Singular
    List<SpanProcessorConfig> processorConfigs();

    /**
     * Constructed span processors.
     *
     * @return span processors
     */
    @Option.Singular
    List<SpanProcessor> processors();

    /**
     * Span exporters.
     *
     * @return span exporters
     */
    @Option.Configured
    @Option.Singular
    Map<String, SpanExporter> exporters();

    /**
     * String attributes
     *
     * @return string attributes
     */
    @Option.Configured
    @Option.Singular
    Map<String, String> attributes();

    /**
     * Numeric attributes.
     *
     * @return numeric attributes
     */
    @Option.Configured
    @Option.Singular
    Map<String, Number> numericAttributes();

    /**
     * Boolean attributes.
     *
     * @return boolean attributes
     */
    @Option.Configured
    @Option.Singular
    Map<String, Boolean> booleanAttributes();

    /**
     * Used internally to share information with the parent prototype.
     *
     * @return shared tracer builder information
     */
    @Option.Access("")
    TracingBuilderInfo tracingBuilderInfo();

}
