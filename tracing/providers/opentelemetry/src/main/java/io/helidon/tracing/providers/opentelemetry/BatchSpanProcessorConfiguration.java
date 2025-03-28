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

import java.time.Duration;

import io.helidon.common.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Settings for an OpenTelemetry batch span processor.
 */
class BatchSpanProcessorConfiguration extends SpanProcessorConfiguration {

    private final Builder builder;

    static Builder builder(Config batchSpanProcessorConfig) {
        return new Builder().config(batchSpanProcessorConfig);
    }

    static Builder builder() {
        return new Builder();
    }

    private BatchSpanProcessorConfiguration(Builder builder) {
        super(builder);
        this.builder = builder;
    }

    @Override
    SpanProcessor spanProcessor(SpanExporter spanExporter) {
        io.opentelemetry.sdk.trace.export.BatchSpanProcessorBuilder delegate = BatchSpanProcessor.builder(spanExporter);
        if (builder.exporterTimeout != null) {
            delegate.setExporterTimeout(builder.exporterTimeout);
        }
        if (builder.scheduleDelay != null) {
            delegate.setScheduleDelay(builder.scheduleDelay);
        }
        if (builder.maxQueueSize != null) {
            delegate.setMaxQueueSize(builder.maxQueueSize);
        }
        if (builder.maxExportBatchSize != null) {
            delegate.setMaxExportBatchSize(builder.maxExportBatchSize);
        }

        return delegate.build();
    }

    @Configured(description = "OTEL batch span processor configuration")
    static class Builder extends SpanProcessorConfiguration.Builder<Builder, BatchSpanProcessorConfiguration> {

        private Duration exporterTimeout;
        private Duration scheduleDelay;
        private Integer maxQueueSize;
        private Integer maxExportBatchSize;

        /**
         * Create a {@link io.opentelemetry.sdk.trace.export.BatchSpanProcessor} based on the current settings in the builder.
         *
         * @return a new {@code BatchSpanProcessor}
         */
        public BatchSpanProcessorConfiguration build() {
            return new BatchSpanProcessorConfiguration(this);
        }

        /**
         * Apply the specified batch span processor config node to this builder.
         *
         * @param batchSpanProcessorConfig config containing batch span processor settings
         * @return updated builder
         */
        public Builder config(Config batchSpanProcessorConfig) {
            super.config(batchSpanProcessorConfig);
            batchSpanProcessorConfig.get("exporter-timeout").as(Duration.class).ifPresent(this::exporterTimeout);
            batchSpanProcessorConfig.get("schedule-delay").as(Duration.class).ifPresent(this::scheduleDelay);
            batchSpanProcessorConfig.get("max-queue-size").asInt().ifPresent(this::maxQueueSize);
            batchSpanProcessorConfig.get("max-export-batch-size").asInt().ifPresent(this::maxExportBatchSize);

            return this;
        }

        /**
         * Schedule delay for transmitting exporter data.
         *
         * @param scheduleDelay schedule delay
         * @return updated builder
         */
        @ConfiguredOption
        public Builder scheduleDelay(Duration scheduleDelay) {
            this.scheduleDelay = scheduleDelay;
            return this;
        }

        /**
         * Maximum queue size for exporting data.
         *
         * @param maxQueueSize maximum queue size for exporting data
         * @return updated builder
         */
        @ConfiguredOption
        public Builder maxQueueSize(int maxQueueSize) {
            this.maxQueueSize = maxQueueSize;
            return this;
        }

        /**
         * Maximum batch size for exporting data.
         *
         * @param maxExportBatchSize maximum batch size for exporting data
         * @return updated builder
         */
        @ConfiguredOption
        public Builder maxExportBatchSize(int maxExportBatchSize) {
            this.maxExportBatchSize = maxExportBatchSize;
            return this;
        }

        /**
         * Maximum time the processor will wait for its exporter to export a batch of data.
         *
         * @param exporterTimeout maximum time for exporting a batch
         * @return updated builder
         */
        @ConfiguredOption
        public Builder exporterTimeout(Duration exporterTimeout) {
            this.exporterTimeout = exporterTimeout;
            return this;
        }
    }
}
