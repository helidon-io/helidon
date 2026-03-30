# io.helidon.faulttolerance.Bulkhead

## Description

Bulkhead

configuration bean.

## Usages

- [`fault-tolerance.bulkheads`](../config/config_reference.md#a017e8-fault-tolerance-bulkheads)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="ad3a55-enable-metrics"></span> `enable-metrics` | `VALUE` | `Boolean` | `false` | Flag to enable metrics for this instance |
| <span id="af9275-limit"></span> `limit` | `VALUE` | `Integer` | `10` | Maximal number of parallel requests going through this bulkhead |
| <span id="a85a17-queue-length"></span> `queue-length` | `VALUE` | `Integer` | `10` | Maximal number of enqueued requests waiting for processing |

See the [manifest](../config/manifest.md) for all available types.
