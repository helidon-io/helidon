# Overview

## Overview

Scheduling is an essential feature for the Enterprise. Helidon has its own implementation of Scheduling functionality based on [Cron-utils](https://github.com/jmrozanec/cron-utils).

## Maven Coordinates

To enable Scheduling, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../about/managing-dependencies.md)).

```xml
<dependency>
    <groupId>io.helidon.scheduling</groupId>
    <artifactId>helidon-scheduling</artifactId>
</dependency>
```

## Usage

For scheduling periodic tasks, it is possible to choose a fixed rate or a Cron expression.

### Fixed rate

*Scheduling with fixed rate using `Scheduling.fixedRate()` builder.*

```java
FixedRate.builder()
        .delay(10)
        .initialDelay(5)
        .timeUnit(TimeUnit.MINUTES)
        .task(inv -> System.out.println("Every 10 minutes, first invocation 5 minutes after start"))
        .build();
```

Metadata like human-readable interval description or configured values are available through FixedRateInvocation provided as task parameter.

*Invocation metadata*

```java
FixedRate.builder()
        .delay(10)
        .task(inv -> System.out.println("Method invoked " + inv.description()))
        .build();
```

#### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a12d22-delay-by"></span> `delay-by` | `VALUE` | `Duration` | `PT0S` | Initial delay of the first invocation |
| <span id="a394d5-delay-type"></span> [`delay-type`](../config/io_helidon_scheduling_FixedRate_DelayType.md) | `VALUE` | `i.h.s.F.DelayType` | `SINCE_PREVIOUS_START` | Configure whether the interval between the invocations should be calculated from the time when previous task started or ended |
| <span id="a39c2a-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether the task is enabled |
| <span id="a27a1a-id"></span> `id` | `VALUE` | `String` |   | Identification of the started task |
| <span id="a4b0e9-interval"></span> `interval` | `VALUE` | `Duration` |   | Fixed interval between each invocation |

##### Deprecated Options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a44d13-delay"></span> `delay` | `VALUE` | `Long` |   | Fixed rate delay between each invocation |
| <span id="a91a8f-initial-delay"></span> `initial-delay` | `VALUE` | `Long` |   | Initial delay of the first invocation |
| <span id="a776bc-time-unit"></span> [`time-unit`](../config/java_util_concurrent_TimeUnit.md) | `VALUE` | `TimeUnit` | `TimeUnit.SECONDS` | `java.util.concurrent.TimeUnit TimeUnit` used for interpretation of values provided with `io.helidon.scheduling.FixedRateConfig.Builder#delay(long)` and `io.helidon.scheduling.FixedRateConfig.Builder#initialDelay(long)` |

### Cron

For more complicated interval definition, Cron expression can be leveraged with `Scheduling.cron()` builder.

*Scheduling with Cron expression*

```java
Cron.builder()
        .expression("0 15 8 ? * *")
        .task(inv -> System.out.println("Executer every day at 8:15"))
        .build();
```

#### Timezone Configuration

By default, Cron expressions are evaluated using the system’s default timezone. You can specify a custom timezone to control when the cron expression triggers, regardless of the system’s timezone.

*Scheduling with custom timezone*

```java
Cron.builder()
        .expression("0 0 9 * * ?")
        .zone(ZoneId.of("America/New_York"))
        .task(inv -> System.out.println("Executes every day at 9:00 AM Eastern Time"))
        .build();
```

The timezone determines when the cron expression triggers. For example, a cron expression `0 0 9 * * ?` (every day at 9:00 AM) with zone `America/New_York` will trigger at 9:00 AM Eastern Time, regardless of the server’s timezone setting.

#### Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="aa0a55-concurrent"></span> `concurrent` | `VALUE` | `Boolean` | `true` | Allow concurrent execution if previous task didn't finish before next execution |
| <span id="ac430f-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | Whether the task is enabled |
| <span id="a78a5d-expression"></span> `expression` | `VALUE` | `String` |   | Cron expression for specifying period of execution |
| <span id="acc650-id"></span> `id` | `VALUE` | `String` |   | Identification of the started task |
| <span id="a0e1a4-zone"></span> `zone` | `VALUE` | `ZoneId` |   | Time zone to use for cron expression evaluation |

### Cron expression syntax

Cron expressions should be configured as follows.

### Cron expression

*Cron expression format*

```
<seconds> <minutes> <hours> <day-of-month> <month> <day-of-week> <year>
```

