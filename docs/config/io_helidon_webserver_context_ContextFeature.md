# io.helidon.webserver.context.ContextFeature

## Description

Configuration of context feature.

## Usages

- [`server.features.context`](../config/io_helidon_webserver_spi_ServerFeature.md#a57af2-context)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aa10e9-records"></span> [`records`](../config/io_helidon_common_context_http_ContextRecordConfig.md) | `LIST` | `i.h.c.c.h.ContextRecordConfig` |   | List of propagation records |
| <span id="ac7113-sockets"></span> `sockets` | `LIST` | `String` |   | List of sockets to register this feature on |
| <span id="a37f63-weight"></span> `weight` | `VALUE` | `Double` | `1100.0` | Weight of the context feature |

See the [manifest](../config/manifest.md) for all available types.
