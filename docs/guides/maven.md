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
3.  The `helidon-service-maven-plugin` for declarative application bindings
4.  The `helidon-maven-plugin` for custom Java runtime images

## The Helidon Application POM

Helidon examples and projects generated using the [Helidon
Quickstart](quickstart.md) use a Helidon application POM as
their parent. This parent POM provides the following:

1.  Helidon dependency management.
2.  Maven plugin configurations to help in the building and packaging of your
    Helidon application.

If you want to use your own parent POM, then take a look at the [standalone
quickstart example][standalone-quick]. This example has a standalone POM that
you can pattern your own application POM after.

## Dependency Management

In Maven, you use Dependency Management to manage the versions of the
dependencies used by your project so that you do not need to specify versions
when declaring project dependencies.

Helidon provides two POMs that are used together for dependency management:

1.  The Helidon Bill of Materials (BOM) POM (`io.helidon:helidon-bom`): manages
    the version of Helidon artifacts (to align with the Helidon version).
2.  The Helidon Dependencies POM (`io.helidon:helidon-dependencies`): manages
    the versions of third party dependencies to ensure consistency across
    Helidon and your Helidon application. Inherits the Helidon BOM POM.

When you use a Helidon Application POM as your project’s parent pom, you inherit
Helidon’s dependency management. If you have your own parent, then you can
import Helidon dependency management like this:

Import Helidon Dependency Management:

```xml [pom.xml]
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.helidon</groupId>
      <artifactId>helidon-dependencies</artifactId>
      <version>28.0.0-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

You then declare dependencies on Helidon (and other) components without
specifying a version.

Component dependency:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.config</groupId>
  <artifactId>helidon-config-yaml</artifactId>
</dependency>
```

## Declarative Applications

The `io.helidon.service:helidon-service-maven-plugin` generates application
bindings after Java compilation. It discovers service descriptors and resolves
their dependencies during the build. The resulting binding registers services
and their injection plans, avoiding discovery and dependency binding at runtime.
The [Gradle guide](gradle.md#declarative-applications) describes the equivalent
Gradle setup.

Two build stages are required. Annotation processing generates service
descriptors and a compile-time placeholder for a binding referenced by
application source code. The service Maven plugin then replaces that placeholder
with the complete application binding after compilation.

The following standalone `pom.xml` configures both stages for main and test
sources. Use Java 27 and the same Helidon 28 release for dependencies, processors
and the service plugin, replacing `28.0.0` with your selected release.

```xml [pom.xml]
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId>
  <artifactId>declarative-application</artifactId>
  <version>1.0-SNAPSHOT</version>

  <properties>
    <helidon.version>28.0.0</helidon.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.source>27</maven.compiler.source>
    <maven.compiler.release>${maven.compiler.source}</maven.compiler.release>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.helidon</groupId>
        <artifactId>helidon-dependencies</artifactId>
        <version>${helidon.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <dependency>
      <groupId>io.helidon.service</groupId>
      <artifactId>helidon-service-registry</artifactId>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.16.0</version>
        <configuration>
          <annotationProcessorPaths>
            <path>
              <groupId>io.helidon.bundles</groupId>
              <artifactId>helidon-bundles-apt</artifactId>
              <version>${helidon.version}</version>
            </path>
          </annotationProcessorPaths>
        </configuration>
      </plugin>
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
</project>
```

The compiler's `annotationProcessorPaths` configuration applies to main and test
compilation. The `create-application` goal runs in the `compile` phase after
main compilation. The `test-application-create` goal runs in `test-compile`
after test compilation and creates a `TestApplicationBinding` containing both
application and test services. No explicit phase overrides are needed. Run the
usual Maven lifecycle to execute both stages:

```shell [Terminal]
mvn verify
```

The example explicitly enables `generateMain` to generate
`com.example.ApplicationMain`, which starts the registry using
`com.example.ApplicationBinding`. Main generation defaults to `false`.
Alternatively, set `generateMain` to `false`, annotate your own main class with
`@Service.GenerateBinding`, and call
`ServiceRegistryManager.start(ApplicationBinding.create())`. Keep the binding
class name and package configured in the plugin aligned with the annotation;
with the example configuration, put the annotated main class in `com.example`
and use the annotation's default binding name, `ApplicationBinding`.

If you use `io.helidon.applications:helidon-se` as your parent POM, it already
provides Java 27 compiler settings, Maven Compiler Plugin 3.16.0 with
`proc=full`, and Helidon dependency management. You can reuse those inherited
settings when adapting this example, but you must still add the annotation
processor path and both service-plugin executions. Importing only
`helidon-dependencies` provides dependency management; it does not configure
the compiler or build plugins.

The service Maven plugin is separate from the build-tools
`helidon-maven-plugin` described below, which creates custom Java runtime
images.

## The `helidon-maven-plugin`

The Helidon Maven plugin provides the `jlink-image` goal to build a
[custom Java runtime image](jlink.md).

GraalVM Native Image is not supported.

For full documentation of the plugin please see the [Helidon Maven Plugin
README][helidon-maven-pl].

If you use the Helidon application parent POM you will have this plugin
configured for you. If you need to customize the `helidon-maven-plugin` you can
do so in a few ways:

1.  Passing system properties to Maven on the command line.
2.  Setting system properties in your project’s `pom.xml`
3.  Overriding the plugin configuration by using `pluginManagment`

### Pass Property on Command Line

You can override many of the plugin attributes by passing a system property to
the `mvn` command:

```shell [Terminal]
mvn package -Pjlink-image -Djlink.image.aotCache=false
```

### Set Property in pom.xml

Or you can set the properties in your project’s pom.xml:

```xml
<properties>
    <jlink.image.aotCache>false</jlink.image.aotCache>
</properties>
```

### Override Plugin Configuration using `pluginManagement`

For full control you can override the plugin’s configuration using
`pluginManagement`:

Turn off generation of the AOT cache when generating a custom Java runtime
image:

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
