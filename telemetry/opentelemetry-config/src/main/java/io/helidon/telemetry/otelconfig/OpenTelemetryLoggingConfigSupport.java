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

import io.helidon.builder.api.Prototype;
import io.helidon.common.Errors;
import io.helidon.config.Config;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.logs.LogLimits;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.internal.LoggerConfig;

class OpenTelemetryLoggingConfigSupport {

    private static final System.Logger LOGGER = System.getLogger(OpenTelemetryLoggingConfigSupport.class.getName());

    private OpenTelemetryLoggingConfigSupport() {
    }

    static class BuilderDecorator implements Prototype.BuilderDecorator<OpenTelemetryLoggingConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(OpenTelemetryLoggingConfig.BuilderBase<?, ?> target) {

            // Associate each processor with either its named exporter(s) or all exporters.

            Errors.Collector errorsCollector = Errors.collector();

            var loggerConfigBuilder = LoggerConfig.builder();
            target.enabled().ifPresent(loggerConfigBuilder::setEnabled);
            target.minimumSeverity().ifPresent(loggerConfigBuilder::setMinimumSeverity);
            target.traceBased().ifPresent(loggerConfigBuilder::setTraceBased);

            var sdkLoggerProviderBuilder = SdkLoggerProvider.builder();

            target.logLimits().ifPresent(limits -> sdkLoggerProviderBuilder.setLogLimits(() -> limits));

            // Add configured processors to any the app added programmatically.
            target.addProcessors(target.processorConfigs().stream()
                                         .map(processorConfig -> OtelConfigSupport.createLogRecordProcessor(processorConfig,
                                                                                                       target.exporterConfigs(),
                                                                                                       errorsCollector))
                                         .toList());

            target.processors().forEach(sdkLoggerProviderBuilder::addLogRecordProcessor);

            // Exporters are not set on the SDK builder directly; they are used to prepare the processors (above).

            errorsCollector.collect().log(LOGGER);
            target.loggingBuilderInfo(new LoggingBuilderInfo(sdkLoggerProviderBuilder,
                                                             target.attributes().orElseGet(Attributes::builder)));

        }
    }

    static class CustomMethods {

        private CustomMethods() {
        }

        @Prototype.ConfigFactoryMethod
        static LogRecordExporter createLogRecordExporter(Config config) {
            return OtlpExporterConfigSupport.CustomMethods.createLogRecordExporter(config);
        }

        @Prototype.ConfigFactoryMethod("processorConfigs")
        static ProcessorConfig createProcessorConfigs(Config config) {
            return OtelConfigSupport.createProcessorConfig(config);
        }

        @Prototype.ConfigFactoryMethod("logLimits")
        static LogLimits createLogLimits(Config config) {
            var builder = LogLimits.builder();
            var logLimitsConfig = LogLimitsConfig.create(config);

            logLimitsConfig.maxAttributeValueLength().ifPresent(builder::setMaxAttributeValueLength);
            logLimitsConfig.maxNumberOfAttributes().ifPresent(builder::setMaxNumberOfAttributes);

            return builder.build();
        }

        @Prototype.ConfigFactoryMethod("attributes")
        static AttributesBuilder createAttributesBuilder(Config config) {
            return OtelConfigSupport.createAttributesBuilder(config);
        }
    }
}
