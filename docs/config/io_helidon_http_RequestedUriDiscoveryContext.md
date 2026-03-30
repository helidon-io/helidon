# io.helidon.http.RequestedUriDiscoveryContext

## Description

Requested URI discovery settings for a socket.

## Usages

- [`server.protocols.http_1_1.requested-uri-discovery`](../config/io_helidon_webserver_http1_Http1Config.md#afc226-requested-uri-discovery)
- [`server.protocols.http_2.requested-uri-discovery`](../config/io_helidon_webserver_http2_Http2Config.md#a4b1c1-requested-uri-discovery)
- [`server.requested-uri-discovery`](../config/io_helidon_webserver_WebServer.md#aaf9ce-requested-uri-discovery)
- [`server.sockets.protocols.http_1_1.requested-uri-discovery`](../config/io_helidon_webserver_http1_Http1Config.md#afc226-requested-uri-discovery)
- [`server.sockets.protocols.http_2.requested-uri-discovery`](../config/io_helidon_webserver_http2_Http2Config.md#a4b1c1-requested-uri-discovery)
- [`server.sockets.requested-uri-discovery`](../config/io_helidon_webserver_ListenerConfig.md#a1c079-requested-uri-discovery)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aa5773-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true if 'types' or 'trusted-proxies' is set; false otherwise` | Sets whether requested URI discovery is enabled for requestes arriving on the socket |
| <span id="acaa10-trusted-proxies"></span> [`trusted-proxies`](../config/io_helidon_common_configurable_AllowList.md) | `VALUE` | `i.h.c.c.AllowList` |   | Sets the trusted proxies for requested URI discovery for requests arriving on the socket |
| <span id="a3fdad-types"></span> [`types`](../config/io_helidon_http_RequestedUriDiscoveryContext_RequestedUriDiscoveryType.md) | `LIST` | `i.h.h.R.RequestedUriDiscoveryType` |   | Sets the discovery types for requested URI discovery for requests arriving on the socket |

See the [manifest](../config/manifest.md) for all available types.
