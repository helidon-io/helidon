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

import java.util.Map;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Errors;
import io.helidon.config.Config;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingMetricExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.sdk.metrics.InstrumentSelector;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.View;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;

class OpenTelemetryMetricsConfigSupport {

    private OpenTelemetryMetricsConfigSupport() {
    }

    static MetricReader createMetricReader(MetricReaderConfig metricReaderConfig,
                                           Map<String, MetricExporter> exporters,
                                           Errors.Collector errorCollector) {
        var exporterToUse = chooseExporter(metricReaderConfig, exporters, errorCollector);
        if (errorCollector.hasFatal()) {
            return null;
        }
        return switch (metricReaderConfig.type()) {
            case PERIODIC -> {
                var periodicReaderConfig = (PeriodicMetricReaderConfig) metricReaderConfig;
                var builder = PeriodicMetricReader.builder(exporterToUse);
                periodicReaderConfig.executor().ifPresent(builder::setExecutor);
                periodicReaderConfig.interval().ifPresent(builder::setInterval);
                yield builder.build();
            }
        };
    }

    static MetricExporter chooseExporter(MetricReaderConfig metricReaderConfig,
                                         Map<String, MetricExporter> exporters,
                                         Errors.Collector errorCollector) {

        if (metricReaderConfig.exporter().isEmpty()) {
            // Config did not specify which exporter, so make sure there is exactly one and use it.
            if (exporters.size() == 1) {
                return exporters.values().iterator().next();
            }
            if (exporters.size() > 1) {
                errorCollector.fatal(String.format(
                        "Metric reader config must specify the name of one of the configured exporters %s but does not",
                        exporters.keySet()));
                return null;
            }
            /*
            The config contained no exporters, so return null so the caller knows to let OpenTelemetry use
            its default.
             */
            return null;
        } else {
            var exporterName = metricReaderConfig.exporter().get();
            if (exporters.containsKey(exporterName)) {
                return exporters.get(exporterName);
            } else {
                errorCollector.fatal(String.format("Metric exporter %s is not configured", exporterName));
                return null;
            }
        }
    }

    private static MetricExporter createOtlpMetricExporter(MetricExporterConfig metricExporterConfig, Config config) {
        var otlpExporterConfig = OtlpExporterConfig.create(config);
        var protocolType = otlpExporterConfig.protocol().orElse(OtlpExporterProtocolType.DEFAULT);
        return switch (protocolType) {
            case HTTP_PROTO -> createHttpProtobufMetricExporter(metricExporterConfig, otlpExporterConfig);
            case GRPC -> createGrpcMetricExporter(metricExporterConfig, otlpExporterConfig);
        };
    }

    private static MetricExporter createHttpProtobufMetricExporter(MetricExporterConfig metricExporterConfig,
                                                                   OtlpExporterConfig exporterConfig) {
        var builder = OtlpHttpMetricExporter.builder();
        OtlpExporterConfigSupport.CustomMethods.apply(exporterConfig,
                                                      builder::setEndpoint,
                                                      builder::setCompression,
                                                      builder::setTimeout,
                                                      builder::addHeader,
                                                      builder::setRetryPolicy,
                                                      builder::setClientTls,
                                                      builder::setTrustedCertificates,
                                                      builder::setSslContext);
        metricExporterConfig.temporalityPreference().ifPresent(builder::setAggregationTemporalitySelector);
        metricExporterConfig.defaultHistogramAggregation().ifPresent(builder::setDefaultAggregationSelector);

        return builder.build();
    }

    private static MetricExporter createGrpcMetricExporter(MetricExporterConfig metricExporterConfig,
                                                           OtlpExporterConfig exporterConfig) {
        var builder = OtlpGrpcMetricExporter.builder();
        OtlpExporterConfigSupport.CustomMethods.apply(exporterConfig,
                                                      builder::setEndpoint,
                                                      builder::setCompression,
                                                      builder::setTimeout,
                                                      builder::addHeader,
                                                      builder::setRetryPolicy,
                                                      builder::setClientTls,
                                                      builder::setTrustedCertificates,
                                                      builder::setSslContext);
        metricExporterConfig.temporalityPreference().ifPresent(builder::setAggregationTemporalitySelector);
        metricExporterConfig.defaultHistogramAggregation().ifPresent(builder::setDefaultAggregationSelector);

        return builder.build();
    }

