# io.helidon.telemetry.otelconfig.MetricExporterType

## Description

This type is an enumeration.

## Usages

## Allowed Values

| Value | Description |
|----|----|
| `OTLP` | OpenTelemetry Protocol `io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter` and `io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter` |
| `CONSOLE` | Console (`io.opentelemetry.exporter.logging.LoggingMetricExporter` |
| `LOGGING_OTLP` | JSON logging to console `io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingMetricExporter` |

See the [manifest](../config/manifest.md) for all available types.
