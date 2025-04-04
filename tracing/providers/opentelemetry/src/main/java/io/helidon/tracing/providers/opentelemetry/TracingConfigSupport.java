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

package io.helidon.tracing.providers.opentelemetry;

import java.util.Objects;
import java.util.function.Consumer;

import io.helidon.builder.api.Prototype;

import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessorBuilder;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

class TracingConfigSupport {

    static class BuilderDecorator implements Prototype.BuilderDecorator<TracingConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(TracingConfig.BuilderBase<?, ?> builder) {
            ensureDefaultSpanExporter(builder);
            addConfiguredSpanProcessorsToList(builder);
        }

        private static void ensureDefaultSpanExporter(TracingConfig.BuilderBase<?, ?> builder) {
            builder.spanExporters().computeIfAbsent("@default", k -> OtlpGrpcSpanExporter.getDefault());
        }

        private static void addConfiguredSpanProcessorsToList(TracingConfig.BuilderBase<?, ?> builder) {
            builder.spanProcessorConfigs().forEach(spanProcessorConfig -> {
                SpanExporter spanExporter = builder.spanExporters()
                        .get(Objects.requireNonNullElse(spanProcessorConfig.spanExporter(), "@default"));
                SpanProcessor spanProcessor = switch (spanProcessorConfig.type()) {
                    case SIMPLE -> SimpleSpanProcessor.create(spanExporter);
                    case BATCH -> {
                        BatchSpanProcessorConfig batchSpanProcessorConfig = (BatchSpanProcessorConfig) spanProcessorConfig;
                        BatchSpanProcessorBuilder batchSpanProcessorBuilder = BatchSpanProcessor.builder(spanExporter);
                        apply(batchSpanProcessorConfig.maxExportBatchSize(), batchSpanProcessorBuilder::setMaxExportBatchSize);
                        apply(batchSpanProcessorConfig.maxQueueSize(), batchSpanProcessorBuilder::setMaxQueueSize);
                        apply(batchSpanProcessorConfig.scheduleDelay(), batchSpanProcessorBuilder::setScheduleDelay);
                        apply(batchSpanProcessorConfig.timeout(), batchSpanProcessorBuilder::setExporterTimeout);
                        yield batchSpanProcessorBuilder.build();
                    }
                };
                builder.addSpanProcessor(spanProcessor);
            });
        }
        private static <T> void apply(T value, Consumer<T> consumer) {
            if (value != null) {
                consumer.accept(value);
            }
        }
    }

    //    @Prototype.FactoryMethod
    //    static SpanExporter createSpanExporter(Config config) {
    //        int a = 0;
    //        return null;
    //    }
    //
    //    @Prototype.FactoryMethod
    //    Map<String, SpanExporter> createSpanExporters(Config config) {
    //        return Map.of();
    //    }
    //
    //    @Prototype.FactoryMethod
    //    SpanProcessor createSpanProcessor(Config config) {
    //        return null;
    //    }
    //
    //    @Prototype.FactoryMethod
    //    List<SpanProcessor> createSpanProcessors(Config config) {
    //        return List.of();
    //    }
}
