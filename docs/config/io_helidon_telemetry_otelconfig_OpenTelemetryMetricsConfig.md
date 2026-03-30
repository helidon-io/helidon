# io.helidon.telemetry.otelconfig.OpenTelemetryMetricsConfig

## Description

OpenTelemetry metrics settings.

## Usages

- [`telemetry.signals.metrics`](../config/io_helidon_telemetry_otelconfig_HelidonOpenTelemetry.md#a8cca2-signals-metrics)

## Configuration options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a33ff0-attributes"></span> `attributes` | `VALUE` | `i.h.t.o.O.CustomMethods` | Name/value pairs passed to OpenTelemetry |
| <span id="a0f4af-exporters"></span> `exporters` | `MAP` | `i.h.t.o.O.CustomMethods` | Metric exporter configurations, configurable using `io.helidon.telemetry.otelconfig.MetricExporterConfig` |
| <span id="ab707a-readers"></span> `readers` | `LIST` | `i.h.t.o.O.CustomMethods` | Settings for metric readers |
| <span id="a7406f-views"></span> `views` | `LIST` | `i.h.t.o.O.CustomMethods` | Metric view information, configurable using `io.helidon.telemetry.otelconfig.ViewRegistrationConfig` |

See the [manifest](../config/manifest.md) for all available types.