    static class BuilderDecorator implements Prototype.BuilderDecorator<OpenTelemetryMetricsConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(OpenTelemetryMetricsConfig.BuilderBase<?, ?> target) {

            var errorsCollector = Errors.collector();

            var sdkMetricsProviderBuilder = SdkMeterProvider.builder();

            var attributesBuilder = Attributes.builder();
            TypedAttributes.apply(attributesBuilder,
                                  target.stringAttributes(),
                                  target.longAttributes(),
                                  target.doubleAttributes(),
                                  target.booleanAttributes());

            /*
            Defer creation of the readers until here (rather than using a config factory method) because we need the
            exporters to be fully populated first, before preparing the readers, and we cannot be sure that would be
            the case in a config factory method for metric readers.
             */
            target.readers().addAll(target.readerConfigs().stream()
                                            .map(readerConfig -> createMetricReader(readerConfig,
                                                                                    target.exporters(),
                                                                                    errorsCollector))
                                            .toList());

            target.readers().forEach(sdkMetricsProviderBuilder::registerMetricReader);

            target.views()
                    .forEach(viewInfo -> sdkMetricsProviderBuilder.registerView(viewInfo.instrumentSelector, viewInfo.view));

            target.metricsBuilderInfo(new MetricsBuilderInfo(sdkMetricsProviderBuilder, attributesBuilder));
        }
    }

    static class CustomMethods {

        private CustomMethods() {
        }

        /**
         * Convenience method so developers can add OpenTelemetry metric views to the builder --using the "register" method
         * familiar from the OpenTelemetry API--which Helidon then adds to OpenTelemetry.
         *
         * @param builder            builder
         * @param instrumentSelector OpenTelemetry {@link io.opentelemetry.sdk.metrics.InstrumentSelector} for the view
         * @param view               OpenTelemetry {@link io.opentelemetry.sdk.metrics.View} for the view
         */
        @Prototype.BuilderMethod
        static void registerView(OpenTelemetryMetricsConfig.BuilderBase<?, ?> builder,
                                 InstrumentSelector instrumentSelector,
                                 View view) {

            builder.views().add(new ViewInfo(instrumentSelector, view));
        }

        /**
         * Convenience method so developers can add OpenTelemetry metric readers to the builder--using the "register" method
         * familiar from the OpenTelemetry API--which Helidon then adds to OpenTelemetry.
         *
         * @param builder      builder
         * @param metricReader OpenTelemetry {@link io.opentelemetry.sdk.metrics.export.MetricReader} to register
         */
        @Prototype.BuilderMethod
        static void registerMetricReader(OpenTelemetryMetricsConfig.BuilderBase<?, ?> builder,
                                         MetricReader metricReader) {
            builder.addReader(metricReader);
        }

        @Prototype.ConfigFactoryMethod
        public static MetricExporter createExporter(Config config) {
            var exporterConfig = MetricExporterConfig.create(config);

            return switch (exporterConfig.type()) {
                case OTLP -> createOtlpMetricExporter(exporterConfig, config);
                case CONSOLE -> LoggingMetricExporter.create();
                case LOGGING_OTLP -> OtlpJsonLoggingMetricExporter.create();
            };
        }

        @Prototype.ConfigFactoryMethod
        static MetricReaderConfig createMetricReaderConfig(Config config) {
            var readerConfig = MetricReaderConfig.create(config);
            return switch (readerConfig.type()) {
                case PERIODIC -> PeriodicMetricReaderConfig.create(config);
            };
        }
    }

    /**
     * Combines the instrument selector and the view together so they can be handled together.
     *
     * @param instrumentSelector instrument selector
     * @param view               view
     */
    record ViewInfo(InstrumentSelector instrumentSelector, View view) { }
}
