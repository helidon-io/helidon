# io.helidon.common.concurrency.limits.RateLimitingAlgorithmType

## Description

This type is an enumeration.

## Usages

- [`server.concurrency-limit.throughput.rate-limiting-algorithm`](io_helidon_common_concurrency_limits_ThroughputLimit.md#af39b3-rate-limiting-algorithm)
- [`server.features.limits.concurrency-limit.throughput.rate-limiting-algorithm`](io_helidon_common_concurrency_limits_ThroughputLimit.md#af39b3-rate-limiting-algorithm)
- [`server.sockets.concurrency-limit.throughput.rate-limiting-algorithm`](io_helidon_common_concurrency_limits_ThroughputLimit.md#af39b3-rate-limiting-algorithm)

## Allowed Values

| Value          | Description                                                |
|----------------|------------------------------------------------------------|
| `TOKEN_BUCKET` | Requests require tokens from a bucket that fills over time |
| `FIXED_RATE`   | Requests are processed at a fixed rate                     |

See the [manifest](../config/manifest.md) for all available types.
