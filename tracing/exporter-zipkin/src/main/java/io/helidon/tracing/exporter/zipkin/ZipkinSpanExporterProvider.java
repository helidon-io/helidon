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

package io.helidon.tracing.exporter.zipkin;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

import io.helidon.common.Errors;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * OpenTelemetry service provider for {@code otel.traces.exporter=zipkin}.
 *
 * @deprecated Migrate to an OTLP exporter.
 */
@Deprecated(since = "4.5.7", forRemoval = true)
@SuppressWarnings("removal")
public final class ZipkinSpanExporterProvider implements ConfigurableSpanExporterProvider {
    /**
     * Constructor for service loading.
     */
    public ZipkinSpanExporterProvider() {
    }

    @Override
    public String getName() {
        return "zipkin";
    }

    @Override
    public SpanExporter createExporter(ConfigProperties config) {
        Objects.requireNonNull(config, "config");
        var builder = ZipkinSpanExporter.builder();
        Errors.Collector errors = Errors.collector();
        String endpoint = config.getString("otel.exporter.zipkin.endpoint");
        if (endpoint != null) {
            try {
                builder.endpoint(URI.create(endpoint));
            } catch (IllegalArgumentException e) {
                errors.fatal("otel.exporter.zipkin.endpoint must be a valid URI");
            }
        }
        try {
            Duration timeout = config.getDuration("otel.exporter.zipkin.timeout");
            if (timeout != null) {
                builder.timeout(timeout);
            }
        } catch (IllegalArgumentException | ConfigurationException e) {
            errors.fatal("otel.exporter.zipkin.timeout must be a valid duration");
        }
        ZipkinExporterSupport.validate(builder, errors);
        errors.collect().checkValid();
        return builder.build();
    }
}
