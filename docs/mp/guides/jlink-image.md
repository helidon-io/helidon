# Custom Runtime Images with `jlink`

This guide describes how to build a custom runtime image for your Helidon application using Helidon’s support for the JDK’s `jlink` tool.

## Introduction

JDK 9 introduced the [`jlink`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jlink.html) command that supports assembling a set of modules and their dependencies into a custom runtime image. The `helidon-maven-plugin` has support for easily creating a custom runtime image for your Helidon application resulting in a smaller, better performing runtime.

In this guide you will learn how to build a custom runtime image locally on your machine, as well as how to build it in a Docker image.

## What You Need

For this 10 minute tutorial, you will need the following:

|  |  |
|----|----|
| [Java SE 21](https://www.oracle.com/technetwork/java/javase/downloads) ([Open JDK 21](http://jdk.java.net)) | Helidon requires Java 21+ (25+ recommended). |
| [Maven 3.8+](https://maven.apache.org/download.cgi) | Helidon requires Maven 3.8+. |
| [Docker 18.09+](https://docs.docker.com/install/) | If you want to build and run Docker containers. |
| [Kubectl 1.16.5+](https://kubernetes.io/docs/tasks/tools/install-kubectl/) | If you want to deploy to Kubernetes, you need `kubectl` and a Kubernetes cluster (you can [install one on your desktop](../../about/kubernetes.md)). |

Prerequisite product versions for Helidon 4.4.0-SNAPSHOT

*Verify Prerequisites*

``` bash
java -version
mvn --version
docker --version
kubectl version
```

*Setting JAVA_HOME*

``` bash
# On Mac
export JAVA_HOME=`/usr/libexec/java_home -v 21`

# On Linux
# Use the appropriate path to your JDK
export JAVA_HOME=/usr/lib/jvm/jdk-21
```

## Verify JDK

As noted in the prerequisites above, Java 21 or newer is required (Java 25 or newer is recommended).

``` bash
$JAVA_HOME/bin/java --version
```

Creating a custom runtime image requires that the JDK modules are present as `*.jmod` files, and some distributions do not provide them by default. Check the `jmods` directory to ensure they are present:

``` bash
ls $JAVA_HOME/jmods
```

> [!TIP]
> [RPM based](https://en.wikipedia.org/wiki/List_of_Linux_distributions#RPM-based) distributions provide `*.jmod` files in separate `java-*-openjdk-jmods` packages. [Debian based](https://en.wikipedia.org/wiki/List_of_Linux_distributions#Debian-based) distributions provide `*.jmod` files only in the `openjdk-*-jdk-headless` packages.

## Generate the Project

Generate the project using the Helidon MP Quickstart Maven archetype.

``` bash
mvn -U archetype:generate -DinteractiveMode=false \
    -DarchetypeGroupId=io.helidon.archetypes \
    -DarchetypeArtifactId=helidon-quickstart-mp \
    -DarchetypeVersion=4.4.0-SNAPSHOT \
    -DgroupId=io.helidon.examples \
    -DartifactId=helidon-quickstart-mp \
    -Dpackage=io.helidon.examples.quickstart.mp
```

The archetype generates a Maven project in your current directory (for example, `helidon-quickstart-mp`). Change into this directory and build.

``` bash
cd helidon-quickstart-mp
mvn package
```

At this point you can run the application using the JVM:

``` bash
java -jar target/helidon-quickstart-mp.jar
```

In another shell test an endpoint:

``` bash
curl -X GET http://localhost:8080/greet
```

The application should respond with `{"message":"Hello World!"}`

Now stop the running application (by pressing Ctrl+C).

For more information about the Quickstart application and other endpoints it supports see the [Helidon MP quickstart Guide](../../mp/guides/quickstart.md).

## Building a Custom Runtime Image

You can build a custom runtime image in 2 different ways:

- Locally, on your desktop
- Using Docker

### Local Build

Build the custom runtime image using the jlink image profile:

``` bash
mvn package -Pjlink-image
```

> [!TIP]
> This uses the `helidon-maven-plugin` to perform the custom image generation.

After the build completes it will report some statistics about the build including the reduction in image size.

The `target/helidon-quickstart-mp-jri` directory is a self contained custom image of your application. It contains your application, its runtime dependencies and the JDK modules it depends on. You can start your application using the provide `start` script:

``` bash
./target/helidon-quickstart-mp-jri/bin/start
```

### Class Data Sharing (CDS) Archive and AOT Cache

If you are building with Java 24 or earlier a Class Data Sharing (CDS) archive is also included in your custom image by default. The CDS archive improves your application’s startup performance and in-memory footprint. You can learn more about Class Data Sharing in the [JDK documentation](https://docs.oracle.com/en/java/javase/21/vm/class-data-sharing.html).

If you are building with Java 25 or later an AOT Cache is created instead of a CDS archive. The AOT Cache is more advanced than the CDS archive and over time will contain more optimizations for improving application startup performance. You can learn more about the AOT Cache in the following JEPS: [JEP 483](https://openjdk.org/jeps/483), [JEP 515](https://openjdk.org/jeps/515), [JEP 514](https://openjdk.org/jeps/514)

An on-disk cache (CDS Archive or AOT Cache) increases the size of the custom image to get these performance optimizations. It can be of significant size (tens of MB). The size of the on-disk cache is reported at the end of the build output.

If you want to skip the creation of the on-disk cache you can do so by executing your build like this:

#### For Java 24 or earlier

``` bash
mvn package -Pjlink-image -Djlink.image.addClassDataSharingArchive=false
```

#### For Java 25 or later

``` bash
mvn package -Pjlink-image -Djlink.image.aotCache=false
```

For more information on available configuration options see the [`helidon-maven-plugin` documentation](https://github.com/helidon-io/helidon-build-tools/blob/4.0.25/maven-plugins/helidon-maven-plugin/README.md#goal-jlink-image).

### Multi-Stage Docker Build

To build a Docker image with a custom Java runtime image use the jlink Dockerfile included with the quickstart.

``` bash
docker build -t helidon-quickstart-mp-jri -f Dockerfile.jlink .
```

> [!TIP]
> This does a full build inside the Docker container. The first time you run it, it will take a while because it is downloading all of the Maven dependencies and caching them in a Docker layer. Subsequent builds will be much faster as long as you don’t change the `pom.xml` file. If the pom is modified then the dependencies will be re-downloaded.

Start the application:

``` bash
docker run --rm -p 8080:8080 helidon-quickstart-mp-jri:latest
```

You can exercise the application’s endpoints as before.

## Using Custom Runtime Images

Custom runtime images are ideal for use when you want all of the runtime performance of the JDK JVM in a reasonably compact form.

For cases where absolute minimal startup time and image size are required, then consider using [GraalVM Native Images](../../mp/guides/graalnative.md).
