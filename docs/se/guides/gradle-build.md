# Gradle Guide

This guide describes Helidon’s support for Gradle projects.

## Introduction

While most of Helidon’s examples use Maven, you can also use Helidon with a Gradle project. Gradle 8.4+ is required to build Helidon 4 projects.

## Gradle Example

The Helidon [Quickstart Example](https://github.com/helidon-io/helidon-examples/tree/helidon-4.x/examples/quickstarts/helidon-quickstart-se) contains a `build.gradle` file that you can use as an example for building your Helidon application using Gradle.

## Dependency Management

Gradle supports using a Maven POM to perform dependency management. You can use the Helidon Dependencies POM for this purpose. Once you import the Helidon dependency management POM you can specify dependencies without providing a version.

*Using the Helidon Dependencies POM*

```xml
dependencies {
    // import Helidon dependency management
    implementation enforcedPlatform("io.helidon:helidon-dependencies:${project.helidonversion}")

    implementation 'io.helidon.microprofile.bundles:helidon-microprofile'
    implementation 'org.glassfish.jersey.media:jersey-media-json-binding'

    runtimeOnly 'io.smallrye:jandex'
    runtimeOnly 'jakarta.activation:jakarta.activation-api'

    testCompileOnly 'org.junit.jupiter:junit-jupiter-api:'
}
```
