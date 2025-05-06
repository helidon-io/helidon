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

import io.helidon.common.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Common configurable elements for any span processor implementation.
 */
public abstract class SpanProcessorConfiguration {

    private final String exporterName;

    protected SpanProcessorConfiguration(Builder<?, ?> builder) {
        exporterName = builder.exporterName;
    }

    String exporterName() {
        return exporterName;
    }

    static SpanProcessorConfiguration create(Config spanProcessorConfig) {
        return builder(spanProcessorConfig).build();
    }

    static Builder<?, ?> builder(SpanProcessorType spanProcessorType) {
        return switch (spanProcessorType) {
            case BATCH -> BatchSpanProcessorConfiguration.builder();
            case SIMPLE -> SimpleSpanProcessorConfiguration.builder();
        };
    }

    static Builder<?, ?> builder(Config spanProcessorConfig) {
        return builder(spanProcessorConfig.get("processor-type")
                .as(SpanProcessorType.class)
                .orElse(SpanProcessorType.DEFAULT))
                .config(spanProcessorConfig);
    }

    abstract SpanProcessor spanProcessor(SpanExporter spanExporter);

    @Configured
    static abstract class Builder<B extends Builder<B, T>, T extends SpanProcessorConfiguration> implements io.helidon.common.Builder<B, T> {

        private String exporterName = "@default";

        /**
         * Applies the specified span exporter config node to the builder.
         *
         * @param spanProcessorConfig config node containing span exporter settings
         * @return updated builder
         */
        public B config(Config spanProcessorConfig) {
            spanProcessorConfig.get("exporter-name").asString().ifPresent(this::exporterName);
            return identity();
        }

        /**
         * Identifies the named span exporter this processor should use.
         *
         * @param exporterName span exporter name
         * @return updated builder
         */
        @ConfiguredOption
        public B exporterName(String exporterName) {
            this.exporterName = exporterName;
            return identity();
        }
    }

}
