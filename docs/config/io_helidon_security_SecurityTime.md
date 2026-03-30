# io.helidon.security.SecurityTime

## Description

Time used in security, configurable.

## Usages

- [`security.environment.server-time`](../config/io_helidon_security_Security.md#a53e2d-environment-server-time)
- [`server.features.security.security.environment.server-time`](../config/io_helidon_security_Security.md#a53e2d-environment-server-time)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a7c8fd-day-of-month"></span> `day-of-month` | `VALUE` | `Long` |   | Set an explicit value for one of the time fields (such as `ChronoField#YEAR`) |
| <span id="ac4ad0-hour-of-day"></span> `hour-of-day` | `VALUE` | `Long` |   | Set an explicit value for one of the time fields (such as `ChronoField#YEAR`) |
| <span id="a38714-millisecond"></span> `millisecond` | `VALUE` | `Long` |   | Set an explicit value for one of the time fields (such as `ChronoField#YEAR`) |
| <span id="a60f39-minute"></span> `minute` | `VALUE` | `Long` |   | Set an explicit value for one of the time fields (such as `ChronoField#YEAR`) |
| <span id="a35611-month"></span> `month` | `VALUE` | `Long` |   | Set an explicit value for one of the time fields (such as `ChronoField#YEAR`) |
| <span id="ab277a-second"></span> `second` | `VALUE` | `Long` |   | Set an explicit value for one of the time fields (such as `ChronoField#YEAR`) |
| <span id="a0750a-shift-by-seconds"></span> `shift-by-seconds` | `VALUE` | `Long` | `0` | Configure a time-shift in seconds, to move the current time to past or future |
| <span id="ac3ba5-time-zone"></span> `time-zone` | `VALUE` | `ZoneId` |   | Override current time zone |
| <span id="a853ba-year"></span> `year` | `VALUE` | `Long` |   | Set an explicit value for one of the time fields (such as `ChronoField#YEAR`) |

See the [manifest](../config/manifest.md) for all available types.
