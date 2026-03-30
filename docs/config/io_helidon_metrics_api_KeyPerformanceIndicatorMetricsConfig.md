# io.helidon.metrics.api.KeyPerformanceIndicatorMetricsConfig

## Description

Config bean for KPI metrics configuration.

## Usages

- [`metrics.key-performance-indicators`](../config/io_helidon_webserver_observe_metrics_MetricsObserver.md#a86e3a-key-performance-indicators)
- [`server.features.observe.observers.metrics.key-performance-indicators`](../config/io_helidon_webserver_observe_metrics_MetricsObserver.md#a86e3a-key-performance-indicators)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="ab606e-extended"></span> `extended` | `VALUE` | `Boolean` | `false` | Whether KPI extended metrics are enabled |
| <span id="a7786c-long-running-requests-threshold"></span> `long-running-requests.threshold` | `VALUE` | `Duration` | `PT10S` | Threshold in ms that characterizes whether a request is long running |

See the [manifest](../config/manifest.md) for all available types.
