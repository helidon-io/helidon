<!--@frontmatter
description: "Using Helidon in your Maven project"
navigation:
  icon: i-lucide-wrench
-->
# Maven

This guide describes Helidon’s support for Maven projects.

## Introduction

Helidon supports Maven by providing the following:

1.  The Helidon Application parent POM
2.  Dependency management via the Helidon BOM and Dependencies POMs
3.  The `helidon-service-maven-plugin` application linker
4.  The `helidon-maven-plugin` for custom Java runtime images

## The Helidon Application POM

Helidon examples and projects generated using the [Helidon
Quickstart](quickstart.md) use a Helidon application POM as their parent. This
parent provides dependency management and plugin configuration for building
and packaging a Helidon application.

If you use your own parent POM, see the
[standalone quickstart example][standalone-quick] for a complete example.

## Dependency Management

Maven dependency management lets a project declare dependencies without
repeating their versions.

Helidon provides two BOMs:

1.  `io.helidon:helidon-bom` manages Helidon artifact versions.
2.  `io.helidon:helidon-dependencies` inherits the Helidon BOM and manages
    third-party dependency versions.

The Helidon Application POM already provides this dependency management. With
another parent, import it explicitly:

```xml [pom.xml]
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.helidon</groupId>
      <artifactId>helidon-dependencies</artifactId>
      <version>28.0.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

You can then omit versions from managed dependencies:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.config</groupId>
  <artifactId>helidon-config-yaml</artifactId>
</dependency>
```

## Declarative Applications

Helidon Declarative uses build-time annotation processing. The annotation
processors generate service descriptors required by a declarative application.
Configure them whether you use an application binding.

Add the service registry dependency and configure annotation processing:

```xml [pom.xml]
<properties>
  <helidon.version>28.0.0</helidon.version>
</properties>

<dependencies>
  <dependency>
    <groupId>io.helidon.service</groupId>
    <artifactId>helidon-service-registry</artifactId>
  </dependency>
</dependencies>

<build>
  <plugins>
    <!-- Required for Helidon Declarative service descriptors. -->
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <version>3.16.0</version>
      <configuration>
        <release>27</release>
        <annotationProcessorPaths>
          <path>
            <groupId>io.helidon.bundles</groupId>
            <artifactId>helidon-bundles-apt</artifactId>
            <version>${helidon.version}</version>
          </path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>
  </plugins>
</build>
```

An application binding is an optional build-time linker. It can hard-wire
service metadata and injection plans to avoid classpath scanning and reflection
during bootstrap. The linker runs after compilation and before packaging.

The following configuration shows annotation processing together with the
optional linker:

```xml [pom.xml]
<properties>
  <helidon.version>28.0.0</helidon.version>
</properties>

<build>
  <plugins>
    <!-- Required for Helidon Declarative service descriptors. -->
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <version>3.16.0</version>
      <configuration>
        <release>27</release>
        <annotationProcessorPaths>
          <path>
            <groupId>io.helidon.bundles</groupId>
            <artifactId>helidon-bundles-apt</artifactId>
            <version>${helidon.version}</version>
          </path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>

    <!-- Optional application linker. -->
    <plugin>
      <groupId>io.helidon.service</groupId>
      <artifactId>helidon-service-maven-plugin</artifactId>
      <version>${helidon.version}</version>
      <configuration>
        <packageName>com.example</packageName>
        <generateMain>true</generateMain>
      </configuration>
      <executions>
        <execution>
          <id>create-application</id>
          <goals>
            <goal>create-application</goal>
          </goals>
        </execution>
        <execution>
          <id>test-application-create</id>
          <goals>
            <goal>test-application-create</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

`create-application` links main services after main compilation.
`test-application-create` links main and test services after test compilation.
Both goals use their lifecycle defaults, so no phase configuration is needed.

Run the usual Maven lifecycle:

```shell [Terminal]
mvn verify
```

The example generates `com.example.ApplicationMain`. Alternatively, leave
`generateMain` at its default of `false`, annotate your own main class with
`@Service.GenerateBinding`, and start the registry with
`ApplicationBinding.create()`.

The service linker accepts these options:

| Option             | Default              | Purpose                            |
|--------------------|----------------------|------------------------------------|
| `packageName`      | Inferred             | Package for generated classes      |
| `moduleName`       | Inferred             | Application name in the binding    |
| `bindingClassName` | `ApplicationBinding` | Binding simple name                |
| `generateBinding`  | `true`               | Generate an application binding    |
| `generateMain`     | `false`              | Generate a registry main class     |
| `mainClassName`    | `ApplicationMain`    | Generated main simple name         |
| `validate`         | `true`               | Validate services during linking   |
| `failOnError`      | `true`               | Fail on linking errors             |
| `failOnWarning`    | `false`              | Fail on linking warnings           |
| `release`          | `27`                 | Java release for generated sources |
| `compilerArgs`     | None                 | Additional compiler arguments      |

The test goal defaults to `TestApplicationBinding` and never generates a main
class. Put goal-specific options inside the corresponding `execution` when
main and test linking need different values.

The Helidon Application parent already provides Java compiler settings and
dependency management. You must still add the annotation processor path and
the service linker executions you want to use.

The service linker is separate from the build-tools
`helidon-maven-plugin` described below.

## The `helidon-maven-plugin`

The Helidon Maven plugin provides the `jlink-image` goal to build a
[custom Java runtime image](jlink.md).

GraalVM Native Image is not supported.

For full documentation, see the
[Helidon Maven Plugin README][helidon-maven-pl].

If you use the Helidon Application parent POM, this plugin is already
configured. You can customize it by passing system properties, declaring
properties in the project POM, or overriding its `pluginManagement`
configuration.

### Pass Property on Command Line

```shell [Terminal]
mvn package -Pjlink-image -Djlink.image.aotCache=false
```

### Set Property in pom.xml

```xml [pom.xml]
<properties>
  <jlink.image.aotCache>false</jlink.image.aotCache>
</properties>
```

### Override Plugin Configuration using `pluginManagement`

```xml [pom.xml]
<build>
  <pluginManagement>
    <plugins>
      <plugin>
        <groupId>io.helidon.build-tools</groupId>
        <artifactId>helidon-maven-plugin</artifactId>
        <executions>
          <execution>
            <id>jlink-image</id>
            <configuration>
              <aotCache>false</aotCache>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </pluginManagement>
</build>
```

[standalone-quick]: https://github.com/helidon-io/helidon-examples/tree/helidon-27.x/examples/quickstarts/helidon-standalone-quickstart-se
[helidon-maven-pl]: https://github.com/helidon-io/helidon-build-tools/tree/master/maven-plugins/helidon-maven-plugin
