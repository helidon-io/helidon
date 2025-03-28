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

import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;

class LoggingOtlpSpanExporterConfig extends SpanExporterConfiguration {

    private final SpanExporter exporter = OtlpJsonLoggingSpanExporter.create();

    static Builder builder() {
        return new Builder();
    }

    protected LoggingOtlpSpanExporterConfig(Builder builder) {
        super(builder);
    }

    @Override
    public SpanExporter spanExporter() {
        return exporter;
    }

    static class Builder extends SpanExporterConfiguration.Builder<Builder, LoggingOtlpSpanExporterConfig> {

        @Override
        public LoggingOtlpSpanExporterConfig build() {
            return new LoggingOtlpSpanExporterConfig(this);
        }
    }
}
