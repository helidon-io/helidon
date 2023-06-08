# pico-integrations-oci

**_Special Note:_**
If your application is using **Helidon MP** then you should be using [OciExtension](../../../integrations/oci/sdk/cdi/src/main/java/io/helidon/integrations/oci/sdk/cdi/OciExtension.java) and _NOT_ these modules. These modules are only intended for non-MP based Helidon applications.

## Modules

* [processor](./processor) - required to be in the APT classpath.
* [runtime](./runtime) - required to be added as a normal dependency in your final application.
* [tests](./tests) - tests for OCI integration.

## Usage

In your pom.xml, add this plugin to be run as part of the compilation phase:
```pom.xml
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
            <forceJavacCompilerUse>true</forceJavacCompilerUse>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.helidon.pico.integrations.oci</groupId>
                        <artifactId>helidon-pico-integrations-oci-processor</artifactId>
                        <version>${helidon.version}</version>
                    </path>
                </annotationProcessorPaths>
        </configuration>
    </plugin>
```

Add the runtime dependency to your pom.xml, along with any other OCI SDK library that is required by your application:
```pom.xml
    <dependency>
        <groupId>io.helidon.pico</groupId>
        <artifactId>helidon-pico-runtime</artifactId>
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
