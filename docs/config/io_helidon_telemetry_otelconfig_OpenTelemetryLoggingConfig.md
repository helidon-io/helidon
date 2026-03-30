# io.helidon.telemetry.otelconfig.OpenTelemetryLoggingConfig

## Description

Configuration settings for OpenTelemetry logging.

## Usages

- [`telemetry.signals.logging`](../config/io_helidon_telemetry_otelconfig_HelidonOpenTelemetry.md#aa0da5-signals-logging)

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a070ac-attributes"></span> `attributes` | `VALUE` | `i.h.t.o.O.CustomMethods` | Name/value pairs passed to OpenTelemetry |
| <span id="aaa180-enabled"></span> `enabled` | `VALUE` | `Boolean` | Whether the OpenTelemetry logger should be enabled |
| <span id="a5919f-exporters"></span> `exporters` | `MAP` | `i.h.t.o.O.CustomMethods` | Log record exporters |
| <span id="afaf4d-log-limits"></span> `log-limits` | `VALUE` | `i.h.t.o.O.CustomMethods` | Log limits to apply to log transmission |
| <span id="a33fd0-minimum-severity"></span> [`minimum-severity`](../config/io_opentelemetry_api_logs_Severity.md) | `VALUE` | `i.o.a.l.Severity` | Minimum severity level of log records to process |
| <span id="af7d01-processors"></span> `processors` | `LIST` | `i.h.t.o.O.CustomMethods` | Settings for logging processors |
| <span id="a251a3-trace-based"></span> `trace-based` | `VALUE` | `Boolean` | Whether to include only log records from traces which are sampled |

See the [manifest](../config/manifest.md) for all available types.
