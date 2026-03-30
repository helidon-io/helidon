# io.helidon.metrics.api.ScopeConfig

## Description

Configuration settings for a scope within the

MetricsConfigBlueprint#METRICS_CONFIG_KEY

config section.

## Usages

- [`metrics.scoping.scopes`](../config/io_helidon_metrics_api_ScopingConfig.md#aacbce-scopes)
- [`server.features.observe.observers.metrics.scoping.scopes`](../config/io_helidon_metrics_api_ScopingConfig.md#aacbce-scopes)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a8bb6f-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether the scope is enabled |
| <span id="a29406-filter-exclude"></span> `filter.exclude` | `VALUE` | `Pattern` |   | Regular expression for meter names to exclude |
| <span id="a5d31f-filter-include"></span> `filter.include` | `VALUE` | `Pattern` |   | Regular expression for meter names to include |
| <span id="a589d8-name"></span> `name` | `VALUE` | `String` |   | Name of the scope to which the configuration applies |

See the [manifest](../config/manifest.md) for all available types.
