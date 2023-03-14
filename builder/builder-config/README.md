# builder-config

This module can be used at compile time or at runtime.

The primary usage for this module involves the [ConfigBean](./src/main/java/io/helidon/builder/config/ConfigBean.java) annotation.
A {@code ConfigBean} is another {@link io.helidon.builder.BuilderTrigger} that extends the {@link io.helidon.builder.Builder} concept to support the integration to Helidon's configuration sub-system. It basically provides everything that [io.helidon.builder.Builder](../builder) provides. However, unlike the base <i>Builder</i> generated classes that can handle any object type, the types used within your target <i>ConfigBean</i>-annotated interface must have all of its attribute getter method types resolvable by Helidon's [Config](../../common/config) sub-system.

One should write a <i>ConfigBean</i>-annotated interface in such a way as to group the collection of configurable elements that logically belong together to then be delivered (and perhaps drive an activation of) one or more java service types that are said to be "[ConfiguredBy](../../pico/configdriven)" the given <i>ConfigBean</i> instance.

The <i>ConfigBean</i> is therefore a logical grouping for the "pure configuration set of attributes (and sub-<i>ConfigBean</i> attributes) that typically originate from an external media store (e.g., property files, config maps, etc.), and are integrated via Heldion's [Config](../../common/config) subsystem at runtime.

The [builder-config-processor](../builder-config-processor) module is required to be on the APT classpath in order to code-generate the implementation classes for the {@code ConfigBean}. This can replace the normal use of the [builder-processor](../processor) that supports just the <i>Builder</i> annotation. Using the builder-config-processor will support both <i>Builder</i> and <i>ConfigBean</i> annotation types as part of its processing.

## Example
```java
@ConfigBean
public interface MyConfigBean {
    String getName();
    int getPort();
}
```
When [Helidon Pico](../../pico) services are incorporated into the application lifecycle at runtime, the configuration sub-system is scanned at startup and <i>ConfigBean</i> instances are created and fed into the <i>ConfigBeanRegistry</i>. This mapping occurs based upon the [io.helidon.config.metadata.ConfiguredOption#key()](../../config/metadata/src/main/java/io/helidon/config/metadata/ConfiguredOption.java) on each of the <i>ConfigBean</i>'s attributes. If no such <i>ConfiguredOption</i> annotation is found then the type name is used as the key (e.g., MyConfigBean would map to "my-config-bean").

Here is a modified example that shows the use of <i>ConfiguredOption</i> having default values applied.

```java
@ConfigBean("server")
public interface ServerConfig {
    @ConfiguredOption("0.0.0.0")
    String host();

    @ConfiguredOption("0")
    int port();
}
```

<i>ConfigBean</i> generated sources have a few extra methods on them. The most notable of these methods is the <i>toBuilder(Config cfg)</i> static method as demonstrated below.
```java
Config cfg = ...
ServerConfig config = DefaultServerConfig.toBuilder(cfg).build();
```

The above can be used to programmatically create <i>ConfigBean</i> instances directly. However, when using [Helidon Pico](../../pico), and using various annotation attributes (see [the javadoc](./src/main/java/io/helidon/builder/config/ConfigBean.java) for details) on <i>ConfigBean</i>, the runtime behavior can be simplified further to automatically create these bean instances, and further drive startup activation of the services that are declared to be "configured by" these config bean instances. This means that simply having configuration present from your config subsystem will drive bean and service activations accordingly.
