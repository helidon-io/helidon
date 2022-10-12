# pico-builder

the <b>Pico Builder</b> provides compile-time code generation for Builders. In spirit it is inspired by Lombok, but the implementation is extremely lite-weight and primitive in that it does not have a rich set of options available or error checking built in.

Supported annotation types (see [api](./api)):
* Builder - similar to Lombok's SuperBuilder.
* Default - similar to Lombok's Default.
* Singular - similar to Lombok's Singular.

Supported constructs:
* Any primitive or object type, with special handling for List, Map, and Set types. The bean interface should only have is() and get() type methods that take no arguments. If other methods are required then you will need to provide default or static implementations on your bean interface.
* tostring(), hashCode(), and equals() - out of the box for all generated types.
* Stream support on all generated types.

Pico Builder is completely independent of other parts of Pico, but used by Pico internally during its compile-time in order to generate builders that are then provided in various parts of its SPI.

## Usage
1. Write your interface that you want to have a builder for.
```java
public interface MyConfigBean {
    String getName();
    boolean isEnabled();
    int getPort();
}
```
2. Annotate your interface definition with <i>Builder</i>, and optionally use <i>Default</i> or <i>Singular</i>.
3. Compile (using the <i>pico-builder-processor</i> in your annotation classpath).

The result of this will create (under ./target/generated-sources):
* MyConfigBeanImpl (in the same package as MyConfigBean) that will support multi-inheritance builders named MyConfigBeanImpl.Builder.
* Support for toString(), hashCode(), and equals() are always included.
* Support for toBuilder().
* Support for streams (see javadoc for [Builder](./api/src/main/java/io/helidon/pico/builder/Builder.java)).

The implementation of the processor also allows for a provider-based extensibility mechanism.

## modules
* [api](./api) - defines the compile-time (source retention only) annotations.
* [spi](./spi) - defines the SPI runtime definitions used by tools.
* [tools](./tools) - provides the creators and code generators, as well as the default provider implementations.
* [processor](./processor) - the annotation processor which delegates to the tools module at compile time.
* [test-builder](./test-builder) - tests that can also serve as examples for usage.

Note that these modules are intended only for compile-time usages. There is no need to include any of the above in your application at runtime.

## provider
To implement your own provider:
* Write an implementation for <i>BuilderCreator</i> having a higher <i>Weighted</i> value as compared to <i>DefaultBuilderCreator</i>.
* Include your module with this creator in your annotation classpath.

See [test-builder](./test-builder) for usage examples.
