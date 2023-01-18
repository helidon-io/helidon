# pico-processor

This module provides *compile-time only* annotation processing, and is designed to look for javax/jakarta inject type annotations to then code-generate supporting DI activator source artifacts in support for your injection points and dependency model. It leverages the [tools module](../tools/README.md) to perform the necessary code generation when Pico annotations are found.

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
                    <groupId>io.helidon.pico</groupId>
                    <artifactId>helidon-pico-processor</artifactId>
                    <version>${helidon.version}</version>
                </path>
                <!-- optionally handle javax also (the default is jakarta.*) -->
                <path>
                    <groupId>javax.inject</groupId>
                    <artifactId>javax.inject</artifactId>
                    <version>${javax.injection.version}</version>
                </path>
                <path>
                    <groupId>javax.annotation</groupId>
                    <artifactId>javax.annotation-api</artifactId>
                    <version>${javax.annotations.version}</version>
                </path>
            </annotationProcessorPaths>
        </configuration>
    </plugin>
```
