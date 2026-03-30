# io.helidon.common.concurrency.limits.FixedLimit

## Description

Configuration of

FixedLimit

.

## Usages

- [`server.concurrency-limit.fixed`](../config/io_helidon_common_concurrency_limits_Limit.md#aaa497-fixed)
- [`server.features.limits.concurrency-limit.fixed`](../config/io_helidon_common_concurrency_limits_Limit.md#aaa497-fixed)
- [`server.sockets.concurrency-limit.fixed`](../config/io_helidon_common_concurrency_limits_Limit.md#aaa497-fixed)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a25d33-enable-metrics"></span> `enable-metrics` | `VALUE` | `Boolean` | `false` | Whether to collect metrics for the fixed limit implementation |
| <span id="ab0a2f-fair"></span> `fair` | `VALUE` | `Boolean` | `false` | Whether the `java.util.concurrent.Semaphore` should be `java.util.concurrent.Semaphore#isFair()` |
| <span id="a3b228-permits"></span> `permits` | `VALUE` | `Integer` | `0` | Number of permit to allow |
| <span id="a4b3e7-queue-length"></span> `queue-length` | `VALUE` | `Integer` | `0` | How many requests can be enqueued waiting for a permit |
| <span id="a50bc8-queue-timeout"></span> `queue-timeout` | `VALUE` | `Duration` | `PT1S` | How long to wait for a permit when enqueued |

See the [manifest](../config/manifest.md) for all available types.
