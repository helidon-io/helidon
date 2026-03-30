# io.helidon.http.media.MediaContext

## Description

Media context to obtain readers and writers of various supported content types.

## Usages

- [`server.media-context`](../config/io_helidon_webserver_WebServer.md#a847a9-media-context)
- [`server.sockets.media-context`](../config/io_helidon_webserver_ListenerConfig.md#ad860a-media-context)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a9ddc1-fallback"></span> [`fallback`](../config/io_helidon_http_media_MediaContext.md) | `VALUE` | `i.h.h.m.MediaContext` |   | Existing context to be used as a fallback for this context |
| <span id="acbfec-media-supports"></span> [`media-supports`](../config/io_helidon_http_media_MediaSupport.md) | `LIST` | `i.h.h.m.MediaSupport` |   | Media supports to use |
| <span id="a24948-media-supports-discover-services"></span> `media-supports-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `media-supports` |
| <span id="a5da96-register-defaults"></span> `register-defaults` | `VALUE` | `Boolean` | `true` | Should we register defaults of Helidon, such as String media support |

See the [manifest](../config/manifest.md) for all available types.
