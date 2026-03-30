# io.helidon.metrics.api.ScopingConfig

## Description

N/A

.

## Usages

- [`metrics.scoping`](../config/io_helidon_webserver_observe_metrics_MetricsObserver.md#a2d4d9-scoping)
- [`server.features.observe.observers.metrics.scoping`](../config/io_helidon_webserver_observe_metrics_MetricsObserver.md#a2d4d9-scoping)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="ac42cc-default"></span> `default` | `VALUE` | `String` | `application` | Default scope value to associate with meters that are registered without an explicit setting; no setting means meters are assigned scope `io.helidon.metrics.api.Meter.Scope#DEFAULT` |
| <span id="aacbce-scopes"></span> [`scopes`](../config/io_helidon_metrics_api_ScopeConfig.md) | `MAP` | `i.h.m.a.ScopeConfig` |   | Settings for individual scopes |
| <span id="a910f9-tag-name"></span> `tag-name` | `VALUE` | `String` | `scope` | Tag name for storing meter scope values in the underlying implementation meter registry |

See the [manifest](../config/manifest.md) for all available types.
