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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.sdk.logs.LogLimits;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;

/**
 * Configuration settings for OpenTelemetry logging. Optional values left unspecified in the configuration defer to the
 * OpenTelemetry defaults.
 */
@Prototype.Configured
@Prototype.Blueprint(decorator = OpenTelemetryLoggingConfigSupport.BuilderDecorator.class)
@Prototype.CustomMethods(OpenTelemetryLoggingConfigSupport.CustomMethods.class)
interface OpenTelemetryLoggingConfigBlueprint {

    /**
     * Whether the OpenTelemetry logger should be enabled. (Passed to OpenTelemetry.)
     *
     * @return true if the OpenTelemetry logger should be enabled, false otherwise
     */
    @Option.Configured
    Optional<Boolean> enabled();

    /**
     * Minimum severity level of log records to process.
     *
     * @return minimum severity level
     */
    @Option.Configured
    Optional<Severity> minimumSeverity();

    /**
     * Whether to include <em>only</em> log records from traces which are sampled. Defaults to the OpenTelemetry default.
     *
     * @return whether to restrict exported log records to only those from sampled traces
     */
    @Option.Configured
    Optional<Boolean> traceBased();

    /**
     * Log limits to apply to log transmission.
     *
     * @return log limits
     */
    @Option.Configured
    Optional<LogLimits> logLimits();

    /**
     * Settings for logging processors.
     *
     * @return logging processors
     */
    @Option.Access("")
    @Option.Configured("processors")
    @Option.Singular
    List<ProcessorConfig> processorConfigs();

    /**
     * Pre-constructed (non-configured) logging processors.
     *
     * @return logging processors
     */
    @Option.Singular
    List<LogRecordProcessor> processors();

    /**
     * Log record exporters.
     * <p>
     * The key in the map is a unique name--of the user's choice--for the exporter config settings.
     * The {@link ProcessorConfig#exporters()} config setting for a processor config specifies zero
     * or more of these names to associate the exporters built from the exporter configs with the processor
     * built from the processor config.
     *
     * @return log record exporters
     */
    @Option.Access("")
    @Option.Configured("exporters")
    @Option.Singular
    Map<String, LogRecordExporter> exporterConfigs();

    /**
     * Name/value pairs passed to OpenTelemetry.
     *
     * @return typed attribute settings
     */
    @Option.Configured
    Optional<AttributesBuilder> attributes();

    /**
     * Information shared with the parent prototype.
     *
     * @hidden internal use only
     * @return shared logging builder information
     */
    @Option.Access("")
    LoggingBuilderInfo loggingBuilderInfo();

}
