# io.helidon.common.concurrency.limits.AimdLimit

## Description

Configuration of

io.helidon.common.concurrency.limits.AimdLimit

.

## Usages

- [`server.concurrency-limit.aimd`](../config/io_helidon_common_concurrency_limits_Limit.md#a1db99-aimd)
- [`server.features.limits.concurrency-limit.aimd`](../config/io_helidon_common_concurrency_limits_Limit.md#a1db99-aimd)
- [`server.sockets.concurrency-limit.aimd`](../config/io_helidon_common_concurrency_limits_Limit.md#a1db99-aimd)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a12670-backoff-ratio"></span> `backoff-ratio` | `VALUE` | `Double` | `0.9` | Backoff ratio to use for the algorithm |
| <span id="a1d183-enable-metrics"></span> `enable-metrics` | `VALUE` | `Boolean` | `false` | Whether to collect metrics for the AIMD implementation |
| <span id="a68122-fair"></span> `fair` | `VALUE` | `Boolean` | `false` | Whether the `java.util.concurrent.Semaphore` should be `java.util.concurrent.Semaphore#isFair()` |
| <span id="a71a0b-initial-limit"></span> `initial-limit` | `VALUE` | `Integer` | `20` | Initial limit |
| <span id="ae27df-max-limit"></span> `max-limit` | `VALUE` | `Integer` | `200` | Maximal limit |
| <span id="a18d50-min-limit"></span> `min-limit` | `VALUE` | `Integer` | `20` | Minimal limit |
| <span id="ab5106-queue-length"></span> `queue-length` | `VALUE` | `Integer` | `0` | How many requests can be enqueued waiting for a permit after the max limit is reached |
| <span id="aa41cd-queue-timeout"></span> `queue-timeout` | `VALUE` | `Duration` | `PT1S` | How long to wait for a permit when enqueued |
| <span id="ad4ee4-timeout"></span> `timeout` | `VALUE` | `Duration` | `PT5S` | Timeout that when exceeded is the same as if the task failed |

See the [manifest](../config/manifest.md) for all available types.
