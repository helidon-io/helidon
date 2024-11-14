# Helidon Builder

This module is used by Helidon to generate types with builders (Prototypes) to be used in API of modules from a blueprint
interface.

There are two modules that are used:

- `helidon-builder-api` - module required in `compile` scope, contains annotations and APIs needed to write blueprints, to compile
  the generated code, and at runtime
- `helidon-builder-codegen` - module to be placed on annotation processor path, generates the sources

There is one module useful for internal development

- `helidon-builder-tests-common-types` (located under `tests/common-types`) that contains blueprints for the types we use in
  `helidon-common-types`. As the common types module is used by the processor, we would end up with a cyclic dependency, so this
  allows us to generate the next iteration of common types (requires manual copying of the generated types)

This document describes the main features and usage, there are further customization option. Kindly check usages of
`Prototype.Blueprint` in this repository, to see examples...

Table of contents:

- [Goals](#goals) - what we do
- [Non-Goals](#non-goals) - what we decided not to do
- [Rules](#rules) - what are the rules when using this module
- [Use Cases](#use-cases) - supported use cases
- [Getting Started](#getting-started) - set up your `pom.xml` and use this module
- [API](#api) - more details on available annotations and interfaces

## Goals

Generate all required types for Helidon APIs with builders, that follow the same style (method names, required validation etc.).
Support for builders that can read options from Helidon configuration (`helidon-common-config`, and of course `helidon-config`).

- We MUST NOT change bytecode of user classes
- We MUST NOT use reflection (everything is code generated)
- Support inheritance of prototypes (and of blueprints if in the same module)
- The generated code is the public API (and must have javadoc generated)
- The annotated blueprint interface is used to generate configuration metadata, and configuration documentation (
  see [Config metadata](../config/metadata/README.md))
- Support prototypes configured from Helidon configuration (as an optional feature)
- Support additional methods to be generated (factory methods, prototype methods, builder methods)
- Support the following collections: `List`, `Set`, and `Map`
- Support for default values, for the most commonly used types to be typed explicitly (String, int, long, boolean etc.)
- Support for `enum` options

## Non-Goals

We are not building a general purpose solution, there are limitations that are known and will not be targeted:

- the solution expects that everything is single package - blueprints are required to be package local, which does not allow using
  built types across packages within a single module
- we only support interface based definition of blueprints (no classes)
- we only support non-nullable options, instead of nullable, use `Optional` getters
- implementation types of collections are fixed to `java.util.ArrayList`, `java.util.LinkedHashSet` and `java.util.LinkedHashMap`

## Rules

There are a few rules we required and enforce:

1. Blueprint MUST be an interface
2. Blueprint interface MUST be package private
3. Blueprint interface must have a name that ends with `Blueprint`; the name before `Blueprint` will be the name of the prototype
4. In case we use the blueprint -> prototype -> runtime type use case (see below):
   1. The blueprint must extend `Prototype.Factory<RuntimeType>` where `RuntimeType` is the type of the runtime object
   2. The runtime type must be annotated with `@RuntimeType.PrototypedBy(PrototypeBlueprint.class)`
   3. The runtime type must implement `RuntimeType.Api<Prototype>`
   4. The runtime type must have a `public static Prototype.Builder builder()` method implemented by user
   5. The runtime type must have a `public static RuntimeType create(Prototype)` method implemented by user
   6. The runtime type must have a `public static RuntimeType create(Consumer<Prototype.Builder>)` method implemented by user

## Use Cases

There are two use cases we cover:

1. We need a type with a builder (we will use `Keys` as an example)
2. We need a runtime object, with a prototype with a builder (we will use `Retry` as an example)

For both use cases, we need to understand how to create instances, obtain builders etc.

### Type with a builder

For this simple approach, the user facing API will look as:

```java
Keys keys = Keys.builder()
        .name("name")
        .build();
```

Configuration based API:

```java
// the location of config is arbitrary, the API expects in ono the Keys node
Keys keys = Keys.create(config.get("keys"));
```

The "blueprint" of such type:

```java
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

@Prototype.Blueprint
@Prototype.Configured // support method config(Config) on the builder, and a static create(Config)
interface KeysBlueprint {
    @Option.Configured
    String name();
}
```

This will generate:

- `Keys extends KeysBlueprint` interface
- `Keys.BuilderBase implements Keys` base builder, to support extensibility of `Keys`
- `Keys.Builder extends Keys.BuilderBase, io.helidon.common.Builder<Builder, Keys>` inner class - the fluent API builder
  for `Keys`
- `Keys.BuilderBase.KeysImpl implements Keys` implementation of `Keys`

### Runtime object, blueprint, builder

For this approach, the user facing API will be similar to:

```java
Retry retry = Retry.builder() // method builder is not generated, must be hand coded, and will return "RetryPrototype.builder()"
        .build(); // generated, creates a Retry instance through a factory method defined on Retry or on RetryPrototypeBlueprint

RetryPrototype prototype = RetryPrototype.builder()
        .buildPrototype(); // alternative build method to obtain the intermediate prototype object

Retry retryFromPrototype = prototype.build(); // to runtime type
```

The "blueprint" of such type:

```java
@Prototype.Blueprint
@Prototype.Configured // support method config(Config) on the builder, and a static create(Config) method if desired
intrerface RetryPrototypeBlueprint extends Prototype.Factory

<Retry> {
    @Option.Configured
    String name ();
}
```

## Getting Started

1. Write your interface that you want to have a builder for

```java
interface MyConfigBeanBlueprint {
    String getName();

    boolean isEnabled();

    int getPort();
}
```

2. Annotate your interface definition with `@Blueprint`, and optionally use `@Prototype.Configured` and `@Option.Configured`,
   `@Option.Singular` etc. to customize the getter methods. Remember to review the annotation attributes javadoc for any
   customizations
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
                        <!-- Codegen integration with Java annotation processing -->
                        <groupId>io.helidon.codegen</groupId>
                        <artifactId>helidon-codegen-apt</artifactId>
                        <version>${helidon.version}</version>
                    </path>
                    <path>
                        <groupId>io.helidon.builder</groupId>
                        <artifactId>helidon-builder-codegen</artifactId>
                        <version>${helidon.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
            <!--
            Only for Helidon developers:
             the following section is to enable correct reactor ordering without adding processor 
             to module classpath/module path
             this is ONLY needed when adding (any) Helidon processor to a Helidon module (within the same Maven project)
            -->
            <dependencies>
                <dependency>
                    <groupId>io.helidon.builder</groupId>
                    <artifactId>helidon-builder-processor</artifactId>
                    <version>${helidon.version}</version>
                </dependency>
            </dependencies>
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
* Support for validation of required and non-nullable options (required options are options that have `@Option.Required` and are
  primitive), non-nullable option is any option that is not primitive, collection, and does not return an `Optional`)
* Support for builder decorator (`@Bluprint(decorator = MyDecorator.class)`), `class MyDecorator implements BuilderDecorator`

## API

The API has to sections:

1. Inner types of `Prototype` class to configure `Blueprints`, and of `RuntimeType` to configure runtime types
2. Inner types of `Option` class to configure options

### Prototype

Annotations:

| Annotation                  | Required | Description                                                                                                                                                                                                  |
|-----------------------------|----------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Prototype.Blueprint`       | `true`   | Annotation on the blueprint interface is required to trigger annotation processing                                                                                                                           |
| `Prototype.Implement`       | `false`  | Add additional implemented types to the generated prototype                                                                                                                                                  |
| `Prototype.Annotated`       | `false`  | Allows adding an annotation (or annotations) to the generated class or methods                                                                                                                               |
| `Prototype.FactoryMethod`   | `false`  | Use in generated code to mark static factory methods, also can be used on blueprint factory methods to be used during code generation, and on custom methods to mark static methods to be added to prototype |
| `Prototype.Singular`        | `false`  | Used for lists, sets, and maps to add methods `add*`/`put*` in addition to the full collection setters                                                                                                       |     
| `Prototype.SameGeneric`     | `false`  | Use for maps, where we want a setter method to use the same generic type for key and for value (such as `Class<T> key, T valuel`)                                                                            |
| `Prototype.Redundant`       | `false`  | A redundant option will not be part of generated `toString`, `hashCode`, and `equals` methods (allows finer grained control)                                                                                 |
| `Prototype.Confidential`    | `false`  | A confidential option will not have value visible when `toString` is called, only if it is `null` or it has a value (`****`)                                                                                 |
| `Prototype.CustomMethods`   | `false`  | reference a class that will contain declarations (all static) of custom methods to be added to the generated code, can add prototype, builder, and factory methods                                           |
| `Prototype.BuilderMethod`   | `false`  | Annotation to be placed on factory methods that are to be added to builder, first parameter is the `BuilderBase<?, ?>` of the prototype                                                                      |
| `Prototype.PrototypeMethod` | `false`  | Annotation to be placed on factory methods that are to be added to prototype, first parameter is the prototype instance                                                                                      |
| `RuntimeType.PrototypedBy`  | `true`   | Annotation on runtime type that is created from a `Prototype`, to map it to the prototype it can be created from, used to trigger annotation processor for validation                                        |

Interfaces:

| Interface                     | Generated | Description                                                                                                                                                                                                                        |
|-------------------------------|-----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `RuntimeType.Api`             | `false`   | runtime type must implement this interface to mark which prototype is used to create it                                                                                                                                            |
| `Prototype.Factory`           | `false`   | if blueprint implements factory, it means the prototype is used to create a single runtime type and will have methods `build` and `get` both on builder an on prototype interface that create a new instance of the runtime object |
| `Prototype.BuilderDecorator`  | `false`   | custom decorator to modify builder before validation is done in method `build`                                                                                                                                                     |
| `Prototype.Api`               | `true`    | all prototypes implement this interface                                                                                                                                                                                            |
| `Prototype.Builder`           | `true`    | all prototype builders implement this interface, defines method `buildPrototype`                                                                                                                                                   |
| `Prototype.ConfiguredBuilder` | `true`    | all prototype builders that support configuration implement this interface, defines method `config(Config)`                                                                                                                        |

### Option

| Annotation              | Description                                                                                                                                                                                                                                                                          |
|-------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `@Option.Singular`      | For collection based options. Adds setter for a single value (for `List<String> algorithms()`, there would be the following setters: `algorithms(List<String>)`, `addAlgorithms(List<String>)`, `addAlgorithm(String)`)                                                              |
| `@Option.Configured`    | For options that are configured from config (must be explicitly marked, default is not-configured), also ignored unless `@Prototype.Configured` is specified on the blueprint interface                                                                                              |
| `@Option.Required`      | We can recognize required options through signature in most cases (any option that does not return an `Optional` and does not have a default value); this option is useful for primitive types, where we need an explicit value set, rather than using the primitive's default value |
| `@Option.Provider`      | Satisfied by a provider implementation, see javadoc for details                                                                                                                                                                                                                      |
| `@Option.AllowedValues` | Allowed values for the property, not required for `enum`, where we create this automatically, though we can configure description of each value (works automatically for `enum` defined in the same module); the description is used for generated documentation                     |                    
| `@Option.SameGeneric`   | Advanced configuration of a Map, where the map accepts two typed values, and we must use the same generic on setters (such as `Map<Class<Object>, Object>` - `<T> Builder put(Class<T>, T)`)                                                                                         |
| `@Option.Redundant`     | Marks an option that is not used by equals and hashCode methods                                                                                                                                                                                                                      |
| `@Option.Confidential`  | Marks an option that will not be visible in `toString()`                                                                                                                                                                                                                             |
| `@Option.Deprecated`    | Marks a deprecated option that has a replacement option in this builder, use Java's deprecation for other cases, they will be honored in the generated code                                                                                                                          |
| `@Option.Type`          | Explicitly defined type of a property (may include generics), in case the type is code generated in the current module, and we cannot obtain the correct information from the annotation processing environment                                                                      |
| `@Option.Decorator`     | Support for field decoration (to do side-effects on setter call)                                                                                                                                                                                                                     |

To configure default value(s) of an option, one of the following annotations can be used (mutually exclusive!).
Most defaults support an array, to provide default values for collections.

| Annotation               | Description                                                                                        |
|--------------------------|----------------------------------------------------------------------------------------------------|
| `@Option.Default`        | Default value(s) that are `String` or we support coercion to the correct type (`enum`, `Duration`) |
| `@Option.DefaultInt`     | Default value(s) that are `int`                                                                    |
| `@Option.DefaultLong`    | Default value(s) that are `long`                                                                   |
| `@Option.DefaultDouble`  | Default value(s) that are `double`                                                                 |
| `@Option.DefaultBoolean` | Default value(s) that are `boolean`                                                                |
| `@Option.DefaultMethod`  | Static method to invoke to obtain a default value                                                  |
| `@Option.DefaultCode`    | Source code to add to the generated assignment, single line only supported                         |
