/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.helidon.tracing.opentelemetrycompat.jaegerexporter;

import java.time.Duration;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSpanExporterProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * {@link SpanExporter} SPI implementation for {@link
 * JaegerGrpcSpanExporter}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 *
 * @deprecated Use {@code OtlpGrpcSpanExporter} or {@code OtlpHttpSpanExporter} from <a
 *     href="https://github.com/open-telemetry/opentelemetry-java/tree/main/exporters/otlp/all">opentelemetry-exporter-otlp</a>
 *     instead.
 */
@Deprecated
public class JaegerGrpcSpanExporterProvider implements ConfigurableSpanExporterProvider {
  @Override
  public String getName() {
    return "jaeger";
  }

  @Override
  public SpanExporter createExporter(ConfigProperties config) {
    JaegerGrpcSpanExporterBuilder builder =
        JaegerGrpcSpanExporter.builder();

    String endpoint = config.getString("otel.exporter.jaeger.endpoint");
    if (endpoint != null) {
      builder.setEndpoint(endpoint);
    }

    Duration timeout = config.getDuration("otel.exporter.jaeger.timeout");
    if (timeout != null) {
      builder.setTimeout(timeout);
    }

    return builder.build();
  }
}
