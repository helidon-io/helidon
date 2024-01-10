Injections
---

# Notes and questions

Users can inject:

- `Contract` - the actual instance of the service
- `Supplier<Contract>` - the service MUST NOT be activated until `.get()` is called, can be injected regardless of what service
  implements
- `InjectionPointProvider<Contract>` - if the service implements this interface (handled by the Activator itself)
- `ServiceInstance<Contract>` - instance with metadata

+ Optional, List and combinations

Activation (when):

- `@Eager` services activate on scope activation
- Service instance (what user annotated) - when activation required (somebody injecting contract, or registry instance)
- Service targets (`QualifiedInstance`) - when resolving what to inject (`explodeFilterAndSort`), see types in `Activators`
  and `ActivatorsPerLookup`

Service can implement:

- `Contract` - the simplest service, just implement a contract and annotate with scope
- `Supplier<Contract>` - supplier of a contract (sometimes we cannot implement a service type - such as String) - not considered a
  service provider, as it MUST supply a value
- `InjectionPointProvider<Contract>` - supplier of qualified instances based on lookup (or injection into an injection point)
- `ServicesProvider` - supplier of qualified instances (once) - config beans use this approach
- `@DrivenBy(Driver.class)` - must not implement supplier, injection point provider - a service driven by instances of another
  contract (Driver)
  // future
- QualifiedProvider
- TypedQualifiedProvider

# Types

## User facing

### `ServicesProvider`

This service actually provides more than one instance, the instances may have different qualifiers.
(ServiceProviderProvider)

Scope of services: inherited from provider
Mutability: immutable within the scope
Registry: MUST store the result and re-use it

### `InjectionPointProvider`

This service provides instances based on lookup (usually based on injection point).

Scope of services: inherited from provider
Mutability: immutable for injection point, mutable for manual lookup (i.e. it would be too expensive to cache for lookup
combinations)
Registry: MUST store the result for injection points, re-invokes the provider for manual lookups

# Injection points

Each service may have zero or more injection points.

Injection point is either of:

1. parameter of a non-private constructor (the constructor is annotated with `Injection.Inject`, or is the only constructor
   available)
2. parameter of a non-private method (the method is annotated with `Injection.Inject`)
3. non-private field annotated with `Injection.Inject` (not recommended, hard to test)

To understand what can be injected, let's prepare a few terms:
`Service` - the implementation type annotated with a scope (such as `Injection.Singleton`)
`Contract` - an interface the `Service` implements (expected to be annotated by `Injection.Contract`)

Then the injection point type can be either of:

1. `Service`: any service can be injected when its implementation type is used (see `Direct` provider type below)
2. `Contract`: this is the case that injection is designed for (see `Contract` provider type below), `Service` is considered one
   of the contracts of the service, so all further options are for `Contract` only
3. `Optional<Contract>`: injection point that may not have a service that satisfies it
4. `List<Contract>`: there may be zero or more instances available and we want them all
5. `Supplier<Contract>`: the injected instance is obtained only once `.get()` is called, used to break cyclic dependency, or to
   get instances from non-singleton scopes only when needed
6. `Supplier<Optional<Contract>>`: a combination of the above - supplier is needed, yet the service is optional
7. `Supplier<List<Contract>>`: same as above, but we either need to break cyclic dependency, or some of the services may be in a
   non-singleton scope, or some services may use ServicesProvider or InjectionPoint provider that may yield 0..N values (not
   exactly 1)
8. `Optional<Supplier<Contract>>`: WITH RESTRICTIONS
9. `List<Supplier<Contract>>`: WITH RESTRICTIONS

Relevant methods on Service registry (`Services` class) and their mapping to the injection points (ignoring shortcut methods),
and behavior on "not found"

