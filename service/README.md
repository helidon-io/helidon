Helidon Service Registry
----

# Core Service Registry

Provides a replacement for Java ServiceLoader with basic inversion of control mechanism.
Each service may have constructor parameters that expect instances of other services.
The types can be as follows (`Contract` is used as the contract the service implements)

- `Contract` - simply get an instance of another service
- `Optional<Contract>` - get an instance of another service, the other service may not be available
- `List<Contract>` - get instances of all services that are available
- `Supplier<Contract>`, `Supplier<Optional<Contract>>`, `Supplier<List<Contract>>` - equivalent methods but the value is resolved
  when `Supplier.get()` is called, to allow more control

Equivalent behavior can be achieved programmatically through `io.helidon.service.registry.ServiceRegistry` instance. This can be
obtained from a `ServiceRegistryManager`.

## Declare a service

Use `io.helidon.service.registry.Service.Provider` annotation on your service provider type (implementation of a contract).
Use `io.helidon.service.registry.Service.Contract` on your contract interface (if not annotated, such an interface would not be
considered a contract and will not be discoverable using the registry - configurable).
Use `io.helidon.service.registry.Service.ExternalContracts` on your service provider type to
add other types as contracts, even if not annotated with `Contract` (i.e. to support third party libraries).

Use `io.helidon.service.registry.Service.Descriptor` to create a hand-crafted service descriptor (see below "Behind the scenes")

## Helidon services

There are a few services you can expect in Helidon that you can use in your own services. This of course depends on what modules
are available.

| Module           | Contract   | Description                                                                                      |
|------------------|------------|--------------------------------------------------------------------------------------------------|
| `helidon-config` | MetaConfig | Provides Config instance to configure config sources and other components                        |
| `helidon-config` | Config     | The target config instance, created from meta configuration (can be overridden via GlobalConfig) |

## Behind the scenes

For each service, Helidon generates a service descriptor (`ServiceProvider__ServiceDescriptor`).
This descriptor is discovered at runtime and used to instantiate a service without the need to use reflection.

Reflection is used only to obtain an instance of the service descriptor (by using its public `INSTANCE` singleton field). As both
the descriptor and the `INSTANCE` field are always public, there is no need to add `opens` to `module-info.java`.
Support for GraalVM native image is handled in Helidon native image extension, by registering all service descriptors for
reflection (the class, and the field).

# Inject Service Registry

An extension to `ServiceRegistry` that provides full-blown dependency injection, supporting interception.
