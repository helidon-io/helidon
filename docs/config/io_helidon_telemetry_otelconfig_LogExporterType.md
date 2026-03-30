# io.helidon.telemetry.otelconfig.LogExporterType

## Description

This type is an enumeration.

## Usages

## Allowed Values

| Value | Description |
|----|----|
| `OTLP` | OpenTelemetry Protocol `io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter` and `io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporterBuilder` |
| `CONSOLE` | Console `io.opentelemetry.exporter.logging.SystemOutLogRecordExporter` |
| `LOGGING_OTLP` | Writes logs to a Logger in OTLP JSON format `io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingLogRecordExporter` |

See the [manifest](../config/manifest.md) for all available types.
