Helidon Testing
-----

This module provides features that are most commonly required to test a Helidon application.

# TestConfigSource

Testing may require setting configuration values after the config instance is created, but before the value
is used. This can be achieved through `TestConfigSource` (when creating configuration instance by hand or via
ServiceRegistry; note that when using registry, there is nothing required to be done by you).

Simply create a new instance of `TestConfigSource` (may only be one for the whole test class, as config is created only once in the registry).

# Configuration annotations

Configuration can be further customized by using configuration annotations.

The following annotations are supported, to provide customized configuration.

The following table shows all configuration types and their weight used when constructing config (if relevant):

| Source                | Weight    | Description                                                                                                                      |
|-----------------------|-----------|----------------------------------------------------------------------------------------------------------------------------------|
| `TestConfigSource`    | 1_000_000 | Use `TestConfig` to set values, always the highest weight form this table                                                        |
| `@TestConfig.Profile` | N/A       | Customization of the config profile to use. Defaults to `test`                                                                   |
| `@TestConfig.Value`   | 954_000   | Used to set a single key/value pair. Weight can be customized. Repeatable.                                                       |
| `@TestConfig.Values`  | 953_000   | Use to set multiple key/value pairs, with a format defined (such as yaml, properties). Weight can be customized.                 |   
| `@TestConfig.File`    | 952_000   | Use to set a whole config file. Type is determined based on file suffix. Weight can be customized. Repeatable.                   |
| `@TestConfig.Source`  | 951_000   | Use to set a single config source. Weight can be customized. This annotation belongs to static method producing a config source. |

