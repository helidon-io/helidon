# io.helidon.webserver.concurrency.limits.LimitsFeature

## Description

Server feature that adds limits as filters.

## Usages

- [`server.features.limits`](../config/io_helidon_webserver_spi_ServerFeature.md#ab4b8f-limits)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a5339e-concurrency-limit"></span> [`concurrency-limit`](../config/io_helidon_common_concurrency_limits_Limit.md) | `VALUE` | `i.h.c.c.l.Limit` |   | Concurrency limit to use to limit concurrent execution of incoming requests |
| <span id="a4ee72-concurrency-limit-discover-services"></span> `concurrency-limit-discover-services` | `VALUE` | `Boolean` | `false` | Whether to enable automatic service discovery for `concurrency-limit` |
| <span id="afab78-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether this feature is enabled, defaults to `true` |
| <span id="ac5835-sockets"></span> `sockets` | `LIST` | `String` |   | List of sockets to register this feature on |
| <span id="a37665-weight"></span> `weight` | `VALUE` | `Double` | `2000.0` | Weight of the context feature |

See the [manifest](../config/manifest.md) for all available types.
