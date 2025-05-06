/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

/**
 * Open telemetry support for Helidon tracing.
 */
@SuppressWarnings({ "requires-automatic", "requires-transitive-automatic" })
module io.helidon.tracing.providers.opentelemetry {
    requires io.helidon.config;
    requires io.opentelemetry.context;
    requires io.opentelemetry.extension.trace.propagation;
    requires io.opentelemetry.sdk;
    requires io.opentelemetry.sdk.common;
    requires io.opentelemetry.sdk.trace;
    requires io.opentelemetry.semconv;

    requires static io.helidon.config.metadata;

    requires transitive io.helidon.tracing;
    requires transitive io.opentelemetry.api;
    requires io.helidon.common.configurable;
    requires io.helidon.common.context;

    requires static io.opentelemetry.exporter.otlp;
    requires static io.opentelemetry.exporter.zipkin;
    requires static io.opentelemetry.exporter.logging;
    requires static io.opentelemetry.exporter.logging.otlp;


    exports io.helidon.tracing.providers.opentelemetry;

    uses io.helidon.tracing.SpanListener;

    provides io.helidon.tracing.spi.TracerProvider
            with io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracerProvider;

    provides io.helidon.common.context.spi.DataPropagationProvider
            with io.helidon.tracing.providers.opentelemetry.OpenTelemetryDataPropagationProvider;

    provides io.helidon.config.spi.ConfigMapperProvider
            with io.helidon.tracing.providers.opentelemetry.OtelConfigMapper;

}