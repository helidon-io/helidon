# New Feature Catalog

Current `FeatureCatalog` explicitly mentions all Helidon features and adds them.
This requires us to keep track of features separately from the feature sources, which is hard to refactor (and keep in sync).
To remedy this situation, the following approach is now to be used:
- Each feature `module-info.java` is to be annotated with `@Feature` annotation
- For preview features (production ready, but being worked on), use `@Preview`
- For incubating features (not production ready, for preview only), use `@Incubating`
- For information related to Native image, use `@Aot`

An annotation processor is available to process this annotation and generate a runtime property file with module information.

## Module info updates

The module info must require the API, as it is used within the file. As the annotations are source only, we can use 
`requires static`:

```java
requires static io.helidon.common.features.api;
```

Module info example:
```java
import io.helidon.common.features.api.Aot;
import io.helidon.common.features.api.Feature;
import io.helidon.common.features.api.HelidonFlavor;
import io.helidon.common.features.api.Preview;

/**
 * GraphQL server integration with Helidon Reactive WebServer.
 */
@Preview
@Feature(value = "GraphQL", 
        in = HelidonFlavor.SE, 
        invalidIn = {HelidonFlavor.MP, HelidonFlavor.NIMA})
@Aot(description = "Incubating support, tested on limited use cases")
module io.helidon.nima.graphql.server {
    requires static io.helidon.common.features.api;
    // other module dependencies and configuration
}
```

## Dependency to be added
```xml
<dependency>
    <groupId>io.helidon.common.features</groupId>
    <artifactId>helidon-common-features-api</artifactId>
    <scope>provided</scope>
    <optional>true</optional>
</dependency>
```

## Annotation processor setup
This example provides full `plugins` tag, if exists, update only relevant sections.
```xml
<plugins>
    <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <configuration>
            <annotationProcessorPaths>
                <path>
                    <groupId>io.helidon.common.features</groupId>
                    <artifactId>helidon-common-features-processor</artifactId>
                    <version>${helidon.version}</version>
                </path>
            </annotationProcessorPaths>
        </configuration>
    </plugin>
</plugins>
```
