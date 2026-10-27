<!--@frontmatter
description: "Using Helidon in your Gradle project"
navigation:
  icon: i-lucide-wrench
-->
# Gradle

This guide describes Helidon’s support for Gradle projects.

## Introduction

While most of Helidon’s examples use Maven, you can also use Helidon with a
Gradle project. Helidon 28 requires Java 27. Use Gradle 9.8.1 or later, which
supports Java 27 for both the Gradle runtime and Java toolchains. See
[Gradle's Java compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html).

## Gradle Example

The Helidon [Quickstart Example][quickstart-examp] contains a `build.gradle`
file that you can use as an example for building your Helidon application using
Gradle.

## Declarative Applications

The `io.helidon.service` Gradle plugin generates application bindings after
Java compilation. It discovers service descriptors and resolves their
dependencies during the build, using the same generator as
`helidon-service-maven-plugin`. The resulting binding registers the services
and their injection plans, avoiding discovery and dependency binding at
runtime.

Annotation processing is also required: it generates service descriptors and
a placeholder for a binding referenced by application source code. The Gradle
plugin replaces that placeholder with the complete application binding.

Add Maven Central to plugin resolution in `settings.gradle`:

```groovy [settings.gradle]
pluginManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = 'my-application'
```

Apply the plugin and configure annotation processing in `build.gradle`. Use
the same Helidon 28 release version for the plugin, dependencies and processors,
replacing `28.0.0` below with your selected release.

```groovy [build.gradle]
plugins {
    id 'application'
    id 'io.helidon.service' version '28.0.0'
}

def helidonVersion = '28.0.0'

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(27)
    }
}

dependencies {
    implementation enforcedPlatform("io.helidon:helidon-dependencies:${helidonVersion}")
    implementation 'io.helidon.applications:helidon-se'

    annotationProcessor "io.helidon.bundles:helidon-bundles-apt:${helidonVersion}"
    testAnnotationProcessor "io.helidon.bundles:helidon-bundles-apt:${helidonVersion}"
}

helidonService {
    packageName = 'com.example'
    generateMain = true
}

application {
    mainClass = 'com.example.ApplicationMain'
}
```

Alternatively, use your own main class annotated with `@Service.GenerateBinding`
and call `ServiceRegistryManager.start(ApplicationBinding.create())` from the
same package as the binding. Leave `generateMain` at its default of `false`.

### Build Tasks

`generateApplicationBindings` runs after `compileJava` and `processResources`.
It reads the compiler outputs and generates binding and optional main classes
in its own directory, without copying or modifying compiler output. The generated
directory comes first in the source set's class directories so completed bindings
take precedence over annotation processor placeholders. Other language compilers,
such as Groovy, can still consume Java compiler output before binding generation.
The combined class directories are used by `classes`, `run`, distributions and
test compilation. The application `jar` task excludes a raw placeholder only when
the generated binding is included at the same archive path.
`generateTestApplicationBindings` runs after test compilation and
resource processing, and generates `TestApplicationBinding` containing both
application and test services before `test` runs.

Generation calls the same typed generator API as the Maven plugin directly in
the Gradle daemon. The generator is included in the plugin's implementation
dependencies and uses the daemon's JDK and Java compiler. Run Gradle on Java 27
or later, using a JDK that can load the application's compiled classes and compile
its selected Java release. Selecting a newer application toolchain alone does
not change the JDK used for binding generation.

Run the usual Gradle tasks:

```shell
./gradlew build
./gradlew run
```

Generated sources are under `build/generated/sources/helidon/main` and
`build/generated/sources/helidon/test`. Generated classes are under
`build/classes/helidon/main` and `build/classes/helidon/test`. These directories
are task outputs; do not add the generated sources to `compileJava` or modify
the generated class directories yourself. Compilation outputs remain separate so
unchanged builds can stay up to date. Changes to application classes,
resources, runtime dependencies or generation options regenerate the binding
and remove obsolete generated output.

The plugin leaves processed resources and additional compiler outputs in their
own directories. It reads those directories when generating bindings; it does
not copy or merge them into the generated output. Archive exclusions apply only
to shadowed class entries in the application JAR; resources and other archives
retain their usual behavior. The existing compiler directories and other contributions
are retained. Other plugins and
build scripts can continue adding outputs through Gradle's source-set model.
When several compiler outputs contain resources with the same path, directory
execution preserves those resources separately. Combining them in a single
archive requires the build's usual resource-merging or duplicate-handling policy;
the Helidon plugin does not choose that policy.

Configure a Java compiler's raw output with its `destinationDirectory` property.
Configure the generated class output with `classesDirectory` on
`generateApplicationBindings` (or `generateTestApplicationBindings`). Both follow
`layout.buildDirectory` by default. Each generation task modifies only its own
generated output.

The `helidonService` extension options, the generation task names above and
their documented `classesDirectory` property form the supported build-script
configuration contract. Use `tasks.named` to customize a task created by the
plugin:

```groovy [build.gradle]
tasks.named('generateApplicationBindings') {
    classesDirectory = layout.buildDirectory.dir('custom/completed-main')
}
```

The generation task implementation type is internal. Registering additional
binding-generation tasks through that type is outside the supported
configuration contract.

### Configuration

The `helidonService` extension accepts these options. They correspond to the
Maven generator options where applicable.

| Option | Default | Purpose |
| --- | --- | --- |
| `packageName` | Inferred from sources or module exports | Package for generated application classes |
| `moduleName` | Module name, otherwise `unnamed/<package>` | Binding's application name |
| `bindingClassName` | `ApplicationBinding` | Application binding simple class name |
| `generateBinding` | `true` | Generate the application binding |
| `generateMain` | `false` | Generate a main class that starts the registry using the binding |
| `mainClassName` | `ApplicationMain` | Generated main simple class name |
| `validate` | `true` | Validate services during generation |
| `failOnError` | `true` | Fail the build on generation errors |
| `failOnWarning` | `false` | Fail the build on generation warnings |
| `generateTestBinding` | `true` | Generate a binding for application and test services |
| `testBindingClassName` | `TestApplicationBinding` | Test binding simple class name |
| `testPackageName` | Inferred from application and test sources | Package for the test binding |

Generation uses the release and arguments configured on the corresponding
Gradle `JavaCompile` task, with the Gradle daemon's compiler. Compiler options
must therefore be supported by that compiler. Keep the plugin version aligned
with the application dependencies; the generator version follows the plugin.

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

    testImplementation 'org.junit.jupiter:junit-jupiter-api'
}
```

[quickstart-examp]: https://github.com/helidon-io/helidon-examples/tree/helidon-28.x/examples/quickstarts/helidon-quickstart-se
