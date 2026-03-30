# io.helidon.webserver.staticcontent.TemporaryStorage

## Description

Configuration of temporary storage for classpath based handlers.

## Usages

- [`server.features.static-content.classpath.temporary-storage`](../config/io_helidon_webserver_staticcontent_ClasspathHandlerConfig.md#a19deb-temporary-storage)
- [`server.features.static-content.temporary-storage`](../config/io_helidon_webserver_staticcontent_StaticContentFeature.md#a0dc23-temporary-storage)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a1d2d5-delete-on-exit"></span> `delete-on-exit` | `VALUE` | `Boolean` | `true` | Whether temporary files should be deleted on JVM exit |
| <span id="ad7c21-directory"></span> `directory` | `VALUE` | `Path` |   | Location of the temporary storage, defaults to temporary storage configured for the JVM |
| <span id="acd2ca-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether the temporary storage is enabled, defaults to `true` |
| <span id="ab3f0d-file-prefix"></span> `file-prefix` | `VALUE` | `String` | `helidon-ws` | Prefix of the files in temporary storage |
| <span id="aca37b-file-suffix"></span> `file-suffix` | `VALUE` | `String` | `.je` | Suffix of the files in temporary storage |

See the [manifest](../config/manifest.md) for all available types.