| Order | Name | Supported values | Supported field format | Optional |
|----|----|----|----|----|
| 1 | seconds | 0-59 | CONST, LIST, RANGE, WILDCARD, INCREMENT | false |
| 2 | minutes | 0-59 | CONST, LIST, RANGE, WILDCARD, INCREMENT | false |
| 3 | hours | 0-23 | CONST, LIST, RANGE, WILDCARD, INCREMENT | false |
| 4 | day-of-month | 1-31 | CONST, LIST, RANGE, WILDCARD, INCREMENT, ANY, LAST, WEEKDAY | false |
| 5 | month | 1-12 or JAN-DEC | CONST, LIST, RANGE, WILDCARD, INCREMENT | false |
| 6 | day-of-week | 1-7 or SUN-SAT | CONST, LIST, RANGE, WILDCARD, INCREMENT, ANY, NTH, LAST | false |
| 7 | year | 1970-2099 | CONST, LIST, RANGE, WILDCARD, INCREMENT | true |

Cron expression fields

| Name | Regex format | Example | Description |
|----|----|----|----|
| CONST | \d+ | 12 | exact value |
| LIST | \d+,\d+(,\d+)\* | 1,2,3,4 | list of constants |
| RANGE | \d+-\d+ | 15-30 | range of values from-to |
| WILDCARD | \\ | \* | all values withing the field |
| INCREMENT | \d+\\\d+ | 0/5 | initial number / increments, 2/5 means 2,7,9,11,16,…​ |
| ANY | \\ | ? | any day(apply only to day-of-week and day-of-month) |
| NTH | \\ | 1#3 | nth day of the month, 2#3 means third monday of the month |
| LAST | \d\*L(+\d+\|\\\d+)? | 3L-3 | last day of the month in day-of-month or last nth day in the day-of-week |
| WEEKDAY | \\ | 1#3 | nearest weekday of the nth day of month, 1W is the first monday of the week |

Field formats

| Cron expression      | Description           |
|----------------------|-----------------------|
| \* \* \* \* \* ?     | Every second          |
| 0/2 \* \* \* \* ? \* | Every 2 seconds       |
| 0 45 9 ? \* \*       | Every day at 9:45     |
| 0 15 8 ? \* MON-FRI  | Every workday at 8:15 |

Examples

Metadata like human-readable interval description or configured values are available through CronInvocation provided as task parameter.

## Configuration

Scheduling is configurable with [Helidon Config](../se/config/introduction.md).

*Example of configuring*

```java
FixedRate.builder()
        .config(Config.create(() -> ConfigSources.create(
                """
                        delay: 4
                        delay-type: SINCE_PREVIOUS_END
                        initial-delay: 1
                        time-unit: SECONDS
                        """,
                MediaTypes.APPLICATION_X_YAML)))
        .task(inv -> System.out.println("Every 4 minutes, first invocation 1 minutes after start"))
        .build();
```

## Task Management

A `io.helidon.scheduling.TaskManager` can be used to manage tasks that are started within Helidon. When using imperative programming model, you can either provide a custom implementation of this interface to task builder (method `taskManager`), or you can use the "default" one that can be obtained by invoking `io.helidon.service.registry.Services.get(TaskManager.class)`. When using the default `TaskManager` from `io.helidon.service.registry.Services`, there is no need to explicitly register it with the task builders.

When using declarative programming model, the `TaskManager` can be injected. It is a `Singleton` service that will be used by all scheduled tasks in the current application.

## Examples

### Fixed Rate Example

For simple fixed rate invocation use .

*Example of scheduling with fixed rate using `FixedRate.builder()` builder.*

```java
FixedRate.builder()
        .delay(10)
        .initialDelay(5)
        .timeUnit(TimeUnit.MINUTES)
        .task(inv -> System.out.println("Every 10 minutes, first invocation 5 minutes after start"))
        .build();
```

Metadata like human-readable interval description or configured values are available through `FixedRateInvocation` provided as task parameter.

*Example with invocation metadata*

```java
FixedRate.builder()
        .delay(10)
        .task(inv -> System.out.println("Method invoked " + inv.description()))
        .build();
```

## Reference

- [Cron-utils GitHub page](https://github.com/jmrozanec/cron-utils)
- [Helidon Scheduling JavaDoc](/apidocs/io.helidon.microprofile.scheduling/io/helidon/microprofile/scheduling/package-summary.html)
