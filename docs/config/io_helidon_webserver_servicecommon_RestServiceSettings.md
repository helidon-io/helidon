# io.helidon.webserver.servicecommon.RestServiceSettings

## Description

Common settings across REST services.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aaa195-cors"></span> [`cors`](../config/io_helidon_cors_CrossOriginConfig.md) | `MAP` | `i.h.c.CrossOriginConfig` |   | Sets the cross-origin config builder for use in establishing CORS support for the service endpoints |
| <span id="a9f672-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Is this service enabled or not |
| <span id="ae37e4-routing"></span> `routing` | `VALUE` | `String` |   | Sets the routing name to use for setting up the service's endpoint |
| <span id="aa843e-web-context"></span> `web-context` | `VALUE` | `String` |   | Sets the web context to use for the service's endpoint |

See the [manifest](../config/manifest.md) for all available types.
