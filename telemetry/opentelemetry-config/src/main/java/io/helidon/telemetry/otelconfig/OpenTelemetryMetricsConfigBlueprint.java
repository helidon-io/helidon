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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricReader;

/**
 * OpenTelemetry metrics settings.
 */
@Prototype.Configured
@Prototype.Blueprint(decorator = OpenTelemetryMetricsConfigSupport.BuilderDecorator.class)
@Prototype.CustomMethods(OpenTelemetryMetricsConfigSupport.CustomMethods.class)
interface OpenTelemetryMetricsConfigBlueprint extends TypedAttributes {

    /**
     * Constructed metric readers.
     *
     * @return metric readers
     */
    @Option.Singular
    List<MetricReader> readers();

    /**
     * Settings for metric readers.
     *
     * @return metric readers
     */
    @Option.Access("")
    @Option.Configured("readers")
    @Option.Singular
    List<MetricReaderConfig> readerConfigs();

    /**
     * Metric exporter configurations, configurable using {@link io.helidon.telemetry.otelconfig.MetricExporterConfig}.
     *
     * @return metric exporters
     */
    @Option.Configured("exporters")
    @Option.Singular
    Map<String, MetricExporter> exporters();

    /**
     * Metric view information, configurable using {@link io.helidon.telemetry.otelconfig.ViewRegistrationConfig}.
     *
     * @return metric view information
     */
    @Option.Access("")
    @Option.Configured("views")
    List<OpenTelemetryMetricsConfigSupport.ViewRegistration> viewRegistrations();

    /**
     * Information shared with the parent prototype.
     *
     * @hidden internal use only
     * @return shared metrics builder information
     */
    @Option.Access("")
    MetricsBuilderInfo metricsBuilderInfo();

}
