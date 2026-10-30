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
[Gradle's Java compatibility matrix][gradle-java].

## Gradle Example

The Helidon [Quickstart Example][quickstart-examp] contains a `build.gradle`
file that you can use as an example for building your Helidon application using
Gradle.

## Dependency Management

Gradle can use the Helidon Dependencies POM as an enforced platform. After you
import it, declare managed dependencies without versions.

```groovy [build.gradle]
dependencies {
    // Import Helidon dependency management
    implementation enforcedPlatform("io.helidon:helidon-dependencies:${helidonVersion}")

    // Use Helidon dependencies without specifying the version
    implementation 'io.helidon.http.media:helidon-http-media-jsonp'
    testImplementation 'org.junit.jupiter:junit-jupiter-api'
}
```

## Declarative Applications

Helidon Declarative uses build-time annotation processing. The following
minimal setup enables annotation processing:

```groovy [build.gradle]
plugins {
    id 'application'
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
    // Import Helidon dependency management
    implementation enforcedPlatform("io.helidon:helidon-dependencies:${helidonVersion}")

    // Required for Helidon Declarative service descriptors.
    annotationProcessor("io.helidon.bundles:helidon-bundles-apt:${helidonVersion}")
    testAnnotationProcessor("io.helidon.bundles:helidon-bundles-apt:${helidonVersion}")
}
```

An application binding is an optional build-time linker. It can hard-wire
service metadata and injection plans to avoid classpath scanning and reflection
during bootstrap. The linker runs after compilation and before packaging.

To enable it, add Maven Central to plugin resolution in `settings.gradle`:

```groovy [settings.gradle]
pluginManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = 'my-application'
```

Then apply the service plugin in addition to configuring annotation processing:

```groovy [build.gradle]
plugins {
    id 'application'
    // Helidon Service Gradle Plugin
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
    // Import Helidon dependency management
    implementation enforcedPlatform("io.helidon:helidon-dependencies:${helidonVersion}")
    implementation 'io.helidon.applications:helidon-se'

    // Required for Helidon Declarative service descriptors
    annotationProcessor("io.helidon.bundles:helidon-bundles-apt:${helidonVersion}")
    testAnnotationProcessor("io.helidon.bundles:helidon-bundles-apt:${helidonVersion}")
}

helidonService {
    packageName = 'com.example'
    generateMain = true
}

application {
    mainClass = 'com.example.ApplicationMain'
}
```

Alternatively, use your own main class annotated with
`@Service.GenerateBinding` and start the registry with
`ApplicationBinding.create()`. Leave `generateMain` at its default of `false`.

### Build Tasks

`generateApplicationBindings` links the compiled application before `classes`
and `jar`. Generated classes are kept in their own output directory and are
placed before compiler output, so a completed binding shadows its compile-time
placeholder. The application JAR omits the shadowed placeholder.

`generateTestApplicationBindings` runs after test compilation. It creates a
test binding containing both application and test services before `test` runs.

Run the usual Gradle tasks:

```shell
./gradlew build
./gradlew run
```

Generated sources are under `build/generated/sources/helidon/main` and
`build/generated/sources/helidon/test`. Generated classes are under
`build/classes/helidon/main` and `build/classes/helidon/test`.

### Customize Binding Tasks

`GenerateBindings` is an incubating public task type. Use `tasks.named` to
extend a task created by the plugin. Its `classpath` and `sourceRoots` are
additive file collections, so `from(...)` retains the plugin's existing inputs.

This example adds the output of another Java compiler to the default binding:

```groovy [build.gradle]
import io.helidon.service.gradle.plugin.GenerateBindings

def compileExtra = tasks.register('compileExtraJava', JavaCompile) {
    dependsOn tasks.named('compileJava')
    source = fileTree('src/extra/java')
    classpath = sourceSets.main.compileClasspath + files(
            tasks.named('compileJava', JavaCompile).flatMap {
                it.destinationDirectory
            })
    options.annotationProcessorPath = configurations.annotationProcessor
    destinationDirectory = layout.buildDirectory.dir('classes/extra/main')
}

tasks.named('generateApplicationBindings', GenerateBindings) {
    dependsOn compileExtra
    classpath.from(compileExtra.flatMap { it.destinationDirectory })
    sourceRoots.from(file('src/extra/java'))
}

// Include the additional compiler output in packaging and execution.
sourceSets.main.output.classesDirs.from(
        compileExtra.flatMap { it.destinationDirectory })
```

Use `tasks.register` for an independent binding target:

```groovy [build.gradle]
import io.helidon.service.gradle.plugin.GenerateBindings

sourceSets { other }
configurations {
    otherImplementation.extendsFrom implementation
    otherAnnotationProcessor.extendsFrom annotationProcessor
}

def compileOther = tasks.named(
        sourceSets.other.compileJavaTaskName, JavaCompile)
def generateOtherBinding = tasks.register(
        'generateOtherBinding', GenerateBindings) {
    dependsOn compileOther
    classpath.from(
            compileOther.flatMap { it.destinationDirectory },
            configurations.otherRuntimeClasspath)
    sourceRoots.from(sourceSets.other.allJava.sourceDirectories)
    options.bindingClassName = 'OtherBinding'
    prependClassesTo(sourceSets.other)
}

tasks.named(sourceSets.other.classesTaskName) {
    dependsOn generateOtherBinding
}

tasks.register('otherJar', Jar) { jar ->
    dependsOn generateOtherBinding
    archiveClassifier = 'other'
    from(sourceSets.other.output)
    generateOtherBinding.get().addExclusionsTo(jar)
}
```

`prependClassesTo(sourceSet)` puts the task's generated class directory first,
retains existing class directories, and adds those directories to the task's
inputs. `addExclusionsTo(jarTask)` makes the archive depend on generation and
omits placeholders shadowed by generated bindings.

### Configuration

The `helidonService` extension accepts these options:

| Option | Default | Purpose |
| --- | --- | --- |
| `packageName` | Inferred | Package for generated classes |
| `moduleName` | Inferred | Application name in the binding |
| `bindingClassName` | `ApplicationBinding` | Binding simple name |
| `generateBinding` | `true` | Generate an application binding |
| `generateMain` | `false` | Generate a registry main class |
| `mainClassName` | `ApplicationMain` | Generated main simple name |
| `validate` | `true` | Validate services during linking |
| `failOnError` | `true` | Fail on linking errors |
| `failOnWarning` | `false` | Fail on linking warnings |
| `generateTestBinding` | `true` | Generate a test binding |
| `testBindingClassName` | `TestApplicationBinding` | Test binding name |
| `testPackageName` | Inferred | Package for the test binding |

Keep the plugin version aligned with the application dependencies. The plugin
uses the Gradle daemon's JDK and Java compiler, so run Gradle on Java 27 or
later.

[gradle-java]: https://docs.gradle.org/current/userguide/compatibility.html
[quickstart-examp]:
  https://github.com/helidon-io/helidon-examples/tree/helidon-28.x/examples/quickstarts/helidon-quickstart-se
