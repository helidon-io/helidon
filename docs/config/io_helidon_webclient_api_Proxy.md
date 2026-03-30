# io.helidon.webclient.api.Proxy

## Description

A definition of a proxy server to use for outgoing requests.

## Usages

- [`clients.proxy`](../config/io_helidon_webclient_api_WebClient.md#a62d6a-proxy)
- [`security.providers.idcs-role-mapper.oidc-config.webclient.proxy`](../config/io_helidon_webclient_api_WebClient.md#a62d6a-proxy)
- [`security.providers.oidc.webclient.proxy`](../config/io_helidon_webclient_api_WebClient.md#a62d6a-proxy)
- [`server.features.security.security.providers.idcs-role-mapper.oidc-config.webclient.proxy`](../config/io_helidon_webclient_api_WebClient.md#a62d6a-proxy)
- [`server.features.security.security.providers.oidc.webclient.proxy`](../config/io_helidon_webclient_api_WebClient.md#a62d6a-proxy)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a5bcd5-force-http-connect"></span> `force-http-connect` | `VALUE` | `Boolean` |   | Forces HTTP CONNECT with the proxy server |
| <span id="aa6a36-host"></span> `host` | `VALUE` | `String` |   | Sets a new host value |
| <span id="a593f2-no-proxy"></span> `no-proxy` | `LIST` | `String` |   | Configure a host pattern that is not going through a proxy |
| <span id="a6da75-password"></span> `password` | `LIST` | `String` |   | Sets a new password for the proxy |
| <span id="a2b2b9-port"></span> `port` | `VALUE` | `Integer` |   | Sets a port value |
| <span id="a6098e-type"></span> [`type`](../config/io_helidon_webclient_api_Proxy_ProxyType.md) | `VALUE` | `i.h.w.a.P.ProxyType` | `HTTP` | Sets a new proxy type |
| <span id="aee505-username"></span> `username` | `VALUE` | `String` |   | Sets a new username for the proxy |

See the [manifest](../config/manifest.md) for all available types.
