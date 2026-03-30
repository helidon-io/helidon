# io.helidon.telemetry.otelconfig.PeriodicMetricReaderConfig

## Description

Settings for OpenTelemetry periodic metric reader.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a173a8-exporter"></span> `exporter` | `VALUE` | `String` |   | Name of the configured metric exporter to use for this metric reader |
| <span id="a5a14c-interval"></span> `interval` | `VALUE` | `Duration` |   | Metric reader read interval |
| <span id="a1a217-type"></span> [`type`](../config/io_helidon_telemetry_otelconfig_MetricReaderType.md) | `VALUE` | `i.h.t.o.MetricReaderType` | `PERIODIC` | Metric reader type |

See the [manifest](../config/manifest.md) for all available types.
