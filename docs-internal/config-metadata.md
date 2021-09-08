# Configuration metadata

A new module `helidon-config-metadata` now exists with annotations that can be used in Helidon source code
the document what configuration is used.

These annotations are processed by `helidon-config-metadata-processor`.

To add meta configuration:

1. Add the following dependency to your `pom.xml`:
```xml
<dependency>
    <groupId>io.helidon.config</groupId>
    <artifactId>helidon-config-metadata</artifactId>
    <scope>provided</scope>
    <optional>true</optional>
</dependency>
```
2. Add the following compiler plugin configuration to add the annotation processor:
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <forceJavacCompilerUse>true</forceJavacCompilerUse>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.helidon.config</groupId>
                        <artifactId>helidon-config-metadata-processor</artifactId>
                        <version>${helidon.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```
3. Update the `module-info.java` by adding `requires static io.helidon.config.metadata;`
4. Annotate the configured class using `@Configured` - usually the builder class. If there is only a factory method, annotate 
     the class containing the factory method
5. Annotate builder methods using `@ConfiguredOption` - the type of the parameter will be used as type of the property, provides 
     full customization using annotation properties
6. In case a factory method is the only one available, annotate it with repeating `@ConfiguredOption` to list all annotations
7. Look at existing examples if in doubt
8. Check the output in `target/classes/META-INF/helidon` to see what was generated

