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

import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Settings for an OpenTelemetry Zipkin exporter.
 */
public class ZipkinSpanExporterConfiguration extends SpanExporterConfiguration.Basic {

    private final SpanExporter spanExporter;

    private ZipkinSpanExporterConfiguration(Builder builder) {
        super(builder);
        var exporterBuilder = ZipkinSpanExporter.builder();
        builder.apply(exporterBuilder::setEndpoint, exporterBuilder::setCompression, exporterBuilder::setReadTimeout);
        spanExporter = exporterBuilder.build();
    }

    static public Builder builder() {
        return new Builder();
    }

    /**
     * Creates a Zipkin span exporter config object from a config node containing Zipkin span exporter
     * settings.
     *
     * @param zipkinSpanExporterConfig config node containing Zipkin span exporter settings
     * @return the corresponding config object for an OpenTelemetry {@code ZipkinSpanExporter}
     */
    public static ZipkinSpanExporterConfiguration create(Config zipkinSpanExporterConfig) {
        return builder().config(zipkinSpanExporterConfig).build();
    }

    @Override
    public SpanExporter spanExporter() {
        return spanExporter;
    }

    @Configured
    public static class Builder extends SpanExporterConfiguration.Basic.Builder<Builder, ZipkinSpanExporterConfiguration> {

        protected Builder() {
            super("http", "localhost", 9411, "/api/v2/spans", null, null);
        }

        /**
         * Applies the specified Zipkin span config node to the builder.
         *
         * @param zipkinSpanExporterConfig config node containing OpenTelemetry Zipkin span settings
         * @return updated builder
         */
        public Builder config(Config zipkinSpanExporterConfig) {
            return super.config(zipkinSpanExporterConfig);
        }

        /**
         * Builds the {@link io.opentelemetry.exporter.zipkin.ZipkinSpanExporter} from the builder.
         *
         * @return resulting {@code ZipkinSpanExporter}
         */
        public ZipkinSpanExporterConfiguration build() {
            return new ZipkinSpanExporterConfiguration(this);
        }
    }
}
