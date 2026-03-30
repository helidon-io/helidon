# io.helidon.webserver.observe.metrics.AutoHttpMetricsPathConfig

## Description

Settings for path-based automatic metrics configuration.

## Usages

- [`metrics.auto-http-metrics.paths`](../config/io_helidon_webserver_observe_metrics_AutoHttpMetricsConfig.md#a6fb0d-paths)
- [`server.features.observe.observers.metrics.auto-http-metrics.paths`](../config/io_helidon_webserver_observe_metrics_AutoHttpMetricsConfig.md#a6fb0d-paths)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a28a09-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether automatic metrics are to be enabled for requests which match the specified `io.helidon.http.PathMatcher` and HTTP methods |
| <span id="a0cd6b-methods"></span> `methods` | `LIST` | `String` |   | HTTP methods for which this path config applies; default is to match all HTTP methods |
| <span id="ae9d47-path"></span> `path` | `VALUE` | `String` |   | Path matching expression for this path config entry |

See the [manifest](../config/manifest.md) for all available types.
