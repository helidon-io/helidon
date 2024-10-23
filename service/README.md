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
 Alternatively, 
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

## Annotation processor setup

To use Service registry code generator, you need to add the Helidon annotation processor and the service registry code generator to your annotation processor path.

For Maven:
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.helidon.codegen</groupId>
                        <artifactId>helidon-codegen-apt</artifactId>
                        <version>${helidon.version}</version>
                    </path>
                    <path>
                        <groupId>io.helidon.service</groupId>
                        <artifactId>helidon-service-codegen</artifactId>
                        <version>${helidon.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

Additional options can be configured to customize the behavior. For example the default approach is that all contracts
are auto-discovered. We can switch contract discovery to annotated only, in such a case the following annotations are available:
Use `io.helidon.service.registry.Service.Contract` on your contract interface (if not annotated, such an interface would not be
considered a contract and will not be discoverable using the registry - configurable).
Use `io.helidon.service.registry.Service.ExternalContracts` on your service provider type to
add other types as contracts, even if not annotated with `Contract` (i.e. to support third party libraries).

There is also an option to exclude specific types from being contracts (such as `Closeable` could be excluded).

To enable this (Maven):

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <compilerArgs>
            <!-- Disable automatic adding of contracts -->
            <arg>-Ahelidon.registry.autoAddNonContractInterfaces=false</arg>
            <!-- Add contract exclusion (not needed if above is set to true) -->
            <arg>-Ahelidon.registry.nonContractTypes=java.io.Serializable,java.lang.AutoCloseable,java.io.Closeable</arg>
        </compilerArgs>
        <!-- Annotation processor setup etc. -->
    </configuration>
</plugin>
```

## Behind the scenes

For each service, Helidon generates a service descriptor (`ServiceProvider__ServiceDescriptor`).
This descriptor is discovered at runtime and used to instantiate a service without the need to use reflection.

Reflection is used only to obtain an instance of the service descriptor (by using its public `INSTANCE` singleton field). As both
the descriptor and the `INSTANCE` field are always public, there is no need to add `opens` to `module-info.java`.
Support for GraalVM native image is handled in Helidon native image extension, by registering all service descriptors for
reflection (the class, and the field).

### Registry file format

The service registry uses a `service-registry.json` file in `META-INF/helidon` directory to store the main metadata of
the service. This is to allow proper ordering of services (Service weight is one of the information stored) and
lazy loading of services (which is the approach chosen in the core service registry).

The format is as follows (using `//` to comment sections, not part of the format):

```json
// root is an array of modules (we always generate a single module, but this allows a combined array, i.e. when using shading
[
  {
    // version of the metadata file, defaults to 1 (and will always default to 1)
    "version": 1,
    // name of the module
    "module": "io.helidon.example",
    // all services in this module
    "services": [
      {
        // version of the service descriptor, defaults to 1 (and will always default to 1)
        "version": 1,
        // core (Service registry) or inject (Service Injection), defaults to core
        "type": "inject",
        // weight, defaults to 100
        "weight": 91.4,
        // class of the service descriptor - generated type that contains public constant INSTANCE
        "descriptor": "io.helidon.example.ServiceImpl__ServiceDescriptor",
        // all contracts this service implements
        "contracts": [
          "io.helidon.example.ServiceApi"
        ]
      }
    ]
  }
]
```

# Helidon Service Inject

See details in [Helidon Inject](inject/README.md).