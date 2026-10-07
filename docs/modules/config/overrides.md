<!--@frontmatter
description: "Optional Helidon Config overrides filter and migration"
-->
# Config Overrides Filter

## Overview

The optional `helidon-config-overrides` module replaces values of existing
configuration nodes using wildcard expressions or Java regular expressions.
Its filter provider is discovered automatically by Java service loader or the
service registry. Add the dependency and configure `overrides` in your initial
configuration. Disabling filter services also disables automatic discovery of
this provider.

```xml
<dependency>
    <groupId>io.helidon.config</groupId>
    <artifactId>helidon-config-overrides</artifactId>
</dependency>
```

The legacy core overrides APIs and SPI are marked `forRemoval=true` since
Helidon 28.0.0 in favor of this optional filter module. Overrides functionality
is being replaced, not discontinued. Legacy overrides remain available with
unchanged behavior during staged migration; no removal release or timing has
been decided. Core logs a secret-free warning once per runtime when a legacy
rule first matches. Share migration requirements in
[issue #10415](https://github.com/helidon-io/helidon/issues/10415); lack of feedback
does not establish that the feature has no users.

## Automatic Configuration

For example, `application.properties` can contain:

```properties
overrides.expressions.prod.primary.logging.level = FINEST
overrides.expressions.prod.*.logging.level = WARNING
prod.primary.logging.level = INFO
prod.secondary.logging.level = INFO
```

Build the target as usual:

```java
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

Config target = Config.builder()
        .addSource(ConfigSources.file("application.properties"))
        .build();
```

The provider reads its configuration once from the initial unfiltered Config
view. Inline rules, definition source locations, and polling or watching settings
remain fixed for that Config runtime. To change these settings, build a new
Config runtime. Changing the contents of a monitored definition source instead
creates a new immutable rule snapshot and reloads the target.

## Independently Changing Definitions

Use `overrides.sources` to configure definition sources. The entries use the
ordinary Config source descriptor format. For example, with a YAML config
parser available, the target configuration can contain:

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
prod:
  primary:
    logging:
      level: INFO
  secondary:
    logging:
      level: INFO
```

The definition file contains expressions directly, without an
`overrides.expressions` prefix:

```properties
prod.primary.logging.level = FINEST
prod.*.logging.level = WARNING
```

Each independently built Config runtime has its own factory, definition
configuration, and monitoring resources, even when discovery supplies a shared
provider. The factory captures the latest definition contents when creating
each generation's filter. Previous Config generations keep their original
rules, including values first read after a reload. Read `target.context().last()`
for the latest generation.

Definition changes request asynchronous target reloads; requests may coalesce.
Target listeners receive changed generations when effective configuration values
change. A definition change that affects no target values does not create a
value-change notification.

Call `target.context().stopChangeSupport()` when automatic updates are no longer
needed. This also stops the factory's definition monitoring. An admitted reload
may still finish. Manual reload remains usable after stopping change support
and does not restart monitoring.
It rebuilds from the latest source data already known to Config; it does not
force stopped file sources to reread their files. Build a new runtime if you
need to load new file contents after stopping monitoring.

## Manual Configuration

Manual provider registration is available through the builder. Supply fresh
definition sources so the same provider can safely configure independent Config
runtimes.

`buildProvider()` captures the builder's inline rules and suppliers at that
call. Each supplier is invoked once for each new Config runtime and must return
an independently usable source, including its polling or watching strategy.
Returning the same stateful source instance from multiple invocations can still
share monitoring state across runtimes.

For example:

```java
import java.time.Duration;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.PollingStrategies;
import io.helidon.config.overrides.OverrideConfigFilter;

Config target = Config.builder()
        .disableFilterServices()
        .addSource(ConfigSources.file("application.properties"))
        .addFilterProvider(OverrideConfigFilter.builder()
                .putOverrideExpression("prod.primary.logging.level", "FINEST")
                .addConfigSource(() -> ConfigSources.file("overrides.properties")
                        .pollingStrategy(PollingStrategies.regular(Duration.ofSeconds(2)))
                        .build())
                .buildProvider())
        .build();
```

For fixed rules without definition monitoring, `OverrideConfigFilter.builder()`
also supports `.build()` to produce an immutable filter for `addFilter(...)`.
Such a directly registered filter is reused across reloads. Config warns once
when direct filters coexist with reloads because mutable direct filters may
produce inconsistent state; an immutable fixed filter remains safe to reuse.

## Rule Matching and Migration

The first matching rule wins. Matching is case-sensitive and covers the whole
configuration key. The wildcard `*` becomes `\w+`, matching one or more word
characters; a dot is escaped. For example, `prod.*.logging.level` matches
`prod.primary.logging.level`, but not `prod.my-pod.logging.level`. Other regular
expression metacharacters retain their meaning, so these expressions are not a
general glob language. Use `putOverridePattern(Pattern, String)` for explicit
Java regular expressions.

Rules replace values only on existing nodes; they do not add missing nodes.
Explicit builder patterns precede wildcard expressions even if calls to the
two methods are interleaved. Programmatic rules precede definition source rules.
Definitions loaded as configuration maps do not necessarily preserve the line
order of a legacy overrides document. Avoid overlapping rules or verify their
priority before migrating.

Provider-created filters run before the default value-resolving filter, so
`${...}` references in replacement values can resolve against target values.
Legacy overrides use a separate stage before ordinary filters. Verify ordering
with other custom filters, key-token expansion, list keys, rule priority,
caching, and change notifications in your application; this module is not a
blanket drop-in replacement for every legacy source or predicate.

Ordinary source precedence is a different migration option for known exact
keys. A higher-priority source can add nodes and does not interpret `*.host`
as a wildcard rule. Enumerate concrete keys and decide how newly added keys
should behave before replacing wildcard rules with ordinary sources.

Arbitrary predicate rules may require an application-specific filter provider.
Keep using legacy overrides until migration preserves the behavior you require.
