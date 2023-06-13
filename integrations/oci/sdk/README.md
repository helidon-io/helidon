# helidon-integrations-oci-sdk

There are two different approaches for [OCI SDK](https://docs.oracle.com/en-us/iaas/Content/API/SDKDocs/javasdk.htm) integration from Helidon depending upon which type of application you are developing.
* **Helidon MP** (using _CDI_). For this refer to the [cdi](./cdi) module.
* **Helidon SE** (not using _CDI_). For this refer to the information below.


## Helidon Injection Framework and OCI SDK Integration
This section only applies for **Helidon SE** type applications. If you are using **Helidon MP** then this section does not apply to you, and you should instead refer to the [cdi](./cdi) module.

The **Helidon Injection Framework** offers a few different ways to integrate to 3rd party libraries. The **OCI SDK** library, however, is a little different in that a special type/style of fluent builder is needed when using the **OCI SDK**. This means that you can't simply use the _new_ operator when creating instances; you instead need to use the imperative fluent builder style. Fortunately, though, most of the **OCI SDK** follows the same pattern for accessing the API via this fluent builder style. Since the **Helidon Injection Framework** leverages compile-time DI code generation, this arrangement makes it very convenient to generate the correct underpinnings that leverages a template following this fluent builder style.

The net of all of this is that there are two modules that you will need to integrate DI into your **Helidon SE** application.

1. The [processor](./processor) module is required to be on your compiler / APT classpath. It will observe cases where you are _@Inject_ services from the **OCI SDK** and then code-generate the appropriate [Activator](../api/src/main/java/io/helidon/pico/api/Activator.java)s for those injected services. Remember, the _processor_ module only needs APT classpath during compilation - it is not needed at runtime.

2. The [runtime](./runtime) module is required to be on your runtime classpath. This module supplies the default implementation for OCI authentication providers, and OCI extensibility into Helidon.


### MP Modules
* [cdi](./cdi) - required to be added as a normal dependency in your final application.


### Non-MP Modules
* [processor](./processor) - required to be in the APT classpath.
* [runtime](./runtime) - required to be added as a normal dependency in your final application.
* [tests](./tests) - tests for OCI SDK integration.


### Usage

In your pom.xml, add this plugin to be run as part of the compilation phase:
```pom.xml
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
            <forceJavacCompilerUse>true</forceJavacCompilerUse>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.helidon.integrations.oci.sdk</groupId>
                        <artifactId>helidon-integrations-oci-sdk-processor</artifactId>
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

### How it works
See the [InjectionProcessorObserverForOci javadoc](processor/src/main/java/io/helidon/integrations/oci/sdk/processor/InjectionProcessorObserverForOCI.java) for a description. In summary, this processor will observe **OCI SDK** injection points and then code generate **Activators** enabling injection of SDK services in conjuction with the [runtime](./runtime) module on the classpath.
