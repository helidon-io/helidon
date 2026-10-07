# Config Overrides Filter

The optional `io.helidon.config:helidon-config-overrides` module replaces values
of existing configuration nodes using wildcard expressions or regular
expressions. Its provider is discovered automatically through Java service
loader or the service registry. Configure inline rules under
`overrides.expressions`, or independent definition sources under
`overrides.sources` using ordinary Config source descriptors.

Each Config runtime owns a separate filter factory and its definition monitoring.
The initial override settings are fixed for that runtime; monitored definition
contents can change. Each initial build or reload creates an immutable rule
snapshot, so previous Config generations retain their original values. Stop
automatic monitoring with `config.context().stopChangeSupport()`; manual reload
remains available afterwards. Reload rebuilds from the source data already
known to Config; it does not force stopped file sources to reread their files.

For manual setup, use the builder and register a provider:

```java
Config config = Config.builder()
        .disableFilterServices()
        .addSource(ConfigSources.file("application.properties"))
        .addFilterProvider(OverrideConfigFilter.builder()
                .putOverrideExpression("prod.primary.logging.level", "FINEST")
                .putOverrideExpression("prod.*.logging.level", "WARNING")
                .buildProvider())
        .build();
```

Use fresh-source suppliers with `addConfigSource(...)` when configuring dynamic
definitions manually. The provider can then create independent monitoring
resources for multiple Config runtimes. `buildProvider()` captures inline rules
and supplier references; each supplier is called once per runtime and must
return an independent source and its monitoring strategy. A supplier returning
the same stateful source can still share monitoring state. The builder's
`.build()` instead creates a fixed immutable filter for `addFilter(...)`.

The first matching rule wins, with explicit regular-expression patterns before
wildcard expressions and programmatic rules before definition source rules.
Wildcard `*` matches one or more regex word characters, dots are escaped, and
other regex metacharacters retain their meaning. Rules cannot create missing
nodes. Configuration map traversal need not preserve the order of lines in a
legacy overrides document; verify overlapping rule priority.

Provider-created filters run before default value-reference resolution. Verify
custom filter ordering, key-token expansion, list keys, caching and change
notifications when migrating. Ordinary source precedence is not equivalent:
it can create nodes and does not interpret wildcard rules. Arbitrary predicate
rules may require an application-specific filter provider.

Legacy core overrides APIs are deprecated for removal in favor of this optional
module. Overrides functionality is being replaced, not discontinued; legacy
behavior remains available, and removal timing has not been decided.

See the [Config Overrides Filter guide](../../docs/modules/config/overrides.md)
for automatic configuration, monitoring and migration examples.
