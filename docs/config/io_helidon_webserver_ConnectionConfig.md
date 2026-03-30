# io.helidon.webserver.ConnectionConfig

## Description

Configuration of a server connection (for each connection created by clients).

## Usages

- [`server.connection-config`](../config/io_helidon_webserver_WebServer.md#a20877-connection-config)
- [`server.sockets.connection-config`](../config/io_helidon_webserver_ListenerConfig.md#ac4449-connection-config)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a8f124-connect-timeout"></span> `connect-timeout` | `VALUE` | `Duration` | `PT10S` | Connect timeout |
| <span id="a7e1e7-keep-alive"></span> `keep-alive` | `VALUE` | `Boolean` | `true` | Configure socket keep alive |
| <span id="ab6092-read-timeout"></span> `read-timeout` | `VALUE` | `Duration` | `PT30S` | Read timeout |
| <span id="af8f8a-receive-buffer-size"></span> `receive-buffer-size` | `VALUE` | `Integer` | `32768` | Socket receive buffer size |
| <span id="a81729-reuse-address"></span> `reuse-address` | `VALUE` | `Boolean` | `true` | Socket reuse address |
| <span id="a679f1-send-buffer-size"></span> `send-buffer-size` | `VALUE` | `Integer` | `32768` | Socket send buffer size |
| <span id="a25bab-tcp-no-delay"></span> `tcp-no-delay` | `VALUE` | `Boolean` | `false` | Disable Nagle's algorithm by setting TCP_NODELAY to true |

See the [manifest](../config/manifest.md) for all available types.
