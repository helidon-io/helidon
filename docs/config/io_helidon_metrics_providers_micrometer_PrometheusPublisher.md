# io.helidon.metrics.providers.micrometer.PrometheusPublisher

## Description

Settings for a Micrometer Prometheus meter registry.

## Usages

- [`metrics.publishers.prometheus`](../config/io_helidon_metrics_api_MetricsPublisher.md#a62230-prometheus)
- [`server.features.observe.observers.metrics.publishers.prometheus`](../config/io_helidon_metrics_api_MetricsPublisher.md#a62230-prometheus)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a6614e-descriptions"></span> `descriptions` | `VALUE` | `Boolean` |   | Whether to include meter descriptions in Prometheus output |
| <span id="a248f8-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether the configured publisher is enabled |
| <span id="ae8bbc-interval"></span> `interval` | `VALUE` | `Duration` |   | Step size used in computing "windowed" statistics |
| <span id="abd446-name"></span> `name` | `VALUE` | `String` |   | `N/A` |
| <span id="a3221e-prefix"></span> `prefix` | `VALUE` | `String` |   | Property name prefix |

See the [manifest](../config/manifest.md) for all available types.
