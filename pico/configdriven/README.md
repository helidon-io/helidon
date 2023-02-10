# pico-configdriven

This is a specialization of the [Pico Framework](../) that is based upon Helidon's [configuration](../config) sub-system, and adds support for something called <i>config-driven</i> services using the [@ConfiguredBy](./configdriven/src/main/java/io/helidon/pico/configdriven/ConfiguredBy.java) annotation. When applied to a target service interface it will allow developers to use a higher level aggregation for their application configuration, and then allow the configuration to drive activation of services in the Pico Framework.

There are a few additional caveats to understand about <b>ConfiguredBy</b> and its supporting infrastructure.

* <b>@ConfigBean</b> Builder is used to aggregate configuration attributes to this higher-level, Application-centric configuration beans.
* The Pico Framework needs to be started w/ supporting <b>configdriven</b> modules in order for configuration to drive service activation.

See the user documentation for more information.

## Modules
* [configdriven](configdriven) - The API.
* [processor](processor) - the annotation processor extensions that should be used when using <i>ConfiguredBy</i>.
* [services](services) - the runtime support for config-driven services.
* [tests](tests) - tests that can also serve as examples for usage.
