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

import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Settings for a simple span processor (no settings beyond the inherited span exporter name).
 */
class SimpleSpanProcessorConfiguration extends SpanProcessorConfiguration {

    private SimpleSpanProcessorConfiguration(Builder builder) {
        super(builder);
    }

    static Builder builder(Config batchSpanProcessorConfig) {
        return new Builder().config(batchSpanProcessorConfig);
    }

    static Builder builder() {
        return new Builder();
    }

    @Configured(description = "OTEL simple span processor configuration")
    static class Builder extends SpanProcessorConfiguration.Builder<Builder, SimpleSpanProcessorConfiguration> {

        @Override
        public SimpleSpanProcessorConfiguration build() {
            return new SimpleSpanProcessorConfiguration(this);
        }
    }

    @Override
    SpanProcessor spanProcessor(SpanExporter spanExporter) {
        return SimpleSpanProcessor.create(spanExporter);
    }
}
