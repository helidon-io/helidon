# io.helidon.tracing.providers.opentracing.OpenTracingTracerBuilder

## Description

OpenTracing tracer configuration.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a4f8f8-boolean-tags"></span> `boolean-tags` | `MAP` | `Boolean` |   | Tracer level tags that get added to all reported spans |
| <span id="a75321-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | When enabled, tracing will be sent |
| <span id="af0055-global"></span> `global` | `VALUE` | `Boolean` | `true` | When enabled, the created instance is also registered as a global tracer |
| <span id="a2016b-host"></span> `host` | `VALUE` | `String` |   | Host to use to connect to tracing collector |
| <span id="a603a3-int-tags"></span> `int-tags` | `MAP` | `Integer` |   | Tracer level tags that get added to all reported spans |
| <span id="a42d5e-path"></span> `path` | `VALUE` | `String` |   | Path on the collector host to use when sending data to tracing collector |
| <span id="a12be5-port"></span> `port` | `VALUE` | `Integer` |   | Port to use to connect to tracing collector |
| <span id="acf71d-protocol"></span> `protocol` | `VALUE` | `String` |   | Protocol to use (such as `http` or `https`) to connect to tracing collector |
| <span id="a56543-service"></span> `service` | `VALUE` | `String` |   | Service name of the traced service |
| <span id="ad2ec8-tags"></span> `tags` | `MAP` | `String` |   | Tracer level tags that get added to all reported spans |

See the [manifest](../config/manifest.md) for all available types.
