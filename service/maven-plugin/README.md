Maven Plugin
---

The Helidon Service Maven Plugin provides the following goals:

1. Create application artifacts (`create-application`, `create-test-application`)

# Create application artifacts Maven goals

This goal creates artifacts that are only valid for the service (assembled from libraries and its own sources).

This goal generates application binding - a mapping of services to injection points (to bypass runtime lookups), and of service
descriptors (to bypass runtime service discovery and reflection) - generates class `ApplicationBinding`

The binding class is expected to be used from a custom main class, similar to the following:

```java
public static void main(String[] args) {
    // this will disable service discovery
    ServiceRegistryManager.start(Application__Binding.create());
}
```

To use a generated main class, enable it with the maven plugin, and an `ApplicationMain` class will be code generated with
a similar content.

Usage of this plugin goal is not required, yet it is recommended for final application module, it will add

- binding for injection points, to avoid runtime lookups
- explicit registration of all services into `ServiceRegistryConfig`, to avoid resource lookup and reflection at runtime
- overall speedup of bootstrapping, as all the required tasks to start a service registry are code generated

This goal should not be used for library modules (i.e. modules that do not have a Main class that bootstraps registry).

## Usage

Goal names:

- `create-application` - for production sources
- `create-test-application` - for test sources (only creates binding, main class not relevant)

Configuration options:

| Name               | Property                                        | Default              | Description                                                                     |
|--------------------|-------------------------------------------------|----------------------|---------------------------------------------------------------------------------|
| `packageName`      | `helidon.codegen.package-name`                  | Inferred from module | Package to put the generated classes in                                         |
| `moduleName`       | `helidon.codegen.module-name`                   | Inferred from module | Name of the JPMS module                                                         |
| `validate`         | `helidon.inject.application.validate`           | `true`               | Whether to validate application                                                 |
| `createMain`       | `helidon.inject.application.main.generate`      | `true`               | Whether to create application Main class                                        |
| `mainClassName`    | `helidon.inject.application.main.class.name`    | `ApplicationMain`    | Name of the generated Main class                                                |
| `createBinding`    | `helidon.inject.application.binding.generate`   | `true`               | Whether to create application binding                                           |
| `bindingClassName` | `helidon.inject.application.binding.class.name` | `ApplicationBinding` | Name of the generated binding class, for test, it is `TestApplication__Binding` |
| `failOnError`      | `helidon.inject.fail-on-error`                  | `true`               | Whether to fail when the plugin encounters an error                             |
| `failOnWarning`    | `helidon.inject.fail-on-warning`                | `false`              | Whether to fail when the plugin encounters a warning                            |
| `compilerArgs`     |                                                 |                      | Arguments of the Java compiler (both classes are compiled by the plugin)        | 

Configuration example in `pom.xml`:

```xml

<plugin>
    <groupId>io.helidon.service.registry</groupId>
    <artifactId>helidon-service-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>create-application</id>
            <goals>
                <goal>create-application</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```
