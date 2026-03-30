# io.helidon.webserver.observe.config.ConfigObserver

## Description

Configuration of Config Observer.

## Usages

- [`server.features.observe.observers.config`](../config/io_helidon_webserver_observe_spi_Observer.md#aedc6a-config)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a60005-endpoint"></span> `endpoint` | `VALUE` | `String` | `config` | Endpoint this observer is available on |
| <span id="a8b193-permit-all"></span> `permit-all` | `VALUE` | `Boolean` |   | Permit all access, even when not authorized |
| <span id="a3dbd0-secrets"></span> `secrets` | `LIST` | `String` | `.*password, .*passphrase, .*secret` | Secret patterns (regular expressions) to exclude from output |

See the [manifest](../config/manifest.md) for all available types.
