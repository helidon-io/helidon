# io.helidon.common.socket.SocketOptions

## Description

Socket options.

## Usages

- [`server.connection-options`](../config/io_helidon_webserver_WebServer.md#ac9c91-connection-options)
- [`server.sockets.connection-options`](../config/io_helidon_webserver_ListenerConfig.md#a251be-connection-options)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a964a9-connect-timeout"></span> `connect-timeout` | `VALUE` | `Duration` | `PT10S` | Socket connect timeout |
| <span id="abfb11-read-timeout"></span> `read-timeout` | `VALUE` | `Duration` | `PT30S` | Socket read timeout |
| <span id="aa481e-socket-keep-alive"></span> `socket-keep-alive` | `VALUE` | `Boolean` | `true` | Configure socket keep alive |
| <span id="a0f53d-socket-receive-buffer-size"></span> `socket-receive-buffer-size` | `VALUE` | `Integer` |   | Socket receive buffer size |
| <span id="ab693f-socket-reuse-address"></span> `socket-reuse-address` | `VALUE` | `Boolean` | `true` | Socket reuse address |
| <span id="aaf0e6-socket-send-buffer-size"></span> `socket-send-buffer-size` | `VALUE` | `Integer` |   | Socket send buffer size |
| <span id="ae4080-tcp-no-delay"></span> `tcp-no-delay` | `VALUE` | `Boolean` | `false` | This option may improve performance on some systems |

See the [manifest](../config/manifest.md) for all available types.
