# io.helidon.scheduling.FixedRate

## Description

Scheduling periodically executed task with specified fixed rate.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a12d22-delay-by"></span> `delay-by` | `VALUE` | `Duration` | `PT0S` | Initial delay of the first invocation |
| <span id="a394d5-delay-type"></span> [`delay-type`](../config/io_helidon_scheduling_FixedRate_DelayType.md) | `VALUE` | `i.h.s.F.DelayType` | `SINCE_PREVIOUS_START` | Configure whether the interval between the invocations should be calculated from the time when previous task started or ended |
| <span id="a39c2a-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether the task is enabled |
| <span id="a27a1a-id"></span> `id` | `VALUE` | `String` |   | Identification of the started task |
| <span id="a4b0e9-interval"></span> `interval` | `VALUE` | `Duration` |   | Fixed interval between each invocation |

### Deprecated Options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a44d13-delay"></span> `delay` | `VALUE` | `Long` |   | Fixed rate delay between each invocation |
| <span id="a91a8f-initial-delay"></span> `initial-delay` | `VALUE` | `Long` |   | Initial delay of the first invocation |
| <span id="a776bc-time-unit"></span> [`time-unit`](../config/java_util_concurrent_TimeUnit.md) | `VALUE` | `TimeUnit` | `TimeUnit.SECONDS` | `java.util.concurrent.TimeUnit TimeUnit` used for interpretation of values provided with `io.helidon.scheduling.FixedRateConfig.Builder#delay(long)` and `io.helidon.scheduling.FixedRateConfig.Builder#initialDelay(long)` |

See the [manifest](../config/manifest.md) for all available types.
