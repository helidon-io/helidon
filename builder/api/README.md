# Builder

## Description

There are two use cases we cover:

1. We need a type with a builder (we will use `Keys` as an example)
2. We need a runtime object, with a prototype with a builder (we will use `Retry` as an example)

For both use cases, we need to understand how to create instances, obtain builders etc.

### Type with a builder

For this simple approach, the user facing API will look as it does now:

```java
Keys keys=Keys.builder()
        .name("name")
        .build();
```

The "blueprint" of such type:

```java
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

@Prototype.Blueprint
@Configured // support method config(Config) on the builder, and a static create(Config)
intrerface KeysBlueprint{
@ConfiguredOption(required = true)
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

For this approach, the user facing API will be similar to what we do now:

```java
Retry retry=Retry.builder() // method builder is not generated, must be hand coded, and will return "RetryPrototype.builder()"
        .build(); // generated, creates a Retry instance through a factory method defined on Retry or on RetryPrototypeBlueprint

        RetryPrototype prototype=RetryPrototype.builder()
        .buildPrototype(); // alternative build method to obtain the intermediate prototype object

        Retry retryFromSetup=prototype.build(); // to runtime type
```

The "blueprint" of such type:

```java
@Prototype.Blueprint
@Configured // support method config(Config) on the builder, and a static create(Config) method if desired
intrerface RetryPrototypeBlueprint extends Prototype.Factory<Retry> {
@ConfiguredOption(required = true)
    String name();
            }
```

## Types, interfaces

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
| `RuntimeType.Prototype`     | `true`   | Annotation on runtime type that is created from a `Prototype`, to map it to the prototype it can be created from, used to trigger annotation processor for validation                                        |

Interfaces:

| Interface                      | Generated | Description                                                                                                                                                                                                                        |
|--------------------------------|-----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `RuntimeType`                  | `false`   | runtime type must implement this interface to mark which prototype is used to create it                                                                                                                                            |
| `Prototype.Factory`            | `false`   | if blueprint implements factory, it means the prototype is used to create a single runtime type and will have methods `build` and `get` both on builder an on prototype interface that create a new instance of the runtime object |
| `Prototype.BuilderInterceptor` | `false`   | custom interceptor to modidfy buider before validation is done in method `build`                                                                                                                                                   |
| `Prototype`                    | `true`    | all prototypes implement this interface                                                                                                                                                                                            |
| `Prototype.Builder`            | `true`    | all prototype builders implement this interface, defines method `buildPrototype`                                                                                                                                                   |
| `Prototype.ConfiguredBuilder`  | `true`    | all prototype builders that support configuration implement this interface, defines method `config(Config)`                                                                                                                        |

## Configured providers

We can define a configured option as follows:
`@ConfiguredOption(key = "security-providers", provider = true, providerType = SecurityProviderProvider.class, providerDiscoverServices = false)`

Rules:

1. `providerType` MUST extend `io.helidon.common.config.ConfiguredProvider`
2. The method MUST return a `List` of the type the provider creates, so in this case we consider the `SecurityProviderProvider`
   to be capable of creating a `SecurityProvider` instance from configuration, so the return type would
   be `List<SecurityProvider>`, where `SecurityProvider extends NamedService` and  
   `SecurityProviderProvider extends ConfiguredProvider<SecurityProvider>`

This will expect the following configuration:

```yaml
security-providers:
  discover-services: true # optional, to override "providerDiscoverServices" option
  providers:
    - name: "my-provider"
      type: "http-basic"
      enabled: true
```

The generated code will read all nodes under `providers` and map them to an instance.
