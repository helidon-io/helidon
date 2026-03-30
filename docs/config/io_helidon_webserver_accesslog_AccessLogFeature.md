# io.helidon.webserver.accesslog.AccessLogFeature

## Description

Configuration of access log feature.

## Usages

- [`server.features.access-log`](../config/io_helidon_webserver_spi_ServerFeature.md#a42c97-access-log)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aaefb9-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether this feature will be enabled |
| <span id="a8717c-format"></span> `format` | `VALUE` | `String` |   | The format for log entries (similar to the Apache `LogFormat`) |
| <span id="aeb9ad-logger-name"></span> `logger-name` | `VALUE` | `String` | `io.helidon.webserver.AccessLog` | Name of the logger used to obtain access log logger from `System#getLogger(String)` |
| <span id="a631a5-sockets"></span> `sockets` | `LIST` | `String` |   | List of sockets to register this feature on |
| <span id="ac3d7a-weight"></span> `weight` | `VALUE` | `Double` | `1000.0` | Weight of the access log feature |

See the [manifest](../config/manifest.md) for all available types.
