<!--@frontmatter
description: "Set up your environment and run a Helidon application"
navigation:
  icon: i-lucide-rocket
-->
# Get Started

## Quick Start

If you want to jump in and give Helidon a try make sure you satisfy the
[prerequisites](#system-requirements). Then:

<!--@mdc ::steps -->

### Create a project

```shell [Terminal]
helidon init --batch -Dflavor=se -Dapp-type=quickstart
```

### Build it

```shell [Terminal]
cd quickstart-se; mvn clean install
```

### Run it

```shell [Terminal]
java -jar target/quickstart-se.jar
```

### Try it

```shell [Terminal]
curl http://localhost:8080/greet
```

<!--@mdc :: -->

----------

Congratulations! 🎉 You now have a simple service up and running using Helidon.

- If you prefer not to install the Helidon CLI you can use the [Helidon
  Starter](https://helidon.io/starter).

## In More Detail

Helidon is a framework for developing microservices in Java. As such, you don’t
install Helidon itself. Instead, you install the tools necessary to create an
environment suitable for developing Helidon projects and applications.

## System Requirements

You must have Java and Maven installed on your system to use Helidon.

| Requirement                                     | Description                                  |
|-------------------------------------------------|----------------------------------------------|
| [Java 26][java-26] ([Open JDK 26][open-jdk-26]) | Helidon requires Java 26+.                   |
| [Maven 3.8+][maven-3-8]                         | Helidon requires Maven 3.8+.                 |

> [!NOTE]
> Most of Helidon's examples use Maven, but Helidon can also be used with
> [Gradle](guides/gradle.md).

Verify System Requirements:

```shell [Terminal]
java -version
mvn --version
```

## Setting `JAVA_HOME`

Make sure you set the `JAVA_HOME` environment variable.

<!--@mdc ::code-group -->

```shell [Linux] <!-- @icon i-logos-linux-tux -->
# Enter the appropriate path to your JDK
export JAVA_HOME=/usr/lib/jvm/jdk-26
```

```shell [macOS] <!-- @icon i-simple-icons-apple -->
# Enter the appropriate path to your JDK
export JAVA_HOME=`/usr/libexec/java_home -v 26`
```

```cmd [Windows] <!-- @icon i-logos-microsoft-windows-icon -->
# Enter the appropriate path to your JDK
setx JAVA_HOME=C:\\PROGRA~1\\Java\\jdk-26
```

<!--@mdc :: -->

## Next Steps

Now that your environment is set up, you can get started with Helidon. Try the
[Helidon Quick Start](guides/quickstart.md) to build your first Helidon
project and application.

> [!TIP]
> Read the [Helidon overview](modules/introduction.md) to learn more about
> the programming model.

[java-26]: https://www.oracle.com/technetwork/java/javase/downloads
[open-jdk-26]: http://jdk.java.net
[maven-3-8]: https://maven.apache.org/download.cgi
