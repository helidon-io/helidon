# io.helidon.webserver.websocket.WsConfig

## Description

WebSocket protocol configuration.

## Usages

- [`server.protocols.websocket`](../config/io_helidon_webserver_spi_ProtocolConfig.md#ae25cf-websocket)
- [`server.sockets.protocols.websocket`](../config/io_helidon_webserver_spi_ProtocolConfig.md#ae25cf-websocket)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="ab8bc8-max-frame-length"></span> `max-frame-length` | `VALUE` | `Integer` | `1048576` | Max WebSocket frame size supported by the server on a read operation |
| <span id="a43fd4-name"></span> `name` | `VALUE` | `String` | `websocket` | Name of this configuration |
| <span id="a28c06-origins"></span> `origins` | `LIST` | `String` |   | WebSocket origins |

See the [manifest](../config/manifest.md) for all available types.
