Inject Service Registry
----

An extension to the core service registry, Helidon Service Inject adds a few concepts:

- Injection - injection into constructor
- Scoped service instances - `Singleton` scope, optional `RequestScope`
- Interceptors - intercept method invocation
- Config beans - create instances from config

The main entry point to get service registry is
`io.helidon.service.inject.InjectRegistryManager` (part of implementation, not API), which can be used to obtain
`io.helidon.service.inject.api.InjectRegistry`.
The registry can then be used to lookup services (it extends the existing `ServiceRegistry`).

# Injection and scopes

Annotation type: `io.helidon.service.inject.api.Injection`

Annotations:

| Annotation class | Description                                                                                                                       |
|------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `Inject`         | Marks element as an injection point; although we prefer constructor injection, field and method injection works as well           |
| `Qualifier`      | Marker for annotations that are qualifiers                                                                                        |
| `Named`          | A qualifier that provides a name                                                                                                  |
| `NamedByClass`   | An equivalent of `Named`, that uses the fully qualified class name of the configured class as name                                |
| `Scope`          | Marker for annotations that are scopes                                                                                            |
| `Instance`       | A service that does not have a scope, yet supports injection, and can be looked up in registry                                    |
| `Singleton`      | Singleton scope - a service registry will create zero or one instances of this service (instantiation is lazy)                    |
| `RequestScope`   | Request scope - a service registry will create zero or one instance of this service per request scope instance                    |
| `RunLevel`       | A "layer" in which this service should be instantiated; not executed by injection, will be used when starting application         |
| `CreateFor`      | Create a service instance for each instance of the configured contract available in registry (usually for named)                  |
| `CreateForName`  | Parameter or field that will be injected with the name this service instance is created for (see `CreateFor`)                     |
| `Main`           | Marker for a custom main class, so appropriate stub can be generated during annotation processing, later replaced by Maven plugin |
| `Describe`       | Create a descriptor for a type that is not a service itself, but an instance would be provided at scope creation time             | 

Interfaces:

| Interface class          | Description                                                                                                           |
|--------------------------|-----------------------------------------------------------------------------------------------------------------------|
| `ServicesProvider`       | A service provider that creates zero or more qualified service instances at runtime (used for example by `ConfigBean` |
| `InjectionPointProvider` | A service provider that creates values for specific injection points                                                  |
| `QualifiedProvider`      | A service provider to resolve qualified injection points of any type (used for example by config value injection      |
| `QualifiedInstance`      | Used as a return type of some of the interfaces above, not to be implemented by users                                 |
| `ScopeHandler`           | Extension point to support additional scopes                                                                          |

## Injection into services

A service can have injection points, usually through constructor.

Example:
```java
@Injection.Inject
MyType(Contract1 contract, Supplier<Contract2> contract2, Optional<Contract3> contract3) {
    // ...
}
```

A dependency (such as `Contract1` above) may have the following forms (`Contract` stands for a contract interface, or class):

Instance based:
1. `Contract` - injects an instance of the contract with the highest weight from the registry
2. `Optional<Contract>` - same as previous, the contract may not have an implementation available in registry
3. `List<Contract>` - a list of all available instances in the registry

Supplier based (to break cyclic dependency, and to create instances as late as possible):
1. `Supplier<Contract>`
2. `Supplier<Optional<Contract>>`
3. `Supplier<List<Contract>>`

Service instance based (to obtain registry metadata in addition to the instance):
1. `ServiceInstance<Contract>`
2. `Optional<ServiceInstance<Contract>>`
3. `List<ServiceInstance<Contract>>`

# Interceptors

Interception provides capability to intercept call to a constructor or a method (even to fields when used as injection points).

Interception is (by default) only enabled for elements annotated with an annotation that is a `Trigger`. Annotation processor
configuration allows for creating interception "plumbing" for any annotation, or to disable it altogether.

Interception works "around" the invocation, so it can:

- do something before actual invocation
- modify invocation parameters
- do something after actual invocation
- modify response
- handle exceptions

Annotation type: `io.helidon.service.inject.api.Interception`

Annotations:

| Annotation class | Description                                             |
|------------------|---------------------------------------------------------|
| `Trigger`        | Marker for annotations that should trigger interception |

Interfaces:

| Interface class | Description                                                                                                                                                                                                                                           |
|-----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Interceptor`   | A service implementing this interface, and named with the annotation type (maybe using `NamedByClass`) will be used as interceptor of methods annotated with that annotation. Interceptor must call `proceed` method to handle the interception chain |
| `Factory`       | Used to create intercepted instances using delegation, rather than extension (used for example to intercept `ConfigBean` methods                                                                                                                      |

# Config beans

Config beans are types that are driven by a configuration key. They must use root configuration key (such as server, security).
Config beans may be repeatable, or single instance (see details below in Annotations section).

Annotation type: `io.helidon.service.inject.api.ConfigDriven`

Annotations:

| Annotation class | Description                                                                                            |
|------------------|--------------------------------------------------------------------------------------------------------|
| `ConfigBean`     | Marks a type as a config bean. In Helidon, we expect this to be used on `@Prototype.Blueprint` types   |
| `AtLeastOne`     | At least one instance must be configured in configuration                                              |
| `Repeatable`     | Zero or more instances may be configured in configuration                                              |
| `WantDefault`    | If `@default` (or unnamed) instance is not in configuration, create it using defaults                  |
| `OrDefault`      | Either there is a configured instance, or a default is created (can only be combined with `ConfigBean` |

In case `OrDefault` is used, the config bean is a singleton.
In all other cases, each config bean created is a named instance, where the name is either `@default`, or `name` key provided
in its configuration.

Config beans work in two steps (this is only true for `@Prototype.Blueprint` beans):

1. A `Prototype.Builder` instance is created, which can be updated by other services
2. A `Prototype` instance is created that injects the `Prototype.Builder`

# Startup

Helidon Service Inject adds a new startup provider - so if you use `io.helidon.Main` as your main class, service registry 
would be automatically started as long as it is on your classpath, and services in all run levels that are present will be
looked up (triggering `@PostConstruct` calls, which can be used to start the "container").