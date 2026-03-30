# io.helidon.webserver.observe.ObserveFeature

## Description

Configuration for observability feature itself.

## Usages

- [`server.features.observe`](../config/io_helidon_webserver_spi_ServerFeature.md#a03f1c-observe)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a42c15-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether the observe support is enabled |
| <span id="a0d210-endpoint"></span> `endpoint` | `VALUE` | `String` | `/observe` | Root endpoint to use for observe providers |
| <span id="a837f0-observers"></span> [`observers`](../config/io_helidon_webserver_observe_spi_Observer.md) | `LIST` | `i.h.w.o.s.Observer` |   | Observers to use with this observe features |
| <span id="a1e406-observers-discover-services"></span> `observers-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `observers` |
| <span id="ace9c3-sockets"></span> `sockets` | `LIST` | `String` |   | Sockets the observability endpoint should be exposed on |
| <span id="a1bc81-weight"></span> `weight` | `VALUE` | `Double` | `80.0` | Change the weight of this feature |

### Deprecated Options

| Key | Kind | Type | Description |
|----|----|----|----|
| <span id="a25a02-cors"></span> [`cors`](../config/io_helidon_cors_CrossOriginConfig.md) | `VALUE` | `i.h.c.CrossOriginConfig` | Cors support inherited by each observe provider, unless explicitly configured |

See the [manifest](../config/manifest.md) for all available types.
