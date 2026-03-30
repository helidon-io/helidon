# io.helidon.common.concurrency.limits.ThroughputLimit

## Description

Configuration of

ThroughputLimit

.

## Usages

- [`server.concurrency-limit.throughput`](../config/io_helidon_common_concurrency_limits_Limit.md#a13e9b-throughput)
- [`server.features.limits.concurrency-limit.throughput`](../config/io_helidon_common_concurrency_limits_Limit.md#a13e9b-throughput)
- [`server.sockets.concurrency-limit.throughput`](../config/io_helidon_common_concurrency_limits_Limit.md#a13e9b-throughput)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a7c5c7-amount"></span> `amount` | `VALUE` | `Integer` | `0` | Number of operations to allow during the relevant time window |
| <span id="ae1010-duration"></span> `duration` | `VALUE` | `Duration` | `PT1S` | Duration of the time window over which operations will be counted |
| <span id="a48968-enable-metrics"></span> `enable-metrics` | `VALUE` | `Boolean` | `false` | Whether to collect metrics for the throughput limit implementation |
| <span id="a144dd-fair"></span> `fair` | `VALUE` | `Boolean` | `false` | Whether the `java.util.concurrent.Semaphore` should be `java.util.concurrent.Semaphore#isFair()` |
| <span id="a5fc68-queue-length"></span> `queue-length` | `VALUE` | `Integer` | `0` | How many requests can be enqueued waiting for a permit |
| <span id="a5391a-queue-timeout"></span> `queue-timeout` | `VALUE` | `Duration` | `PT1S` | How long to wait for a permit when enqueued |
| <span id="af39b3-rate-limiting-algorithm"></span> [`rate-limiting-algorithm`](../config/io_helidon_common_concurrency_limits_RateLimitingAlgorithmType.md) | `VALUE` | `i.h.c.c.l.RateLimitingAlgorithmType` | `TOKEN_BUCKET` | The rate limiting algorithm to apply |

See the [manifest](../config/manifest.md) for all available types.
