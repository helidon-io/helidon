# io.helidon.webserver.staticcontent.StaticContentFeature

## Description

Configuration of Static content feature.

## Usages

- [`server.features.static-content`](../config/io_helidon_webserver_spi_ServerFeature.md#a2c874-static-content)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a0df4c-classpath"></span> [`classpath`](../config/io_helidon_webserver_staticcontent_ClasspathHandlerConfig.md) | `LIST` | `i.h.w.s.ClasspathHandlerConfig` |   | List of classpath based static content handlers |
| <span id="ab38bf-content-types"></span> `content-types` | `MAP` | `i.h.w.s.S.StaticContentMethods` |   | Maps a filename extension to the response content type |
| <span id="a5237f-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether this feature is enabled, defaults to `true` |
| <span id="a1e68a-memory-cache"></span> [`memory-cache`](../config/io_helidon_webserver_staticcontent_MemoryCache.md) | `VALUE` | `i.h.w.s.MemoryCache` |   | Memory cache shared by the whole feature |
| <span id="a213ff-path"></span> [`path`](../config/io_helidon_webserver_staticcontent_FileSystemHandlerConfig.md) | `LIST` | `i.h.w.s.FileSystemHandlerConfig` |   | List of file system based static content handlers |
| <span id="a35f4e-sockets"></span> `sockets` | `LIST` | `String` |   | Sockets names (listeners) that will host static content handlers, defaults to all configured sockets |
| <span id="a0dc23-temporary-storage"></span> [`temporary-storage`](../config/io_helidon_webserver_staticcontent_TemporaryStorage.md) | `VALUE` | `i.h.w.s.TemporaryStorage` |   | Temporary storage to use across all classpath handlers |
| <span id="ae5463-weight"></span> `weight` | `VALUE` | `Double` | `95.0` | Weight of the static content feature |
| <span id="a3a92b-welcome"></span> `welcome` | `VALUE` | `String` |   | Welcome-file name |

See the [manifest](../config/manifest.md) for all available types.
