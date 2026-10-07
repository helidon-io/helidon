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

If your application uses `Config.Builder.overrides(...)`, `OverrideSources`,
or `OverrideSource`, follow [Migrating from Config Overrides](#migrating-from-config-overrides).

## Automatic Configuration

For example, `application.properties` can contain:

```properties
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

Both logging levels are now `WARNING`. A matching key must already exist:
the filter does not add another service or logging-level node.

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

## Rule Matching

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
Rules loaded from configuration maps do not have a guaranteed document order.
Use non-overlapping expressions in definition files and inline configuration.
When priority between overlapping rules matters, register them in order using
the builder, as described in the migration guide below.

Provider-created filters run before the default value-resolving filter, so
`${...}` references in replacement values can resolve against target values.
They run after legacy overrides if both mechanisms are configured. Remove the
legacy registration when migrating the same rules so they are not applied twice.

## Migrating from Config Overrides

The legacy core overrides APIs and SPI are marked `forRemoval=true` since
Helidon 28.0.0 in favor of this optional filter module. They remain available
with their existing behavior during migration; no removal version has been
decided. Using these APIs produces Java compiler removal warnings, which can
affect builds that treat warnings as errors. Core also logs a deprecation warning
once per runtime when a legacy rule first matches, without configuration keys
or values in the warning.

The filter preserves wildcard replacement of existing values and supports
updates from independently monitored definition sources. Migration still
requires checking rule order, file parsing, and any custom source or predicate.

### Replace the Legacy Registration

Add the `helidon-config-overrides` dependency shown above. For automatic setup,
put rules under `overrides.expressions` or source descriptors under
`overrides.sources` in the application's configuration, as shown in
[Automatic Configuration](#automatic-configuration) and
[Independently Changing Definitions](#independently-changing-definitions).
Remove the corresponding legacy `.overrides(...)` registration.

For code that configures an external definition file explicitly, replace this
legacy registration:

```java
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.OverrideSources;

Config target = Config.builder()
        .addSource(ConfigSources.file("application.properties"))
        .overrides(OverrideSources.file("overrides.properties"))
        .build();
```

with a filter provider built using the same file:

```java
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.overrides.OverrideConfigFilter;

Config target = Config.builder()
        .disableFilterServices()
        .addSource(ConfigSources.file("application.properties"))
        .addFilterProvider(OverrideConfigFilter.builder()
                .addConfigSource(() -> ConfigSources.file("overrides.properties").build())
                .buildProvider())
        .build();
```

This example disables automatic filter discovery to use only the explicit
registration. If your application relies on other discovered filters, keep
discovery enabled and avoid configuring the same override rules through both
the automatically discovered provider and the explicit provider.

Other common registrations map as follows:

| Legacy registration | Filter configuration |
| --- | --- |
| `OverrideSources.classpath(resource)` or `OverrideSources.url(url)` | Supply the corresponding `ConfigSources.classpath(resource)` or `ConfigSources.url(url)` through `addConfigSource(...)`. |
| `OverrideSources.create(map)` | Add entries with `putOverrideExpression(expression, value)`, then call `buildProvider()`. Preserve intentional priority when iterating the map. |
| `OverrideSources.empty()` | Omit the override rules or provider registration. |
| Custom `OverrideSource` or predicate entries | Provide a `ConfigSource` of expression/value definitions, or implement a `ConfigFilterProvider` for behavior that cannot be expressed as these rules. There is no automatic adapter for arbitrary predicates. |

### Move Meta-configuration into Application Configuration

The legacy `override-source` entry belongs to Config's meta-configuration.
The new provider reads `overrides.sources` from the **initial application
configuration**. Renaming the entry in the meta-configuration is not sufficient.

For example, replace this legacy meta-configuration entry:

```yaml
override-source:
  type: file
  properties:
    path: overrides.properties
```

with this entry in the application configuration:

```yaml
overrides:
  sources:
    - type: file
      properties:
        path: overrides.properties
```

Keep the meta-configuration's application sources. Move any polling, watching,
optional-source, or retry settings into the new source descriptor, using the
ordinary Config source format. Alternatively, register the provider explicitly
as above. Source descriptors and inline rules are captured once when the target
runtime is built; changes to the contents of monitored definition sources are
handled separately.

### Check Definition Parsing and Rule Priority

External definition files contain expressions directly, without the
`overrides.expressions` prefix. The replacement reads them through ordinary
Config sources and parsers. Legacy file sources always parsed Java Properties;
the replacement chooses a parser by media type. Use a `.properties` file for
properties content, or specify its media type explicitly. A YAML definition
source needs YAML content and the YAML parser dependency.

The replacement also constructs a configuration tree. A legacy flat document
could contain both `service=one` and `service.level=two`; a properties Config
source rejects these because `service` cannot be both a value and an object.
Move such rules to the filter builder instead of loading them as a definition
Config. Check escaping and duplicate keys when reusing a legacy file.

Legacy override documents preserve the order of their entries and use the first
matching rule. The filter also uses the first match, but loading a document into
a Config map does not guarantee that its original order survives. Do not rely
on placing a specific rule before a broad rule in a properties file. Make the
expressions non-overlapping, or use ordered builder calls:

```java
import io.helidon.config.overrides.OverrideConfigFilter;

var provider = OverrideConfigFilter.builder()
        .putOverrideExpression("prod.primary.logging.level", "FINEST")
        .putOverrideExpression("prod.*.logging.level", "WARNING")
        .buildProvider();
```

Here the specific expression wins for `prod.primary.logging.level`. Explicit
regular-expression patterns take priority over wildcard expressions, and
builder rules precede source-loaded rules. The [wildcard rules](#rule-matching)
retain their existing boundaries: for example, `*` does not match a hyphen.

### Preserve Reload Behavior

If the legacy source used polling or watching, configure that support on the
new definition source as well; adding a file source alone does not enable it.
See the [automatic](#independently-changing-definitions) and
[builder](#manual-configuration) examples above.

As with legacy overrides, a definition update rebuilds the target configuration
and notifies listeners when effective values change. Old Config snapshots keep
their captured rules. The filter provider schedules rebuilds asynchronously and
may coalesce rapid changes; do not depend on receiving each intermediate file
version. Continue using `onChange(...)` or `context().last()` to obtain updated
Config snapshots. Stopping the target's change support also stops definition
monitoring.

Before switching, verify results with overlapping rules, key-token expansion,
list keys, `${...}` replacement values, and other custom filters. Legacy
overrides run before provider filters; provider filters run before ordinary
filters, including value resolution. Keep using legacy overrides for any
behavior your migration does not yet preserve. Share missing capabilities in
[issue #10415](https://github.com/helidon-io/helidon/issues/10415).

### When Ordinary Source Precedence Is Enough

Ordinary source precedence is a different migration option for known exact
keys. A higher-priority source can add nodes and does not interpret `*.host`
as a wildcard rule. Enumerate concrete keys and decide how newly added keys
should behave before replacing wildcard rules with ordinary sources.

For example, replace `*.host=deployment-host` with concrete entries such as
`database.host` and `service.host` only if you intend to maintain that list and
accept ordinary sources' ability to create those nodes. Check the complete
source order, including system properties and environment variables.
