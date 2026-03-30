# io.helidon.webserver.observe.health.HealthObserver

## Description

Configuration of Health observer.

## Usages

- [`health`](../config/config_reference.md#ac9c2c-health)
- [`server.features.observe.observers.health`](../config/io_helidon_webserver_observe_spi_Observer.md#a27684-health)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a66088-details"></span> `details` | `VALUE` | `Boolean` | `false` | Whether details should be printed |
| <span id="aaa7ec-endpoint"></span> `endpoint` | `VALUE` | `String` | `health` | `N/A` |
| <span id="af4081-exclude"></span> `exclude` | `LIST` | `String` |   | Health check names to exclude in computing the overall health of the server |
| <span id="a27567-use-system-services"></span> `use-system-services` | `VALUE` | `Boolean` | `true` | Whether to use services discovered by `java.util.ServiceLoader` |

See the [manifest](../config/manifest.md) for all available types.
