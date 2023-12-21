# helidon-integrations-oci-sdk

There are two different approaches for [OCI SDK](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk.htm) integration from Helidon depending upon which type of application you are developing.
* **Helidon MP** (using _CDI_). For this refer to the [cdi](./cdi) module.
* **Helidon SE** (not using _CDI_). For this refer to the information below.

## Helidon Injection Framework and OCI SDK Integration
This section only applies for **Helidon SE** type applications. If you are using **Helidon MP** then this section does not apply to you, and you should instead refer to the [cdi](./cdi) module. If you are using **Helidon SE** then continue reading below. Please familiarize yourself with the basics of the [Helidon Injection Framework](../../../inject) and terminology before continuing further.

The **Helidon Injection Framework** offers two modules for integrating with the **OCI SDK API** - the _processor_ module and the _runtime_ module.

1. The [processor](./processor) module is required to be on your compiler / annotation processor [APT] classpath. It is not needed at runtime, however. When used on the compiler / APT classpath, it will observe cases where your application uses the _@Inject_ annotation on API services from the **OCI SDK**.  When it finds such cases, it generates source code defining an _Activator_ representing the API service you are injecting. These generated _Activator_ will then be used by the **Helidon Injection Framework** at runtime.

2. The [runtime](./runtime) module is required to be on your runtime classpath, but is not needed at compile time. This module supplies the default implementation for OCI's authentication providers, as well as other OCI extensibility classes (see the [javadoc](./runtime/src/main/java/io/helidon/integrations/oci/sdk/runtime/package-info.java) for details).


### MP Modules
* [cdi](./cdi) - required to be added as a normal dependency in your final application.


### Non-MP Modules
* [processor](./processor) - required to be in the APT classpath.
* [runtime](./runtime) - required to be added as a normal dependency in your final application.
* [tests](./tests) - tests for OCI SDK integration.


### Usage

In your pom.xml, add the Helidon maven plugin, and the OCI code generation extension to be run as part of the compilation phase:
```pom.xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <forceJavacCompilerUse>true</forceJavacCompilerUse>
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
See the [InjectionProcessorObserverForOci](processor/src/main/java/io/helidon/integrations/oci/sdk/processor/InjectionProcessorObserverForOCI.java) for a more technical description for how the processor observes _@Inject_ usage. In summary, this processor will observe **OCI SDK** injection points and then code generate **Activators** enabling injection of SDK services in conjunction with the [runtime](./runtime) module on the classpath.
