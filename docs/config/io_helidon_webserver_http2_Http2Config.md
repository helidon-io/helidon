# io.helidon.webserver.http2.Http2Config

## Description

HTTP/2 server configuration.

## Usages

- [`server.protocols.http_2`](../config/io_helidon_webserver_spi_ProtocolConfig.md#a6d68d-http_2)
- [`server.sockets.protocols.http_2`](../config/io_helidon_webserver_spi_ProtocolConfig.md#a6d68d-http_2)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a6f761-flow-control-timeout"></span> `flow-control-timeout` | `VALUE` | `Duration` | `PT15S` | Outbound flow control blocking timeout configured as `java.time.Duration` or text in ISO-8601 format |
| <span id="acac36-initial-window-size"></span> `initial-window-size` | `VALUE` | `Integer` | `1048576` | This setting indicates the sender's maximum window size in bytes for stream-level flow control |
| <span id="a01c8a-max-buffered-entity-size"></span> `max-buffered-entity-size` | `VALUE` | `i.h.c.Size` | `64 KB` | Configure the maximum size allowed for an entity that can be explicitly buffered by the application by calling `io.helidon.http.media.ReadableEntity#buffer` |
| <span id="ac24e4-max-concurrent-streams"></span> `max-concurrent-streams` | `VALUE` | `Long` | `8192` | Maximum number of concurrent streams that the server will allow |
| <span id="a82a16-max-empty-frames"></span> `max-empty-frames` | `VALUE` | `Integer` | `10` | Maximum number of consecutive empty frames allowed on connection |
| <span id="a52adc-max-frame-size"></span> `max-frame-size` | `VALUE` | `Integer` | `16384` | The size of the largest frame payload that the sender is willing to receive in bytes |
| <span id="a3aee0-max-header-list-size"></span> `max-header-list-size` | `VALUE` | `Long` | `8192` | The maximum field section size that the sender is prepared to accept in bytes |
| <span id="a980b3-max-rapid-resets"></span> `max-rapid-resets` | `VALUE` | `Integer` | `50` | Maximum number of rapid resets(stream RST sent by client before any data have been sent by server) |
| <span id="a51af5-rapid-reset-check-period"></span> `rapid-reset-check-period` | `VALUE` | `Duration` | `PT10S` | Period for counting rapid resets(stream RST sent by client before any data have been sent by server) |
| <span id="a4b1c1-requested-uri-discovery"></span> [`requested-uri-discovery`](../config/io_helidon_http_RequestedUriDiscoveryContext.md) | `VALUE` | `i.h.h.RequestedUriDiscoveryContext` |   | Requested URI discovery settings |
| <span id="a775e8-send-error-details"></span> `send-error-details` | `VALUE` | `Boolean` | `false` | Whether to send error message over HTTP to client |
| <span id="a33271-validate-path"></span> `validate-path` | `VALUE` | `Boolean` | `true` | If set to false, any path is accepted (even containing illegal characters) |

See the [manifest](../config/manifest.md) for all available types.
