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

import io.helidon.common.config.Config;

/**
 * Types of OpenTelemetry span exporters supported via Helidon {@code tracing} configuration.
 * <p>
 * See <a href="https://opentelemetry.io/docs/languages/java/configuration/#properties-exporters">OTel exporters</a>.
 */
enum ExporterType {

    /**
     * OpenTelemetry Protocol {@link io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter} and
     * {@link io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter}.
     */
    OTLP, // There are different defaults for the different subtypes of OTLP exporters.

    /**
     * Zipkin {@link io.opentelemetry.exporter.zipkin.ZipkinSpanExporter}.
     */
    ZIPKIN,

    /**
     * Console ({@link io.opentelemetry.exporter.logging.LoggingSpanExporter}.
     */
    CONSOLE,

    /**
     * JSON logging to console {@link io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter}.
     */
    LOGGING_OTLP;

    static final String DEFAULT_NAME = "OTLP";
    static final ExporterType DEFAULT = OTLP;

    // This is easier than for some other enums because the OTel-friendly names happen to be just the enum values in lower case.
    static ExporterType from(String value) {
        return ExporterType.valueOf(value.toUpperCase());
    }

    static ExporterType from(Config config) {
        return from(config.asString().orElseThrow());
    }
}
