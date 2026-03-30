# io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig

## Description

Automatic metrics collection settings.

## Usages

- [`metrics.auto-http-metrics`](../config/io_helidon_webserver_observe_metrics_MetricsObserver.md#a4b719-auto-http-metrics)
- [`server.features.observe.observers.metrics.auto-http-metrics`](../config/io_helidon_webserver_observe_metrics_MetricsObserver.md#a4b719-auto-http-metrics)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a9ac57-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether automatic metrics collection as a whole is enabled |
| <span id="a61ecb-opt-in"></span> `opt-in` | `LIST` | `String` |   | Elective attribute for which to opt in |
| <span id="a6fb0d-paths"></span> [`paths`](../config/io_helidon_webserver_observe_metrics_AutoHttpMetricsPathConfig.md) | `LIST` | `i.h.w.o.m.AutoHttpMetricsPathConfig` |   | Automatic metrics collection settings |
| <span id="af4ffb-sockets"></span> `sockets` | `LIST` | `String` |   | Socket names for sockets to be instrumented with automatic metrics |

See the [manifest](../config/manifest.md) for all available types.
