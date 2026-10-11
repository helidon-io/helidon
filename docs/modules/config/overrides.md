<!--@frontmatter
description: "Optional Helidon Config overrides filter"
-->
# Config Overrides Filter

## Overview

The optional `helidon-config-overrides` module replaces values of existing
configuration nodes using wildcard expressions or Java regular expressions.
Add the dependency to enable automatic discovery of the filter provider:

```xml
<dependency>
    <groupId>io.helidon.config</groupId>
    <artifactId>helidon-config-overrides</artifactId>
</dependency>
```

Configure the provider under `overrides` in your application configuration,
or register it explicitly with the Config builder.

## Automatic Configuration

Suppose an application defines a batch size for its test and production
environments. Keep the concrete application keys in `application.properties`
and point the filter at a separate override definition file:

```properties [application.properties]
environments.test.batch-size = 50
environments.prod.batch-size = 100
overrides.sources.0.type = file
overrides.sources.0.properties.path = overrides.properties
```

Put the wildcard rule in `overrides.properties`:

```properties [overrides.properties]
environments.*.batch-size = 200
```

The application file selects the definition source using ordinary Config keys.
The separate file holds expressions that the overrides filter interprets as
matching rules. Ordinary configuration sources do not expand wildcard keys.

Build the configuration as usual:

```java
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

Config target = Config.builder()
        .addSource(ConfigSources.file("application.properties"))
        .build();

assert target.get("environments.test.batch-size").asInt().get() == 200;
assert target.get("environments.prod.batch-size").asInt().get() == 200;
```

| Application key | Source value | Value with the override |
| --- | --- | --- |
| `environments.test.batch-size` | `50` | `200` |
| `environments.prod.batch-size` | `100` | `200` |

A matching key must already exist. The rule does not create
`environments.staging.batch-size` when that environment is absent.

You can also configure wildcard rules inline:

```properties
overrides.expressions.environments.*.batch-size = 200
```

Inline rules, definition source locations, and polling or watching settings
are read when the configuration is built. Build a new configuration to change
these settings. Monitored definition files can update their rules as described
below.

## Monitoring Definition Sources

Use ordinary Config source descriptors under `overrides.sources` to enable
polling or watching. For example, with a YAML config parser available:

```yaml
overrides:
  sources:
    - type: file
      properties:
        path: overrides.properties
        polling-strategy:
          type: regular
          properties:
            interval: PT2S
environments:
  test:
    batch-size: 50
  prod:
    batch-size: 100
```

The definition file contains expressions directly, without an
`overrides.expressions` prefix:

```properties
environments.*.batch-size = 200
```

Definition changes reload the application configuration asynchronously.
Use `onChange(...)` to receive updated configurations when effective values
change, or `target.context().last()` to obtain the latest configuration.
Previous Config instances retain their original rules. Rapid definition
changes may be combined into one reload.

Call `target.context().stopChangeSupport()` to stop automatic updates, including
definition monitoring. Manual reload remains available afterwards, using the
latest source data already known to Config. It does not restart monitoring or
force stopped file sources to reread their files.

## Manual Configuration

Register an `OverrideConfigFilterProvider` through the Config builder:

```java
import java.time.Duration;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.PollingStrategies;
import io.helidon.config.overrides.OverrideConfigFilterProvider;

Config target = Config.builder()
        .disableFilterServices()
        .addSource(ConfigSources.file("application.properties"))
        .addFilterProvider(OverrideConfigFilterProvider.builder()
                .putOverrideExpression("environments.prod.batch-size", "150")
                .addConfigSource(() -> ConfigSources.file("overrides.properties")
                        .pollingStrategy(PollingStrategies.regular(Duration.ofSeconds(2)))
                        .build())
                .build())
        .build();
```

`disableFilterServices()` disables automatic discovery of all filters, so
register any other filters your application needs explicitly as well.
Use a supplier that creates a fresh definition source, as in this example,
when a provider may be reused to build multiple configurations.

For inline-only rules, omit `addConfigSource(...)`:

```java
import io.helidon.config.overrides.OverrideConfigFilterProvider;

var provider = OverrideConfigFilterProvider.builder()
        .putOverrideExpression("environments.prod.batch-size", "150")
        .putOverrideExpression("environments.*.batch-size", "200")
        .build();
```

Here the specific rule sets the production batch size to `150`, while the
wildcard sets the test batch size to `200`.

The provider builder also accepts a configuration component through
`.config(application.get("overrides"))`. The component supports `expressions`
(a wildcard expression/value map), `patterns` (an ordered list of regular
expression rules with `pattern` and `value` settings), and `sources`
(ordinary source descriptors).

For example, configure regular expression rules in priority order:

```yaml
overrides:
  patterns:
    - pattern: 'environments\.prod\.batch-size'
      value: '150'
    - pattern: 'environments\.[^.]+\.batch-size'
      value: '200'
```

The production rule is tested first, then the broader rule.

## Rule Matching

The first matching rule wins. Matching is case-sensitive and covers the whole
configuration key. The wildcard `*` matches one or more word characters; a dot
is treated literally. For example, `environments.*.batch-size` matches
`environments.prod.batch-size`, but not `environments.pre-prod.batch-size`.
Other regular expression metacharacters retain their meaning, so these
expressions are not a general glob language.

For explicit Java regular expressions, use the ordered `patterns` configuration
shown above, or register patterns with the provider builder:

```java
import java.util.regex.Pattern;

import io.helidon.config.overrides.OverrideConfigFilterProvider;

var provider = OverrideConfigFilterProvider.builder()
        .addOverridePattern(rule -> rule
                .pattern(Pattern.compile("environments\\.prod\\.batch-size"))
                .value("150"))
        .addOverridePattern(rule -> rule
                .pattern(Pattern.compile("environments\\.[^.]+\\.batch-size"))
                .value("200"))
        .build();
```

Explicit patterns precede wildcard expressions. Builder rules precede
definition source rules. The configured regex list preserves its rule order;
expression maps and definition source maps do not guarantee document order.
Use non-overlapping expressions in maps. When overlapping rules need a
specific priority, use ordered builder calls or a configured pattern list.

Replacement values can contain `${...}` references to other configuration
values, using Config's normal value resolution.

Overrides apply after source merging and only replace existing nodes. A rule
supplied by a lower-priority source can therefore replace a value supplied by
a higher-priority source. Ordinary source precedence can add nodes and does
not interpret wildcard rules.

Every source that supplies `overrides` settings or definition rules must be
trusted to replace all matching values. If some application sources do not
have that authority, use `disableFilterServices()` and register an
`OverrideConfigFilterProvider` configured explicitly from trusted settings.
