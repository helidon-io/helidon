# Mutability Support

## Overview

An in-memory config tree, once loaded, is immutable, even though the data in the underlying config sources *can* change over time. The config system internally records which config sources it used to load each config tree and some metadata about the configuration. Your application can be aware of updates to the underlying config sources by:

1.  using the metadata the config system maintains,
2.  responding to change when the config sources are updated, or
3.  using `Supplier`s of particular config values to obtain the always-current value for a key.

## Using Config Metadata

### Loading Time

The config system records when it loads each configuration into memory. Your application can retrieve it by invoking the [timestamp method](/apidocs/io.helidon.config/io/helidon/config/Config.html#timestamp--) on any config node:

``` java
Instant loadTime = myConfig.timestamp();
```

### Config Context

The config system maintains a [`Config.Context`](/apidocs/io.helidon.config/io/helidon/config/Config.Context.html) for each `Config` node. Your application can retrieve the context by invoking the `Config.context()` method and then use it for these operations:

| Method | Usage |
|----|----|
| `Instant timestamp()` | Returns the load time of the last loaded configuration that used the context. |
| `Config last()` | Returns the most recently loaded configuration that used the context. |
| `Config reload()` | Reloads the entire config tree from the current contents of the same config sources used to load the tree in which the current node resides. |

Uses of `Config.Context`

Note that the config context describes or replaces a currently-loaded config tree. It by itself does not help your application decide *when* reloading the config might be useful.

## Responding to Changes in Config Sources

Although in-memory config trees do not change once loaded, applications can respond to change in the underlying config sources by:

1.  setting up change detection for the config sources used to build a configuration, and
2.  registering a response to be run when a source changes.

Your code’s response can react to the changes in whatever way makes sense for your application.

The following sections describe these steps in detail.

### Setting up Config Source Change Detection

When the application creates a config source, it can set up change detection for that source. This is called *polling* in the Helidon API but specific change detection algorithms might not use actual polling. You choose a specific [`PollingStrategy`](/apidocs/io.helidon.config/io/helidon/config/spi/PollingStrategy.html) for each config source you want to monitor. See the section on [polling strategies](extensions.md#Config-SPI-PollingStrategy) in the config extensions doc page for more information.

The config system provides some built-in polling strategies, exposed as these methods on the [`PollingStrategies`](/apidocs/io.helidon.config/io/helidon/config/PollingStrategies.html) class:

- `regular(Duration interval)` - a general-purpose scheduled polling strategy with a specified, constant polling interval.
- `watch(Path watchedPath)` - a filesystem-specific strategy to watch specified path. You can use this strategy with the `file` built-in config sources.
- `nop()` - a no-op strategy

This example builds a `Config` object from three sources, each set up with a different polling strategy:

*Build a `Config` with a different `PollingStrategy` for each config source*

``` java
Config config = Config.create(
        ConfigSources.file("conf/dev.properties")
                .pollingStrategy(PollingStrategies.regular(Duration.ofSeconds(2))) 
                .optional(),
        ConfigSources.file("conf/config.properties")
                .changeWatcher(FileSystemWatcher.create()) 
                .optional(),
        ConfigSources.file("my.properties")
                .pollingStrategy(PollingStrategies::nop)); 
```

- Optional `file` source `conf/dev.properties` will be checked for changes every `2` seconds.
- Optional `file` source `conf/config.properties` will be watched by the Java `WatchService` for changes on filesystem.
- The `file` resource `my.properties` will not be checked for changes. `PollingStrategies.nop()` polling strategy is default.

The polling strategies internally inform the config system when they detect changes in the monitored config sources (except that the `nop` strategy does nothing).

### Registering a Config Change Response

To know when config sources have changed, your application must register its interest on the `Config` node of interest. The config system will then notify your application of any change within the subtree rooted at that node. In particular, if you register on the root node, then the config system notifies your code of changes anywhere in the config tree.

#### Registering Actions

You register a function that runs when a change occurs by using the [`Config.onChange()`](/apidocs/io.helidon.config/io/helidon/config/Config.html#onChange(java.util.function.Consumer)) method on the node of interest.

*Subscribe on `greeting` property changes via `onChange` method*

``` java
config.get("greeting") 
        .onChange(changedNode -> { 
            System.out.println("Node " + changedNode.key() + " has changed!");
        });
```

- Navigate to the `Config` node on which you want to register.
- Invoke the `onChange` method, passing a consumer (`Consumer<Config>`). The config system invokes that consumer each time the subtree rooted at the `greeting` node changes. The `changedNode` is a new instance of `Config` representing the updated subtree rooted at `greeting`.

## Accessing Always-current Values

Some applications do not need to respond to change as they happen. Instead, it’s sufficient that they simply have access to the current value for a particular key in the configuration.

Each `asXXX` method on the `Config` class has a companion `asXXXSupplier` method. These supplier methods return `Supplier<XXX>`, and when your application invokes the supplier’s `get` method the config system returns the *then-current value* as stored in the config source.

*Access `greeting` property as `Supplier<String>`*

``` java
// Construct a Config with the appropriate PollingStrategy on each config source.

Supplier<String> greetingSupplier = config.get("greeting") 
        .asString().supplier(); 

System.out.println("Always actual greeting value: " + greetingSupplier.get()); 
```

- Navigate to the `Config` node for which you want access to the always-current value.
- Retrieve and store the returned supplier for later use.
- Invoke the supplier’s `get()` method to retrieve the current value of the node.

> [!IMPORTANT]
> Supplier support requires that you create the `Config` object from config sources that have proper polling strategies set up. The supplier returns refreshed values only after changes have been detected by the polling strategy.
