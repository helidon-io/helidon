/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
import io.helidon.config.Config;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
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
     * @param spanExporters       available span exporters
     * @param errorsCollector     error note collector to report exporter names specified to ber used but not present
     * @return new {@code Processor}
     */
    static SpanProcessor createSpanProcessor(ProcessorConfig spanProcessorConfig,
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
            case ProcessorType.SIMPLE -> SimpleSpanProcessor.create(exporterToUse);
            case ProcessorType.BATCH -> createBatchSpanProcessor((BatchProcessorConfig) spanProcessorConfig, exporterToUse);
        };
    }

    static BatchSpanProcessor createBatchSpanProcessor(BatchProcessorConfig config, SpanExporter spanExporter) {

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

    static ProcessorConfig createProcessorConfig(Config config) {

        // Apply the default.
        return switch (ProcessorConfig.create(config).type()) {
            case ProcessorType.BATCH -> BatchProcessorConfig.create(config);
            case ProcessorType.SIMPLE -> ProcessorConfig.create(config);
        };
    }

    static LogRecordProcessor createLogRecordProcessor(ProcessorConfig processorConfig,
                                                       Map<String, LogRecordExporter> logRecordExporters,
                                                       Errors.Collector errorsCollector) {
        List<LogRecordExporter> exportersToUse = (
                processorConfig.exporters().isEmpty()
                        ? logRecordExporters.values().stream()
                        : processorConfig.exporters().stream()
                                .filter(logRecordExporters::containsKey)
                                .map(logRecordExporters::get))
                .toList();

        Set<String> missingExporterNames = new HashSet<>(processorConfig.exporters());
        missingExporterNames.removeAll(logRecordExporters.keySet());
        if (!missingExporterNames.isEmpty()) {
            errorsCollector.fatal(processorConfig, "Missing exporter(s): " + missingExporterNames);
        }

        LogRecordExporter exporterToUse = exportersToUse.isEmpty()
                ? OtlpHttpLogRecordExporter.getDefault() // The factory method below requires an exporter so use OTel's default.
                : (exportersToUse.size() == 1)
                        ? exportersToUse.getFirst()
                        : LogRecordExporter.composite(exportersToUse);

        return switch (processorConfig.type()) {
            case ProcessorType.SIMPLE -> SimpleLogRecordProcessor.create(exporterToUse);
            case ProcessorType.BATCH ->
                    createBatchLogRecordProcessor((BatchProcessorConfig) processorConfig, exporterToUse);
        };
    }

    static BatchLogRecordProcessor createBatchLogRecordProcessor(BatchProcessorConfig config,
                                                                 LogRecordExporter logRecordExporter) {

        var builder = BatchLogRecordProcessor.builder(logRecordExporter);

        config.maxExportBatchSize().ifPresent(builder::setMaxExportBatchSize);
        config.maxQueueSize().ifPresent(builder::setMaxQueueSize);
        config.scheduleDelay().ifPresent(builder::setScheduleDelay);
        config.timeout().ifPresent(builder::setExporterTimeout);

        return builder.build();
    }

    static AttributesBuilder createAttributesBuilder(Config config) {
        var typedAttributes = TypedAttributes.create(config);
        var builder = Attributes.builder();

        typedAttributes.booleanAttributes().forEach(builder::put);
        typedAttributes.doubleAttributes().forEach(builder::put);
        typedAttributes.stringAttributes().forEach(builder::put);
        typedAttributes.longAttributes().forEach(builder::put);

        return builder;
    }

}
