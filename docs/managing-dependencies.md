# Dependency Management

Helidon provides a "Bill Of Materials" (BOM) to manage dependencies. This is a
special Maven POM file that provides dependency management.

Using the Helidon BOM allows you to use Helidon component dependencies with a
single version: the Helidon version.

## Application POMs

If you created your application using the [Helidon CLI](cli.md) or Maven
archetypes, then your project will have a Helidon Application POM as its parent
POM. In this case, you get Helidon's dependency management automatically.

If your project does not use a Helidon Application POM as its parent, then you
must import the Helidon BOM POM.

## BOM POM

To import the Helidon BOM POM, add the following snippet to your `pom.xml` file.

Import the Helidon BOM:

```xml [pom.xml]
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.helidon</groupId>
      <artifactId>helidon-bom</artifactId>
      <version>${helidon.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

## Helidon Components

After you import the BOM, you can declare dependencies on Helidon components
without specifying a version.

Component dependency:

```xml [pom.xml]
<dependency>
  <groupId>io.helidon.config</groupId>
  <artifactId>helidon-config-yaml</artifactId>
</dependency>
```

## Guides

- Maven Build Guide for [SE](se/guides/maven-build.md) and
  [MP](mp/guides/maven-build.md)
- Gradle Build Guide for [SE](se/guides/gradle-build.md) and
  [MP](mp/guides/gradle-build.md)
