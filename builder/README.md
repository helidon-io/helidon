# Helidon Builder

This module is used by Helidon to generate types with builders (Prototypes) to be used in API of modules from a blueprint interface.

There are two modules that are used:
- `helidon-builder-api` - module required in `compile` scope, contains annotations and APIs needed to write blueprints, and to build the generated code
- `helidon-builder-processor` - module to be placed on annotation processor path, generates the sources

There is one module useful for internal development
- `helidon-builder-tests-common-types` (located under `tests/common-types`) that contains blueprints for the types we use in `helidon-common-types`. As the common types module is used by the processor, we would end up with a cyclic dependency, so this allows us to generate the next iteration of common types (requires manual copying of the generated types)

## Goals

Generate all required types for Helidon APIs with builders, that follow the same style (method names, required validation etc.).

The following list of features is currently supported:
- `Builder` also implements the interface of the type (all getters are available also on builder) 
- `Type` options - interface returns `Type`, such an option MUST NOT be null in the built instance, there is a validation in place on calling the `build` or `buildPrototype` methods. Getters MAY return null on a builder
- `Optional` options - interface returns `Optional<Type>`, setters use just `Type`, there is a package local setter that accepts `Optional` as well, to support updating a builder from an existing instance
- `List` options - interface returns `List<Type>`, never null - if there is no configured value, empty string is returned
- `Set` options - similar to list
- `Map` options - key/value map, builders support any key/value types, but if configuration is used, the key must be a string
- "Singular" for collection based options, which adds setter for a single value (for `List<String> algorithms()`, there would be the following setters: `algorithms(List<String>)`, `addAlgorithms(List<String>)`, `addAlgorithm(String)`)
- A type can be `@Configured`, which adds integration with Helidon common Config module, by adding a static factory method `create(io.helidon.common.Config)` to the generated type, as well as `config(Config)` method to the generated builder, that sets all options annotated with `@ConfiguredOption` from configuration (if present in the Config instance)
- Capability to update the builder before validation (interceptor)
- Support for custom methods (`@Prototype.CustomMethods`) for factory methods, prototype methods, and builder methods

## Non-Goals

We are not building a general purpose solution, there are limitations that are known and will not be targetted:
- the solution expects that everything is single package - blueprints are required to be package local, which does not allow using built types across packages within a single module
- we only support interface based definition of blueprints (no classes)
- we only support non-nullable options, instead of nullable, use `Optional` getters
- implementation types of collections are fixed to `java.util.ArrayList`, `java.util.LinkedHashSet` and `java.util.LinkedHashMap`

## Getting Started
1. Write your interface that you want to have a builder for.
```java
interface MyConfigBeanBlueprint {
    String getName();
    boolean isEnabled();
    int getPort();
}
```
2. Annotate your interface definition with `@Blueprint`, and optionally use `@ConfiguredOption`, `Singular` etc. to customize the getter methods. Remember to review the annotation attributes javadoc for any customizations.
3. Update your pom file to add annotation processor
```xml
    ...
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>io.helidon.builder</groupId>
                            <artifactId>helidon-builder-processor</artifactId>
                            <version>${helidon.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
        </plugins>
    </build>
    ...
```

Generated types will be available under `./target/generated-sources/annotations`
* MyConfigBean (in the same package as MyConfigBeanBlueprint), with inner classes `BuilderBase` (for inheritance), `Builder`,
* Support for `toString()`, `hashCode()`, and `equals()` are always included.
* Support for `builder(MyConfigBean)` to create a new builder from an existing instance
* Support for `from(MyConfigBean)` and `from(MyConfigBean.BuilderBase<?, ?>)` to update builder from an instance or builder
* Support for validation of required and non-nullable options (required options are options that have `@ConfiguredOption(required=true)`, non-nullable option is any option that is not primitive, collection, and does not return an `Optional`)
* Support for builder interception (`@Bluprint(builderInterceptor = MyInterceptor.class)`)
