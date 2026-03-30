# io.helidon.webclient.api.HttpConfigBase

## Description

Common configuration for HTTP protocols.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a0338f-connect-timeout"></span> `connect-timeout` | `VALUE` | `Duration` |   | Connect timeout |
| <span id="a10ec4-follow-redirects"></span> `follow-redirects` | `VALUE` | `Boolean` | `true` | Whether to follow redirects |
| <span id="a5d823-keep-alive"></span> `keep-alive` | `VALUE` | `Boolean` | `true` | Determines if connection keep alive is enabled (NOT socket keep alive, but HTTP connection keep alive, to re-use the same connection for multiple requests) |
| <span id="a37d34-max-redirects"></span> `max-redirects` | `VALUE` | `Integer` | `10` | Max number of followed redirects |
| <span id="adb36e-properties"></span> `properties` | `MAP` | `String` |   | Properties configured for this client |
| <span id="a55ad8-proxy"></span> [`proxy`](../config/io_helidon_webclient_api_Proxy.md) | `VALUE` | `i.h.w.a.Proxy` |   | Proxy configuration to be used for requests |
| <span id="ac96b3-read-timeout"></span> `read-timeout` | `VALUE` | `Duration` |   | Read timeout |
| <span id="a542eb-tls"></span> [`tls`](../config/io_helidon_common_tls_Tls.md) | `VALUE` | `i.h.c.t.Tls` |   | TLS configuration for any TLS request from this client |

See the [manifest](../config/manifest.md) for all available types.
