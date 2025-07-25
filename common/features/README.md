Helidon Features
-----

Helidon considers features to be a tree structure, with top level features being what the user wants to use,
such as `WebServer`, `WebClient`, `Config`, `Security` etc.

There can be "finer-grained" features, such as `Config/YAML`, `Security/Providers/OIDC`. The distinction is done through the
`Features.Path` annotation, where the root path is the top-level feature.

# How to define a Helidon Feature
- Each feature `module-info.java` is to be annotated with `@Features.*` annotations
- For preview features (production ready, but being worked on), use `@Features.Preview`
- For incubating features (not production ready, for preview only), use `@Features.Incubating`
- For information related to Native image, use `@Features.Aot`

An annotation processor (codegen) is available to process these annotations and generate a runtime JSON file with module information.

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
 * Google login authentication provider.
 *
 * @deprecated use our OpenID Connect security provider instead
 */
@Features.Name("Google Login")
@Features.Description("Security provider for Google login button authentication and outbound")
@Features.Flavor({HelidonFlavor.SE, HelidonFlavor.MP})
@Features.Path({"Security", "Provider", "Google-Login"})
@Features.Aot(false)
@Deprecated(forRemoval = true, since = "4.3.0")
module io.helidon.security.providers.google.login {
    requires static io.helidon.common.features.api;
    // other module dependencies and configuration
}
```

## Dependency to be added
```xml
<dependency>
    <groupId>io.helidon.common.features</groupId>
    <artifactId>helidon-common-features-api</artifactId>
    <optional>true</optional>
</dependency>
```

## Annotation processor setup
This example provides full `plugins` tag, if exists, update only relevant sections. This example is for Helidon modules,
when used outside Helidon repository, the `dependencies` section is not required.
```xml
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
                    <groupId>io.helidon.common.features</groupId>
                    <artifactId>helidon-common-features-codegen</artifactId>
                    <version>${helidon.version}</version>
                </path>
            </annotationProcessorPaths>
        </configuration>
        <dependencies>
            <dependency>
                <groupId>io.helidon.codegen</groupId>
                <artifactId>helidon-codegen-apt</artifactId>
                <version>${helidon.version}</version>
            </dependency>
            <dependency>
                <groupId>io.helidon.common.features</groupId>
                <artifactId>helidon-common-features-codegen</artifactId>
                <version>${helidon.version}</version>
            </dependency>
        </dependencies>
    </plugin>
</plugins>
```

# Registry file format

## Version 1

Created by `helidon-common-features-processor` using deprecated `Feature` annotation.
This is now obsolete and will be removed from Helidon in version 5.0.0

The registry is stored in each module in `META-INF/helidon/feature-metadata.properties`.
The following keys are supported:

| Key    | Name             | Description                                     |
|--------|------------------|-------------------------------------------------|
| `m`    | Module           | Name of the module                              |
| `n`    | Name             | Feature name                                    |
| `d`    | Description      | Feature description                             |
| `s`    | Since            | First version that contains this feature        |
| `p`    | Path             | Path of the feature                             |
| `in`   | Flavor           | Flavor(s) that should print this feature        |
| `not`  | Not in Flavor    | Flavor(s) that this feature is not supported in |
| `aot`  | Ahead of time    | Whether ahead of time compilation is supported  |
| `aotd` | Description aot  | Description of AOT support                      |
| `i`    | Incubating       | This is an incubating feature                   |
| `pr`   | Preview          | This is a preview feature                       |
| `dep`  | Deprecated       | This feature is deprecated                      |
| `deps` | Deprecated since | First version this feature was deprecated in    |

## Version 2

Created by `helidon-common-features-codegen`, using new `Features.*` annotations.
The registry is stored in each module in `META-INF/helidon/feature-registry.json`.
The root element is an array, to allow merging of all features into a single file.

The format is as follows (using `//` to comment sections, not part of the format):

```json
// root is an array of modules (we always generate a single module, but this allows a combined array, i.e. when using shading
[
    {
        // version of the metadata file, defaults to 2 (and will always default to 2)
        "version": 2,
        // name of the module (required)
        "module": "io.helidon.example.feature",
        // feature name (required)
        "name": "Feature",
        // optional, defaults to feature name  
        "path": [
            "Example",
            "Feature"
        ],
        // optional, recommended  
        "description": "Description of this feature",
        // optional  
        "since": "4.2.0",
        // which flavor(s) should print this feature (optional)  
        "flavor": [
            "SE"
        ],
        // in which flavor we should warn that this feature is present on classpath (optional)   
        "invalid-flavor": [
            "MP"
        ],
        // native image restrictions (optional), defaults to fully supported 
        "aot": {
            "supported": true,
            // description (optional)
            "description": "Only supports buzz and foo"
        },
        // status of this feature, defaults to "Production", or Deprecated if deprecation is set (optional)
        // allowed values: "Production", "Preview", "Incubating", "Deprecated"
        "status": "Production",
        // deprecation information, defaults to not-deprecated (optional)
        "deprecation": {
            "deprecated": true,
            // optional
            "description": "Use foo module instead",
            // optional
            "since": "4.3.0"
        }
    }
]
```