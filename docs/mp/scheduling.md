# Scheduling

## Overview

Scheduling is an essential feature for the Enterprise. Helidon has its own implementation of Scheduling functionality based on [Cron-utils](https://github.com/jmrozanec/cron-utils).

## Maven Coordinates

To enable Scheduling, add the following dependency to your project’s `pom.xml` (see [Managing Dependencies](../about/managing-dependencies.md)).

``` xml
<dependency>
    <groupId>io.helidon.microprofile.scheduling</groupId>
    <artifactId>helidon-microprofile-scheduling</artifactId>
</dependency>
```

## Usage

For scheduling tasks in Helidon you can choose from `@Scheduling.Cron` or `@Scheduling.FixedRate` annotations by required complexity of invocation interval. All you need is to define a method with one of the annotations in an application scoped bean.

### Fixed rate

For simple fixed rate invocation interval, the `@Scheduling.FixedRate` is the easiest way to schedule task invocation.

``` java
@Scheduling.FixedRate(delayBy = "PT5M", value = "PT10M")
```

Metadata like human-readable interval description or configured values are available through *FixedRateInvocation* injected as method parameter.

### Cron expression

For more complicated interval definition, cron expression can be leveraged with `@Scheduling.Cron` annotation.

``` java
@Scheduling.Cron(value = "0 15 8 ? * *", concurrent = false)
public void methodName() { /* ... */ }
```

### Cron expression

*Cron expression format*

``` text
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

Metadata like human-readable interval description or configured values are available through *CronInvocation* injected as method parameter.

``` java
@Scheduling.Cron("0 15 8 ? * *")
public void methodName(CronInvocation inv) {
    { /* ... */ }
}
```

## Configuration

`Scheduling` annotation properties can be overridden using `application.yaml` properties, if configured in the source code.

The following annotation options can use configuration "expression":

- `Scheduling.Fixed#delayBy()`
- `Scheduling.FixedRate#value()`
- `Scheduling.Cron#value()`

Configuration expressions is a reference to a configuration key, with optional default value:

`${config.key:default-value}`

### Fixed Rate

The Fixed rate annotation can have the delay by and value overridden using config.

*Annotation that allows config overrides*

``` java
@Scheduling.FixedRate(delayBy = "${app.schedule.cache.delay-by:PT5M}", value = "${app.schedule.cache.interval:PT10M}")
```

The default values are 5 minutes for delay-by, and 10 minutes for interval, and could be overridden:

*Overriding annotated values from config*

``` yaml
app:
  schedule:
    cache:
      delay-by: "PT10M"
      interval: "PT1H"
```

The configured values would be a 10-minute delay, with 1-hour interval.

### Cron

The Cron annotation can have the value overridden using config.

*Annotation that allows config overrides*

``` java
@Scheduling.Cron("${app.schedule.cache.cron:0 15 8 ? * *}")
```

The default value is an expression of `0 15 8 ? * *`.

*Overriding annotated values from config*

``` yaml
app:
  schedule:
    cache:
      cron: "* * * * * ?"
```

The configured values would be executing every 1 second.

## Examples

### Fixed rate

*Example of scheduling with fixed rate*

``` java
@Scheduling.FixedRate(delayBy = "PT5M", value = "PT10M")
public void methodName() {
    System.out.println("Every 10 minutes, first invocation 5 minutes after start");
}
```

### FixedRate Metadata Injection

*Example with invocation metadata*

``` java
@Scheduling.FixedRate(delayBy = "PT5M", value = "PT10M")
public void methodName(FixedRateInvocation inv) {
    System.out.println("Method invoked " + inv.description());
}
```

### Cron expression

*Example of scheduling with cron expression*

``` java
@Scheduling.Cron(value = "0 15 8 ? * *", concurrent = false)
public void methodName() {
    System.out.println("Executer every day at 8:15");
}
```

### Scheduled Metadata Injection.

*Example with invocation metadata*

``` java
@Scheduling.Cron("0 15 8 ? * *")
public void methodName(CronInvocation inv) {
    System.out.println("Method invoked " + inv.description());
}
```

## Reference

- [Cron-utils GitHub page](https://github.com/jmrozanec/cron-utils)
- [Helidon Scheduling JavaDoc](/apidocs/io.helidon.microprofile.scheduling/io/helidon/microprofile/scheduling/package-summary.html)
