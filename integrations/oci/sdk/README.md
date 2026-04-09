# helidon-integrations-oci-sdk

This directory contains the legacy OCI SDK integration modules for Helidon 4.x.

> [!WARNING]
> The remaining `helidon-integrations-oci-sdk-*` modules are deprecated in favor of the
> [helidon-integrations-oci module](../oci) and will be removed in a future release.

## Helidon MP / CDI

The [cdi](./cdi) module is deprecated. For new applications and migrations, use
[helidon-integrations-oci module](../oci) instead.

In Helidon MP applications, the replacement path is:

1. Add `helidon-integrations-oci` to provide OCI services via Helidon Service Registry.
2. Use the Helidon MP service-registry CDI bridge from
   `io.helidon.microprofile.cdi.ServiceRegistryExtension` so those services are available for CDI injection.

Refer to the [cdi](./cdi) module only for legacy behavior in existing applications that still depend on
`helidon-integrations-oci-sdk-cdi`.

## Helidon SE / Service Registry

### Helidon Service Registry and OCI SDK Integration
This section describes the legacy **Helidon SE** support. It is also deprecated in favor of
[helidon-integrations-oci module](../oci). Please familiarize yourself with the basics of the
[Helidon Service Registry](../../../service) and terminology before continuing further if you need to maintain an
existing 4.x application.

The **Helidon Service Registry** offers two modules for integrating with the **OCI SDK API** - the _codegen_ module and the _runtime_ module.

1. The [codegen](./codegen) module is required to be on your annotation processor [APT] classpath. It is not needed at runtime, however. When used on the APT classpath, it will observe cases where your application uses the _@Service.Inject_ annotation on API services from the **OCI SDK**.  When it finds such cases, it generates service source code representing the API service you are injecting. These generated _Services_ will then be used by the **Helidon Service Registry** at runtime.

2. The [runtime](./runtime) module is required to be on your runtime classpath, but is not needed at compile time. This module supplies the default implementation for OCI's authentication providers, as well as other OCI extensibility classes (see the [javadoc](./runtime/src/main/java/io/helidon/integrations/oci/sdk/runtime/package-info.java) for configuration details). The [OciExtension](./runtime/src/main/java/io/helidon/integrations/oci/sdk/runtime/OciExtension.java) class is the
main access point for programmatically obtaining the global [OciConfig](./runtime/src/main/java/io/helidon/integrations/oci/sdk/runtime/OciConfigBlueprint.java) configuration.

### Usage

In your pom.xml, add this plugin to be run as part of the compilation phase:
```pom.xml
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
                <groupId>io.helidon.integrations.oci.sdk</groupId>
                <artifactId>helidon-integrations-oci-sdk-codegen</artifactId>
                <version>${helidon.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

Add the runtime dependency to your pom.xml, along with any other OCI SDK library that is required by your application:
```pom.xml
<dependency>
    <groupId>io.helidon.integrations.oci.sdk</groupId>
    <artifactId>helidon-integrations-oci-sdk-runtime</artifactId>
</dependency>

...
<!-- arbitrarily selected OCI libraries - use the libraries appropriate for your application -->
<dependency>
    <groupId>com.oracle.oci.sdk</groupId>
    <artifactId>oci-java-sdk-ailanguage</artifactId>
</dependency>
<dependency>
    <groupId>com.oracle.oci.sdk</groupId>
    <artifactId>oci-java-sdk-objectstorage</artifactId>
</dependency>
```

_**Note that if you are using JPMS (i.e., _module-info.java_), then you will also need to be sure to export the _io.helidon.integrations.generated_ derivative package names from your module(s)._**

Additionally, write your application using **@Inject** for any OCI SDK API. Here is a simple example that uses _Object Storage_:

```java
@Singleton
class AServiceUsingObjectStorage {
    private final ObjectStorage objStorageClient;

    @Inject
    AServiceUsingObjectStorage(ObjectStorage objStorage) {
        this.objStorageClient = Objects.requireNonNull(objStorage);
    }

    String namespaceName() {
        GetNamespaceResponse namespaceResponse = objStorageClient
                .getNamespace(GetNamespaceRequest.builder().build());
        return namespaceResponse.getValue();
    }
    ...
}
```

Besides being able to inject OCI SDK APIs, note also that these contracts are injectable (defined in the runtime module):
* [OciAvailability](runtime/src/main/java/io/helidon/integrations/oci/sdk/runtime/OciAvailability.java) - can be used to determine if the current runtime environment is executing on an OCI compute node.
* [Region](runtime/src/main/java/io/helidon/integrations/oci/sdk/runtime/OciRegionProvider.java) - can be used to determine the current region where the current compute environment is running. It is recommended to inject this as an _java.lang.Optional_ instance.

See the [runtime](./runtime) module for additional notes for configuration and programmatic accessors.

### How it works
See the [OciInjectionCodegenObserver](codegen/src/main/java/io/helidon/integrations/oci/sdk/codegen/OciInjectionCodegenObserver.java) for a more technical description for how the processor observes _@Inject_ usage. In summary, this processor will observe **OCI SDK** injection points and then code generate **Activators** enabling injection of SDK services in conjunction with the [runtime](./runtime) module on the classpath.
