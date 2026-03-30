# io.helidon.webserver.observe.log.LogObserver

## Description

Log Observer configuration.

## Usages

- [`server.features.observe.observers.log`](../config/io_helidon_webserver_observe_spi_Observer.md#a5e04c-log)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a5d87e-endpoint"></span> `endpoint` | `VALUE` | `String` | `log` | `N/A` |
| <span id="a56e47-permit-all"></span> `permit-all` | `VALUE` | `Boolean` |   | Permit all access, even when not authorized |
| <span id="afc46a-stream"></span> [`stream`](../config/io_helidon_webserver_observe_log_LogStreamConfig.md) | `VALUE` | `i.h.w.o.l.LogStreamConfig` |   | Configuration of log stream |

See the [manifest](../config/manifest.md) for all available types.
