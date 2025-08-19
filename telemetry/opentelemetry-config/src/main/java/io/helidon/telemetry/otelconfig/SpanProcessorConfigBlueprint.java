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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Generic configuration for a {@link io.opentelemetry.sdk.trace.SpanProcessor}, linked to a
 * {@link io.opentelemetry.sdk.trace.export.SpanExporter} by its name in the configuration.
 */
@Prototype.Blueprint
@Prototype.Configured
interface SpanProcessorConfigBlueprint {

    /**
     * Name(s) of the span exporter(s) this span processor should use; specifying no names uses all configured exporters (or
     * if no exporters are configured, the default OpenTelemetry exporter(s)).
     * <p>
     * Each name must be the name of one of the configured {@link OpenTelemetryTracingConfig#exporterConfigs()}.
     *
     * @return span exporter name
     */
    @Option.Configured
    List<String> exporters();

    /**
     * Span processor type.
     * @return span processor type
     */
    @Option.Configured
    @Option.Required
    SpanProcessorType type();

}
