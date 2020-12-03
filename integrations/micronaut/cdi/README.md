CDI integration with Micronaut
---

#Introduction

Goals of this integration:

- Allow invocation of Micronaut interceptors on CDI beans
- Allow injection of Micronaut beans into CDI beans

Non-goals:

- Injection of CDI beans into Micronaut beans
- Support for request scope in Micronaut

#Design

What I need to do
 - find all interceptors handled by micronaut
 - find all beans handled by micronaut
 - add interceptor bindings to the CDI bean
 - prepare all execution metadata for that interceptor (executable method)
 - add producers for Micronaut based beans

#Usage

The following must be done to use this integration:

## Annotation processor configuration

The following snippet shows compile configuration with annotation processors for maven
 that enables use of Micronaut Data.
The `helidon-integrations-micronaut-cdi-processor` must be used whenever integrating any
 Micronaut feature into Helidon MP.
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <forceJavacCompilerUse>true</forceJavacCompilerUse>
        <annotationProcessorPaths>
            <path>
                <groupId>io.micronaut</groupId>
                <artifactId>micronaut-inject-java</artifactId>
                <version>${version.lib.micronaut}</version>
            </path>
            <path>
                <groupId>io.micronaut</groupId>
                <artifactId>micronaut-validation</artifactId>
                <version>${version.lib.micronaut}</version>
            </path>
            <path>
                <groupId>io.micronaut.data</groupId>
                <artifactId>micronaut-data-processor</artifactId>
                <version>${version.lib.micronaut.data}</version>
            </path>
            <path>
                <groupId>io.helidon.integrations.micronaut</groupId>
                <artifactId>helidon-integrations-micronaut-cdi-processor</artifactId>
                <version>${helidon.version}</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```
 