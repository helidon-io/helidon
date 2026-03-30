# io.helidon.webserver.spi.ProtocolConfig

## Description

This type is a provider contract.

## Usages

- [`server.protocols`](io_helidon_webserver_WebServer.md#abdf05-protocols)
- [`server.sockets.protocols`](io_helidon_webserver_ListenerConfig.md#ad0b48-protocols)

## Implementations

| Key | Type | Description |
|----|----|----|
| <span id="ab66d9-grpc"></span> [`grpc`](io_helidon_webserver_grpc_GrpcConfig.md) | `i.h.w.g.GrpcConfig` | `N/A` |
| <span id="ab85ee-http_1_1"></span> [`http_1_1`](io_helidon_webserver_http1_Http1Config.md) | `i.h.w.h.Http1Config` | HTTP/1.1 server configuration |
| <span id="a6d68d-http_2"></span> [`http_2`](io_helidon_webserver_http2_Http2Config.md) | `i.h.w.h.Http2Config` | HTTP/2 server configuration |
| <span id="ae25cf-websocket"></span> [`websocket`](io_helidon_webserver_websocket_WsConfig.md) | `i.h.w.w.WsConfig` | WebSocket protocol configuration |

See the [manifest](../config/manifest.md) for all available types.
