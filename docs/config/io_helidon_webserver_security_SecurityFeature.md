# io.helidon.webserver.security.SecurityFeature

## Description

Configuration of security feature fow webserver.

## Usages

- [`server.features.security`](../config/io_helidon_webserver_spi_ServerFeature.md#ad5f1a-security)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="abe190-defaults"></span> [`defaults`](../config/io_helidon_webserver_security_SecurityHandler.md) | `VALUE` | `i.h.w.s.SecurityHandler` |   | The default security handler |
| <span id="accc93-paths"></span> [`paths`](../config/io_helidon_webserver_security_PathsConfig.md) | `LIST` | `i.h.w.s.PathsConfig` |   | Configuration for webserver paths |
| <span id="ad067b-security"></span> [`security`](../config/io_helidon_security_Security.md) | `VALUE` | `i.h.s.Security` |   | Security associated with this feature |
| <span id="ad2ec3-weight"></span> `weight` | `VALUE` | `Double` | `800.0` | Weight of the security feature |

See the [manifest](../config/manifest.md) for all available types.
