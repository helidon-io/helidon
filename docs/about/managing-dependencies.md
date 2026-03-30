# Managing Dependencies

Helidon provides a “Bill Of Materials” (BOM) to manage dependencies. This is a special Maven pom file that provides dependency management.

Using the Helidon BOM allows you to use Helidon component dependencies with a single version: the Helidon version.

## The Helidon Application POMs

If you created your application using the [Helidon CLI](cli.md) or [archetypes](prerequisites.md) then your project will have a Helidon Application POM as its parent POM. In this case you will get Helidon’s dependency management automatically.

If your project doesn’t use a Helidon Application POM as its parent, then you will need to import the Helidon BOM POM.

## The Helidon BOM POM

To import the Helidon BOM POM add the following snippet to your pom.xml file.

*Import the Helidon BOM*

``` xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.helidon</groupId>
            <artifactId>helidon-bom</artifactId>
            <version>4.4.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Using Helidon Component Dependencies

Once you have imported the BOM, you can declare dependencies on Helidon components without specifying a version.

*Component dependency*

``` xml
<dependency>
    <groupId>io.helidon.config</groupId>
    <artifactId>helidon-config-yaml</artifactId>
</dependency>
```

## For More Information

- Maven Build Guide for [SE](../se/guides/maven-build.md) and [MP](../mp/guides/maven-build.md)
- Gradle Build Guide for [SE](../se/guides/gradle-build.md) and [MP](../mp/guides/gradle-build.md)
