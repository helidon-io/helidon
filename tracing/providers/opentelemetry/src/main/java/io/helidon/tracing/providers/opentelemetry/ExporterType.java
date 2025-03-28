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

/**
 * Types of OpenTelemetry span exporters supported via Helidon {@code tracing} configuration.
 * <p>
 * See <a href="https://opentelemetry.io/docs/languages/java/configuration/#properties-exporters">OTel exporters</a>.
 */
public enum ExporterType {

    /**
     * OpenTelemetry Protocol {@link io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter} and
     * {@link io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter}.
     */
    OTLP, // There are different defaults for the two different subtypes of OTLP exporters.

    /**
     * Zipkin {@link io.opentelemetry.exporter.zipkin.ZipkinSpanExporter}.
     */
    ZIPKIN("http", 9411, "api/v2/spans"),

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

    private final String defaultProtocol;
    private final int defaultPort;
    private final String defaultPath;

    ExporterType(String defaultProtocol, int defaultPort, String defaultPath) {
        this.defaultProtocol = defaultProtocol;
        this.defaultPort = defaultPort;
        this.defaultPath = defaultPath;
    }

    ExporterType() {
        this(null, 0, null);
    }

    String defaultProtocol() {
        return defaultProtocol;
    }

    Integer defaultPort() {
        return defaultPort;
    }

    String defaultPath() {
        return defaultPath;
    }
}
