Config Overrides
-----

# Overview

This module adds a config filter that can override configuration values using pattern matching.

Configuration of the overrides filter is expected to be similar to following (example is in properties format):

```properties
# override selected pod (abcdef) logging level
prod.abcdef.logging.level = FINEST

# "production" environment, any pod
prod.*.logging.level = WARNING

# "test" environment, any pod
test.*.logging.level = FINE
```

This will override values as follows:

- exact match of configuration key `prod.abcdef.logging.level` will be replaced with value `FINEST`
- any key that matches pattern (regexp) `prod\.\w+\.logging\.level?` will be replaced with value `WARNING` (i.e. `prod.first.logging.level`, `prod.second.logging.level` etc.)
- any key that matches pattern (regexp) `test\.\w+\.logging\.level?` will be replaced with value `FINE` (i.e. `test.first.logging.level`, `test.second.logging.level` etc.)


Example configuration that could be used to be overridden:

```yaml
# probably from system properties or an environment variables
env: prod 
pod: abcdef

$env:
    $pod:
        logging:
            level: ERROR

        app:
            greeting:  Ahoy
            page-size: 42
```

# Setup

Note that if the config source supports changes (i.e. it is a file config source with a file watcher), or the config that 
was provided when creating the filter supports changes, or the target config instance used to setup the filter supports changes,
the filter would reload its values based on these new config instances.

Note that the behavior is then dependent on how config itself uses filter to apply to requested values.

## Helidon Imperative Programming Model

When using the imperative programming model (i.e. you do everything by hand), you can set up the filter using its builder.

Using explicit override patterns:

```java
import io.helidon.config.overrides.OverrideConfigFilter;

Config config = Config.builder()
        .addFilter(OverrideConfigFilter.builder()
                           .putOverrideExpression("prod.abcdef.logging.level", "FINEST")
                           .putOverrideExpression("prod.*.logging.level", "WARNING")
                           .putOverrideExpression("test.*.logging.level", "FINE")
                           .build())       
        .build();
```

Using custom config sources:

```java
import io.helidon.config.overrides.OverrideConfigFilter;

Config config = Config.builder()
        .addFilter(OverrideConfigFilter.builder()
                           .addConfigSource(ConfigSources.classpath("/overrides.properties").get())
                           .build())       
        .build();
```

Using custom config instance:

```java
Config overrideConfig = Config.builder()
                            .addSource(ConfigSources.classpath("/overrides.properties"))
                            .disableEnvironmentVariablesSource()
                            .disableSystemPropertiesSource()
                            // we do not want to use an override filter for its own config source 
                            .disableFilterServices()
                            .build();

Config config = Config.builder()
        .addFilter(OverrideConfigFilter.create(overrideConfig))
        .build();
```

An alternative is to use config itself to set up config overrides (i.e. when using meta configuration).

```java
Config config = Config.create(); 
```

Then the following configuration must exist in one of the configuration sources that are loaded (YAML is just an example):

```yaml
overrides.expressions:
  "prod.abcdef.logging.level": "FINEST"
  "prod.*.logging.level": "WARNING"
  "test.*.logging.level": "FINE"
```

In case you do not want to honor configuration in target config under the key `overrides.expressions`, you can disable it
through option `useTargetConfig` on the filter builder.

## Helidon declarative programming model

The filter will be discovered by service registry, and it injects `OverrideConfig`. If you want to customize the behavior
of the override config filter, simply create a factory that provides an instance of `OverrideConfig` (note that this instance
MUST NOT use an injected configuration instance, as that would be a cyclic dependency).
