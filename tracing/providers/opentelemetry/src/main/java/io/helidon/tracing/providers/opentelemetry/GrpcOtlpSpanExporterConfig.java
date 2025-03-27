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

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Span exporter config for OTLP span exporter using the grpc protocol.
 */
public class GrpcOtlpSpanExporterConfig extends OtlpSpanExporterConfig {

    private final SpanExporter spanExporter;

    /**
     * Creates a new builder for the span exporter config.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    protected GrpcOtlpSpanExporterConfig(Builder builder) {
        super(builder);
        var exporterBuilder = OtlpGrpcSpanExporter.builder();
        builder.apply(exporterBuilder::setEndpoint,
                      exporterBuilder::setCompression,
                      exporterBuilder::setTimeout,
                      exporterBuilder::addHeader,
                      exporterBuilder::setClientTls,
                      exporterBuilder::setTrustedCertificates);
        spanExporter = exporterBuilder.build();
    }

    @Override
    public SpanExporter spanExporter() {
        return spanExporter;
    }

    /**
     * Builder for an OTLP span exporter config using the grpc protocol.
     */
    public static class Builder extends OtlpSpanExporterConfig.Builder<Builder, GrpcOtlpSpanExporterConfig> {
        public Builder() {
            super("grpc",
                  "localhost",
                  4317,
                  null,
                  null,
                  null,
                  "grpc");
        }

        @Override
        public GrpcOtlpSpanExporterConfig build() {
            return new GrpcOtlpSpanExporterConfig(this);
        }
    }
}
