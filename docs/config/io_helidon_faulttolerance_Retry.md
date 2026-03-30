# io.helidon.faulttolerance.Retry

## Description

Retry

configuration bean.

## Usages

- [`fault-tolerance.retries`](../config/config_reference.md#a614ff-fault-tolerance-retries)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a05ee3-calls"></span> `calls` | `VALUE` | `Integer` | `3` | Number of calls (first try + retries) |
| <span id="a903fc-delay"></span> `delay` | `VALUE` | `Duration` | `PT0.2S` | Base delay between try and retry |
| <span id="ab446f-delay-factor"></span> `delay-factor` | `VALUE` | `Double` | `-1.0` | Delay retry policy factor |
| <span id="a7691c-enable-metrics"></span> `enable-metrics` | `VALUE` | `Boolean` | `false` | Flag to enable metrics for this instance |
| <span id="aee335-jitter"></span> `jitter` | `VALUE` | `Duration` | `PT-1S` | Jitter for `Retry.JitterRetryPolicy` |
| <span id="a9a58f-overall-timeout"></span> `overall-timeout` | `VALUE` | `Duration` | `PT1S` | Overall timeout of all retries combined |

See the [manifest](../config/manifest.md) for all available types.
