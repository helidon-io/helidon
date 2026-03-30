# io.helidon.http.encoding.ContentEncodingContext

## Description

Content encoding support to obtain encoders and decoders.

## Usages

- [`server.content-encoding`](../config/io_helidon_webserver_WebServer.md#a511a0-content-encoding)
- [`server.sockets.content-encoding`](../config/io_helidon_webserver_ListenerConfig.md#a8f3cb-content-encoding)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="ab960c-content-encodings"></span> [`content-encodings`](../config/io_helidon_http_encoding_ContentEncoding.md) | `LIST` | `i.h.h.e.ContentEncoding` |   | List of content encodings that should be used |
| <span id="ac89ac-content-encodings-discover-services"></span> `content-encodings-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `content-encodings` |

See the [manifest](../config/manifest.md) for all available types.
