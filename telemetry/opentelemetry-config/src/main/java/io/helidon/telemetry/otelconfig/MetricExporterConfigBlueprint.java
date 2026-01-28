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

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.DefaultAggregationSelector;

/**
 * OpenTelemetry metric exporter settings.
 */
@Prototype.Configured
@Prototype.Blueprint
@Prototype.CustomMethods(MetricExporterConfigSupport.CustomMethods.class)
interface MetricExporterConfigBlueprint extends OtlpExporterConfigBlueprint {

    /**
     * Metric exporter type.
     *
     * @return metric exporter type
     */
    @Option.Configured
    @Option.Default("OTLP")
    MetricExporterType type();

    /**
     * Preferred output aggregation technique, configurable as a
     * {@link io.helidon.telemetry.otelconfig.MetricTemporalityPreferenceType} value.
     *
     * @return output aggregation technique
     */
    @Option.Configured
    Optional<AggregationTemporalitySelector> temporalityPreference();

    /**
     * Preferred default histogram aggregation technique, configurable as
     * {@link io.helidon.telemetry.otelconfig.MetricDefaultHistogramAggregationConfig}.
     *
     * @return default histogram aggregation technique
     */
    @Option.Configured
    Optional<DefaultAggregationSelector> defaultHistogramAggregation();
}
