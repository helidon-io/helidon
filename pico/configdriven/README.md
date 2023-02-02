# pico-configdriven

This is a specialization of the [builder](../builder) that extends the builder to support additional integration with Helidon's configuration sub-system. It adds support for the [@ConfigBean](builder-config/src/main/java/io/helidon/pico/builder/config/ConfigBean.java) annotation. When applied to a target interface it will map that interface to configuration via a new <i>toBuilder</i> method generated on the implementation as follows:

```java
        ...

	public static Builder toBuilder(io.helidon.common.config.Config cfg) {
        ...
	}
    
        ...
```

There are a few additional caveats to understand about <b>ConfigBean</b> and its supporting infrastructure.

* <b>@Builder</b> can be used in conjunction with <b>@ConfigBean</b>. All attributed will be honored exception one...
* <b>Builder.requireLibraryDependencies<b> is not supported. All generated configuration beans and builders will minimally require a compile-time and runtime dependency on Helidon's <i>common-config</i> module. But for full fidelity support of Helidon's config one should instead use the full <i>config</i> module.

## Modules
* [builder-config](builder-config) - annotations and other SPI types.
* [processor](processor) - the annotation processor that should be used when using <i>ConfigBean</i>s.
* [services](services) - the runtime support for config-driven services.
* [tests](tests) - tests that can also serve as examples for usage.
