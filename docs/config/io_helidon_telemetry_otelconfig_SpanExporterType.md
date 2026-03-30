# io.helidon.telemetry.otelconfig.SpanExporterType

## Description

This type is an enumeration.

## Usages

## Allowed Values

| Value | Description |
|----|----|
| `OTLP` | OpenTelemetry Protocol `io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter` and `io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter` |
| `ZIPKIN` | Zipkin `io.opentelemetry.exporter.zipkin.ZipkinSpanExporter` |
| `CONSOLE` | Console (`io.opentelemetry.exporter.logging.LoggingSpanExporter` |
| `LOGGING_OTLP` | JSON logging to console `io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter` |

See the [manifest](../config/manifest.md) for all available types.
