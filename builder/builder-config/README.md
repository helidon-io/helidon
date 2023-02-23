# builder-config

This module can be used at compile time or at runtime.

It extends <b>[Builder](../)</b> to offer <b>[ConfigBean](./src/main/java/io/helidon/builder/config/ConfigBean.java)</b>, and the backing SPI supporting classes that integrates to [Pico](../../pico)'s [Config-Driven Services](../../pico/configdriven) as well as <i>Helidon's Configuration</i> subsystems.

Usage is simple - use <i>@ConfigBean</i> instead of (or in addition to) <i>@Builder</i>.

```java
@ConfigBean("server")
public interface ServerConfig {
    @ConfiguredOption("0.0.0.0")
    String host();

    @ConfiguredOption("0")
    int port();
}
```

Then, code can be written to build <i>ServerConfig</i> directly from <i>Config</i>:
```java
ServerConfig config = DefaultServerConfig.toBuilder(config).build();
```
