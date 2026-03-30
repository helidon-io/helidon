# io.helidon.webserver.staticcontent.FileSystemHandlerConfig

## Description

File system based static content handler configuration.

## Usages

- [`server.features.static-content.path`](../config/io_helidon_webserver_staticcontent_StaticContentFeature.md#a213ff-path)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="ac88e2-cached-files"></span> `cached-files` | `LIST` | `String` |   | A set of files that are cached in memory at startup |
| <span id="ac0aa6-content-types"></span> `content-types` | `MAP` | `i.h.w.s.S.BaseMethods` |   | Maps a filename extension to the response content type |
| <span id="aa4368-context"></span> `context` | `VALUE` | `String` | `/` | Context that will serve this handler's static resources, defaults to `/` |
| <span id="a49101-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether this handle is enabled, defaults to `true` |
| <span id="a39930-location"></span> `location` | `VALUE` | `Path` |   | The directory (or a single file) that contains the root of the static content |
| <span id="af8862-memory-cache"></span> [`memory-cache`](../config/io_helidon_webserver_staticcontent_MemoryCache.md) | `VALUE` | `i.h.w.s.MemoryCache` |   | Handles will use memory cache configured on `StaticContentConfig#memoryCache()` by default |
| <span id="a7cfd0-record-cache-capacity"></span> `record-cache-capacity` | `VALUE` | `Integer` |   | Configure capacity of cache used for resources |
| <span id="aac296-sockets"></span> `sockets` | `LIST` | `String` |   | Sockets names (listeners) that will host this static content handler, defaults to all configured sockets |
| <span id="a3d76e-welcome"></span> `welcome` | `VALUE` | `String` |   | Welcome-file name |

See the [manifest](../config/manifest.md) for all available types.
