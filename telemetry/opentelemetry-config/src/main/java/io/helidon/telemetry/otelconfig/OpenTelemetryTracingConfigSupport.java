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

import io.helidon.builder.api.Prototype;
import io.helidon.common.Errors;
import io.helidon.common.config.Config;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;

class OpenTelemetryTracingConfigSupport {

    private static final System.Logger LOGGER = System.getLogger(OpenTelemetryTracingConfigSupport.class.getName());

    static class BuilderDecorator implements Prototype.BuilderDecorator<OpenTelemetryTracingConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(OpenTelemetryTracingConfig.BuilderBase<?, ?> target) {

            // Associate each processor with either its named exporter(s) or all exporters.

            Errors.Collector errorsCollector = Errors.collector();

            var sdkTracerProviderBuilder = SdkTracerProvider.builder();

            var attributesBuilder = Attributes.builder();
            target.attributes().forEach((key, value) -> storeAttribute(attributesBuilder, key, value));
            target.longAttributes().forEach(attributesBuilder::put);
            target.doubleAttributes().forEach(attributesBuilder::put);
            target.booleanAttributes().forEach(attributesBuilder::put);
            target.stringAttributes().forEach(attributesBuilder::put);

            target.sampler().ifPresent(sdkTracerProviderBuilder::setSampler);
            target.spanLimits().ifPresent(sdkTracerProviderBuilder::setSpanLimits);

            // Add configured processors to any the app added programmatically.
            target.addProcessors(target.processorConfigs().stream()
                                         .map(processorConfig -> OtelConfigSupport.createSpanProcessor(processorConfig,
                                                                                                       target.exporterConfigs(),
                                                                                                       errorsCollector))
                                         .toList());

            target.processors().forEach(sdkTracerProviderBuilder::addSpanProcessor);

            // Exporters are not set on the SDK builder directly; they are used to prepare the processors (above).

            errorsCollector.collect().log(LOGGER);
            target.tracingBuilderInfo(new TracingBuilderInfo(sdkTracerProviderBuilder, attributesBuilder));
        }

        private static void storeAttribute(AttributesBuilder attributesBuilder, String key, Object value) {
            switch (value) {
            case Boolean b -> attributesBuilder.put(key, b);
            case Long l -> attributesBuilder.put(key, l);
            case Double d -> attributesBuilder.put(key, d);
            case String s -> attributesBuilder.put(key, s);
            default -> throw new IllegalArgumentException("Unexpected type " + value.getClass().getName()
                                                                  + " in attribute key: '"
                                                                  + key + "', value: '"
                                                                  + value + "'");
            }
        }

    }

    static class CustomMethods {

        private CustomMethods() {
        }

        @Prototype.FactoryMethod
        static Object createAttributes(Config config) {
            return config.isLeaf() ? parseValue(config.asString().get()) : null;
        }

        @Prototype.FactoryMethod
        static SpanProcessorConfig createProcessorConfigs(Config config) {
            return OtelConfigSupport.createProcessorConfig(config);
        }

        @Prototype.FactoryMethod
        static Sampler createSampler(Config config) {
            return OtelConfigSupport.createSampler(config);
        }

        @Prototype.FactoryMethod
        static SpanLimits createSpanLimits(Config config) {
            return OtelConfigSupport.createSpanLimits(config);
        }

        @Prototype.FactoryMethod
        static SpanExporter createExporterConfigs(Config config) {
            return OtlpExporterConfigSupport.CustomMethods.createSpanExporter(config);
        }

        @Prototype.FactoryMethod
        static Number createNumericAttributes(Config config) {
            return config.asDouble().get();
        }

        private static Object parseValue(String str) {
            if (str.equalsIgnoreCase("true") || str.equalsIgnoreCase("false")) {
                return Boolean.parseBoolean(str);
            }
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException e) {
                // Ignore for the moment
            }
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                return str;
            }
        }

    }

}