| Injection point type           | Registry method                              | Not found behavior                                                                                                |
|--------------------------------|----------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| `Contract`                     | `T get(Lookup)`                              | Throws an exception                                                                                               |
| `Optional<Contract>`           | `Optional<T> first(Lookup)`                  | `Optional.empty()`                                                                                                |
| `Supplier<Contract>`           | `Supplier<T> supply(Lookup)`                 | Throws an exception (either lookup if no matching descriptor found, or runtime, if not resolved into an instance) |
| `Supplier<Optional<Contract>>` | `Supplier<Optional<T> supplyFirst(Lookup)`   | `Optional.empty()`                                                                                                |
| `List<Contract>`               | `List<T> all(Lookup)`                        | Empty list                                                                                                        |
| `Supplier<List<Contract>>`     | `Supplier<List<T>> supplyAll(Lookup lookup)` | Empty list                                                                                                        |

# Providers

A user implements a scoped typed, that acts as a provider of a service. The following options are available (the type is expected
to be annotated with a scope, such as `@Injection.Singleton`):

| Type                  | Declaration                                                 | Description                                                                |
|-----------------------|-------------------------------------------------------------|----------------------------------------------------------------------------|
| Direct                | `class Service`                                             | A simple service implementation                                            |
| Contract              | `class Service implements Contract`                         | Service implements an injectable contract interface                        |
| Provider              | `class Service implements Supplier<Contract>`               | Service provider (can use `ServiceProvider` instead of `Supplier` as well) |
| IP provider           | `class Service implements InjectionPointProvider<Contract>` | Service that provides instance(s) based on injection point                 |
| Provider of providers | `class Service implements ServicesProvider<Contract>`       | Service that provides 0 to N other services                                |

The following table lists appropriate use cases for each provider type (and how it is used in Helidon):

| Type                  | Use case                                                       | Example in Helidon             |
|-----------------------|----------------------------------------------------------------|--------------------------------|
| Direct                | Simple service                                                 | Testing - such as `ToolBox`    |
| Contract              | Implementation of a contract (most common)                     | `ConfigProducer`               |
| Provider              | Create an instance per call, usually in non-singleton services | N/A                            |
| IP provider           | Where we need to analyze the injection point                   | `OciRegionProvider`            |
| Provider of providers | Create one or more instances based on external state           | `ConfigDrivenServiceProvider`  |
| Injection resolver    | Injection point may be resolved by the service itself          | `ConfigDrivenInstanceProvider` |

# Driven by

Services may be annotated with `@DrivenBy(Contract.class)` - in such a case, a new service instance will be created for
each `Contract` instance in the registry, named with the same name.

The driven service MAY have an injection point with `Contract` that is unqualified, which will be satisfied by the driving
instance. The driven service MAY have an injection point of type String, annotated with `@Name` qualifier, which will be
satisfied by the name of the driving instance. If the driving instance is not named, default name will be used (driven
instances are always named)

# Config Beans

Config beans are based on types that are discovered from configuration.
Each instance created for a specific path in configuration tree is called a `Config Bean`.
Config beans may have zero or more instances, depending on `@ConfigBean` annotation on the config bean Blueprint.
Each instance that is created is automatically `@Named`, with `@default` being the default name.
Config beans are always created as `@Singleton`, and `@Eager`, so the instances are available in the registry immediately
after the singleton scope is active.

# Service registry startup sequence

1. Create `Services` instance
2. Apply all modules
    1. Register ServiceManager for each ServiceDescriptor
3. Apply all applications
    1. Register service injection plans for each ServiceManager (which ServiceDescriptors should satisfy each injection point)
4. Initialize `@Service` scope (never creates any instances)
5. Initialize `@Singleton` scope (may create instances of eager services, may need injections of suppliers from other scopes)

`ServiceManager`

- 1:1 mapping to service descriptor
- 1:N mapping to `Activator`

`Activator`

- each service manager has an instance manager for the service it holds, and may have additional instance managers if the service
  is a `ServicesProvider`
- all of these instances are managed together (lifecycle), the "main" instance can receive injection points and have postconstruct
  and predestroy