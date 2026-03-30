# io.helidon.faulttolerance.Timeout

## Description

Timeout

configuration bean.

## Usages

- [`fault-tolerance.timeouts`](../config/config_reference.md#abdf4d-fault-tolerance-timeouts)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a04782-current-thread"></span> `current-thread` | `VALUE` | `Boolean` | `false` | Flag to indicate that code must be executed in current thread instead of in an executor's thread |
| <span id="a2cfc5-enable-metrics"></span> `enable-metrics` | `VALUE` | `Boolean` | `false` | Flag to enable metrics for this instance |
| <span id="a37c97-timeout"></span> `timeout` | `VALUE` | `Duration` | `PT10S` | Duration to wait before timing out |

See the [manifest](../config/manifest.md) for all available types.
