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
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Span exporter config for OTLP span exporter using the http/protobuf protocol.
 */
public class HttpProtobufOtlpSpanExporterConfig extends OtlpSpanExporterConfiguration {

    private final SpanExporter spanExporter;

    /**
     * Creates a new builder for the span exporter config.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    protected HttpProtobufOtlpSpanExporterConfig(Builder builder) {
        super(builder);

        var exporterBuilder = OtlpHttpSpanExporter.builder();
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
     * Builder for OTLP span exporter config using the http/protobuf protocol.
     */
    public static class Builder extends OtlpSpanExporterConfiguration.Builder<Builder, HttpProtobufOtlpSpanExporterConfig> {

        public Builder() {
            super("http",
                  "localhost",
                  4318,
                  null,
                  null,
                  null,
                  "http");
        }

        @Override
        public HttpProtobufOtlpSpanExporterConfig build() {
            return new HttpProtobufOtlpSpanExporterConfig(this);
        }
    }
}
