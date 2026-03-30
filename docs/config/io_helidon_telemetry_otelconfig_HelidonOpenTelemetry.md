# io.helidon.telemetry.otelconfig.HelidonOpenTelemetry

## Description

OpenTelemetry settings.

## Usages

- [`telemetry`](../config/config_reference.md#ae1891-telemetry)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="acc8da-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether the OpenTelemetry support is enabled |
| <span id="a104b9-global"></span> `global` | `VALUE` | `Boolean` | `true` | Whether the `io.opentelemetry.api.OpenTelemetry` instance created from this configuration should be made the global one |
| <span id="a9f65d-propagators"></span> `propagators` | `LIST` | `i.h.t.o.O.CustomMethods` |   | OpenTelemetry `io.opentelemetry.context.propagation.TextMapPropagator` instances added explicitly by the app |
| <span id="a2f6cf-service"></span> `service` | `VALUE` | `String` |   | Service name used in sending telemetry data to the collector |
| <span id="aa0da5-signals-logging"></span> [`signals.logging`](../config/io_helidon_telemetry_otelconfig_OpenTelemetryLoggingConfig.md) | `VALUE` | `i.h.t.o.OpenTelemetryLoggingConfig` |   | OpenTelemetry logging settings |
| <span id="a8cca2-signals-metrics"></span> [`signals.metrics`](../config/io_helidon_telemetry_otelconfig_OpenTelemetryMetricsConfig.md) | `VALUE` | `i.h.t.o.OpenTelemetryMetricsConfig` |   | OpenTelemetry metrics settings |
| <span id="a9cc8d-signals-tracing"></span> [`signals.tracing`](../config/io_helidon_telemetry_otelconfig_OpenTelemetryTracingConfig.md) | `VALUE` | `i.h.t.o.OpenTelemetryTracingConfig` |   | OpenTelemetry tracing settings |

See the [manifest](../config/manifest.md) for all available types.
