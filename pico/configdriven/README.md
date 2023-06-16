# Config-driven

This is a specialization of the [Pico](../)'s that is based upon </i>Helidon's [configuration](../../config)</i> subsystem, and adds support for something called <i>config-driven</i> services using the [@ConfigDriven](./api/src/main/java/io/helidon/pico/configdriven/api/ConfigDriven.java) annotation. When applied to a target service interface it will allow developers to use a higher level aggregation for their application configuration, and then allow the configuration to drive activation of services in the Pico Framework. The [@ConfigBean](./api/src/main/java/io/helidon/pico/configdriven/api/ConfigBean.java) annotation can be used to customize behavior of a type that acts as a config-bean. 

See the user documentation for more information.

## Modules
* [api](api) - the config-driven API & SPI.
* [processor](processor) - the annotation processor extensions that should be used when using <i>ConfiguredBy</i>.
* [runtime](runtime) - the runtime support for config-driven services.
* [tests](tests) - tests that can also serve as examples for usage.

# What is a config-bean?

Config bean can be any type that
- provides a public static factory method `ConfigBeanType create(io.helidon.common.Config config)`
- is annotated with `@Configured(root = true, prefix = "some.prefix")`

By referencing it from `@ConfigDriven` annotation, the config-driven annotation processor would take note of the bean, and make sure we can discover it at runtime and create instances from configuration.

A config-bean can also have `@ConfigBean` annotation to control the way bean instances are created (if we want a default instance, if the bean must have a configured option, and if it repeatable).

The expectation is that config-beans are created using `helidon-builder-processor`, by annotating a `Blueprint` with `Configured` annotation, and optionally with the `ConfigBean` annotation.

# What is a config-driven type

Any service annotated with `@ConfigBean(ConfigBean.class)` is a config-driven service.
Such a service will be added to service registry FOR EACH config bean instance that is discovered from configuration. For repeatable types, each instance has a name obtained from configuration (either the configuration node, or a `name` property), 
as a `Named` qualifier.

