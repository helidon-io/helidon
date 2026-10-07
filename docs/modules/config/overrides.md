<!--@frontmatter
description: "Optional Helidon Config overrides filter and migration"
-->
# Config Overrides Filter

## Overview

The optional `helidon-config-overrides` module replaces values of existing
configuration nodes using wildcard expressions or Java regular expressions.
It uses the ordinary Config filter API. Add the dependency and register a filter
or filter factory explicitly; the dependency alone does not activate overrides
through service discovery or the service registry.

```xml
<dependency>
    <groupId>io.helidon.config</groupId>
    <artifactId>helidon-config-overrides</artifactId>
</dependency>
```

The examples use `io.helidon.config.overrides.OverrideConfigFilter`, a public
type distinct from the legacy implementation in core Config.

Core overrides remain available with unchanged behavior during staged
deprecation. Their APIs and SPI are marked `forRemoval=true` since Helidon
28.0.0; removal timing remains undecided. Core logs a secret-free warning once
per runtime when a legacy rule first matches. Share migration requirements in
[issue #10415](https://github.com/helidon-io/helidon/issues/10415); lack of feedback
does not establish that the feature has no users.

## Fixed Rules

Build an immutable filter when the replacement values do not need to change:

```java
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.overrides.OverrideConfigFilter;

Config target = Config.builder()
        .addSource(ConfigSources.file("application.properties"))
        .addFilter(OverrideConfigFilter.builder()
                .putOverrideExpression("prod.primary.logging.level", "FINEST")
                .putOverrideExpression("prod.*.logging.level", "WARNING")
                .build())
        .build();
```

The first matching rule wins. Matching is case-sensitive and covers the whole
configuration key. The wildcard `*` becomes `\w+`, matching one or more word
characters; a dot is escaped. For example, `prod.*.logging.level` matches
`prod.primary.logging.level`, but not `prod.my-pod.logging.level`. Other regular
expression metacharacters are not escaped, so these expressions are not a
general glob language. Use `putOverridePattern(Pattern, String)` for explicit
Java regular expressions.

Rules replace values only on existing nodes; they do not add missing nodes.
The rules and compiled patterns are fixed for the life of the filter, including
when the target reloads. Register more specific expressions before broader
expressions. Explicit patterns precede wildcard expressions in the builder's
combined rules, even if calls to the two methods are interleaved. Programmatic
rules precede definitions loaded with `addConfigSource`.

The builder's `addConfigSource` option loads definition sources once. The private
definition configuration disables environment variables, system properties
and filter discovery, and stops its background change support after capturing
the snapshot. Use a caller-owned definition configuration for changing rules.
Definitions loaded from configuration are traversed as a configuration map;
do not assume their order is the line order of a legacy overrides document.
Avoid overlapping rules or verify their priority before migrating.

## Rules in the Target Configuration

Register a factory to capture new immutable rules for each target generation:

```java
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.overrides.OverrideConfigFilter;

Config target = Config.builder()
        .addSource(ConfigSources.file("application.properties"))
        .addFilter(OverrideConfigFilter.fromConfig())
        .build();
```

The target source can define the expressions under `overrides.expressions`:

```properties
overrides.expressions.prod.primary.logging.level = FINEST
overrides.expressions.prod.*.logging.level = WARNING
prod.primary.logging.level = INFO
prod.secondary.logging.level = INFO
```

Target reloads capture a new rule snapshot. Earlier `Config` generations retain
their original rules, including values first accessed after a reload. Use
`target.context().last()` when reading the latest generation. Configure source
polling, watching or event support if the target should reload automatically.

## Independently Changing Definitions

A separate configuration can load and monitor replacement definitions. Register
its filter factory on the target, then connect definition change notifications
to target reloads:

```java
import java.time.Duration;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.PollingStrategies;
import io.helidon.config.overrides.OverrideConfigFilter;

Config definitions = Config.builder()
        .addSource(ConfigSources.file("overrides.properties")
                           .pollingStrategy(PollingStrategies.regular(Duration.ofSeconds(2))))
        .disableEnvironmentVariablesSource()
        .disableSystemPropertiesSource()
        .disableFilterServices()
        .disableValueResolving()
        .build();
try {
    Config target = Config.builder()
            .addSource(ConfigSources.file("application.properties"))
            .addFilter(OverrideConfigFilter.fromConfig(definitions))
            .build();
    try {
        try (var changes = OverrideConfigFilter.connect(definitions, target)) {
            // Run the application here, reading target.context().last().
        }
    } finally {
        target.context().stopChangeSupport();
    }
} finally {
    definitions.context().stopChangeSupport();
}
```

This example disables value resolution on the definitions so references in
replacement values can be resolved against the target instead. The file
contains expressions directly, without the `overrides.expressions` prefix:

```properties
prod.primary.logging.level = FINEST
prod.*.logging.level = WARNING
```

`fromConfig(definitions)` captures `definitions.context().last()` whenever the
target rebuilds. The factory alone does not trigger a target reload after a
definition change. `connect(definitions, target)` subscribes to definition
changes and schedules an initial reconciliation, covering changes during target
construction. Reloads run asynchronously on the common pool and can coalesce
multiple changes. Target listeners receive new generations when effective
values change; an existing generation is not mutated in place.

Keep the returned connection alive for the required application lifetime.
Closing it stops forwarding changes and releases its reference to the target.
A reload already admitted can finish after close. The connection borrows both
configurations and does not stop their polling or watching; the application
must stop change support on each configuration it owns, as shown above.
Definition and target configurations must be independent trees. Do not connect
a configuration to itself, use a target subtree as definitions, or create
indirect dependency cycles.

`OverrideConfigFilter.create(definitions)` instead captures the supplied
snapshot once. It does not follow later changes or stop caller-owned change
support.

## Checking a Migration

Register the replacement before other custom filters that should see replaced
values. The default value-resolving filter runs after explicitly registered
filters, allowing `${...}` references in replacement values to use the target
configuration. Recheck this order if you register value resolution explicitly
or use other discovered filters. Unlike the separate legacy overrides stage,
this module participates in ordinary filter ordering. Also verify key-token
expansion, overlapping rule priority, cached values and change notifications
with your application sources.

Ordinary source precedence is a different migration option for known exact
keys. A higher-priority source can add nodes and does not interpret `*.host`
as a wildcard rule. Enumerate concrete keys and decide how newly added keys
should behave before replacing wildcard rules with ordinary sources.

Arbitrary predicate rules may require an application-specific `ConfigFilter`.
Keep using legacy overrides until the replacement preserves the semantics your
application needs; this module does not remove or disable the core feature.
