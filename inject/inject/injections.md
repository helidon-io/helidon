Injections
---

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
   non-singleton scope

The following is not supported (with explanation):

1. `Optional<Supplier<Contract>>`: we may only be able to discover that the service is not available when calling the supplier,
   not when injecting (see `IP Provider` or `Provider of providers` types below, and also when the supplier is in different scope)
2. `List<Supplier<Contract>>`: for the same reasons as the previous case - we may not be able to compute the full list at time of
   injection

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

| Type                     | Declaration                                                 | Description                                                                |
|--------------------------|-------------------------------------------------------------|----------------------------------------------------------------------------|
| Direct                   | `class Service`                                             | A simple service implementation                                            |
| Contract                 | `class Service implements Contract`                         | Service implements an injectable contract interface                        |
| Provider                 | `class Service implements Supplier<Contract>`               | Service provider (can use `ServiceProvider` instead of `Supplier` as well) |
| IP provider              | `class Service implements InjectionPointProvider<Contract>` | Service that provides instance(s) based on injection point                 |
| Provider of providers    | `class Service implements ServiceProviderProvider`          | Service that provides 0 to N other services                                |
| Injection resolver       | `class Service implements InjectionResolver`                | Service that may resolve some injection points itself                      |
| Qualified provider       | `class Service implements QualifiedProvider`                | Service provider for a qualifier that resolves at runtime                  |
| Typed qualified provider | `class Service implements QualifiedProviderTyped<Contract>` | Service provider for a qualifier that resolves at runtime                  |

Notes

- `InjectionResolver` - work around for config driven to work

The following table lists appropriate use cases for each provider type (and how it is used in Helidon):

| Type                     | Use case                                                                                                       | Example in Helidon             |
|--------------------------|----------------------------------------------------------------------------------------------------------------|--------------------------------|
| Direct                   | Simple service                                                                                                 | Testing - such as `ToolBox`    |
| Contract                 | Implementation of a contract (most common)                                                                     | `ConfigProducer`               |
| Provider                 | Create an instance per call, usually in non-singleton services                                                 | N/A                            |
| IP provider              | Where we need to analyze the injection point                                                                   | `OciRegionProvider`            |
| Provider of providers    | Create one or more instances based on external state                                                           | `ConfigDrivenServiceProvider`  |
| Injection resolver       | Injection point may be resolved by the service itself                                                          | `ConfigDrivenInstanceProvider` |
| Qualified provider       | A qualifier on more than one type                                                                              | TBD: `HttpHeaderProvider`      |
| Typed qualified provider | A qualified type for a specific contract, when we need to satisfy same contract differently based on qualifier | TBD: `HttpUriProvider`         |


