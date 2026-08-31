<!--@frontmatter
description: "Using Helidon in your Gradle project"
navigation:
  icon: i-lucide-wrench
-->
# Gradle

This guide describes Helidon’s support for Gradle projects.

## Introduction

While most of Helidon’s examples use Maven, you can also use Helidon with a
Gradle project. Gradle 8.4+ is required to build Helidon projects.

## Gradle Example

The Helidon [Quickstart Example][quickstart-examp] contains a `build.gradle`
file that you can use as an example for building your Helidon application using
Gradle.

## Dependency Management

Gradle supports using a Maven POM to perform dependency management. You can use
the Helidon Dependencies POM for this purpose. Once you import the Helidon
dependency management POM you can specify dependencies without providing a
version.

```groovy [build.gradle] <!-- @icon i-vscode-icons-file-type-gradle -->
dependencies {
    // import Helidon dependency management
    implementation enforcedPlatform("io.helidon:helidon-dependencies:${project.helidonversion}")

    implementation 'io.helidon.applications:helidon-se'
    implementation 'io.helidon.http.media:helidon-http-media-jsonp'

    testCompileOnly 'org.junit.jupiter:junit-jupiter-api:'
}
```

[quickstart-examp]: https://github.com/helidon-io/helidon-examples/tree/helidon-27.x/examples/quickstarts/helidon-quickstart-se
