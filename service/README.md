Helidon Service Registry
----

# Core Service Registry

Provides a replacement for Java ServiceLoader with basic inversion of control mechanism.
Each service may have constructor parameters that expect instances of other services.

The constructor dependency types can be as follows (`Contract` is used as the contract the service implements)

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
add other types as contracts, even if not annotated with `Contract` (i.e. to support third party libraries). Alternatively, 
`java.util.function.Supplier` can also be used in this scenario.

Use `io.helidon.service.registry.Service.Descriptor` to create a hand-crafted service descriptor (see below "Behind the scenes")

Service example:

```java
import io.helidon.service.registry.Service;

@Service.Provider
class MyService implements MyContract {
    MyService() {
    }

    @Override
    public String message() {
        return "MyService";
    }
}
```

Service with dependency example:

```java
import io.helidon.service.registry.Service;

@Service.Provider
class MyService2 implements MyContract2 {
    private final MyContract dependency;

    MyService2(MyContract dependency) {
        this.dependency = dependency;
    }

    @Override
    public String message() {
        return dependency.message();
    }
}
```

Service with `java.util.function.Supplier` as a contract example:

```java
import java.util.function.Supplier;

import io.helidon.service.registry.Service;

@Service.Provider
// the type must be fully qualified, as it is code generated
class MyService3 implements Supplier<Optional<com.foo.bar.MyContract3>> {
    
    MyService3() {
    }

    @Override
    public Optional<MyContract3> get() {
         return Optional.of(MyContract3.builder().message("MyService3").build());
    }
}
```

## Behind the scenes

For each service, Helidon generates a service descriptor (`ServiceProvider__ServiceDescriptor`).
This descriptor is discovered at runtime and used to instantiate a service without the need to use reflection.

Reflection is used only to obtain an instance of the service descriptor (by using its public `INSTANCE` singleton field). As both
the descriptor and the `INSTANCE` field are always public, there is no need to add `opens` to `module-info.java`.
Support for GraalVM native image is handled in Helidon native image extension, by registering all service descriptors for
reflection (the class, and the field).

### Registry file format

The service registry uses a `service.registry` in `META-INF/helidon` directory to store the main metadata of
the service. This is to allow proper ordering of services (Service weight is one of the information stored) and
lazy loading of services (which is the approach chosen in the core service registry).

The format is as follows:

```
registry-type:service-descriptor-type:weight(double):contracts(comma separated)
```

Example:

```
core:io.helidon.ContractImpl__ServiceDescriptor:101.3:io.helidon.Contract1,io.helidon.Contract2
```
