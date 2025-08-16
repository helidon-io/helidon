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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.helidon.common.Errors;
import io.helidon.common.config.Config;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;

class OtelConfigSupport {

    private OtelConfigSupport() {
    }

    /**
     * Creates an {@link io.opentelemetry.sdk.trace.SpanProcessor}, using either the named exporters of (if no names are
     * specified) all exporters.
     *
     * @param spanProcessorConfig span processor config
     * @param spanExporters available span exporters
     * @param errorsCollector error note collector to report exporter names specified to ber used but not present
     * @return new {@code Processor}
     */
    static SpanProcessor createSpanProcessor(SpanProcessorConfig spanProcessorConfig,
                                             Map<String, SpanExporter> spanExporters,
                                             Errors.Collector errorsCollector) {

        List<SpanExporter> exportersToUse = (
                spanProcessorConfig.exporters().isEmpty()
                        ? spanExporters.values().stream()
                        : spanProcessorConfig.exporters().stream()
                                .filter(spanExporters::containsKey)
                                .map(spanExporters::get))
                .toList();

        Set<String> missingExporterNames = new HashSet<>(spanProcessorConfig.exporters());
        missingExporterNames.removeAll(spanExporters.keySet());
        if (!missingExporterNames.isEmpty()) {
            errorsCollector.fatal(spanProcessorConfig, "Missing exporter(s): " + missingExporterNames);
        }

        SpanExporter exporterToUse = exportersToUse.isEmpty()
                ? OtlpHttpSpanExporter.getDefault() // The factory method below requires an exporter so use OTel's default.
                : (exportersToUse.size() == 1)
                        ? exportersToUse.getFirst()
                        : SpanExporter.composite(exportersToUse);

        return switch (spanProcessorConfig.type()) {
            case SpanProcessorType.SIMPLE -> SimpleSpanProcessor.create(exporterToUse);
            case SpanProcessorType.BATCH ->
                    createBatchSpanProcessor((BatchSpanProcessorConfig) spanProcessorConfig, exporterToUse);
        };
    }

    static BatchSpanProcessor createBatchSpanProcessor(BatchSpanProcessorConfig config, SpanExporter spanExporter) {

        var builder = BatchSpanProcessor.builder(spanExporter);

        config.maxExportBatchSize().ifPresent(builder::setMaxExportBatchSize);
        config.maxQueueSize().ifPresent(builder::setMaxQueueSize);
        config.scheduleDelay().ifPresent(builder::setScheduleDelay);
        config.timeout().ifPresent(builder::setExporterTimeout);

        return builder.build();
    }

    static Sampler createSampler(Config samplerConfig) {
        return createSampler(SamplerConfig.create(samplerConfig));
    }

    static Sampler createSampler(SamplerConfig samplerConfig) {

        return switch (samplerConfig.type()) {
            case SamplerType.ALWAYS_OFF -> Sampler.alwaysOff();
            case SamplerType.ALWAYS_ON -> Sampler.alwaysOn();
            case SamplerType.PARENTBASED_ALWAYS_OFF -> Sampler.parentBased(Sampler.alwaysOff());
            case SamplerType.PARENTBASED_ALWAYS_ON -> Sampler.parentBased(Sampler.alwaysOn());
            case SamplerType.PARENTBASED_TRACEIDRATIO -> Sampler.parentBased(
                    Sampler.traceIdRatioBased(samplerConfig.param()
                                                      .map(Number::doubleValue)
                                                      .orElseThrow()));
            case SamplerType.TRACEIDRATIO -> Sampler.traceIdRatioBased(samplerConfig.param()
                                                                                 .map(Number::doubleValue)
                                                                                 .orElseThrow());
        };
    }

    static SpanLimits createSpanLimits(Config config) {
        return createSpanLimits(SpanLimitsConfig.create(config));
    }

    static SpanLimits createSpanLimits(SpanLimitsConfig config) {
        var builder = SpanLimits.builder();

        config.maxAttributes().ifPresent(builder::setMaxNumberOfAttributes);
        config.maxAttributeValueLength().ifPresent(builder::setMaxAttributeValueLength);
        config.maxEvents().ifPresent(builder::setMaxNumberOfEvents);
        config.maxLinks().ifPresent(builder::setMaxNumberOfLinks);
        config.maxAttributeValueLength().ifPresent(builder::setMaxAttributeValueLength);

        return builder.build();
    }

    static SpanProcessorConfig createProcessorConfig(Config config) {

        // Apply the default.
        return switch (SpanProcessorConfig.create(config).type()) {
            case SpanProcessorType.BATCH -> BatchSpanProcessorConfig.create(config);
            case SpanProcessorType.SIMPLE -> SpanProcessorConfig.create(config);
        };
    }

}
