# io.helidon.telemetry.otelconfig.OpenTelemetryTracingConfig

## Description

OpenTelemetry tracer settings.

## Usages

- [`telemetry.signals.tracing`](../config/io_helidon_telemetry_otelconfig_HelidonOpenTelemetry.md#a9cc8d-signals-tracing)

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a3d5a9-attributes"></span> `attributes` | `VALUE` | `i.h.t.o.O.CustomMethods` | Name/value pairs passed to OpenTelemetry |
| <span id="ae0ab8-exporters"></span> `exporters` | `MAP` | `i.h.t.o.O.CustomMethods` | Span exporters |
| <span id="ae1681-processors"></span> `processors` | `LIST` | `i.h.t.o.O.CustomMethods` | Settings for span processors |
| <span id="ace1fe-sampler"></span> `sampler` | `VALUE` | `i.h.t.o.O.CustomMethods` | Tracing sampler |
| <span id="abff29-span-limits"></span> `span-limits` | `VALUE` | `i.h.t.o.O.CustomMethods` | Tracing span limits |

See the [manifest](../config/manifest.md) for all available types.
