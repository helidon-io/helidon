# pico-configdriven

This is a specialization of the [Pico](../)'s that is based upon </i>Helidon's [configuration](../../config)</i> subsystem, and adds support for something called <i>config-driven</i> services using the [@ConfiguredBy](./configdriven/src/main/java/io/helidon/pico/configdriven/ConfiguredBy.java) annotation. When applied to a target service interface it will allow developers to use a higher level aggregation for their application configuration, and then allow the configuration to drive activation of services in the Pico Framework.

There are a few additional caveats to understand about <b>ConfiguredBy</b> and its supporting infrastructure.

* [@ConfigBean Builder](../../builder/builder-config) is used to aggregate configuration attributes to this higher-level, application-centric configuration beans.
* The Pico Framework needs to be started w/ supporting <b>configdriven</b> modules in order for configuration to drive service activation.

See the user documentation for more information.

## Modules
* [configdriven](configdriven) - the config-driven API & SPI.
* [processor](processor) - the annotation processor extensions that should be used when using <i>ConfiguredBy</i>.
* [services](services) - the runtime support for config-driven services.
* [tests](tests) - tests that can also serve as examples for usage.

## Usage Example
1. Follow the basics instructions for [using Pico](../pico/README.md).

2. Write your [ConfigBean](../../builder/builder-config).

```java
@ConfigBean("server")
public interface ServerConfig {
    @ConfiguredOption("0.0.0.0")
    String host();

    @ConfiguredOption("0")
    int port();
}
```

3. Write your [ConfiguredBy](./configdriven/src/main/java/io/helidon/pico/configdriven/ConfiguredBy.java) service.

```java
@ConfiguredBy(ServerConfig.class)
class LoomServer implements WebServer {
    @Inject
    LoomServer(ServerConfig serverConfig) {
        ...
    }
}
```

## How It Works
At Pico startup initialization, and if <i>configdriven/services</i> is in the runtime classpath, then the Helidon's configuration tree will be scanned for "ConfigBean eligible" instances. And when a configuration matches then the config bean instance is built and fed into a <i>ConfigBeanRegistry</i>. If the <i>ConfiguredBy</i> services is declared to be "driven" (the default value), then the server (in this example the <i>LoomServer</i>) will be automatically started. In this way, the presence of configuration drives demand for a service implicitly starting that service (or services) that are declared to be configured by that config bean (in this example <i>ServerConfig</i>).
