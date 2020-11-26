Helidon Config API
_____________________

# Helidon Config 2.0

This section describes API changes between Helidon 1.x and Helidon 2.0 for Config

## Meta Configuration
Issue:  https://github.com/oracle/helidon/issues/1101
PR:     https://github.com/oracle/helidon/pull/1102

### API Changes

| Class             | Method            | Type | Description                |
| ----------------- | ----------------- | ---- | -------------------------- |
| `Config.Builder`  | `loadSourcesFrom` | remove | Replaced with methods on `MetaConfig` |
|                   | `builderLoadSourceFrom` | remove | Replaced with methods on `MetaConfig` |
|                   | `addSource`       | add  | Add a config source        |
|                   | `config(Config)`  | add  | Use meta configuration to configure the builder |
|                   | `metaConfig()`    | add  | Use meta configuration (located automatically) to configure the builder |
| `ConfigSources`   | `file(Path)`      | add  | Create a FileConfigSource builder from Path |
|                   | `load(Supplier...)` | remove | Replaced with methods on `MetaConfig` |
|                   | `load(Config)`    | remove | Replaced with methods on `MetaConfig` |
| `MetaConfig`      |                   | add  | A new class for all things related to meta configuration |
| `MetaConfigFinder`|                   | add  | Utility class to locate meta configuration and default configuration |
| `MetaProviders`   |                   | add  | Utility class to gather service loader implementations and to add built-ins |
| `ClasspathConfigSource`, `ClasspathOverrideSource`, `DirectoryConfigSource`, `FileConfigSource`, `FileOverrideSource`, `PrefixedConfigSource`, `UrlConfigSource`, `UrlOverrideSource`, `etcd.EtcdConfigSource`, `git.GitConfigSource` | | | |
|                   | `builder()`       | add  | Refactored to support builder pattern as rest of Helidon | 
| `.Builder`        | constructor       | mod  | No longer public, no parameters, usages replaced with `builder()` |
|                   | property setter   | add  | For each config source a property setter is added (`url`, `resource` etc.) |
|                   | `init` -> `config`| rename | Meta configuration method was renamed from `init` to `config` and made public |
|                   | `build()`         | behavior | Method can now throw an `IllegalArgumentException` - originally the constructor would fail with `NullPointerException` |
| `MapConfigSource` | constructor       | mod  | No long public |
|                   | `create`          | add  | Static factory methods |
| `spi.AbstractConfigSource`, `spi.AbstractParsableConfigSource`, `spi.AbstractSource`  | `init`        | mod  | Renamed to `config` and made public |
| `spi.ConfigSource`    | `create(Config)`  | remove | Moved to `MetaConfig` |
| `spi.ConfigSourceProvider`|           | add  | Java service loader service to support custom meta configurable sources |
| `spi.OverrideSource`  | `create`      | mod  | No longer throws an `IOException`, now throws `ConfigException` |
| `spi.OverrideSourceProvider` |        | add  | Java service loader service to support custom meta configurable override sources |
| `spi.PollingStrategyProvider` |       | add  | Java service loader service to support custom meta configurable polling strategies |
| `spi.RetryPolicy` | `create(Config)`  | remove | Moved to `MetaConfig` |
|                   | `get`             | remove | No longer implements `Supplier` |
| `spi.RetryPolicyProvider` |           | add  | Java service loader service to support custom meta configurable retry policies |

                    
# Other proposed features

## Source types
### Lazy config sources
We do not support config sources that require lazy access to values (using term "lazy sources" in the text)

_This may be sources that cannot list the keys, or where listing the keys is not feasible (speed, memory consumption etc.)_

To support such source types, we need to change approach to our configuration tree:
1. Each node created from known keys has to keep reference to all sources
2. Whenever a node value is requested, lazy sources must be queried for value (according to priority)
3. If a value is provided, it is cached forever

Other changes:
1. Notifications for changes can be provided only for cached keys
2. Config Source SPI will have to be updated, so a source can mark itself as lazy
3. Methods that traverse the tree (asMap, traverse, nodeList etc.) would only return
    the known key
    1. We must refactor our own usages of config to use direct key access wherever possible
    2. We should look into integrations to see if we can support lazy loading of configuration properties
        1. Jersey
        2. Weld
4. Behavior must be clearly documented

## Mutable sources with no notification support
Some of our config source are mutable, yet do not support notifications.
We should change all config sources to support notifications if so chosen by the user.
If need be, these should be polled regularly and compared with previous version. 

Currently known sources that should be refactored:
1. System properties 

### Change support
Current change support is too complex and uses APIs not suitable for the purpose:
1. Flow API should not be used in Config API
2. method "onChange" should be "onChange(Consumer<Config>)" - threadContext expects a function with undefined behavior for returned 
    boolean
3. Remove dependency on SubmissionPublisher (and on project Reactor transitively)

## Polling strategies
1. Check if these can be simplified, as threadContext API and SPI is not easy to use.
2. Make sure one thing can be achieved only one way - e.g why do we have
    polling and watching both available for File config sources?
    
## File Detector SPI
Currently the FileDetector service does not work consistently in all environments.
Known problems:
1. when running JDK9+ using maven exec plugin (test with yaml config)
2. when running in some docker images (need to find the failing image)

## Source is Supplier
Currently the ConfigSource interface extends Supplier and default implementation
of the `get()` method returns `this`.
Reason behind this (probably) is to have a single set of methods on `Builder`, that
only accept `Supplier<ConfigSource>` and you can send in either a `Builder<? extends ConfigSource`
or an actual instance of a `ConfigSource`.

This is confusing and it is hard to clearly understand the behavior of such methods.

We should introduce builder methods for `ConfigSource` instances and remove the `Supplier` from
`ConfigSource` interface.  
