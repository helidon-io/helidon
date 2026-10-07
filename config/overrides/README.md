# Config Overrides

This optional module provides immutable filters that replace existing configuration value nodes using wildcard
expressions or regular expressions. Add the `io.helidon.config:helidon-config-overrides` dependency and register a filter
or filter factory explicitly. Adding the dependency does not discover or activate a filter automatically.

Core overrides remain available during staged deprecation. Removal timing is undecided. The replacement participates
in ordinary filter ordering rather than the separate core overrides stage; verify key-token expansion, value-resolution
order, expression priority, and reload behavior before migrating. Filters do not add missing nodes. Ordinary source
precedence can add nodes and does not interpret these wildcard expressions.

## Fixed programmatic rules

```java
Config config = Config.builder()
        .addFilter(OverrideConfigFilter.builder()
                .putOverrideExpression("prod.abcdef.logging.level", "FINEST")
                .putOverrideExpression("prod.*.logging.level", "WARNING")
                .putOverrideExpression("test.*.logging.level", "FINE")
                .build())
        .build();
```

The first matching rule wins. The wildcard `*` becomes `\w+`, matching one or more word characters; a dot is escaped.
For example, `prod.*.logging.level` matches `prod.abcdef.logging.level` but not `prod.my-pod.logging.level`.
This retains the legacy wildcard conversion rather than implementing a general glob language. Use `putOverridePattern`
for explicit Java regular expressions. Other regular expression metacharacters in expressions retain their meaning.
Replacement values and compiled patterns are fixed for the life of the filter.

The builder's `addConfigSource` option loads definition sources once when the filter is constructed. Programmatic
regular expression patterns precede wildcard expressions, which precede source definitions. The private definition
configuration disables environment variables, system
properties and filter discovery; background change support started for it is stopped after the snapshot is captured.
Do not use this option for continuously changing override definitions.

## Rules in the target configuration

Register the factory so each target configuration generation receives its own immutable rules:

```java
Config config = Config.builder()
        .addFilter(OverrideConfigFilter.fromConfig())
        .build();
```

The target's sources can contain:

```yaml
overrides.expressions:
  "prod.abcdef.logging.level": "FINEST"
  "prod.*.logging.level": "WARNING"
  "test.*.logging.level": "FINE"
```

Target reloads capture a new rule snapshot. Existing `Config` generations keep their original rules, including values
first accessed after a reload. Registering a fixed filter with `addFilter(ConfigFilter)` instead keeps fixed rules across
target reloads.

## Independently changing definitions

Create a separate, caller-owned definition configuration and register its factory:

```java
Config definitions = Config.builder()
        .addSource(ConfigSources.file("overrides.properties"))
        .disableEnvironmentVariablesSource()
        .disableSystemPropertiesSource()
        .disableFilterServices()
        .build();

Config target = Config.builder()
        .addFilter(OverrideConfigFilter.fromConfig(definitions))
        .build();

try (var changes = OverrideConfigFilter.connect(definitions, target)) {
    // Keep the connection alive while the application uses target.context().last().
}
```

The definitions contain expressions directly, without the `overrides.expressions` prefix:

```properties
prod.abcdef.logging.level = FINEST
prod.*.logging.level = WARNING
test.*.logging.level = FINE
```

Configure polling, watching, or event support on the definition sources when automatic change detection is required.
The factory captures `definitions.context().last()` for each target rebuild. The connection asynchronously reloads
the target after definition changes and schedules initial reconciliation to cover changes during target construction.
Changes can be coalesced; listeners observe target generations after reload, rather than immediate mutation of an
existing generation. A fixed `OverrideConfigFilter.create(definitions)` captures only the supplied snapshot and does
not follow changes.

Definition and target configurations must be independent trees. Do not connect a configuration to itself, use a node
from the target as its definitions, or introduce indirect dependency cycles. Close the connection when no longer needed;
an already admitted reload may complete after close. The connection borrows both configurations and does not stop their
background change support. The caller remains responsible for stopping that support when each configuration is no longer
needed, using `context().stopChangeSupport()`.

This module does not change core Config APIs or remove legacy overrides. Applications should validate migration against
their filtering, ordering, wildcard matching and notification requirements before switching.
