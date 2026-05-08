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

/**
 * Types of OpenTelemetry log exporters supported via Helidon telemetry logging configuration.
 * <p>
 * See <a href="https://opentelemetry.io/docs/languages/java/configuration/#properties-exporters">OTel exporters</a>.
 */
public enum LogExporterType {

    /*
    Enum values are chosen to be the upper-case version of the OTel setting values so Helidon's built-in enum config mapping
    works.
     */

    /**
     * OpenTelemetry Protocol {@link io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter} and
     * {@link io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporterBuilder}.
     */
    OTLP, // There are different defaults for the different subtypes of OTLP exporters.

    /**
     * Console {@link io.opentelemetry.exporter.logging.SystemOutLogRecordExporter}.
     */
    CONSOLE,

    /**
     * Writes logs to a Logger in OTLP JSON
     * format {@link io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingLogRecordExporter}.
     */
    LOGGING_OTLP;

    static final LogExporterType DEFAULT = OTLP;
}
