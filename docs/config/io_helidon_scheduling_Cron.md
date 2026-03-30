# io.helidon.scheduling.Cron

## Description

Scheduling periodically executed task with specified cron expression.

## Usages

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aa0a55-concurrent"></span> `concurrent` | `VALUE` | `Boolean` | `true` | Allow concurrent execution if previous task didn't finish before next execution |
| <span id="ac430f-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether the task is enabled |
| <span id="a78a5d-expression"></span> `expression` | `VALUE` | `String` |   | Cron expression for specifying period of execution |
| <span id="acc650-id"></span> `id` | `VALUE` | `String` |   | Identification of the started task |
| <span id="a0e1a4-zone"></span> `zone` | `VALUE` | `ZoneId` |   | Time zone to use for cron expression evaluation |

See the [manifest](../config/manifest.md) for all available types.
