# io.helidon.tracing.Tracer

## Description

Tracer configuration.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="ae09ad-boolean-tags"></span> `boolean-tags` | `MAP` | `Boolean` |   | Tracer level tags that get added to all reported spans |
| <span id="a6d3db-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | When enabled, tracing will be sent |
| <span id="a701d5-global"></span> `global` | `VALUE` | `Boolean` | `true` | When enabled, the created instance is also registered as a global tracer |
| <span id="a7d2ab-host"></span> `host` | `VALUE` | `String` |   | Host to use to connect to tracing collector |
| <span id="ad8eb0-int-tags"></span> `int-tags` | `MAP` | `Integer` |   | Tracer level tags that get added to all reported spans |
| <span id="a912bb-path"></span> `path` | `VALUE` | `String` |   | Path on the collector host to use when sending data to tracing collector |
| <span id="ad6020-port"></span> `port` | `VALUE` | `Integer` |   | Port to use to connect to tracing collector |
| <span id="a3c6c7-protocol"></span> `protocol` | `VALUE` | `String` |   | Protocol to use (such as `http` or `https`) to connect to tracing collector |
| <span id="af9a68-service"></span> `service` | `VALUE` | `String` |   | Service name of the traced service |
| <span id="a0f568-tags"></span> `tags` | `MAP` | `String` |   | Tracer level tags that get added to all reported spans |

See the [manifest](../config/manifest.md) for all available types.
