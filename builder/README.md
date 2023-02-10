# builder

The <b>Helidon Builder</b> provides compile-time code generation for fluent builders. It was inspired by [Lombok]([https://projectlombok.org/), but the implementation here in Helidon is different in a few ways:
<ol>
    <li>The <i>Builder</i> annotation targets interface or annotation types only. Your interface effectively contains the attributes of your getter as well as serving as the contract for your getter methods.</li>
    <li>Generated classes implement your target interface (or annotation or abstract class) and provide a fluent builder that will always have an implementation of <i>toString(), hashCode(), and equals().</i> implemented</li>
    <li>Generated classes always behave like a <i>SuperBuilder</i> from Lombok. Basically this means that builders can form
      a hierarchy on the types they target (e.g., Level2 derives from Level1 derives from Level0, etc.).</li>
    <li>Lombok uses AOP while the Helidon Builder generates source code. You can use the <i>Builder</i> annotation (as well as other annotations in the package and <i>ConfiguredOption</i>) to control the naming and other features of what and how the implementation classes are generated and behave.</li>
    <li>Builders are extensible - you can provide your own implementation of the <b>Builder Processor SPI</b> to customize the generated classes for your situation.</li>
</ol>

Supported annotation types (see [builder](./builder/src/main/java/io/helidon/builder) for further details):
* Builder - similar to Lombok's SuperBuilder.
* Singular - similar to Lombok's Singular.
* NonNull - accomplished alternatively via Helidon's <i>ConfiguredOption#required</i>.
* Default - accomplished alternatively via Helidon's <i>ConfiguredOption#value</i>.

Explicitly unsupported (i.e., these are just a few of the types that do not have a counterpart from Helidon's Builder):
* NoArgsConstructor - must instead use one of the <i>toBuilder()</i> methods
* AllArgsConstructor - must instead use one of the <i>toBuilder()</i> methods

Any and all types are supported by the Builder, with special handling for List, Map, Set, and Optional types. The target interface,
however, should only contain getter like methods (i.e., has a non-void return and takes no arguments). All static and default methods
are ignored on the target being processed.

The Helidon Builder is independent of other parts of Helidon. It can therefore be used in a standalone manner. The
generated implementation class will not require any special module to support those classes - just the types from your interface
and standard JRE types are used. This is made possible when your <i>Builder</i> has the <i>requireBuilderLibrary=false</i>. See the javadoc for details.

## Getting Started
1. Write your interface that you want to have a builder for.
```java
public interface MyConfigBean {
    String getName();
    boolean isEnabled();
    int getPort();
}
```
2. Annotate your interface definition with <i>Builder</i>, and optionally use <i>ConfiguredOption</i>, <i>Singular</i>, etc. Remember to review the annotation attributes javadoc for any customizations.
3. Compile (using the <i>builder-processor</i> in your annotation classpath).

The result of this will create (under ./target/generated-sources/annotations):
* MyConfigBeanImpl (in the same package as MyConfigBean) that will support multi-inheritance builders named MyConfigBeanImpl.Builder.
* Support for toString(), hashCode(), and equals() are always included.
* Support for toBuilder().
* Support for streams (see javadoc for [Builder](./builder/src/main/java/io/helidon/builder/Builder.java)).
* Support for attribute visitors (see [test-builder](./tests/builder/src/main/java/io/helidon/builder/test/testsubjects/package-info.java)).
* Support for attribute validation (see ConfiguredOption#required() and [test-builder](./tests/builder/src/main/java/io/helidon/builder/test/testsubjects/package-info.java)).
* Support for builder interception (i.e., including decoration or mutation). (see [test-builder](./tests/builder/src/main/java/io/helidon/builder/test/testsubjects/package-info.java)).

## Modules
* [builder](./builder) - provides the compile-time annotations, as well as optional runtime supporting types.
* [types](./types) - provides the basic types for builder.
* [processor-spi](./processor-spi) - defines the Builder Processor SPI runtime definitions used by builder tooling. This module is only needed at compile time.
* [processor-tools](./processor-tools) - provides the concrete creators & code generators. This module is only needed at compile time.
* [processor](./processor) - the annotation processor which delegates to the processor-tools module for the main processing logic. This module is only needed at compile time.
* [builder-config](./builder-config) - extension to the builder to additionally support [Helidon (Common) Config](../common/config).
* [builder-config-processor](./builder-config-processor) - defines the ConfigBean builder.
* [tests](./tests) - tests that can also serve as examples for usage.

## Customizations and Extensibility
To implement your own custom <i>Builder</i>:
* See [builder-config](../builder-config) serving as an example.

## Usage
See [tests/builder](./tests/builder) for usage examples.
