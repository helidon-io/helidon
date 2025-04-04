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

package io.helidon.tracing.providers.opentelemetry;

import java.util.List;
import java.util.Map;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * OpenTelemetry tracing configuration.
 * <p>
 * Helidon automatically builds span exporters and span processors as described in the configuration. Developers can also add
 * prebuilt {@link io.opentelemetry.sdk.trace.export.SpanExporter} instances using
 * {@link TracingConfig.Builder#putSpanExporter(String, io.opentelemetry.sdk.trace.export.SpanExporter)} and prebuilt
 * {@link io.opentelemetry.sdk.trace.SpanProcessor} instances using
 * {@link TracingConfig.Builder#addSpanProcessor(io.opentelemetry.sdk.trace.SpanProcessor)}.
 */
@Prototype.Blueprint(decorator = TracingConfigSupport.BuilderDecorator.class)
@Prototype.Configured("tracing")
@Prototype.CustomMethods(TracingConfigSupport.class)
interface TracingConfigBlueprint {

    /**
     * Sampler configuration.
     *
     * @return sampler settings
     */
    @Option.Configured
    SamplerConfig sampler();

    /**
     * Span exporters used for transmitting tracing spans.
     *
     * @return span exporters
     */
    @Option.Configured
    @Option.Singular
    Map<String, SpanExporter> spanExporters();

    @Option.Configured("span-processors")
    @Option.Singular
    List<SpanProcessorConfig> spanProcessorConfigs();

    @Option.Singular
    List<SpanProcessor> spanProcessors();

    @Option.Configured("propagation")
    @Option.Singular
    List<ContextPropagation> contextPropagations();

}

