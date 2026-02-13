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

    /**
     * Creates a {@link io.opentelemetry.sdk.metrics.export.MetricReader} based on the reader config and
     * the named exporter referenced from the config.
     *
     * @param metricReaderConfig config for the {@code MetricReader}
     * @param exporters the named exporters in the configuration
     * @param errorCollector error collector to report problems detected in the configuration
     * @return {@code MetricReader}
     */
    static MetricReader createMetricReader(MetricReaderConfig metricReaderConfig,
                                           Map<String, MetricExporter> exporters,
                                           Errors.Collector errorCollector) {
        var exporterToUse = chooseExporter(metricReaderConfig, exporters, errorCollector);
        if (errorCollector.hasFatal()) {
            return null;
        }
        return switch (metricReaderConfig.type()) {
            /*
            New reader types appear in later releases of OpenTelemetry.
             */
            case PERIODIC -> {
                var periodicReaderConfig = (PeriodicMetricReaderConfig) metricReaderConfig;
                var builder = PeriodicMetricReader.builder(exporterToUse);
                periodicReaderConfig.executor().ifPresent(builder::setExecutor);
                periodicReaderConfig.interval().ifPresent(builder::setInterval);
                yield builder.build();
            }
        };
    }

    /**
     * Returns the exporter to use for the provided metric reader config, looking up an explicitly-referenced
     * exporter if the config has one, otherwise using the single exporter configured, otherwise (no exporters
     * configured) letting OpenTelemetry use its default.
     *
     * @param metricReaderConfig metric reader config settings
     * @param exporters named exporters from the config
     * @param errorCollector error collector for reporting any new config errors detected
     * @return {@code MetricExporter}
     */
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
            target.readerConfigs().stream()
                    .map(readerConfig -> createMetricReader(readerConfig,
                                                            target.exporters(),
                                                            errorsCollector))
                            .forEach(reader -> target.readers().add(reader));
            /*
            Register configured and programmatically-added readers.
             */
            target.readers().forEach(sdkMetricsProviderBuilder::registerMetricReader);

            target.viewRegistrations()
                    .forEach(viewRegistration -> sdkMetricsProviderBuilder.registerView(viewRegistration.instrumentSelector,
                                                                                        viewRegistration.view));

            target.metricsBuilderInfo(new MetricsBuilderInfo(sdkMetricsProviderBuilder, attributesBuilder));
        }
    }

    static class CustomMethods {

        private CustomMethods() {
        }

        /**
         * Convenience method so developers can add OpenTelemetry metric views to the builder--using the "registerView" method
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

            builder.viewRegistrations().add(new ViewRegistration(instrumentSelector, view));
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


        @Prototype.ConfigFactoryMethod
        static ViewRegistration createViewRegistration(Config config) {
            var viewRegistrationConfig = ViewRegistrationConfig.create(config);
            var viewBuilder = View.builder();

            viewRegistrationConfig.name().ifPresent(viewBuilder::setName);
            viewRegistrationConfig.description().ifPresent(viewBuilder::setDescription);
            viewRegistrationConfig.attributeFilter().ifPresent(viewBuilder::setAttributeFilter);
            viewBuilder.setAggregation(viewRegistrationConfig.aggregation());

            return new ViewRegistration(viewRegistrationConfig.instrumentSelector(), viewBuilder.build());
        }
    }

    /**
     * Combines the instrument selector and the view of a view registration.
     *
     * @param instrumentSelector instrument selector
     * @param view               view
     */
    record ViewRegistration(InstrumentSelector instrumentSelector, View view) { }
}
