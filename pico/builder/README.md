# pico-builder

The <b>Helidon Pico Builder</b> provides compile-time code generation for fluent builders. It was inspired by [Lombok]([https://projectlombok.org/), but the implementation here in Helidon is different in a few ways:
<ol>
    <li>Pico Builders target interface or annotation types only.</li>
    <li>Generated classes implement the target interface (or annotation) and provide a fluent builder that will always have an implementation of <i>toString(), hashCode(), and equals().</i></li>
    <li>Generated classes always behave like a <i>SuperBuilder</i> from Lombok. Basically this means that builders can form
      a hierarchy on the types they target (e.g., Level2 derives from Level1 derives from Level0, etc.).</li>
    <li>Lombok uses AOP while the Pico Builder generates source code (in conjunction with the proper Builder annotation processor).</li>
    <li>Pico Builders are extensible - you can provide your own implementation of the SPI to customize the generated classes.</li>
</ol>

Supported annotation types (see [api](./api/src/main/java/io/helidon/pico/builder/api) for further details):
* Builder - similar to Lombok's SuperBuilder.
* Singular - similar to Lombok's Singular.

Any and all types are supported by the Builder, with special handling for List, Map, Set, and Optional types. The target interface,
however, should only contain getter like methods (i.e., has a non-void return and takes no arguments). All static and default methods
are ignored on the target being processed.

The Helidon Pico Builder is completely independent of other parts of Pico. It can therefore be used in a standalone manner. The
generated implementation class will not require any special module to support those classes - just the types from your interface
and standard JRE types are used.

## Usage
1. Write your interface that you want to have a builder for.
```java
public interface MyConfigBean {
    String getName();
    boolean isEnabled();
    int getPort();
}
```
2. Annotate your interface definition with <i>Builder</i>, and optionally use <i>ConfiguredOption</i>, <i>Singular</i>, etc.
3. Compile (using the <i>pico-builder-processor</i> in your annotation classpath).

The result of this will create (under ./target/generated-sources):
* MyConfigBeanImpl (in the same package as MyConfigBean) that will support multi-inheritance builders named MyConfigBeanImpl.Builder.
* Support for toString(), hashCode(), and equals() are always included.
* Support for toBuilder().
* Support for streams (see javadoc for [Builder](./api/src/main/java/io/helidon/pico/builder/Builder.java)).
* Support for attribute visitors (see [test-builder](./test-builder/src/main/java/io/helidon/pico/builder/test/testsubjects/package-info.java)).
* Support for attribute validation (see ConfiguredOption#required() and [test-builder](./test-builder/src/main/java/io/helidon/pico/builder/test/testsubjects/package-info.java)).

The implementation of the processor also allows for a provider-based extensibility mechanism.

## modules
* [api](./api) - defines the compile-time annotations. Typically this module is only needed at compile time.
* [spi](./spi) - defines the SPI runtime definitions used by builder tooling. Only needed at compile time.
* [tools](./tools) - provides the creators / code generators. Only needed at compile time.
* [runtime-tools](./runtime-tools) - provides optional runtime utility classes the can helpful at compile time as well as runtime.
* [processor](./processor) - the annotation processor which delegates to the tools module for processing. Only needed at compile time.
* [test-builder](./test-builder) - internal tests that can also serve as examples for usage.

## Customizations
To implement your own custom <i>Builder</i>:
* Write an implementation of <i>BuilderCreator</i> having a higher-than-default <i>Weighted</i> value as compared to <i>DefaultBuilderCreator</i>.
* Include your module with this creator in your annotation processing path.

See [test-builder](./test-builder) for usage examples.
