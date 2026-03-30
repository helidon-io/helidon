# io.helidon.telemetry.otelconfig.ProcessorConfig

## Description

Generic configuration for a processor such as a

io.opentelemetry.sdk.trace.SpanProcessor

, linked to an exporter such as a

io.opentelemetry.sdk.trace.export.SpanExporter

by its name in the configuration.

## Usages

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a01017-exporters"></span> `exporters` | `LIST` | `String` | Name(s) of the exporter(s) this processor should use; specifying no names uses all configured exporters (or if no exporters are configured, the default OpenTelemetry exporter(s)) |
| <span id="ad8c7e-type"></span> [`type`](../config/io_helidon_telemetry_otelconfig_ProcessorType.md) | `VALUE` | `i.h.t.o.ProcessorType` | Processor type |

See the [manifest](../config/manifest.md) for all available types.
