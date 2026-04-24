# Maven Guide

This guide describes Helidon’s support for Maven projects.

## Introduction

Helidon supports Maven by providing the following:

1.  The Helidon Application parent POM
2.  Dependency management via the Helidon BOM and Dependencies POMs
3.  The `helidon-maven-plugin`

## The Helidon Application POM

Helidon examples and projects generated using the [Helidon Quickstart](../../mp/guides/quickstart.md) use a Helidon application POM as their parent. This parent POM provides the following:

1.  Helidon dependency management.
2.  Maven plugin configurations to help in the building and packaging of your Helidon application.

If you want to use your own parent POM, then take a look at the [standalone quickstart example](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/quickstarts/helidon-standalone-quickstart-mp). This example has a standalone POM that you can pattern your own application POM after.

For more details on Helidon application POMs see the [Helidon’s Application POMS](https://github.com/oracle/helidon/tree/main/docs-internal/application-pom.md)

## Dependency Management

In Maven, you use Dependency Management to manage the versions of the dependencies used by your project so that you do not need to specify versions when declaring project dependencies.

Helidon provides two POMs that are used together for dependency management:

1.  The Helidon Bill of Materials (BOM) POM (`io.helidon:helidon-bom`): manages the version of Helidon artifacts (to align with the Helidon version).
2.  The Helidon Dependencies POM (`io.helidon:helidon-dependencies`): manages the versions of third party dependencies to ensure consistency across Helidon and your Helidon application. Inherits the Helidon BOM POM.

When you use a Helidon Application POM as your project’s parent pom, you inherit Helidon’s dependency management. If you have your own parent, then you can import Helidon dependency management like this:

*Import Helidon Dependency Management*

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.helidon</groupId>
            <artifactId>helidon-dependencies</artifactId>
            <version>4.4.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

You then declare dependencies on Helidon (and other) components without specifying a version.

*Component dependency*

```xml
<dependency>
    <groupId>io.helidon.config</groupId>
    <artifactId>helidon-config-yaml</artifactId>
</dependency>
```

## The `helidon-maven-plugin`

Helidon provides a Maven plugin that, among other things, provides the following goals:

1.  jlink-image: Build a [custom runtime Java image](../../mp/guides/jlink-image.md).
2.  native-image: Build a [GraalVM native image](../../mp/guides/graalnative.md). Note: this capability is now provided via the [Maven plugin for GraalVM Native Image](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html)

For full documentation of the plugin please see the [Helidon Maven Plugin README](https://github.com/oracle/helidon-build-tools/tree/master/maven-plugins/helidon-maven-plugin).

If you use the Helidon application parent POM you will have this plugin configured for you. If you need to customize the `helidon-maven-plugin` you can do so in a few ways:

1.  Passing system properties to Maven on the command line.
2.  Setting system properties in your project’s `pom.xml`
3.  Overriding the plugin configuration by using `pluginManagment`

### Pass Property on Command Line

You can override many of the plugin attributes by passing a system property to the `mvn` command:

```bash
mvn -Djlink.image.addClassDataSharingArchive=false package
```

### Set Property in pom.xml

Or you can set the properties in your project’s pom.xml:

```xml
<properties>
    <jlink.image.addClassDataSharingArchive>false</jlink.image.addClassDataSharingArchive>
    <native.image.reportExceptionStackTraces>true</native.image.reportExceptionStackTraces>
</properties>
```

### Override Plugin Configuration using `pluginManagement`

For full control you can override the plugin’s configuration using `pluginManagement`:

*Turn off generation of the CDS Archive when generating a custom Java runtime image*

```xml
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
                          <addClassDataSharingArchive>false</addClassDataSharingArchive>
                      </configuration>
                  </execution>
              </executions>
          </plugin>
      </plugins>
  </pluginManagement>
</build>
```
